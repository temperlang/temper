# /// script
# requires-python = ">=3.11"
# ///
"""Counts the expressions typing leaves of unknown type, slice 7's measure
(notes/history/milestone-4.md), with the calls it leaves unresolved and the `==`
uses that rely on identity, per file and in total.

Covers Kotlin Temper's functional tests, each built as check_functional.py
builds it (std's files it imports aren't counted there), std as one
library, and the libraries under each LIBRARY directory given, found as
`temper test` finds them (each directory with a config.temper.md). Uses
`run-temper --untyped`. The tests and std come from this repository's
crates/temper-funtests/cases/ and lib/std/, or, with --temper, from a Kotlin
Temper checkout. Lists the functional tests with no expression of unknown type, then the most
common kinds of what's left.

    uv run compat/count_untyped.py [--verbose] [--causes N] [--temper DIR] [LIBRARY ...]

--verbose lists each file and each expression of unknown type, call not
resolved, and `==` relying on identity.
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
FILE = re.compile(r"^file (.*) exprs (\d+) untyped (\d+) calls (\d+) unresolved (\d+) identity (\d+)$")
ITEM = re.compile(r"^  (untyped|unresolved|identity) (\d+:\d+) (.*?) \| (.*)$")


def sources(directory: Path) -> list[str]:
    """A directory's own Temper files."""
    return sorted(str(p) for p in directory.iterdir() if p.name.endswith((".temper", ".temper.md")))


def library_files(root: Path) -> list[str]:
    """A library's sources: under its root, not under another library's,
    nor in temper.out, temper.keep or a dot directory; not its config."""
    found = []
    for path in sorted(root.rglob("*")):
        parts = path.relative_to(root).parts
        if any(p.startswith(".") or p in ("temper.out", "temper.keep") for p in parts[:-1]):
            continue
        inner = path.parent
        nested = any((root / Path(*parts[:i]) / "config.temper.md").exists() for i in range(1, len(parts)))
        if nested or not path.is_file() or not path.name.endswith((".temper", ".temper.md")):
            continue
        if path.name == "config.temper.md" and inner == root:
            continue
        found.append(str(path))
    return found


def libraries(directory: Path) -> list[Path]:
    """The libraries' roots under a directory."""
    roots = []
    for config in sorted(directory.rglob("config.temper.md")):
        parts = config.relative_to(directory).parts
        if not any(p.startswith(".") or p in ("temper.out", "temper.keep") for p in parts):
            roots.append(config.parent)
    return roots


class File:
    def __init__(self, path: str, counts: list[int]):
        self.path = path
        self.exprs, self.untyped, self.calls, self.unresolved, self.identity = counts
        self.items: list[tuple[str, str, str, str]] = []


def count(runner: Path, files: list[str], keep) -> list[File]:
    """Runs `run-temper --untyped` on files built as one library, keeping
    the files whose paths `keep` accepts."""
    result = subprocess.run([str(runner), "--untyped", "--std", str(STD), *files], capture_output=True, timeout=120)
    out = []
    current = None
    for line in result.stdout.decode("utf-8", "replace").splitlines():
        if m := FILE.match(line):
            current = File(m[1], [int(m[i]) for i in range(2, 7)]) if keep(m[1]) else None
            if current:
                out.append(current)
        elif (m := ITEM.match(line)) and current:
            current.items.append((m[1], m[2], m[3], m[4]))
    return out


def summarize(name: str, files: list[File]) -> tuple[int, int, int, int, int]:
    totals = tuple(sum(getattr(f, a) for f in files) for a in ("exprs", "untyped", "calls", "unresolved", "identity"))
    exprs, untyped, calls, unresolved, identity = totals
    print(f"{name}: {len(files)} files, {untyped} of {exprs} expressions untyped, "
          f"{unresolved} of {calls} calls unresolved, {identity} == on identity")
    return totals


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("libraries", nargs="*", type=Path)
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--causes", type=int, default=15)
    parser.add_argument("--temper", type=Path, help="use this Temper checkout's tests and std")
    args = parser.parse_args()
    if args.temper:
        global SUITE, STD
        SUITE = args.temper / "functional-test-suite/src/commonMain/resources"
        STD = args.temper / "frontend/src/commonMain/resources/std"

    subprocess.run(["cargo", "build", "-q", "--release", "-p", "temper-frontend"], cwd=ROOT, check=True)
    runner = ROOT / "target/release/run-temper"

    groups: list[tuple[str, list[File]]] = []
    tests = []
    for main in sorted(SUITE.rglob("*.temper.md")):
        if main.name == f"{main.parent.name}.temper.md":
            directory = main.parent
            files = count(runner, sources(directory), lambda p, d=str(directory): str(Path(p).parent) == d)
            tests.append((str(directory.relative_to(SUITE)), files))
    groups.append(("functional tests", [f for _, files in tests for f in files]))
    std_files = library_files(STD)
    groups.append(("std", count(runner, std_files, lambda p: p.startswith(str(STD) + "/"))))
    for directory in args.libraries:
        for root in libraries(directory):
            prefix = str(root) + "/"
            groups.append((str(root), count(runner, library_files(root), lambda p, prefix=prefix: p.startswith(prefix))))

    grand = [0] * 5
    for name, files in groups:
        totals = summarize(name, files)
        grand = [a + b for a, b in zip(grand, totals)]
        if args.verbose:
            for f in files:
                print(f"  {f.path}: {f.untyped} of {f.exprs} untyped, {f.unresolved} of {f.calls} unresolved, {f.identity} identity")
                for label, where, what, text in f.items:
                    print(f"    {label} {where} {what} | {text}")
    print(f"total: {grand[1]} of {grand[0]} expressions untyped, {grand[3]} of {grand[2]} calls unresolved, "
          f"{grand[4]} == on identity")

    typed = [name for name, files in tests if all(f.untyped == 0 for f in files)]
    resolved = [name for name, files in tests if all(f.untyped == 0 and f.unresolved == 0 and f.identity == 0 for f in files)]
    print(f"\nfully typed functional tests ({len(typed)} of {len(tests)}), * if every call is resolved too:")
    for name in typed:
        print(f"  {name}{' *' if name in resolved else ''}")
    near = sorted((sum(f.untyped for f in files), name) for name, files in tests if name not in typed)
    print("\nnearest functional tests (untyped count):")
    for n, name in near[:15]:
        print(f"  {n:4} {name}")

    for label in ("untyped", "unresolved", "identity"):
        causes = collections.Counter(what for _, files in groups for f in files for (l, _, what, _) in f.items if l == label)
        if causes:
            print(f"\nmost common {label}:")
            for what, n in causes.most_common(args.causes):
                print(f"  {n:6} {what}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
