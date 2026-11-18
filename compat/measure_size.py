# /// script
# requires-python = ">=3.11"
# ///
"""Measures tempest binary size and peak memory, with Kotlin for reference.

Sizes are for the `small` profile (size-optimized, LTO, stripped) and the
plain release profile, plus `temper-wasm` built for wasm32 (`small-wasm`, the
same but aborting on panic), raw and gzipped. Peak memory is the maximum resident set size of one
`--bench 1` run over all Temper files (read everything, then lex and parse it
all once, and also build grammar trees for it all, and also settle each
file as a library), for our binaries and for Kotlin's oracles on the JVM.

    uv run compat/measure_size.py [--record] [--shares] [DIR ...]

`--record` also appends the results as rows to notes/size-and-speed.csv,
with today's date, the commit, and `tree=dirty` if the work tree has
changes, and redraws the charts (size_speed_charts.py). Explain them in
notes/size-and-speed.md only where rows can't.
`--shares` also measures each backend's share of the small `tempest` and
of wasm: the build with every backend less the build without that one
(`backend-<name>-small`, `backend-<name>-wasm`), and what any one brings
beyond its own share (`backends-shared-small`, `backends-shared-wasm`:
the build with only Python less the build with none, less Python's
share), with `tempest-small-no-backends` and `wasm-small-no-backends`.

Peak memory is the measured binary's own: its `VmHWM` read at its exit,
under ptrace, after its `exec`. Linux's `ru_maxrss` from `wait4` also
counts the image the child had before `exec`, here a copy of this Python
process, which set a floor of this process's own size on every reading
(the 20.4 MiB rows of 2026-11-03).
"""

import argparse
import ctypes
import csv
import datetime
import gzip
import os
import signal
import subprocess
import sys
from pathlib import Path

from check_lex import DEFAULT_DIRS, ROOT, temper_files
from check_ast import commands as ast_commands
from check_parse import commands
import size_speed_charts

BINARIES = ["lex-tokens", "parse-tree", "ast-tree", "run-temper", "tempest"]
# Binaries built with temper-frontend's default features, which include `net`.
NET = {"run-temper", "tempest"}
# The `tempest` crate's binary is `temper`; its rows keep the crate's
# name, as the earlier rows have it.
FILES = {"tempest": "temper"}
CSV = ROOT / "notes/size-and-speed.csv"
BACKENDS = ["py", "rust", "js", "java", "csharp", "cpp", "lua"]
WASM = ROOT / "target/wasm32-unknown-unknown/small-wasm/temper_wasm.wasm"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("dirs", nargs="*", type=Path, default=DEFAULT_DIRS)
    parser.add_argument("--record", action="store_true", help="append results to notes/size-and-speed.csv")
    parser.add_argument("--shares", action="store_true", help="also measure each backend's share")
    args = parser.parse_args()
    rows: list[tuple[str, str, str, str]] = []  # what, value, unit, conditions
    if args.shares:
        rows += shares()

    kotlin, _ = commands()  # Also builds the release binaries.
    kotlin_ast, _ = ast_commands()
    subprocess.run(["cargo", "build", "-q", "--release", "-p", "temper-frontend", "-p", "tempest"], cwd=ROOT, check=True)
    subprocess.run(
        ["cargo", "build", "-q", "--profile", "small", "-p", "temper-syntax", "-p", "temper-frontend", "-p", "tempest"],
        cwd=ROOT,
        check=True,
    )
    for name in BINARIES:
        small = (ROOT / "target/small" / FILES.get(name, name)).stat().st_size
        release = (ROOT / "target/release" / FILES.get(name, name)).stat().st_size
        print(f"{name}: {kib(small)} small, {kib(release)} release")
        features = "features=net" if name in NET else ""
        rows += [(f"{name}-small", str(small), "bytes", features), (f"{name}-release", str(release), "bytes", features)]

    subprocess.run(
        ["cargo", "build", "-q", "--profile", "small-wasm", "--target", "wasm32-unknown-unknown", "-p", "temper-wasm"],
        cwd=ROOT,
        check=True,
    )
    wasm = WASM.read_bytes()
    gzipped = len(gzip.compress(wasm, 9))
    print(f"temper-wasm: {kib(len(wasm))}, {kib(gzipped)} gzipped")
    rows += [("wasm-small", str(len(wasm)), "bytes", ""), ("wasm-small-gzip", str(gzipped), "bytes", "")]

    files = temper_files(args.dirs)
    total = sum(os.path.getsize(f) for f in files)
    print(f"{len(files)} files, {kib(total)}")
    count = f"files={len(files)}"
    for what, metric, printer, oracle in (
        ("lex and parse", "peak-memory-parse-all", "parse-tree", kotlin),
        ("lex, parse and build trees for", "peak-memory-ast-all", "ast-tree", kotlin_ast),
    ):
        rust = peak_kib([str(ROOT / "target/small" / printer), "--bench", "1", *files])
        jvm = peak_kib(oracle + ["--bench", "1", *files])
        print(f"peak memory, {what} all: rust {rust / 1024:.1f} MiB, kotlin (JVM) {jvm / 1024:.1f} MiB")
        rows += [(metric, mib(rust), "MiB", f"run=rust;{count}"), (metric, mib(jvm), "MiB", f"run=kotlin-jvm;{count}")]
    rust = peak_kib([str(ROOT / "target/small/run-temper"), "--bench", "1", *files])
    print(f"peak memory, lex, parse, build trees for and settle all: rust {rust / 1024:.1f} MiB")
    rows.append(("peak-memory-settle-all", mib(rust), "MiB", f"run=rust;{count}"))
    if args.record:
        record(rows)
    return 0


def record(rows: list[tuple[str, str, str, str]]) -> None:
    """Appends rows to the CSV, marking them `tree=dirty` if there are changes."""
    git = ["git", "-C", str(ROOT)]
    commit = subprocess.run([*git, "rev-parse", "--short", "HEAD"], capture_output=True, text=True, check=True)
    status = subprocess.run([*git, "status", "--porcelain"], capture_output=True, text=True, check=True)
    tree = "tree=dirty" if status.stdout.strip() else ""
    date = datetime.date.today().isoformat()
    with CSV.open("a", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        for what, value, unit, conditions in rows:
            conditions = ";".join(c for c in (conditions, tree) if c)
            writer.writerow([date, commit.stdout.strip(), what, value, "", unit, conditions, "measure_size.py"])
    size_speed_charts.render()
    print(f"recorded {len(rows)} rows in {CSV.name}")


def small_sizes(features: list[str]) -> tuple[int, int, int]:
    """The small `tempest` (with `net`) and wasm, raw and gzipped, built
    with only these backends."""
    subprocess.run(
        ["cargo", "build", "-q", "--profile", "small", "-p", "tempest", "--no-default-features",
         "--features", ",".join(["net", *features])],
        cwd=ROOT,
        check=True,
    )
    native = (ROOT / "target/small/temper").stat().st_size
    command = ["cargo", "build", "-q", "--profile", "small-wasm", "--target", "wasm32-unknown-unknown",
               "-p", "temper-wasm", "--no-default-features"]
    if features:
        command += ["--features", ",".join(features)]
    subprocess.run(command, cwd=ROOT, check=True)
    wasm = WASM.read_bytes()
    return native, len(wasm), len(gzip.compress(wasm, 9))


def shares() -> list[tuple[str, str, str, str]]:
    """Each backend's share of the small `tempest` and of wasm, and what
    they share (see the module's doc)."""
    rows: list[tuple[str, str, str, str]] = []
    every = small_sizes(BACKENDS)
    none = small_sizes([])
    rows += [("tempest-small-no-backends", str(none[0]), "bytes", "features=net"),
             ("wasm-small-no-backends", str(none[1]), "bytes", "")]
    share = {}
    for backend in BACKENDS:
        without = small_sizes([b for b in BACKENDS if b != backend])
        share[backend] = [a - b for a, b in zip(every, without)]
        print(f"{backend}: {kib(share[backend][0])} small, {kib(share[backend][1])} wasm "
              f"({kib(share[backend][2])} gzipped)")
        rows += [(f"backend-{backend}-small", str(share[backend][0]), "bytes", "features=net"),
                 (f"backend-{backend}-wasm", str(share[backend][1]), "bytes", "")]
    only_py = small_sizes(["py"])
    shared = [o - n - s for o, n, s in zip(only_py, none, share["py"])]
    print(f"shared: {kib(shared[0])} small, {kib(shared[1])} wasm ({kib(shared[2])} gzipped)")
    rows += [("backends-shared-small", str(shared[0]), "bytes", "features=net"),
             ("backends-shared-wasm", str(shared[1]), "bytes", "")]
    # Leave the default builds in place.
    small_sizes(BACKENDS)
    return rows


_libc = ctypes.CDLL(None, use_errno=True)
_libc.ptrace.argtypes = [ctypes.c_long, ctypes.c_long, ctypes.c_void_p, ctypes.c_void_p]
PTRACE_TRACEME, PTRACE_CONT, PTRACE_SETOPTIONS = 0, 7, 0x4200
PTRACE_O_TRACEEXIT, PTRACE_EVENT_EXIT = 0x40, 6


def peak_kib(command: list[str]) -> int:
    """Runs a command and returns its own peak resident set size in KiB:
    its `VmHWM` when it exits, read while ptrace holds it there, which
    counts only what it used after its `exec` (see the module's doc)."""
    pid = os.fork()
    if pid == 0:
        try:
            _libc.ptrace(PTRACE_TRACEME, 0, None, None)
            devnull = os.open(os.devnull, os.O_WRONLY)
            os.dup2(devnull, 1)
            os.execvp(command[0], command)
        finally:
            os._exit(127)
    _, status = os.waitpid(pid, 0)  # Stopped at its exec.
    _libc.ptrace(PTRACE_SETOPTIONS, pid, None, ctypes.c_void_p(PTRACE_O_TRACEEXIT))
    _libc.ptrace(PTRACE_CONT, pid, None, None)
    peak = 0
    while True:
        _, status = os.waitpid(pid, 0)
        if os.WIFEXITED(status) or os.WIFSIGNALED(status):
            break
        sig = os.WSTOPSIG(status)
        if status >> 16 == PTRACE_EVENT_EXIT:
            with open(f"/proc/{pid}/status") as f:
                for line in f:
                    if line.startswith("VmHWM:"):
                        peak = int(line.split()[1])
            sig = 0
        elif sig == signal.SIGTRAP:
            sig = 0  # A later exec's stop.
        _libc.ptrace(PTRACE_CONT, pid, None, ctypes.c_void_p(sig))
    code = os.waitstatus_to_exitcode(status)
    if code != 0:
        raise subprocess.CalledProcessError(code, command)
    return peak


def kib(n: int) -> str:
    return f"{n / 1024:.0f} KiB"


def mib(n_kib: int) -> str:
    return f"{n_kib / 1024:.1f}"


if __name__ == "__main__":
    sys.exit(main())
