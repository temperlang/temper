# /// script
# requires-python = ">=3.11"
# ///
"""Times the settle loop on every Temper file, each as its own library.

Alternates two passes over all files: up to the end of the module step
(`ast-tree --module --bench`), and the same plus copying into a library
arena and settling (`run-temper --bench`). The difference is the settle
loop's cost. There's no Kotlin side: its frontend stages don't map onto
ours, and this compares nothing it could run alone.

    uv run compat/bench_settle.py [--runs N] [--rounds N] [--record] [--csv FILE] [DIR ...]

DIR defaults to the Temper, Chromarite, and Ordable checkouts beside this
repository. `--record` appends two rows to the CSV (record_times.py's;
the nightly stats workflow passes this repository's own Temper files):
the median of the runs' medians for each side, `lex-parse-module-ms` and
`lex-parse-module-settle-ms`, with the runs' range in `notes`.
"""

import argparse
import statistics
import subprocess
import sys
from pathlib import Path

from bench_lex import bench, spread
from check_lex import DEFAULT_DIRS, ROOT, temper_files
import record_times


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("dirs", nargs="*", type=Path, default=DEFAULT_DIRS)
    parser.add_argument("--runs", type=int, default=3, help="runs per side")
    parser.add_argument("--rounds", type=int, default=20, help="timed rounds per run")
    parser.add_argument("--record", action="store_true", help="append the medians to the CSV")
    parser.add_argument("--csv", type=Path, default=record_times.CSV, help="the CSV (default stats/size-and-speed.csv)")
    args = parser.parse_args()

    subprocess.run(["cargo", "build", "-q", "--release"], cwd=ROOT, check=True)
    release = ROOT / "target/release"
    sides = {
        "module step": [str(release / "ast-tree"), "--module"],
        "settled": [str(release / "run-temper")],
    }
    files = temper_files(args.dirs)
    medians: dict[str, list[float]] = {side: [] for side in sides}
    header = ""
    for _ in range(args.runs):
        for side, command in sides.items():
            header, times = bench(command, 1, args.rounds, files)
            medians[side].append(statistics.median(times))
    print(header.removeprefix("# "))
    for side, values in medians.items():
        print(f"{side}: median {spread(values)} ms per pass over all files")
    settle = [s - m for s, m in zip(medians["settled"], medians["module step"])]
    print(f"settling alone: {spread(settle)} ms")
    if args.record:
        cond = record_times.conditions("run=rust", f"files={len(files)}", "features=net", "build=release")
        note = f"bench_settle.py, median of {args.runs} runs' medians of {args.rounds} rounds"
        record_times.write([
            record_times.row(what, f"{statistics.median(medians[side]):.1f}", "ms", cond,
                             f"{note} ({spread(medians[side])})")
            for what, side in (("lex-parse-module-ms", "module step"), ("lex-parse-module-settle-ms", "settled"))
        ], args.csv)
    return 0


if __name__ == "__main__":
    sys.exit(main())
