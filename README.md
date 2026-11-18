# Size and speed of Temper's compiler

This branch, `stats`, holds data only: the size and speed of `temper`,
the compiler on `main`, measured over time. It shares no history with
`main`, so `main` gets no commits for measurements.

- [size-and-speed.csv](size-and-speed.csv): every measurement, a row
  each.
- [charts/](charts): drawn from the CSV by `main`'s
  `compat/size_speed_charts.py`.

`main`'s [docs/contributing/size-and-speed.md](https://github.com/temperlang/temper/blob/main/docs/contributing/size-and-speed.md)
says what's measured and how, and shows the charts, as does the [docs
site](https://temperlang.dev/docs/contributing/size-and-speed.html);
[notes/size-and-speed.md](https://github.com/temperlang/temper/blob/main/notes/size-and-speed.md)
explains why numbers moved.

## How rows are added

Every night, `main`'s [stats.yml](https://github.com/temperlang/temper/blob/main/.github/workflows/stats.yml)
measures `main`'s latest commit (if it has no rows yet) on one of
GitHub's runners, appends the rows, redraws the charts, and commits
them here through GitHub's API, as `github-actions[bot]`, signed by
GitHub. Rows are only ever appended. A row added by hand needs a signed
commit too, and a word in `notes` on how it was measured.

Rows up to 2026-11-13 were measured on a developer's machine, by the
same scripts, with Kotlin Temper beside them for comparison; the
commits they name come from the history before this repository was
published, so most of them aren't in it.

## Columns

    date,commit,what,value,value_max,unit,conditions,notes

- `date` and `commit`: when, and the commit measured (seven hex
  digits; empty if it wasn't recorded). `tree=dirty` in `conditions`
  means that commit plus uncommitted work, which `notes` describes.
- `what`: a stable metric id.
  - Sizes: `<binary>-small` and `<binary>-release` (`tempest` is the
    `temper` binary, its crate's name), `wasm-small` and
    `wasm-small-gzip`; `tempest-small-no-backends` and
    `wasm-small-no-backends`; each backend's share of the small
    `temper` and of the wasm, `backend-<name>-small` and
    `backend-<name>-wasm` (the build with every backend less the build
    without that one); and what any one brings beyond its own share,
    `backends-shared-small` and `backends-shared-wasm`.
  - Peak memory of one `--bench 1` run over every Temper file:
    `peak-memory-parse-all` (lex and parse), `peak-memory-ast-all`
    (with grammar trees), and `peak-memory-settle-all` (each file
    settled as a library).
  - Stage times over every Temper file: `lex-ms`, `lex-parse-ms`,
    `lex-parse-grammar-ms`, `lex-parse-module-ms`, and
    `lex-parse-module-settle-ms`. Interpreter loops, per iteration:
    `interp-loop-us`, `interp-calls-us`, `interp-closures-us`, and
    `interp-fib-us`. Kotlin's time over ours: `*-kotlin-over-rust`.
  - Whole test runs: `chromarite-tests`, `ordable-tests`, and
    `ordable-import`.
  - Build and test steps, one row per run, wall seconds: a backend
    crate's `cargo test -p temper-be-<backend>` (mostly its functional
    tests) is `funtest-<backend>-s`; another crate's,
    `test-<crate>-s`; `clippy-s`; `build-tests-s` (every test built);
    `build-wasm-small-s`; `build-tempest-release-s` (from clean); and
    `build-tempest-incremental-s` (after touching one frontend file).
    Each comes with its CPU seconds, children included, as
    `<id>-cpu-s`.
- `value` and `value_max`: a number, or a range's low and high ends
  (most of the older timings are ranges of medians). The charts plot
  the middle, with the range as a whisker.
- `unit`: `bytes`, `KiB`, `MiB`, `ms`, `us`, `s`, or `ratio`. The
  oldest sizes are in KiB.
- `conditions`: `key=value` pairs joined by `;`:
  - `ci=github`, `cpu=<model>`, and `cores=<n>`: measured nightly on
    a GitHub runner, which CPU it had, and how many cores;
  - `run=rust`, `kotlin-warm`, or `kotlin-jvm`; `runner=tempest`,
    `py`, or `lua`; `features=net` or `no-net` (none before `net`
    existed); `build=release`; `args=--module`;
  - `files=N`: how many Temper files were read (on GitHub, this
    repository's own; before, about 850 from three repositories);
  - `load=...`: what else was running, when known; and `tree=dirty`.
- `notes`: short free text: which script measured it, and how, or what
  changed.

## Sizes are exact; times are a trend

Sizes are the same wherever they're built with the same toolchain.
Times aren't: GitHub's shared runners differ in CPU from night to night
(see `cpu=`), share their hosts, and vary by tens of percent, so read
the timing charts' rolling median, not a single night, and compare a
change's speed by alternating runs on one quiet machine (`main`'s
`compat/bench_*.py`), not by these rows. The times from GitHub's
runners and the earlier ones from a local machine are charted apart.
