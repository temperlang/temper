# /// script
# requires-python = ">=3.11"
# ///
"""Checks that a build's generated C++ headers include only what they use.

For the nightly's real libraries (nightly.yml's `libraries` job). A
library's generated header includes the runtime's `base.hpp` and the
other runtime headers it names, never the whole runtime (`core.hpp`),
so a user's file that includes it parses only that. For each generated
header of each library (not std's or the runtime's, which the C++
backend's own tests check), this checks that it doesn't include
`core.hpp`, and that a file including it alone compiles
(`-fsyntax-only`, C++14, as the generated CMake builds).

    uv run .github/ci-tools/check_library_cpp.py OUT

OUT is a backend directory as `temper build -b cpp` or `temper test -b
cpp` writes it (`temper.out/cpp`): a directory per library beside
`std` and `temper-core`. A generated header starts `#pragma once`,
then includes the runtime; host code's headers, copied as they are,
are left out. The compiler is CXX, or `g++`. Exit status 1 if any
header fails, with GitHub Actions' `::error` lines.
"""

import os
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

GENERATED = "#pragma once\n\n#include <temper-core/"
WHOLE = "#include <temper-core/core.hpp>"
SKIP = {"std", "temper-core"}


def headers(out: Path) -> list[Path]:
    found = []
    for library in sorted(p for p in out.iterdir() if p.is_dir() and p.name not in SKIP):
        for header in sorted(library.rglob("*.hpp")):
            if header.read_text(encoding="utf-8").startswith(GENERATED):
                found.append(header)
    return found


def check(out: Path, header: Path, scratch: Path, cxx: str) -> str | None:
    rel = header.relative_to(out).as_posix()
    if WHOLE in header.read_text(encoding="utf-8"):
        return f"{rel} includes the whole runtime, temper-core/core.hpp"
    probe = scratch / (rel.replace("/", "--") + ".cpp")
    probe.write_text(f"#include <{rel}>\n", encoding="utf-8")
    command = [cxx, "-std=c++14", "-fsyntax-only", "-I", str(out), str(probe)]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        return f"{rel} doesn't compile alone:\n{result.stderr}"
    return None


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    out = Path(sys.argv[1])
    cxx = os.environ.get("CXX", "g++")
    found = headers(out)
    if not found:
        print(f"::error::no generated headers in {out}")
        return 1
    with tempfile.TemporaryDirectory() as scratch, ThreadPoolExecutor(2) as pool:
        problems = [
            p
            for p in pool.map(lambda h: check(out, h, Path(scratch), cxx), found)
            if p is not None
        ]
    for problem in problems:
        first, _, rest = problem.partition("\n")
        print(f"::error::{first}")
        if rest:
            print(rest)
    print(f"{len(found)} headers, {len(problems)} failing")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
