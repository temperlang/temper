# CI and supply chain

CI runs other people's code: GitHub Actions, compilers, and the
checkers of generated code. Each is pinned, so a re-pointed tag or a
compromised release can't reach our builds, or our release token,
unnoticed; and each pin moves only on purpose, to a release at least two
weeks old, so a bad release has time to be caught first.

## What runs where

Linux x64 on every push and pull request, so a change is checked in
minutes; every other platform `temper` is released for nightly or
weekly, and whenever someone asks (below).

### Every push and pull request: Linux x64

[ci.yml](../../.github/workflows/ci.yml) runs on every push to `main`
and every pull request, on `ubuntu-24.04`, in four parallel jobs, so a
run takes about as long as its slowest job. It can also be run by hand
on any pushed branch (Actions, CI, "Run workflow", or `gh workflow run
ci.yml --ref <branch>`), as for a branch not yet ready for a pull
request; with no base to compare with, every job runs.

| Job | What it checks |
| --- | --- |
| `lint, core` | formatting; clippy, with and without default features; the workflows parse as YAML; the docs' links; Temper's functional tests on the interpreter; the size and speed charts; the rules for release tags; then every crate's tests but the backends' and `tempest`'s, and again without default features, `tempest`'s included |
| `cli` | The `tempest` crate's tests with every backend, so every toolchain |
| `py, js, lua, rust` | those backends' tests, with mypy, pyright, TypeScript, Mocha, LuaLS, Lua 5.4.6 and LuaSocket, and again on LuaJIT; the Rust runtime's own; and the version's spellings, sorted by each ecosystem's comparator |
| `java, csharp, cpp` | those backends' tests, with Java 21 and JUnit, .NET 6, and g++ 13, sanitized too, and libcurl; and `strict`: all generated C++ at -O2 by clang++ (g++ 12 and 14, and 14 at -O3, nightly) |
| `release, playground` | `temper`'s release build, the VS Code extension (tested in a real VS Code), the docs' examples, and the docs site; the wasm build, the playground, and its smoke test in headless Chromium |

`cli` and `release, playground` check out the whole history and its
tags (`fetch-depth: 0`), as binaries.yml and pages.yml do, so the
`temper` and playground they build name a real dev build
(`0.7.0-alpha.0.123`); the other jobs' builds don't say their version,
so they check out only the commit.

Each check is a step of its own that runs even when an earlier one
fails, so a failure names its check, and one doesn't hide another
(unless it needs a build that failed). Each job sets up only the
toolchains its tests use, through one local action,
[.github/actions/toolchains](../../.github/actions/toolchains/action.yml),
which also says why each tool is there. The tests run with
`TEMPER_REQUIRE_CHECKERS`, so a missing tool fails them rather than
skipping.

**What a change runs.** A first job, `changes`, lists the files changed
(`git diff` against the pull request's base, or against the commit
before a push), and the others run by what they read:

| Changed | Runs |
| --- | --- |
| only `notes/` | `lint, core`, without `core`'s tests |
| only `docs/`, `README.md`, and `notes/` | `lint, core` and `release, playground`: the REPL transcripts in the docs (`core`), the docs' examples, and the docs site |
| anything else | everything |

So notes alone skip the builds; anything outside `notes/`, `docs/`, and
`README.md`, even another Markdown file, runs everything, as do a new
branch and a base that can't be fetched. A skipped job counts as passed
for a required check (a workflow-level `paths-ignore` would leave
required checks waiting instead).

Each job caches its build with `Swatinem/rust-cache`, keyed by job (and
by group's `id`, for the backends, since keys can't have commas), saved
only from `main`; pull requests read
`main`'s. The cache holds third-party crates, built, and cargo's
downloads: the workspace's own crates are rebuilt in each job, which is most
of a job's build, so there are few jobs, each running many checks. A
newer push to a pull request cancels the older run.

**ccache.** The jobs that compile C++ (`cli`, `java, csharp, cpp`, and
nightly.yml's `cpp`, `libraries`, and `platforms`) compile it through
ccache, set up by one local action,
[.github/actions/ccache](../../.github/actions/ccache/action.yml): CMake's
builds by `CMAKE_CXX_COMPILER_LAUNCHER`, and the C++ backend's own
tests, which run `g++` and `clang++` themselves, through ccache's links
named for them, first on `PATH`. Each job keeps its own cache (1 GB at
most), saved under a key of its own each run on `main`, as rust-cache's,
and restored from the newest, so it follows the code; pull requests
read `main`'s. The runtime's precompiled header is cached too, so the
files that include it hit. Each job's summary shows ccache's stats. Not
on Windows, where CMake's MSVC builds put debug info in `.pdb` files,
which ccache can't cache. The `netresponse` tests' generated C++ names
the port of a server the test starts, so their files miss every run.

**Rough timings:** 3 to 8 minutes a job in November 2026's runs, so a
run takes about as long as `java, csharp, cpp`, the longest
(notes/test-speed.md, "C++ in CI"). A change to notes alone runs only
`lint, core`'s lints and checks, a few minutes. GitHub's standard
runners are free for public repositories, so the job count is about
time, not cost.

### Nightly, weekly, and on demand: the other platforms

[nightly.yml](../../.github/workflows/nightly.yml) runs its Linux jobs
and `platforms-quick` every night (03:41 UTC), `platforms` every
Sunday (05:17 UTC), and all of them whenever it's run by hand: on
GitHub, **Actions → Nightly → Run workflow**, picking a branch (or `gh
workflow run nightly.yml --ref BRANCH`). Its failures block nothing;
they're the cue to fix a platform before release.

A scheduled run skips what an earlier one already tested on the same
code: a first job, `recent`, reads the date of `main`'s latest commit,
and the nightly `cpp`, `huge`, and `platforms-quick` jobs run only if
it's under 26 hours old (a day, with room for GitHub's delays in
starting scheduled runs), and the weekly `platforms` only if it's
under 8 days.
`libraries` and `latest-checkers` run every night regardless, since
they test other repositories' `main` and the checkers' latest
releases. Run by hand, everything runs.

Besides its Linux jobs (more C++ compilers, `strict` by g++ 12 and 14
and clang++ at -O2 and g++ 14 at -O3, the huge program on every
backend, real libraries, and the checkers' latest releases; below), it
tests, natively, each other
platform [binaries.yml](../../.github/workflows/binaries.yml) releases
for, one job each from one definition, in two tiers:

| Target                      | Runner             |
| --------------------------- | ------------------ |
| `aarch64-unknown-linux-gnu` | `ubuntu-24.04-arm` |
| `aarch64-apple-darwin`      | `macos-15`         |
| `x86_64-pc-windows-msvc`    | `windows-2025`     |
| `aarch64-pc-windows-msvc`   | `windows-11-arm`   |

There's no Intel macOS (`x86_64-apple-darwin`) binary, as in Temper's
earlier releases' bundles; Intel Macs can build `temper` from source with
cargo.

- **`platforms-quick`, nightly,** tests the code that differs by
  platform, which needs no backend's toolchain: first `temper-os`, the
  OS calls, linted by clippy as that platform compiles them (Linux's
  push CI lints only Linux's), with the inventory of unsafe code; the
  REPL's Ctrl-C
  (SIGINT on Unix, the console's handler on Windows), ANSI color on
  Windows' console, and where history is kept (`~/Library/Application
  Support` on macOS, `%APPDATA%` on Windows); and child processes'
  (`temper-tmpl`'s `children.rs`): process groups and time limits on
  Unix, and time limits alone on Windows, which has no process groups
  yet. Then all of `temper`'s tests without backends.
- **`platforms`, weekly,** does the same, then each backend's tests, a
  step each, with the same toolchain action as `ci.yml`'s, and
  `temper`'s tests with that platform's backends built in. It repeats
  the quick tier's steps, which share its build, so every run of
  either tier checks the REPL and child processes.

Tests that need Linux or Unix skip by `cfg`: the terminal test needs
Linux's `script` (so it runs on Linux Arm64 too), and the Ctrl-C test
sends SIGINT. On Windows, a test of its own sends Ctrl-Break, which,
unlike Ctrl-C, can go to one console process group, and the REPL
takes as Ctrl-C. A UTF-8 test, on every platform, checks piped
output's bytes, so no code page converts them. So that a platform
can't pass by leaving tests out,
[tests-ran.sh](../../.github/ci-tools/tests-ran.sh) fails a step
unless at least a given number of tests passed (for the REPL's
command, all of them on macOS and Windows); raise its counts as
tests are added. The backends' tests run with
`TEMPER_REQUIRE_CHECKERS`, so a missing tool fails, except what a
platform is let off by name (below). Windows checks text files out
with CRLF line ends, so the job turns that off (`core.autocrlf false`)
before checking out. Warnings are errors (`RUSTFLAGS`), so code only
these platforms compile stays as clean as clippy keeps Linux's; the
last step, `temper`'s tests with backends, builds without, as
`temper test -b rust` passes them on to cargo on generated crates.

**Each platform's toolchains:**

| | Linux Arm64 | macOS Arm64 | Windows x64 | Windows Arm64 |
| --- | --- | --- | --- | --- |
| Python | the image's 3.12 | 3.12 from uv | 3.12 from uv | 3.12 from uv |
| mypy, pyright, TypeScript, Mocha | pinned | pinned | pinned | pinned |
| Node | 24 | 24 | 24 | 24 |
| Lua | 5.4.6 and LuaSocket, Ubuntu's | Homebrew's, no LuaSocket | MSYS2's, no LuaSocket | **none** |
| LuaLS | **skipped** | **skipped** | **skipped** | **none** |
| Java, JUnit | 21, 5.9.2 | 21, 5.9.2 | 21, 5.9.2 | 21, 5.9.2 |
| .NET | 6 | 6 | 6 | 6 |
| C++ | g++ 13, sanitized, and `strict` by g++ 12 and 14 and clang++ | Apple's clang, through CMake | MSVC, through CMake (Ninja) | MSVC, through CMake (Ninja) |
| Rust | 1.85.0 | 1.85.0 | 1.85.0 | 1.85.0 |

What's left out, and why:

- **LuaLS** is checked against a SHA-256 recorded for each platform's
  download in [install.py](../../.github/ci-tools/install.py)'s
  `LUALS_SHA256`, and only Linux x64's is recorded. The job lets it be
  missing (`TEMPER_ALLOW_MISSING: LuaLS`), so the Lua tests skip their
  type check, saying so. To check LuaLS on a platform, record the
  `sha256sum` of its asset (`lua-language-server-VERSION-linux-arm64.tar.gz`,
  `-darwin-arm64.tar.gz`, or `-win32-x64.zip`) there; once all three
  are, drop the allowance. LuaLS has no Windows Arm64 build.
- **Lua on Windows Arm64:** its image has no MSYS2, so the job leaves
  out the Lua backend (its tests, and the `lua` feature from
  `tempest`'s).
- **LuaSocket off Linux:** std `net`'s requests on Lua fail as Kotlin's
  suite expects of a Lua without a client, as the tests note.
- **The C++ backend's own runs on macOS and Windows** (`runs`,
  `strict`, `co_await_interop`, and `net_client_from_host`, skipped by
  name): they build with g++'s options and sanitizers. C++ is tested
  there through CMake, as users build it, by `temper`'s tests
  (`temper test -b cpp` and CMake's build of `temper build`'s
  output); the C++ backend's goldens and other tests still run.
- **LuaJIT** runs on Linux x64 only, in `ci.yml`.

`TEMPER_ALLOW_MISSING` takes comma-separated words, each matched in a
missing tool's message; the test still says what it skipped.

**Timings,** to be checked against the first runs: `platforms`' jobs
build `temper` twice (with and without `RUSTFLAGS`) and each backend's
tests, so perhaps 30 to 60 minutes, Windows the slowest;
`platforms-quick`'s build `temper` once, without toolchains, so perhaps
10 to 15. Standard runners are free for public repositories, on every
OS; `platforms` runs weekly, and the nightly jobs skip a day without
commits, to keep the queue short and spare GitHub's machines.
Should GitHub stop offering an image, drop its row and keep building
that target in binaries.yml.

[binaries.yml](../../.github/workflows/binaries.yml) builds the released
binaries nightly, cross-compiling Windows Arm64 on x64. Its `smoke`
job runs each platform's archive, as users download it, on that
platform before anything is published: its contents, `version`,
`init`, `test` and `run` on the interpreter, and the REPL, piped and
(off Windows) on a terminal
([smoke.py](../../.github/ci-tools/smoke.py)). nightly.yml's
`platforms` jobs test the rest, with each language's tools.

### On a `v` tag: the release

[release.yml](../../.github/workflows/release.yml) checks the tag
against the workspace's version, calls binaries.yml for the binaries,
packs the runtimes and std for each registry with the Linux x64 `temper`
just built ([release.py](../../.github/ci-tools/release.py)), and makes
the GitHub release, a pre-release for an alpha, beta, or rc tag
([Releasing](releasing.md)). It publishes to no package registry.

## What's pinned

- **Actions,** GitHub's own and third parties', by full commit SHA with
  the version in a comment (`uses: actions/checkout@<sha> # v4.4.0`).
  `dtolnay/rust-toolchain` is pinned to the commit of its `1.85.0`
  branch, which names the toolchain; it has no version tags.
- **Tokens:** every workflow's token is read-only (`permissions:
  contents: read`) except where a job needs more: the `release` and
  `nightly` job in [binaries.yml](../../.github/workflows/binaries.yml)
  and the `release` job in
  [release.yml](../../.github/workflows/release.yml) (`contents:
  write`), and [pages.yml](../../.github/workflows/pages.yml) (`pages:
  write`, `id-token: write`). release.yml's call of binaries.yml grants
  it `contents: write` too, as a called workflow can't otherwise have
  what its `nightly` job asks for, though that job never runs for a
  tag; binaries.yml's other jobs stay read-only.
- **Crates:** every `cargo` command over the workspace runs with
  `--locked`, so CI builds exactly `Cargo.lock`. Crates the backends'
  tests generate have no lockfile and aren't locked.
- **uv,** by version and SHA-256 (setup-uv's `version:` and
  `checksum:`).
- **Checkers of generated code,** and Mocha, which runs generated JS
  tests, installed by
  [.github/ci-tools/install.py](../../.github/ci-tools/install.py):
  TypeScript, pyright, and Mocha from the lockfile beside it, by `npm ci
  --ignore-scripts`; mypy at an exact version by `uv tool install`; and
  LuaLS at an exact version, its download checked against a recorded
  SHA-256.
- **Lua and LuaSocket,** from Ubuntu's archive (`lua5.4` and
  `lua-socket`), by pinning the jobs that install them to
  `ubuntu-24.04`, whose packages are Lua 5.4.6 and LuaSocket 3.1.0, the
  versions the tests use locally. The install step checks both
  versions, so a change fails there, not in the tests.
- **VS Code,** for the extension's tests, by `VSCODE_VERSION` in
  [editors/vscode/build.py](../../editors/vscode/build.py).
- **The version comparators** (ci.yml's `Version comparators` step):
  `packaging` 25.0 and node-semver 7.7.2, by exact version, into the
  job's temp directory; LuaRocks' sources at v3.11.1's commit; Maven
  and the .NET SDK as the image has them.
- **Packing a release** (release.yml's `packages`): Rust 1.99.0 (its
  `dtolnay/rust-toolchain` branch's SHA), as only cargo 1.90 and later
  packs temper-std beside an unpublished temper-core; hatchling 1.27.0,
  which builds the wheels and sdists (`PY_BUILD_CONSTRAINTS` in
  release.py); LuaRocks, zip, and unzip from Ubuntu's archive; Java 21,
  .NET 6, and Node 24, as the tests', and Java 8, the Maven toolchain
  temper-core's pom asks for; Maven as the image has it.

- **Runner images:** ci.yml's and pages.yml's jobs are pinned to
  `ubuntu-24.04`, for Lua (above); nightly.yml's `platforms` and `platforms-quick` to the
  images in its table above, so a new image arrives as a deliberate
  bump, not a surprise.

Not pinned: the other jobs' runner images, and what the images and
`apt` install besides Lua (ccache too, and on macOS, Homebrew's); Node,
Java, and .NET by major version; and
Playwright's Chromium (from its own CDN, by the version in the Mermaid
renderer's lockfile).

## Flaky downloads

Downloads from servers far from GitHub's runners sometimes fail, so CI
avoids or retries them: Lua comes from Ubuntu's mirrors, not lua.org;
[apt-install.sh](../../.github/ci-tools/apt-install.sh) skips packages
the image has, updates from Ubuntu's own sources only, and retries each
download three times with a 30-second timeout, so a dead mirror fails
in minutes; Playwright's Chromium is installed without `--with-deps`
(which runs apt), as the image has Chrome's libraries; VS Code is cached
(`actions/cache`, keyed on its version), and
[test/run.ts](../../editors/vscode/test/run.ts) tries a failed download
three times before running the tests, which run once, and prints each
error inside an `AggregateError` or `cause`.

## Latest checkers, nightly

New checker releases often find new things to say about generated
code. The `latest-checkers` jobs in
[nightly.yml](../../.github/workflows/nightly.yml) run the backends'
tests with one checker at a time at its latest release (TypeScript,
pyright, mypy, or LuaLS), the rest at their pins. They block nothing: a
failure, named for its checker and with the versions in its summary,
is the cue to fix the backend or the tests before bumping that pin.

## The huge program, nightly

Generated data can be past compilers' limits: Java's 64 KB methods and
65,535-byte constants, MSVC's 16,380-byte literals, Lua 5.1's
constants a function, and g++'s and rustc's time and memory,
superlinear in a function's size. Backends split such code under
limits of their own (each backend's `Limits`), and the testkit's
`huge` program, an 8,500-item list, a 1,000-entry map, an 80 KB
string, and 6,000 statements, checks it compiles and runs. That adds
up to a minute to a backend's tests, so only nightly.yml sets
`TEMPER_HUGE`, which adds it to every backend's `runs` (`cpp` for C++,
`huge` for the rest, a job each). Every push checks the same splits
on a small copy of it (`split_shape`, beside Java's `java-split`), each
backend's limits set low, against goldens; set `TEMPER_HUGE=1`
locally to run the huge program too.

## Real libraries' Python types, nightly

Each nightly `libraries` job that tests a library on `py` then
type-checks the library's generated packages, tests aside, with mypy
and pyright, strict and at their pins, as the Python backend's tests
check theirs ([check_library_py.py](../../.github/ci-tools/check_library_py.py),
which also runs locally on a `temper.out/py`). It's a ratchet:
[library-py-baseline.json](../../.github/ci-tools/library-py-baseline.json)
gives each library's allowed errors per checker (none, if unlisted), a
count over its allowance fails the job, and one under it passes with a
notice to lower the allowance in the same change, so allowances only
shrink. Once every count is 0, `--strict` allows none whatever the
baseline says.

## Bumping a pin

- **Actions:** Dependabot ([.github/dependabot.yml](../../.github/dependabot.yml))
  opens one monthly pull request updating the SHAs and their comments,
  in the workflows and the local action together, for releases at least
  two weeks old, majors included. Read each major's release notes and
  check the inputs we pass before merging, and after merging, run
  Nightly, Binaries, and Pages by hand, since only `ci.yml` runs on the
  pull request. By hand: resolve the release's
  tag to its commit (`git ls-remote <repo> refs/tags/<tag>`, taking the
  `^{}` line for an annotated tag), and update the SHA and the comment
  everywhere the action is used.
- **Ubuntu, for Lua:** moving a pinned job to a newer image changes
  `lua5.4` and `lua-socket`; update the versions its Lua step checks,
  and the tests' local versions, together.
- **macOS and Windows images:** the matrices of `platforms` and
  `platforms-quick` in nightly.yml, once GitHub offers the newer image;
  its cache starts afresh.
- **VS Code:** `VSCODE_VERSION` in build.py; CI's cache key follows it.
- **The Rust toolchain:** take the SHA of the new version's branch
  (`git ls-remote https://github.com/dtolnay/rust-toolchain
  refs/heads/<version>`), and update the workspace's `rust-version`.
- **uv:** `version:` and `checksum:` in each workflow and in
  [the toolchains action](../../.github/actions/toolchains/action.yml);
  the checksum for `x86_64-unknown-linux-gnu-<version>` is in setup-uv's
  `src/download/checksum/known-checksums.json`, which setup-uv also
  checks the other platforms' downloads against.
- **TypeScript, pyright, Mocha:** the version in
  [package.json](../../.github/ci-tools/package.json), then `npm install
  --package-lock-only --ignore-scripts` there.
- **mypy, LuaLS:** `MYPY`, `LUALS`, and `LUALS_SHA256` in
  [install.py](../../.github/ci-tools/install.py); each hash is
  `sha256sum` of that platform's asset from LuaLS's release, as
  `lua-language-server-<version>-linux-x64.tar.gz`.

A checker bump comes with any goldens or backend fixes it needs, after
the nightly job for it has passed.
