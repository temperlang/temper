# /// script
# requires-python = ">=3.11"
# ///
"""Counts the functional tests that translate cleanly to tmpl, and why the
rest don't.

Builds each of Kotlin Temper's functional tests as check_functional.py
builds it and runs `run-temper --tmpl` on it, which translates the library
to tmpl and validates it. Each test is either ok (translated, with no
validator problems), not yet (it uses something tmpl's translation doesn't
cover yet, or has errors), or invalid (translated, but the validator found
problems: a bug to fix). Lists the ok tests, the invalid ones with their
problems, and the most common reasons for not yet. The tests and std come
from this repository's crates/temper-funtests/cases/ and lib/std/, or, with
--temper, from a Kotlin Temper checkout.

    uv run compat/count_tmpl.py [--verbose] [--temper DIR]

--verbose lists each test with its status and where it stopped.
"""

import argparse
import collections
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SUITE = ROOT / "crates" / "temper-funtests" / "cases"
STD = ROOT / "lib" / "std"
NOT_YET = re.compile(r"^tmpl: not yet in tmpl: (.*?)(?: at (\S+))?$")


def sources(directory: Path) -> list[str]:
    """A directory's own Temper files, the main one first, as it names the
    library."""
    files = sorted(str(p) for p in directory.iterdir() if p.name.endswith((".temper", ".temper.md")))
    main = str(directory / f"{directory.name}.temper.md")
    return [main] + [f for f in files if f != main]


def status(runner: Path, files: list[str]) -> tuple[str, str, list[str]]:
    """A test's status (ok, not yet, or invalid), its reason or where, and
    the validator's problems."""
    # `testServerPort`, which std `net`'s tests read, at the testkit's port;
    # translating sends nothing.
    port = ["--test-server-port", "28461"]
    result = subprocess.run([str(runner), "--tmpl", "--std", str(STD), *port, *files], capture_output=True, timeout=120)
    lines = result.stderr.decode("utf-8", "replace").splitlines()
    for i, line in enumerate(lines):
        if line == "tmpl: ok":
            return "ok", "", []
        if m := NOT_YET.match(line):
            return "not yet", m[1], [m[2] or ""]
        if line.startswith("tmpl: invalid"):
            return "invalid", line[len("tmpl: "):], [p.strip() for p in lines[i + 1:]]
    return "not yet", "run-temper failed", lines[-3:]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--temper", type=Path, help="use this Temper checkout's tests and std")
    args = parser.parse_args()
    if args.temper:
        global SUITE, STD
        SUITE = args.temper / "functional-test-suite/src/commonMain/resources"
        STD = args.temper / "frontend/src/commonMain/resources/std"

    subprocess.run(["cargo", "build", "-q", "--locked", "--release", "-p", "temper-frontend"], cwd=ROOT, check=True)
    runner = ROOT / "target/release/run-temper"

    results = []
    for main in sorted(SUITE.rglob("*.temper.md")):
        if main.name == f"{main.parent.name}.temper.md":
            name = str(main.parent.relative_to(SUITE))
            results.append((name, *status(runner, sources(main.parent))))

    counts = collections.Counter(s for _, s, _, _ in results)
    print(f"{len(results)} functional tests: {counts['ok']} ok, {counts['not yet']} not yet, "
          f"{counts['invalid']} invalid")
    if args.verbose:
        for name, s, why, extra in results:
            where = f" at {extra[0]}" if s == "not yet" and extra and extra[0] else ""
            print(f"  {s:8} {name}{': ' + why if why else ''}{where}")
    print("\nok:")
    for name, s, _, _ in results:
        if s == "ok":
            print(f"  {name}")
    invalid = [(name, why, problems) for name, s, why, problems in results if s == "invalid"]
    if invalid:
        print("\ninvalid:")
        for name, why, problems in invalid:
            print(f"  {name}: {why}")
            for p in problems:
                print(f"    {p}")
    reasons = collections.Counter(why for _, s, why, _ in results if s == "not yet")
    print("\nnot yet, by what each test first needs:")
    for why, n in reasons.most_common():
        print(f"  {n:4} {why}")
    return 1 if invalid else 0


if __name__ == "__main__":
    sys.exit(main())
