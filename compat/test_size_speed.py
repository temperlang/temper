# /// script
# requires-python = ">=3.11"
# ///
"""Tests the size and speed scripts' rows, from writing to the charts.

record_times.py's pure parts (metric ids, a times log's runs, the
conditions, with GitHub's runner's and its CPU model, and the measured
files' counts); the charts per 1,000 lines; a round trip:
rows written twice to a new CSV through record_times.write, then drawn
by size_speed_charts.render; and the stop before starting a new default
CSV, however its path is spelled; each in a temporary directory. Fast,
with nothing measured; CI's `lint` job runs it.

    uv run compat/test_size_speed.py
"""

import csv
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
        set_all(values)
        yield
    finally:
        set_all(old)


def set_all(values: dict[str, str | None]) -> None:
    for k, v in values.items():
        if v is None:
            os.environ.pop(k, None)
        else:
            os.environ[k] = v


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
    with environment(GITHUB_ACTIONS="true"):
        parts = record_times.conditions("a=1", dirty=False).split(";")
    assert parts[:2] == ["a=1", "ci=github"] and parts[2].startswith("cpu="), parts
    assert parts[3:] == [f"cores={os.cpu_count()}"], parts


def test_cpu_model() -> None:
    # A CPU name with `;` and `=` in it must not add conditions or values.
    model = record_times.cpu_model("vendor_id\t: x\nmodel name\t: Xeon; Fast=yes  @ 2.1GHz\nmodel name\t: other\n")
    assert model == "Xeon Fast yes @ 2.1GHz", model
    assert record_times.cpu_model("") == "unknown"


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


def test_corpus(tmp: Path) -> None:
    # Files, lines, and tokens of the measured files; the lexer's
    # ignorable tokens (comments, spaces, margins) aren't counted.
    a, b = tmp / "a.temper", tmp / "b.temper.md"
    a.write_text("let x = 1;\n// one\n")
    b.write_text("# Doc\n\n    f();\nno newline at the end")
    lexer = tmp / "lexer.py"
    lexer.write_text("import sys\n"
                     "for path in sys.argv[1:]:\n"
                     "    print('# ' + path)\n"
                     "    for kind in ('Word', 'Space', 'Comment', 'Margin', 'Punctuation'):\n"
                     "        print(kind + '\\t0\\t1\\t-\\t-\\tx')\n")
    files = [str(a), str(b)]
    assert record_times.corpus(files) == ["files=2", "lines=6"], record_times.corpus(files)
    got = record_times.corpus(files, [sys.executable, str(lexer)])
    assert got == ["files=2", "lines=6", "tokens=4"], got


def test_per_lines() -> None:
    # The per-1,000-lines charts take only rows with a line count; the
    # raw charts take every row.
    rows = [
        record_times.row("lex-parse-module-settle-ms", "20.0", "ms", "run=rust;files=9;ci=github", "old",
                         "2026-11-20", "aaaaaaa"),
        record_times.row("lex-parse-module-settle-ms", "10.0", "ms",
                         "run=rust;files=2;lines=500;tokens=900;ci=github", "new", "2026-11-21", "bbbbbbb"),
        record_times.row("peak-memory-settle-all", "30.0", "MiB", "run=rust;files=2;lines=2000;ci=github", "new",
                         "2026-11-21", "bbbbbbb"),
    ]
    charts = {c.name: c for c in size_speed_charts.CHARTS}

    def mids(name: str) -> list[tuple[str, float]]:
        data = size_speed_charts.gather(rows, charts[name])
        return [(p.key[1], p.mid) for points in data.points for p in points.values()]

    assert mids("settle-time") == [("aaaaaaa", 20.0), ("bbbbbbb", 10.0)], mids("settle-time")
    assert mids("settle-time-per-1k-lines") == [("bbbbbbb", 20.0)], mids("settle-time-per-1k-lines")
    assert mids("peak-memory-per-1k-lines") == [("bbbbbbb", 15.0)], mids("peak-memory-per-1k-lines")
    drawn = size_speed_charts.draw(charts["settle-time-per-1k-lines"],
                                   size_speed_charts.gather(rows, charts["settle-time-per-1k-lines"]))
    assert "ms per 1,000 lines" in drawn and "aaaaaaa" not in drawn, drawn


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
    assert "12.3" in ci and "abc1234" in ci, ci
    for name in ("funtest-times-local", "settle-time", "build-times"):
        text = (charts / f"{name}.svg").read_text()
        assert "No runs recorded yet" in text and "12.3" not in text, (name, text)
    for name in ("settle-time-per-1k-lines", "peak-memory-per-1k-lines"):
        text = (charts / f"{name}.svg").read_text()
        assert "No runs with a line count recorded yet" in text and "12.3" not in text, (name, text)


def test_check_csv(tmp: Path) -> None:
    # The default CSV, missing, stops, however it's spelled; one given
    # elsewhere is made.
    default = size_speed_charts.CSV
    size_speed_charts.CSV = tmp.resolve() / "stats-missing" / "size-and-speed.csv"
    cwd = Path.cwd()
    try:
        os.chdir(tmp)
        for spelling in (size_speed_charts.CSV, Path("stats-missing/size-and-speed.csv"),
                         Path("stats-missing/../stats-missing/size-and-speed.csv")):
            try:
                record_times.check_csv(spelling)
            except SystemExit as e:
                assert "git worktree add stats stats" in str(e.code), (spelling, e.code)
            else:
                raise AssertionError(f"check_csv let a missing default CSV through as {spelling}")
            assert "git worktree add stats stats" in str(size_speed_charts.missing(spelling, "--csv FILE")), spelling
        record_times.check_csv(tmp / "elsewhere.csv")
        assert size_speed_charts.missing(tmp / "elsewhere.csv", "--csv FILE") == f"{tmp / 'elsewhere.csv'} is missing"
    finally:
        os.chdir(cwd)
        size_speed_charts.CSV = default


def main() -> int:
    test_metric()
    test_conditions()
    test_cpu_model()
    test_rows_for()
    test_per_lines()
    with tempfile.TemporaryDirectory() as tmp:
        test_from_log(Path(tmp))
    with tempfile.TemporaryDirectory() as tmp:
        test_corpus(Path(tmp))
    with tempfile.TemporaryDirectory() as tmp:
        test_round_trip(Path(tmp))
    with tempfile.TemporaryDirectory() as tmp:
        test_check_csv(Path(tmp))
    print("test_size_speed.py: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
