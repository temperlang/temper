# Size and speed

Every night, the [Stats workflow](../../.github/workflows/stats.yml)
measures `main`'s latest commit on one of GitHub's Linux x64 runners
and records the results on the `stats` branch: a branch of data alone,
[size-and-speed.csv](https://github.com/temperlang/temper/blob/stats/size-and-speed.csv)
and its charts, whose
[README](https://github.com/temperlang/temper/blob/stats/README.md)
says what each column and metric means. `main` gets no commits for it.
Why the numbers moved, and what to try next, is in
[notes/size-and-speed.md](../../notes/size-and-speed.md).

## What's measured

On one runner, one step after another, with no build cache:

- **Build and test times**
  ([record_times.py](../../compat/record_times.py) `--steps`): `temper`'s
  release build from clean and after touching one frontend file, every
  test built, clippy, each crate's tests with every backend's toolchain
  (as [CI](ci.md) runs them), and the small wasm build; wall and CPU
  seconds each.
- **Sizes** ([measure_size.py](../../compat/measure_size.py)
  `--no-kotlin`): `temper` as released (the `small` profile) and as a
  plain release build, the other binaries, and the playground's wasm,
  raw and gzipped; on Sundays and when run by hand, each backend's
  share too (`--shares`).
- **Peak memory and settle time** over this repository's Temper files
  ([measure_size.py](../../compat/measure_size.py) and
  [bench_settle.py](../../compat/bench_settle.py), the median of five
  runs).

Each row names the runner's CPU and core count. Sizes are exact; times
on shared runners vary by tens of percent from night to night, so read
the timing charts as a trend, and measure a change's speed by
alternating runs on one quiet machine (`compat/bench_*.py`). The
workflow commits through GitHub's API, so GitHub signs each commit
([CI](ci.md#nightly-size-and-speed)).

## Charts

![Binary sizes](https://raw.githubusercontent.com/temperlang/temper/stats/charts/sizes.svg)
![Settle time](https://raw.githubusercontent.com/temperlang/temper/stats/charts/settle-time.svg)
![Peak memory](https://raw.githubusercontent.com/temperlang/temper/stats/charts/peak-memory.svg)
![Functional tests by backend](https://raw.githubusercontent.com/temperlang/temper/stats/charts/funtest-times.svg)
![Build and lint times](https://raw.githubusercontent.com/temperlang/temper/stats/charts/build-times.svg)

Until 2026-11-13, times were taken on a developer's machine, charted
apart, as were Ordable's test times on Temper's two compilers:

![Functional tests by backend, on a local machine](https://raw.githubusercontent.com/temperlang/temper/stats/charts/funtest-times-local.svg)
![Build and lint times, on a local machine](https://raw.githubusercontent.com/temperlang/temper/stats/charts/build-times-local.svg)
![Ordable's tests](https://raw.githubusercontent.com/temperlang/temper/stats/charts/ordable-tests.svg)

## Running it locally

Each step runs locally too, writing its rows wherever `--csv` says
(appending, with a header if the file is new):

    uv run compat/record_times.py --steps --record --csv /tmp/stats/size-and-speed.csv
    uv run compat/measure_size.py --no-kotlin --record --csv /tmp/stats/size-and-speed.csv lib crates
    uv run compat/bench_settle.py --runs 5 --rounds 10 --record --csv /tmp/stats/size-and-speed.csv lib crates
    uv run compat/size_speed_table.py --csv /tmp/stats/size-and-speed.csv

To draw the charts, check `stats` out beside the code, at `stats/`
(ignored by `main`), and append there, or copy its CSV to the
directory first:

    git worktree add stats stats
    uv run compat/size_speed_charts.py --stats stats

The scripts' defaults are that checkout's `size-and-speed.csv` and
`charts/`. Only the commit needs GitHub: [commit_stats.py](../../.github/ci-tools/commit_stats.py)
`DIR --branch stats --headline TEXT --repo temperlang/temper --dry-run -`
prints the request it would send.
