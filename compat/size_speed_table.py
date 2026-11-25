# /// script
# requires-python = ">=3.11"
# ///
"""Prints the latest size and speed measurements as a Markdown table.

Reads size-and-speed.csv in a checkout of the `stats` branch (stats/,
beside this repository's files), or `--csv FILE`. For each metric
(`what`) and its conditions, leaving out `tree=`, `load=`, the files'
`files=`, `lines=`, and `tokens=`, and the runner's `cpu=` and `cores=`,
it shows the rows
from the latest date and commit, as a range if there are several, and
skips conditions not measured on the metric's latest date. Sizes in bytes
show as KiB. Give metric ids to show only those, in that order.

    uv run compat/size_speed_table.py [--csv FILE] [WHAT ...]
"""

import argparse
import csv
import sys
from pathlib import Path

from size_speed_charts import CSV, missing
IGNORED = ("tree=", "load=", "files=", "lines=", "tokens=", "cpu=", "cores=")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("what", nargs="*", help="metric ids to show (default: all)")
    parser.add_argument("--csv", type=Path, default=CSV)
    args = parser.parse_args()
    if problem := missing(args.csv, "--csv FILE"):
        sys.exit(problem)

    with args.csv.open(newline="") as f:
        rows = list(csv.DictReader(f))
    groups: dict[tuple[str, str], list[dict[str, str]]] = {}
    for row in rows:
        if args.what and row["what"] not in args.what:
            continue
        conditions = ";".join(c for c in row["conditions"].split(";") if c and not c.startswith(IGNORED))
        groups.setdefault((row["what"], conditions), []).append(row)

    newest: dict[str, str] = {}
    for (what, _), group in groups.items():
        newest[what] = max([newest.get(what, ""), *(row["date"] for row in group)])
    order = {what: i for i, what in enumerate(args.what)}
    print("| What | Conditions | Value | Date | Commit |")
    print("|---|---|---|---|---|")
    for (what, conditions), group in sorted(groups.items(), key=lambda g: (order.get(g[0][0], 0), g[0])):
        latest = max(row["date"] for row in group)
        if latest != newest[what]:
            continue
        last = [row for row in group if row["date"] == latest]
        last = [row for row in last if row["commit"] == last[-1]["commit"]]
        unit = last[0]["unit"]
        values = [v for row in last for v in (row["value"], row["value_max"]) if v]
        if unit == "bytes":
            values, unit = [f"{int(v) / 1024:.0f}" for v in values], "KiB"
        low, high = min(values, key=float), max(values, key=float)
        value = low if float(low) == float(high) else f"{low}–{high}"
        commit = f"`{last[-1]['commit']}`" if last[-1]["commit"] else ""
        print(f"| `{what}` | {conditions} | {value} {unit} | {latest} | {commit} |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
