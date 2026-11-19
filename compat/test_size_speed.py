# /// script
# requires-python = ">=3.11"
# ///
"""Tests the size and speed scripts' rows, from writing to the charts.

record_times.py's pure parts (metric ids, a times log's runs, the
conditions, with GitHub's runner's), and a round trip: rows written
twice to a new CSV through record_times.write, then drawn by
size_speed_charts.render, each into a temporary directory. Fast, with
nothing measured; CI's `lint` job runs it.

    uv run compat/test_size_speed.py
"""

import csv
import io
import os
import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import record_times  # noqa: E402
import size_speed_charts  # noqa: E402


@contextmanager
def environment(**values: str | None):
    """Sets (or with None, unsets) environment variables for a while."""
    old = {k: os.environ.get(k) for k in values}
    try:
        for k, v in values.items():
            os.environ.pop(k, None) if v is None else os.environ.__setitem__(k, v)
        yield
    finally:
        for k, v in old.items():
            os.environ.pop(k, None) if v is None else os.environ.__setitem__(k, v)


@contextmanager
def cpuinfo(text: str):
    """record_times' /proc/cpuinfo, as `text`."""
    record_times.open = lambda *args, **kwargs: io.StringIO(text)
    try:
        yield
    finally:
        del record_times.open


def test_metric() -> None:
    for step, what in {
        "temper-be-py": "funtest-py-s",
        "temper-be-cpp": "funtest-cpp-s",
        "temper-be-testkit": "test-temper-be-testkit-s",
        "temper-frontend": "test-temper-frontend-s",
        "tempest": "test-tempest-s",
        "clippy": "clippy-s",
        "wasm": "build-wasm-small-s",
        "build-tests": "build-tests-s",
    }.items():
        assert record_times.metric(step) == what, (step, record_times.metric(step), what)


def test_conditions() -> None:
    with environment(GITHUB_ACTIONS=None):
        assert record_times.conditions("a=1", "", dirty=True, load="busy") == "a=1;load=busy;tree=dirty"
        assert record_times.conditions(dirty=False) == ""
    # A CPU name with `;` and `=` in it must not add conditions or values.
    with environment(GITHUB_ACTIONS="true"), cpuinfo("vendor_id\t: x\nmodel name\t: Xeon; Fast=yes  @ 2.1GHz\n"):
        parts = record_times.conditions("a=1", dirty=False).split(";")
    assert parts == ["a=1", "ci=github", "cpu=Xeon Fast yes @ 2.1GHz", f"cores={os.cpu_count()}"], parts


def test_rows_for() -> None:
    rows = record_times.rows_for("x-s", "2026-11-19", "abc1234", 1.26, 3.0, "c=1", "n")
    assert [(r["what"], r["value"]) for r in rows] == [("x-s", "1.3"), ("x-cpu-s", "3.0")], rows
    assert all((r["date"], r["commit"], r["unit"], r["conditions"], r["notes"])
               == ("2026-11-19", "abc1234", "s", "c=1", "n") for r in rows), rows


def test_from_log(tmp: Path) -> None:
    log = tmp / "times.tsv"
    lines = [
        ("date", "commit", "dirty", "step", "wall_s", "cpu_s", "result"),
        # Run 1, with a failed step.
        ("2026-11-18T10:00:00", "aaaaaaa", "clean", "clippy", "10", "20", "ok"),
        ("2026-11-18T10:01:00", "aaaaaaa", "clean", "temper-be-py", "30", "40", "0"),
        ("2026-11-18T10:02:00", "aaaaaaa", "clean", "wasm", "5", "6", "1"),
        # Run 2: clippy again starts it.
        ("2026-11-19T10:00:00", "bbbbbbb", "dirty", "clippy", "11", "21", "ok"),
        ("2026-11-19T10:01:00", "bbbbbbb", "dirty", "temper-be-py", "31", "41", "ok"),
    ]
    log.write_text("".join("\t".join(line) + "\n" for line in lines))
    with environment(GITHUB_ACTIONS=None):
        every = record_times.from_log(log, last=False, load=None)
        last = record_times.from_log(log, last=True, load="busy")
    got = [(r["date"], r["commit"], r["what"], r["value"], r["conditions"]) for r in every]
    assert got == [
        ("2026-11-18", "aaaaaaa", "clippy-s", "10.0", ""),
        ("2026-11-18", "aaaaaaa", "clippy-cpu-s", "20.0", ""),
        ("2026-11-18", "aaaaaaa", "funtest-py-s", "30.0", ""),
        ("2026-11-18", "aaaaaaa", "funtest-py-cpu-s", "40.0", ""),
        ("2026-11-19", "bbbbbbb", "clippy-s", "11.0", "tree=dirty"),
        ("2026-11-19", "bbbbbbb", "clippy-cpu-s", "21.0", "tree=dirty"),
        ("2026-11-19", "bbbbbbb", "funtest-py-s", "31.0", "tree=dirty"),
        ("2026-11-19", "bbbbbbb", "funtest-py-cpu-s", "41.0", "tree=dirty"),
    ], got
    assert [(r["what"], r["value"], r["conditions"]) for r in last] == [
        ("clippy-s", "11.0", "load=busy;tree=dirty"),
        ("clippy-cpu-s", "21.0", "load=busy;tree=dirty"),
        ("funtest-py-s", "31.0", "load=busy;tree=dirty"),
        ("funtest-py-cpu-s", "41.0", "load=busy;tree=dirty"),
    ], last


def test_round_trip(tmp: Path) -> None:
    stats = tmp / "stats"
    path = stats / "size-and-speed.csv"
    written = [record_times.row("funtest-py-s", "12.3", "s", "ci=github", "n", "2026-11-19", "abc1234")]
    record_times.write(written, path)
    record_times.write(written, path)
    lines = path.read_text().splitlines()
    # The stats branch's columns, in its order.
    assert lines[0] == "date,commit,what,value,value_max,unit,conditions,notes", lines[0]
    assert len(lines) == 3 and lines.count(lines[0]) == 1, lines
    with path.open(newline="") as f:
        read = list(csv.DictReader(f))
    assert read == written * 2, read
    size_speed_charts.render(stats)
    charts = stats / "charts"
    ci = (charts / "funtest-times.svg").read_text()
    assert "<desc id=\"d\">Python: 12.3 s at first, 12.3 s latest (abc1234).</desc>" in ci, ci
    for name in ("funtest-times-local", "settle-time", "build-times"):
        text = (charts / f"{name}.svg").read_text()
        assert "No runs recorded yet" in text and "12.3" not in text, (name, text)


def test_check_csv(tmp: Path) -> None:
    # The default CSV, missing, stops; one given elsewhere is made.
    default = record_times.CSV
    record_times.CSV = tmp / "stats-missing" / "size-and-speed.csv"
    try:
        try:
            record_times.check_csv(record_times.CSV)
        except SystemExit as e:
            assert "git worktree add stats stats" in str(e.code), e.code
        else:
            raise AssertionError("check_csv let a missing default CSV through")
        record_times.check_csv(tmp / "elsewhere.csv")
    finally:
        record_times.CSV = default


def main() -> int:
    test_metric()
    test_conditions()
    test_rows_for()
    with tempfile.TemporaryDirectory() as tmp:
        test_from_log(Path(tmp))
    with tempfile.TemporaryDirectory() as tmp:
        test_round_trip(Path(tmp))
    with tempfile.TemporaryDirectory() as tmp:
        test_check_csv(Path(tmp))
    print("test_size_speed.py: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
