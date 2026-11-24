# pr-stats

This is an orphan branch of temperlang/temper: it shares no history with
`main` and holds no compiler source, only a small CI tool for measuring
open branches (pull requests and drafts) against `main` without merging
anything or touching those branches. It's orphan by design, so it stays
tiny and can be reused for other simple CI tasks like this one.

## What it measures

For each branch in `branches.txt`, one CI job builds `temper` from the
branch and from its merge-base with `main` (the `small` profile, which
is what's released) on the same runner and runs `measure.py`, which
compares them, alternating which runs first, with five runs of each.
Measuring against the merge-base, not `main`'s tip, means each table
shows only that branch's own changes, even when it's behind `main`. A
stacked branch can name its base branch instead (below). It measures:

- the binary's size;
- `temper build` of all of std (through a tiny library that uses it), of
  [Kelpto](https://github.com/temperlang/kelpto), and of
  [Argmas](https://github.com/temperlang/argmas) (their default
  branches): wall time, CPU time, and peak memory, as median and range,
  and the generated code's size per backend;
- `temper test -b py` on Argmas, wall time.

A library that fails to build with either binary is skipped, and the job
summary says why. Each job's summary has its table (metric, the
merge-base, the branch, and the change in percent), and also says, for
information, how many commits the branch is behind `main`.

## Use

1. Edit `branches.txt`: one branch per line; `#` starts a comment. For
   a branch stacked on another, put its base branch after it, as
   `error-custom-kinds error-common-ground`; it's then measured against
   its merge-base with that branch.
2. Commit and push to `pr-stats`. The push runs
   `.github/workflows/pr-stats.yml` (`workflow_dispatch` works only for
   workflows on the default branch, so a push is the trigger).
3. Read each job's summary in the Actions run.

`measure.py` needs only Python 3.11 or later and runs locally too:

    python3 measure.py --base path/to/temper-main --head path/to/temper-branch \
        --lib argmas=path/to/argmas --test-lib argmas --repeats 2

`python3 measure.py --help` lists the options.
