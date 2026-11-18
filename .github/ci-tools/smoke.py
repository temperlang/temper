# /// script
# requires-python = ">=3.11"
# ///
"""Smoke-tests a packaged `temper`, the archive users download.

Unpacks the archive (`temper-TARGET.tar.gz`, or `.zip` on Windows),
checks it holds the binary and the license files, and runs it as a
user would, with no language toolchains: `version`, `init` into an
empty directory, `test` and `run` on the interpreter, and the REPL,
with piped input and, where there's a pseudo-terminal (Linux and
macOS), as an interactive session with its line editor.

    uv run .github/ci-tools/smoke.py temper-TARGET.tar.gz
    uv run .github/ci-tools/smoke.py path/to/temper   # a binary as it is
"""

import os
import re
import subprocess
import sys
import tarfile
import tempfile
import time
import zipfile
from pathlib import Path

TIMEOUT = 60
ANSI = re.compile(rb"\x1b\[[0-9;?]*[A-Za-z]")
LIBRARY = """
    export let triple(n: Int): Int { n * 3 }
    console.log("hello ${triple(14).toString()}");
    test("triple") { assert(triple(2) == 6) }
"""


def run(temper: Path, *args: str, cwd: Path | None = None, stdin: str | None = None) -> str:
    print(f"+ temper {' '.join(args)}", flush=True)
    done = subprocess.run([temper, *args], cwd=cwd, input=stdin, capture_output=True, text=True, encoding="utf-8",
                          timeout=TIMEOUT)
    out = done.stdout.replace("\r\n", "\n")
    print(out + done.stderr, end="", flush=True)
    if done.returncode != 0:
        sys.exit(f"smoke: temper {' '.join(args)} exited {done.returncode}")
    return out


def expect(what: str, text: str, want: str) -> None:
    if want not in text:
        sys.exit(f"smoke: {what}: expected {want!r} in {text!r}")


def interactive_repl(temper: Path) -> None:
    """A REPL session on a pseudo-terminal: two inputs, then Ctrl-D.

    Answers the line editor's cursor-position queries, as a terminal
    would, and on any timeout kills the REPL and shows what it wrote,
    rather than hang.
    """
    import pty
    import select
    import signal

    print("+ temper repl (on a terminal)", flush=True)
    pid, fd = pty.fork()
    if pid == 0:
        os.execve(temper, [str(temper), "repl"], {**os.environ, "TERM": "xterm-256color"})
    seen = b""

    def fail(why: str) -> None:
        os.kill(pid, signal.SIGKILL)
        os.waitpid(pid, 0)
        sys.exit(f"smoke: interactive REPL: {why}; it wrote {seen!r}")

    def read_until(want: bytes) -> None:
        nonlocal seen
        end = time.monotonic() + TIMEOUT
        while time.monotonic() < end:
            if select.select([fd], [], [], 0.2)[0]:
                try:
                    chunk = os.read(fd, 4096)
                except OSError:
                    break
                if not chunk:
                    break
                seen += chunk
                # A cursor-position query (DSR): answer row 1, column 1.
                for _ in range(chunk.count(b"\x1b[6n")):
                    os.write(fd, b"\x1b[1;1R")
                if want in ANSI.sub(b"", seen):
                    return
        fail(f"expected {want!r}")

    read_until(b"$ ")
    os.write(fd, b"let x = 20;\r")
    read_until(b"interactive#0: void")
    os.write(fd, b"x + 22\r")
    read_until(b"interactive#1: 42")
    os.write(fd, b"\x04")
    end = time.monotonic() + TIMEOUT
    while time.monotonic() < end:
        done, status = os.waitpid(pid, os.WNOHANG)
        if done:
            break
        # Keep reading, so the REPL never blocks writing to a full pty.
        if select.select([fd], [], [], 0.2)[0]:
            try:
                seen += os.read(fd, 4096)
            except OSError:
                pass
    else:
        fail("still running after Ctrl-D")
    if os.waitstatus_to_exitcode(status) != 0:
        sys.exit(f"smoke: interactive REPL exited {os.waitstatus_to_exitcode(status)}")
    print("interactive REPL: ok", flush=True)


def unpack(archive: Path, into: Path) -> Path:
    """The binary in an archive, after checking what the archive holds."""
    if archive.name.endswith(".zip"):
        with zipfile.ZipFile(archive) as z:
            z.extractall(into)
    else:
        with tarfile.open(archive) as t:
            t.extractall(into, filter="data")
    (top,) = into.iterdir()
    exe = "temper.exe" if sys.platform == "win32" else "temper"
    have = sorted(p.name for p in top.iterdir())
    want = sorted([exe, "COPYRIGHT", "LICENSE-APACHE", "LICENSE-MIT", "NOTICE"])
    if have != want:
        sys.exit(f"smoke: {archive.name} holds {have}, not {want}")
    temper = top / exe
    if sys.platform != "win32" and not os.access(temper, os.X_OK):
        sys.exit(f"smoke: {exe} in {archive.name} isn't executable")
    return temper


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    given = Path(sys.argv[1]).resolve()
    with tempfile.TemporaryDirectory() as unpacked:
        if given.name.endswith((".tar.gz", ".zip")):
            smoke(unpack(given, Path(unpacked)))
        else:
            smoke(given)
    print("smoke: ok")


def smoke(temper: Path) -> None:
    expect("version", run(temper, "version"), "temper ")
    with tempfile.TemporaryDirectory() as tmp:
        project = Path(tmp) / "smoke"
        run(temper, "init", "-o", str(project))
        module = project / "src" / "smoke.temper.md"
        module.write_text(module.read_text(encoding="utf-8") + LIBRARY, encoding="utf-8")
        expect("test", run(temper, "test", "-b", "interp", cwd=project), "Tests passed: 1 of 1")
        expect("run", run(temper, "run", "--library", "smoke", "-b", "interp", cwd=project), "hello 42")
        piped = run(temper, "repl", cwd=project, stdin="let x = 20;\nx + 22\n")
        expect("piped REPL", piped, "interactive#1: 42")
    if sys.platform != "win32":
        interactive_repl(temper)


if __name__ == "__main__":
    main()
