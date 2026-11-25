# /// script
# requires-python = ">=3.11"
# ///
"""Turns build and test step times into rows of the size and speed CSV.

The CSV is the `stats` branch's size-and-speed.csv, checked out at
stats/ beside this repository's files (`--csv` names another), with the
columns

    date,commit,what,value,value_max,unit,conditions,notes

Three ways in:

- `--steps` runs and times the build and test steps itself, one after
  another, as the nightly stats workflow (.github/workflows/stats.yml)
  does: tempest's release build from clean (a target directory of its
  own) and again after touching one frontend file; every test built
  (`build-tests-s`); clippy; each workspace crate's `cargo test -p`;
  and the small wasm build. A step that fails isn't recorded, and the
  rest still run (exit 1 at the end).
- `--time ID -- COMMAND...` times one command, as metric ID.
- A tab-separated LOG of step times, one row per step of a CI-style
  run, with the columns

      date  commit  dirty  step  wall_s  cpu_s  result

  (`date` an ISO time, `dirty` `clean` or `dirty`, `result` `ok` or `0`
  for a step that passed). A run starts where a step repeats, so a log
  of many runs splits into them; `--last` takes only the last run.

Each timed step is two rows: its wall seconds as `value`, under the
step's metric id, and its CPU seconds (the step's processes', children
included) as the same id with `-cpu-s` for `-s`. Ids: `clippy-s`; a
backend crate's `cargo test -p temper-be-X` is `funtest-X-s`, since its
functional tests are most of it; another crate's is `test-<crate>-s`;
the wasm build is `build-wasm-small-s`; other steps keep their names,
with `-s`.

Rows are printed, or with `--record` appended to the CSV (made, with
its header, if missing, though only a `--csv` one: if the default
stats/size-and-speed.csv is missing, `stats` isn't checked out, and the
rows would start a new history there, so it stops before measuring).
Redraw the charts after with
compat/size_speed_charts.py.

    uv run compat/record_times.py --steps [--record] [--csv FILE]
    uv run compat/record_times.py --time ID [--load TEXT] [--record] [--csv FILE] -- CMD...
    uv run compat/record_times.py LOG [--last] [--load TEXT] [--record] [--csv FILE]

Conditions: `--load TEXT` adds `load=TEXT` (`load=busy`, or
`load=2-agents`); a dirty tree adds `tree=dirty`; and in GitHub Actions
(`GITHUB_ACTIONS=true`), `ci=github` with the runner's CPU model and
core count (`cpu=...;cores=N`), so the charts keep the runners' noisy
trend apart from a quiet machine's runs. measure_size.py and
bench_settle.py write their rows through this module too, saying what
they measured over with `files=N;lines=N;tokens=N` (`corpus`).
"""

import argparse
import csv
import datetime
import json
import os
import re
import resource
import shutil
import subprocess
import sys
import time
from pathlib import Path

from size_speed_charts import CSV, ROOT, is_default, missing
FIELDS = ["date", "commit", "what", "value", "value_max", "unit", "conditions", "notes"]


def metric(step: str) -> str:
    if step == "clippy":
        return "clippy-s"
    if step == "wasm":
        return "build-wasm-small-s"
    if step.startswith("temper-be-") and step != "temper-be-testkit":
        return f"funtest-{step.removeprefix('temper-be-')}-s"
    if step.startswith("temper-") or step == "tempest":
        return f"test-{step}-s"
    return f"{step}-s"


def runs(rows: list[dict[str, str]]) -> list[list[dict[str, str]]]:
    """The log's runs: a new one wherever a step repeats."""
    out: list[list[dict[str, str]]] = []
    seen: set[str] = set()
    for row in rows:
        if not out or row["step"] in seen:
            out.append([])
            seen = set()
        out[-1].append(row)
        seen.add(row["step"])
    return out


# Rows, for this script, measure_size.py, and bench_settle.py.


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()


def commit() -> str:
    return git("rev-parse", "--short=7", "HEAD")


def today() -> str:
    return datetime.date.today().isoformat()


def cpu_model(cpuinfo: str) -> str:
    """The CPU's model name from /proc/cpuinfo's text, as a condition's
    value: without `;` or `=`, its spaces collapsed."""
    model = next((line.split(":", 1)[1] for line in cpuinfo.splitlines() if line.startswith("model name")),
                 "unknown")
    return " ".join(re.sub(r"[;=]", " ", model).split())


def ci_conditions() -> list[str]:
    """`ci=github`, the CPU's model, and its cores, in GitHub Actions."""
    if os.environ.get("GITHUB_ACTIONS") != "true":
        return []
    try:
        cpuinfo = Path("/proc/cpuinfo").read_text()
    except OSError:
        cpuinfo = ""
    return ["ci=github", f"cpu={cpu_model(cpuinfo)}", f"cores={os.cpu_count()}"]


def conditions(*given: str, dirty: bool | None = None, load: str | None = None) -> str:
    """The given conditions, then `load=`, `tree=dirty` (changes to
    tracked files, by `git status`, unless `dirty` says), and CI's,
    joined by `;`."""
    if dirty is None:
        dirty = bool(git("status", "--porcelain", "--untracked-files=no"))
    parts = [c for c in given if c]
    if load:
        parts.append(f"load={load}")
    if dirty:
        parts.append("tree=dirty")
    return ";".join(parts + ci_conditions())


# The token printer `corpus` counts tokens with, built by
# `cargo build --release -p temper-syntax`.
LEX_TOKENS = ROOT / "target/release/lex-tokens"
# Token kinds the lexer finds that aren't counted as tokens.
IGNORABLE = {"Comment", "Margin", "Space"}


def corpus(files: list[str], lexer: list[str] | None = None) -> list[str]:
    """Conditions saying what was measured: `files=N` and `lines=N`, and
    with a token printer (`lex-tokens`), `tokens=N`, leaving out
    comments, spaces, and margins. Rows over a moving set of files (this
    repository's, at each commit) can then be compared per line."""
    lines = sum(len(Path(f).read_text("utf-8").splitlines()) for f in files)
    out = [f"files={len(files)}", f"lines={lines}"]
    if lexer:
        printed = subprocess.run(lexer + files, capture_output=True, text=True, check=True).stdout
        tokens = sum(1 for line in printed.splitlines()
                     if line and not line.startswith("# ") and line.split("\t", 1)[0] not in IGNORABLE)
        out.append(f"tokens={tokens}")
    return out


def row(what: str, value: str, unit: str, cond: str, note: str, date: str = "", rev: str = "") -> dict[str, str]:
    return {"date": date or today(), "commit": rev or commit(), "what": what, "value": value, "value_max": "",
            "unit": unit, "conditions": cond, "notes": note}


def check_csv(path: Path) -> None:
    """Stops if `path` is the default CSV, however it's spelled, and it's
    missing (`stats` not checked out), rather than starting a new one
    there."""
    if is_default(path) and (problem := missing(path, "--csv FILE")):
        sys.exit(problem)


def write(rows: list[dict[str, str]], path: Path | None) -> None:
    """Appends rows to the CSV at `path` (made, with its header, if
    missing, unless it's the default; see check_csv), or prints them if
    `path` is None."""
    if path is None:
        csv.DictWriter(sys.stdout, FIELDS, lineterminator="\n").writerows(rows)
        return
    check_csv(path)
    new = not path.exists()
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", newline="") as f:
        writer = csv.DictWriter(f, FIELDS, lineterminator="\n")
        if new:
            writer.writeheader()
        writer.writerows(rows)
    print(f"recorded {len(rows)} rows in {path}")


# Times.


def rows_for(what: str, date: str, rev: str, wall: float, cpu: float, cond: str, note: str) -> list[dict[str, str]]:
    return [
        row(what, f"{wall:.1f}", "s", cond, note, date, rev),
        row(what.removesuffix("-s") + "-cpu-s", f"{cpu:.1f}", "s", cond, note, date, rev),
    ]


def from_log(path: Path, last: bool, load: str | None) -> list[dict[str, str]]:
    with path.open(newline="") as f:
        logged = list(csv.DictReader(f, delimiter="\t"))
    chosen = runs(logged)
    if last:
        chosen = chosen[-1:]
    out = []
    for run in chosen:
        for step in run:
            if step["result"] not in ("ok", "0"):
                continue
            out += rows_for(
                metric(step["step"]),
                step["date"][:10],
                step["commit"],
                float(step["wall_s"]),
                float(step["cpu_s"]),
                conditions(dirty=step["dirty"] == "dirty", load=load),
                "record_times.py",
            )
    return out


def timed(what: str, command: list[str], cond: str, note: str = "record_times.py --time") -> list[dict[str, str]] | None:
    """Runs a command, returning its rows, or None if it failed."""
    print(f"=== {what}: {' '.join(command)}", flush=True)
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    start = time.monotonic()
    done = subprocess.run(command, cwd=ROOT)
    wall = time.monotonic() - start
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    if done.returncode != 0:
        print(f"{' '.join(command)} failed ({done.returncode}); {what} not recorded", file=sys.stderr)
        return None
    cpu = (after.ru_utime - before.ru_utime) + (after.ru_stime - before.ru_stime)
    print(f"{what}: {wall:.1f} s, {cpu:.1f} s CPU", flush=True)
    return rows_for(what, today(), commit(), wall, cpu, cond, note)


def crates() -> list[str]:
    """The workspace's crates, by name."""
    meta = subprocess.run(["cargo", "metadata", "--no-deps", "--format-version", "1"], cwd=ROOT,
                          capture_output=True, text=True, check=True)
    return sorted(p["name"] for p in json.loads(meta.stdout)["packages"])


# The file `build-tempest-incremental-s` touches: the frontend's root,
# so everything that depends on it rebuilds.
TOUCHED = ROOT / "crates/temper-frontend/src/lib.rs"


def steps(load: str | None) -> tuple[list[dict[str, str]], bool]:
    """Times the build and test steps (see the module's doc), returning
    their rows and whether every step passed."""
    clean = ROOT / "target/stats-clean"
    shutil.rmtree(clean, ignore_errors=True)
    release = ["cargo", "build", "--locked", "--release", "-p", "tempest", "--target-dir", str(clean)]
    plan: list[tuple[str, list[str]]] = [
        ("build-tempest-release-s", release),
        ("build-tempest-incremental-s", release),
        ("build-tests-s", ["cargo", "test", "--locked", "--workspace", "--no-run"]),
        ("clippy-s", ["cargo", "clippy", "--locked", "--workspace", "--all-targets", "--", "-D", "warnings"]),
        *[(metric(c), ["cargo", "test", "--locked", "-p", c]) for c in crates()],
        ("build-wasm-small-s", ["cargo", "build", "--locked", "--profile", "small-wasm",
                                "--target", "wasm32-unknown-unknown", "-p", "temper-wasm"]),
    ]
    cond = conditions(load=load)  # Before the steps, whose leftovers aren't changes.
    out: list[dict[str, str]] = []
    passed = True
    for what, command in plan:
        if what == "build-tempest-incremental-s":
            TOUCHED.touch()
        rows = timed(what, command, cond, "record_times.py --steps")
        out += rows or []
        passed = passed and rows is not None
    shutil.rmtree(clean, ignore_errors=True)
    return out, passed


def main() -> int:
    parser = argparse.ArgumentParser(description="Turns build and test step times into CSV rows.")
    parser.add_argument("log", nargs="?", type=Path, help="a times log (TSV)")
    parser.add_argument("--last", action="store_true", help="only the log's last run")
    parser.add_argument("--time", metavar="ID", help="time the command after --, as metric ID")
    parser.add_argument("--steps", action="store_true", help="run and time the build and test steps")
    parser.add_argument("--load", help="what else was running, as load=TEXT")
    parser.add_argument("--record", action="store_true", help="append to the CSV")
    parser.add_argument("--csv", type=Path, default=CSV, help="the CSV (default stats/size-and-speed.csv)")
    argv = sys.argv[1:]
    split = argv.index("--") if "--" in argv else len(argv)
    args = parser.parse_args(argv[:split])
    command = argv[split + 1:]
    if args.record:
        check_csv(args.csv)
    passed = True
    if args.steps:
        rows, passed = steps(args.load)
    elif args.time:
        if not command:
            parser.error("--time needs a command after --")
        rows = timed(args.time, command, conditions(load=args.load))
        if rows is None:
            return 1
    elif args.log:
        rows = from_log(args.log, args.last, args.load)
    else:
        parser.error("give a log, --time, or --steps")
    write(rows, args.csv if args.record else None)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
