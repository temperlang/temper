# /// script
# requires-python = ">=3.11"
# ///
"""Runs Kotlin Temper's functional tests on our settle loop and interpreter.

Each test is a directory under functional-test-suite/src/commonMain/resources
whose main file is named after it (`fibonacci/fibonacci.temper.md`). The
directory's own .temper and .temper.md files are built as one library with
crates/temper-frontend's `run-temper`, and what it logs is compared with the
expected output: the main file's ```log blocks that apply to Kotlin's
`interp` run (a block may name runs, or exclude them with `~`), joined, as Kotlin's
MarkdownFileBasedFunctionalTestBase reads them. A test without any passes
only if it builds without errors and runs without failing, or, if its
metadata says `expectRunFailure = true`, fails. A test whose metadata says
`runAsTest = true` instead runs its `test` cases, and passes if the ones
that fail are those its `expectedFailures` names, each failure's message
containing the expected text, as Kotlin's `assertTestingTestFromJunit`
checks. Doesn't run Kotlin Temper. The tests and `std` come from this
repository's crates/temper-funtests/cases/ and lib/std/, or, with --temper,
from a Kotlin Temper checkout. A local web server answers as Kotlin's
`TestWebServer` does, at the port each test gets as `testServerPort`.

    uv run compat/check_functional.py [--verbose] [--temper DIR] [TEST ...]

TEST filters by path, as `algos/fibonacci` or `fibonacci`. --verbose shows
diagnostics and a diff for failing tests. A test in KNOWN_GAPS still runs,
but failing it doesn't fail the check, while passing it does, so the list
stays current.
"""

import argparse
import difflib
import re
import subprocess
import sys
import threading
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# This repository's by default; --temper points both at a checkout instead.
SUITE = ROOT / "crates" / "temper-funtests" / "cases"
STD = ROOT / "lib" / "std"
# A log block may name the runs it applies to, ```log interp, or exclude
# them, ```log ~interp; ours is Kotlin's `interp` run.
# Tests we don't pass yet, and why: each is also skipped for Kotlin's
# `interp` run in its FunctionalTestStatus.kt, or is listed in
# notes/testing-and-milestones.md.
KNOWN_GAPS: dict[str, str] = {}
START = re.compile(r"^[ \t]*```log((?:[ \t]+~?[A-Za-z0-9_-]+)*)[ \t]*$")
RUN_NAMES = {"interp"}
END = re.compile(r"^[ \t]*```[ \t]*$")
# `"name" to """message"""` or `"name" to "message"` in `expectedFailures`.
FAILURE = re.compile(r'"((?:[^"\\]|\\.)*)"\s+to\s+(?:"""(.*?)"""|"((?:[^"\\]|\\.)*)")', re.S)


def applies(names: str) -> bool:
    """Whether a log block naming `names` applies to our run, as Kotlin's
    `logBlockApplies` decides."""
    words = names.split()
    includes = [w for w in words if not w.startswith("~")]
    excludes = [w[1:] for w in words if w.startswith("~")]
    return (not includes or any(w in RUN_NAMES for w in includes)) and not any(w in RUN_NAMES for w in excludes)


def expected_output(main: Path) -> list[str]:
    """The lines in the main file's ```log blocks that apply to our run."""
    lines, inside, keep = [], False, True
    for line in main.read_text("utf-8").splitlines():
        if inside:
            if END.match(line):
                inside = False
            elif keep:
                lines.append(line)
        elif start := START.match(line):
            inside, keep = True, applies(start[1])
    return lines


def expected_failures(main_text: str) -> dict[str, str]:
    """The `expectedFailures` metadata: each failing test's name and part
    of its message."""
    meta = [line.split("@meta:arg", 1)[1].strip() for line in main_text.splitlines() if "@meta:arg" in line]
    start = next((i for i, line in enumerate(meta) if line.startswith("expectedFailures")), None)
    if start is None:
        return {}
    end = next((i for i in range(start, len(meta)) if meta[i] in (")", "),")), len(meta))
    entries = FAILURE.finditer("\n".join(meta[start : end + 1]))
    return {m[1]: m[2] if m[2] is not None else m[3] for m in entries}


def test_results_ok(stdout: list[str], expected: dict[str, str]) -> bool:
    """Whether the JUnit reports in a test run's output fail as expected."""
    reports = re.findall(r"<testsuites>.*?</testsuites>", "\n".join(stdout), re.S)
    failures = []
    for report in reports:
        for case in ET.fromstring(report).iter("testcase"):
            failures += [(case.get("name", ""), f.get("message", "")) for f in case.iter("failure")]
    if not reports:
        return False
    names = sorted(name.lower() for name, _ in failures)
    if names != sorted(name.lower() for name in expected):
        return False
    return all(any(cause in message for _, message in failures) for cause in expected.values())


class TestWebHandler(BaseHTTPRequestHandler):
    """As Kotlin Temper's `TestWebServer`: `{"method": "POST", "json":
    true}`, say, as JSON for any path, saying whether the request's
    Content-Type was JSON, but 404 and "not here" as text for paths
    starting `/missing`."""

    protocol_version = "HTTP/1.1"

    def answer(self) -> None:
        self.rfile.read(int(self.headers.get("Content-Length") or 0))
        if self.path.startswith("/missing"):
            status, kind, body = 404, "text/plain", b"not here"
        else:
            # Only the media type counts, as a client may add a charset.
            json = (self.headers.get("Content-Type") or "").split(";")[0].strip() == "application/json"
            body = f'{{"method": "{self.command}", "json": {"true" if json else "false"}}}'.encode()
            status, kind = 200, "application/json"
        self.send_response(status)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = answer

    def log_message(self, format, *args) -> None:
        pass


def start_test_server() -> ThreadingHTTPServer:
    server = ThreadingHTTPServer(("127.0.0.1", 0), TestWebHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def tests() -> list[tuple[str, Path]]:
    """Each test's name, as `algos/fibonacci`, and its directory."""
    found = []
    for main in sorted(SUITE.rglob("*.temper.md")):
        if main.name == f"{main.parent.name}.temper.md":
            found.append((str(main.parent.relative_to(SUITE)), main.parent))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("tests", nargs="*")
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--temper", type=Path, help="use this Temper checkout's tests and std")
    args = parser.parse_args()
    if args.temper:
        global SUITE, STD
        SUITE = args.temper / "functional-test-suite/src/commonMain/resources"
        STD = args.temper / "frontend/src/commonMain/resources/std"

    subprocess.run(["cargo", "build", "-q", "--locked", "--release", "-p", "temper-frontend"], cwd=ROOT, check=True)
    runner = ROOT / "target/release/run-temper"
    server = start_test_server()
    port = ["--test-server-port", str(server.server_address[1])]
    passed, failed, gaps, stale = [], [], [], []
    for name, directory in tests():
        if args.tests and not any(t in name for t in args.tests):
            continue
        files = sorted(str(p) for p in directory.iterdir() if p.name.endswith((".temper", ".temper.md")))
        expected = expected_output(directory / f"{directory.name}.temper.md")
        main_text = (directory / f"{directory.name}.temper.md").read_text("utf-8")
        expect_failure = "expectRunFailure = true" in main_text
        as_test = "runAsTest = true" in main_text
        test_flag = ["--test"] if as_test else []
        try:
            result = subprocess.run([str(runner), *test_flag, *port, "--std", str(STD), *files], capture_output=True, timeout=60)
            actual = result.stdout.decode("utf-8", "replace").splitlines()
            errors = result.stderr.decode("utf-8", "replace")
            clean = result.returncode == 0 and not any(line.startswith("error ") for line in errors.splitlines())
        except subprocess.TimeoutExpired:
            actual, errors, clean = [], "timed out", False
        if as_test:
            ok = clean and test_results_ok(actual, expected_failures(main_text))
        else:
            ok = actual == expected and (expected or clean != expect_failure)
        if ok and name in KNOWN_GAPS:
            stale.append(name)
            continue
        if ok:
            passed.append(name)
            continue
        if name in KNOWN_GAPS:
            gaps.append(name)
            continue
        failed.append(name)
        if args.verbose:
            print(f"--- {name}")
            print(errors.rstrip())
            for line in difflib.unified_diff(expected, actual, "expected", "actual", lineterm="", n=1):
                print(line)
    server.shutdown()
    total = len(passed) + len(failed) + len(gaps) + len(stale)
    print(f"{len(passed) + len(stale)} of {total} functional tests pass:")
    for name in passed:
        print(f"  {name}")
    if gaps:
        print(f"{len(gaps)} known {'gap' if len(gaps) == 1 else 'gaps'} (KNOWN_GAPS):")
        for name in gaps:
            print(f"  {name}: {KNOWN_GAPS[name]}")
    if stale:
        print(f"{len(stale)} in KNOWN_GAPS now pass; take them out:")
        for name in stale:
            print(f"  {name}")
    if failed:
        print(f"{len(failed)} failed:")
        for name in failed:
            print(f"  {name}")
    # Nonzero on any failure or stale gap, so CI notices.
    return 1 if failed or stale else 0


if __name__ == "__main__":
    sys.exit(main())
