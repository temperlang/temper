#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# ///
"""Compare two `temper` binaries' size and speed on the same machine.

    python3 measure.py --base BIN --head BIN [--base-name NAME] [--head-name NAME]
        [--title TEXT] [--base-src DIR] [--head-src DIR] [--main-ref REF]
        [--lib NAME=DIR]... [--test-lib NAME] [--repeats N] [--timeout S]

Stdlib only, so it runs on a bare runner's python3 (3.11 or later).

Measured, alternating base and head (base first on odd runs, head first on
even ones) so drift on the machine falls on both alike:

- the binary's size;
- `temper build` (default backends) of `std`, a generated one-file library
  that uses std/json, which makes temper build and emit all of std and
  temper-core, and of each `--lib` work root: wall time, CPU time (user +
  system), and peak RSS, each the median of `--repeats` runs with the range;
  and the generated output's size per backend (from the first run; the
  output is expected to be the same each run);
- `temper test -b py` of `--test-lib`, wall time, on a fresh copy each run.

Each build or test runs once with each binary first; a workload that fails
with either is skipped, with the reason under the table. Every run writes a
fresh output directory, so nothing is incremental.

Peak RSS comes from `wait4`, which on Linux also counts the image the child
had before `exec` (a sliver of this Python process), so it has a floor of
a few MiB; fine for comparing two binaries.

Base is normally the head's merge-base with what it would merge into, so
the deltas are the head's own; if the base has commits the head lacks, the
summary warns that the deltas count those too, reversed. `--main-ref` (a
ref in the head checkout, as origin/main) only adds, for information, how
far behind main the head is.

Prints a Markdown table (metric, base, head, delta) and appends it to
$GITHUB_STEP_SUMMARY when that is set.
"""

import argparse
import os
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path

STD_CONFIG = """\
# Std user

    export let name = "std-user";
    export let version = "0.0.0";
"""

# Using anything from std makes temper build and emit all of it.
STD_USES = """\
# Uses std

    let { JsonTextProducer } = import("std/json");

    export let f(): String throws Bubble {
      let p = new JsonTextProducer();
      p.stringValue("hi");
      p.toJsonString()
    }
"""


@dataclass
class Run:
    ok: bool
    wall: float
    cpu: float
    rss: int  # bytes
    log: str


@dataclass
class Row:
    metric: str
    base: list[float]
    head: list[float]
    unit: str  # "B" or "s"


@dataclass
class Report:
    rows: list[Row] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)


def run(cmd: list[str], cwd: Path, timeout: float) -> Run:
    """Run cmd, returning its wall time, CPU time, and peak RSS from wait4."""
    start = time.perf_counter()
    with tempfile.TemporaryFile() as out:
        proc = subprocess.Popen(cmd, cwd=cwd, stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        deadline = start + timeout
        while True:
            pid, status, usage = os.wait4(proc.pid, os.WNOHANG)
            if pid:
                break
            if time.perf_counter() > deadline:
                proc.kill()
                pid, status, usage = os.wait4(proc.pid, 0)
                status = -1
                break
            time.sleep(0.01)
        wall = time.perf_counter() - start
        proc.returncode = 0  # Reaped here; keeps Popen from waiting again.
        out.seek(0)
        log = out.read().decode(errors="replace")
    ok = status == 0
    if status == -1:
        log += f"\n(timed out after {timeout:.0f} s)"
    return Run(ok, wall, usage.ru_utime + usage.ru_stime, usage.ru_maxrss * 1024, log)


def tree_size(path: Path) -> int:
    return sum(p.stat().st_size for p in path.rglob("*") if p.is_file() and not p.is_symlink())


def tail(log: str, lines: int = 3) -> str:
    kept = [line for line in log.strip().splitlines() if line.strip()][-lines:]
    return " / ".join(kept) or "(no output)"


def orders(repeats: int) -> list[tuple[str, str]]:
    return [("base", "head") if i % 2 == 0 else ("head", "base") for i in range(repeats)]


def measure_build(
    report: Report, name: str, root: Path, bins: dict[str, Path], work: Path, repeats: int, timeout: float
) -> None:
    """Time `temper build` of the work root at root, and size its output."""
    runs: dict[str, list[Run]] = {"base": [], "head": []}
    outputs: dict[str, Path] = {}
    for i, order in enumerate(orders(repeats)):
        for side in order:
            out = work / f"out-{name}-{side}-{i}"
            r = run([str(bins[side]), "build", "-w", str(root), "-o", str(out)], root, timeout)
            if not r.ok:
                report.notes.append(f"`{name}` skipped: `temper build` failed with {side}: {tail(r.log)}")
                return
            runs[side].append(r)
            if i == 0:
                outputs[side] = out
            else:
                shutil.rmtree(out, ignore_errors=True)
        if i == 0:
            print(f"  {name}: builds with both", file=sys.stderr)
    add_run_rows(report, f"{name}: build", runs)
    backends = sorted({p.name for side in outputs.values() for p in side.iterdir() if p.is_dir()})
    for backend in backends:
        sizes = {s: [tree_size(o / backend)] if (o / backend).is_dir() else [0.0] for s, o in outputs.items()}
        report.rows.append(Row(f"{name}: {backend} output", sizes["base"], sizes["head"], "B"))
    for out in outputs.values():
        shutil.rmtree(out, ignore_errors=True)


def measure_test(
    report: Report, name: str, root: Path, bins: dict[str, Path], work: Path, repeats: int, timeout: float
) -> None:
    """Time `temper test -b py` on a fresh copy of the work root each run."""
    runs: dict[str, list[Run]] = {"base": [], "head": []}
    for i, order in enumerate(orders(repeats)):
        for side in order:
            copy = work / f"test-{name}-{side}-{i}"
            shutil.copytree(root, copy, ignore=shutil.ignore_patterns(".git", "temper.out"))
            r = run([str(bins[side]), "test", "-b", "py"], copy, timeout)
            shutil.rmtree(copy, ignore_errors=True)
            if not r.ok:
                report.notes.append(f"`{name}` tests skipped: `temper test -b py` failed with {side}: {tail(r.log)}")
                return
            runs[side].append(r)
    report.rows.append(Row(f"{name}: `test -b py` wall", [r.wall for r in runs["base"]], [r.wall for r in runs["head"]], "s"))


def add_run_rows(report: Report, label: str, runs: dict[str, list[Run]]) -> None:
    b, h = runs["base"], runs["head"]
    report.rows.append(Row(f"{label} wall", [r.wall for r in b], [r.wall for r in h], "s"))
    report.rows.append(Row(f"{label} CPU", [r.cpu for r in b], [r.cpu for r in h], "s"))
    report.rows.append(Row(f"{label} peak RSS", [r.rss for r in b], [r.rss for r in h], "B"))


def scale_for(value: float, unit: str) -> tuple[float, str]:
    if unit == "B":
        for name, scale in (("MiB", 1 << 20), ("KiB", 1 << 10)):
            if value >= scale:
                return scale, name
    return 1, unit


def fmt(values: list[float], unit: str) -> str:
    """The median, and the range if the values differ, in the median's unit."""
    med = statistics.median(values)
    scale, name = scale_for(med, unit)
    digits = 0 if scale == 1 and unit == "B" else 2
    text = f"{med / scale:.{digits}f} {name}"
    lo, hi = min(values), max(values)
    lo_text, hi_text = f"{lo / scale:.{digits}f}", f"{hi / scale:.{digits}f}"
    if lo_text != hi_text:
        text += f" ({lo_text}–{hi_text})"
    return text


def delta(base: list[float], head: list[float]) -> str:
    b, h = statistics.median(base), statistics.median(head)
    if b == h:
        return "0"
    if b == 0:
        return "new"
    return f"{(h - b) / b * 100:+.1f}%"


def git(src: Path | None, *args: str) -> str | None:
    if src is None:
        return None
    r = subprocess.run(["git", "-C", str(src), *args], capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None


def markdown(report: Report, args: argparse.Namespace) -> str:
    base_sha = git(args.base_src, "rev-parse", "HEAD")
    head_sha = git(args.head_src, "rev-parse", "HEAD")
    lines = [f"### {args.title or f'`{args.head_name}` against `{args.base_name}`'}", ""]
    shas = []
    if base_sha:
        shas.append(f"{args.base_name} `{base_sha[:10]}`")
    if head_sha:
        shas.append(f"{args.head_name} `{head_sha[:10]}`")
    if shas:
        lines.append(", ".join(shas) + ".")
    if base_sha and head_sha:
        behind = git(args.head_src, "rev-list", "--count", f"HEAD..{base_sha}")
        if behind and behind != "0":
            lines.append(
                f"**{args.head_name} is {behind} commit(s) behind {args.base_name}**, so the deltas"
                f" also count {args.base_name}'s changes since, reversed."
            )
        elif behind is None:
            lines.append(f"(Couldn't tell whether {args.head_name} is behind {args.base_name}.)")
    if args.main_ref and head_sha:
        behind_main = git(args.head_src, "rev-list", "--count", f"HEAD..{args.main_ref}")
        if behind_main is not None:
            lines.append(f"For information, {args.head_name} is {behind_main} commit(s) behind `{args.main_ref}`.")
    lines.append(
        f"Times: median of {args.repeats} runs (range), alternating which binary runs first."
        " Δ is head's median against base's."
    )
    lines += ["", f"| Metric | {args.base_name} | {args.head_name} | Δ |", "|---|--:|--:|--:|"]
    for row in report.rows:
        lines.append(f"| {row.metric} | {fmt(row.base, row.unit)} | {fmt(row.head, row.unit)} | {delta(row.base, row.head)} |")
    if report.notes:
        lines.append("")
        lines += [f"- {note}" for note in report.notes]
    return "\n".join(lines) + "\n"


def parse_lib(spec: str) -> tuple[str, Path]:
    name, sep, path = spec.partition("=")
    if not sep or not name or not path:
        raise argparse.ArgumentTypeError(f"expected NAME=DIR, got {spec!r}")
    return name, Path(path).resolve()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", type=Path, required=True, help="base temper binary")
    parser.add_argument("--head", type=Path, required=True, help="head temper binary")
    parser.add_argument("--base-name", default="base")
    parser.add_argument("--head-name", default="head")
    parser.add_argument("--base-src", type=Path, help="base checkout, for its commit")
    parser.add_argument("--head-src", type=Path, help="head checkout, for its commit and how far behind it is")
    parser.add_argument("--title", help="the summary's heading (default: HEAD-NAME against BASE-NAME)")
    parser.add_argument("--main-ref", metavar="REF", help="a ref in --head-src to say how far behind it head is")
    parser.add_argument("--lib", type=parse_lib, action="append", default=[], metavar="NAME=DIR",
                        help="a work root to build (may repeat)")
    parser.add_argument("--test-lib", metavar="NAME", help="a --lib whose `temper test -b py` to time")
    parser.add_argument("--no-std", action="store_true", help="skip the std build")
    parser.add_argument("--repeats", type=int, default=5)
    parser.add_argument("--timeout", type=float, default=900, help="seconds per command")
    args = parser.parse_args()
    bins = {"base": args.base.resolve(), "head": args.head.resolve()}
    libs = dict(args.lib)
    for name, root in libs.items():
        if not root.is_dir():
            parser.error(f"--lib {name}: no directory {root}")
    if args.test_lib and args.test_lib not in libs:
        parser.error(f"--test-lib {args.test_lib} isn't a --lib")

    report = Report()
    report.rows.append(Row("temper binary size", [bins["base"].stat().st_size], [bins["head"].stat().st_size], "B"))
    with tempfile.TemporaryDirectory(prefix="pr-stats-") as tmp:
        work = Path(tmp)
        if not args.no_std:
            std = work / "std-user"
            std.mkdir()
            (std / "config.temper.md").write_text(STD_CONFIG)
            (std / "uses.temper.md").write_text(STD_USES)
            print("measuring std", file=sys.stderr)
            measure_build(report, "std", std, bins, work, args.repeats, args.timeout)
        for name, root in libs.items():
            print(f"measuring {name}", file=sys.stderr)
            measure_build(report, name, root, bins, work, args.repeats, args.timeout)
        if args.test_lib:
            print(f"testing {args.test_lib}", file=sys.stderr)
            measure_test(report, args.test_lib, libs[args.test_lib], bins, work, args.repeats, args.timeout)

    text = markdown(report, args)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(text + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
