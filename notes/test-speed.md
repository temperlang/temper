# Test speed: `tempest test` on every backend

Part of the [rewrite plan](README.md). A proposal (2026-10-30), not a
decision.

`tempest test` runs a library's `test` blocks on the interpreter today,
which is fast but checks no backend. Kotlin's `temper test -b LANG`
builds each backend's output as a real package (Maven, `dotnet`, CMake,
cargo, pip, npm) and runs it with that ecosystem's tools. Leaning on
package descriptions is reliable and reuses the tooling, but some
backends are slow that way. Testing on any backend should be as fast as
we can make it, without losing that reliability.

## Two paths, kept in agreement

1. **The package path:** what Kotlin does, and what users get. Generated
   output always includes the package descriptions. CI runs this path.
2. **The fast path:** the same generated sources, built and run by
   calling each language's tools directly, skipping package managers
   and per-package build setup. It's the local default.

A check that the two agree keeps the fast path honest: the same passes
and failures on the functional tests and library tests, in CI at least,
maybe nightly. Where they can't agree (dependency resolution, native
libraries), that library falls back to the package path.

## The fast path by backend

First guesses, to be checked against measurements:

- **Python and Lua:** run from the generated folders with the runtime on
  `PYTHONPATH` or `LUA_PATH`, all tests in one process, with no venv or
  install. The Lua backend's harness does this (below, "Lua's fast path,
  as tried").
- **JS:** link packages into one `node_modules` and run everything in one
  Node process. The functional-test harness already copies the
  runtime this way instead of running `npm install`.
- **Rust:** a persistent target directory, `temper-core` built once per
  machine, one test binary per library, and a dev profile tuned for build
  speed. The functional-test harness already shares one offline cargo
  workspace and target directory.
- **Java:** `javac` against a prebuilt runtime, without Maven, and a
  small test runner of our own. The Java backend's functional-test
  harness does this already (below, "Java's fast path, as tried").
- **C#:** Roslyn's `csc` directly, rather than a build per package.
  The C# backend's functional-test harness does this already (below,
  "C#'s fast path, as tried").
- **C++:** the compiler directly, a precompiled header for the runtime,
  and outputs cached by content hash. The C++ backend's harness does the
  first two (below, "C++'s fast path, as tried").
- **Go:** `go test`, already fast with its build and test caches.

## Java's fast path, as tried (2026-10-30)

The Java backend's run test (`crates/temper-be-java/tests/functional.rs`)
uses no Maven, and what it found, unmeasured, by eye:

- **The runtime needs nothing but the JDK.** `temper-core`'s main
  sources compile with `javac --release 8` alone, in about a second,
  without its `pom.xml`; JUnit is only for its own test. So "prebuilt"
  can be a directory of classes, compiled once and kept, keyed by a hash
  of the sources and the JDK's version, with no jar.
- **One `javac` for everything generated.** Each library is its own
  package, so all of them, and a test harness, compile in one run, a
  second or two for 10 small libraries; JVM startup, paid once, is most
  of it. A later step could keep one compiler warm (the
  `javax.tools.JavaCompiler` API in a long-lived JVM).
- **Running is cheap.** A small program's `java -cp … Main` takes about
  0.05 to 0.1 seconds; `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC` suit
  short runs. Many libraries' tests could share one JVM, through a
  runner of our own that loads each library's classes in turn.
- **What the package path adds:** dependency resolution (here only
  `temper-core`, and JUnit for tests), the jar, and Maven's own startup,
  which is the slow part. `pom.xml` is still generated, as Kotlin's, so
  the package path stays available.

## C#'s fast path, as tried (2026-10-30)

The C# backend's run test (`crates/temper-be-csharp/tests/functional.rs`)
uses no MSBuild and no NuGet restore, and what it found, by eye on the
.NET 6.0.402 SDK:

- **`csc` runs alone.** `dotnet exec SDK/Roslyn/bincore/csc.dll`, with
  `-noconfig -nostdlib` and .NET 6's reference assemblies
  (`packs/Microsoft.NETCore.App.Ref/6.0.x/ref/net6.0/*.dll`) as `-r:`s,
  is all a library needs; the SDK's .NET analyzers load with
  `-analyzer:` and a global config (`dotnet_analyzer_diagnostic.severity
  = warning` is `AnalysisMode` `All`). What MSBuild adds that matters:
  `DefineConstants` (`TRACE` keeps the runtime's `Trace.WriteLine`
  calls, which logging falls back to; without it nothing logs) and
  `NoWarn` 1701 and 1702.
- **The runtime is one more `csc`,** about half a second, kept under
  `CARGO_TARGET_TMPDIR`, keyed by a hash of its sources, the options,
  and the SDK's version. Its one dependency, `Microsoft.Extensions.
  Logging` 8.0.0 and what that needs, are read straight from NuGet's
  cache (`~/.nuget/packages/ID/8.0.0/lib/net6.0/`), never restored.
- **One `csc` for every library:** each library has its own namespace,
  so all of them compile into one assembly in one run, about a second
  (1.06 to 1.16 seconds, three runs) for 12 small libraries with the
  analyzers; Roslyn's startup is most
  of it. Each generated `Program.cs` declares `Program` in the global
  namespace, so one is compiled as itself and the rest, renamed, into a
  runner that runs a library's static constructor by name, a few tenths
  of a second more. The whole test takes about 3 seconds warm.
- **Running is cheap:** `dotnet Runner.dll NAME` takes about 0.04 to
  0.05 seconds, given a `runtimeconfig.json` (with `rollForward` `Major`,
  so a newer runtime will do) and the dependencies copied beside it. Many
  libraries' tests could share one process, as Java's could share one
  JVM.
- **No build servers:** `csc.dll` run directly starts no
  `VBCSCompiler` and no MSBuild node, so nothing lingers; `dotnet
  build` would need `-p:UseSharedCompilation=false -nodeReuse:false`
  (.NET 6's SDK has no `--disable-build-servers`), and `dotnet
  build-server shutdown` after.
- **What the package path adds:** restore (offline, `--source
  ~/.nuget/packages` works, since NuGet's cache is a folder feed), a
  build of `TemperLang.Core` for both of its frameworks, and MSBuild's
  own startup: about 7 seconds cold for one library, by hand. The
  `.csproj`s are still generated, as Kotlin's, so that path stays
  available.

## C++'s fast path, as tried (2026-10-30)

The C++ backend's run test (`crates/temper-be-cpp/tests/functional.rs`)
uses no CMake, and what it found, by eye (not measured) with g++ 13.3:

- **The runtime is header-only,** so "built once" means a precompiled
  header: a one-line `runtime.hpp` including `<temper-core/core.hpp>`
  (a header can't be precompiled as the main file with `#pragma once`
  in it), compiled with `-x c++-header` into a `.gch` of about 58 MB in
  about 1.4 seconds, and used through `-include runtime.hpp`, so it's
  the first thing each file sees. The generated header's own `#include
  <temper-core/core.hpp>` is then skipped, as `#pragma once` carries
  over. One per set of options (plain, AddressSanitizer with
  UndefinedBehaviorSanitizer, ThreadSanitizer), since a `.gch` serves
  only the options it was built with; `-Winvalid-pch` makes a mismatch
  an error rather than a silent slow path. Kept under
  `CARGO_TARGET_TMPDIR`, keyed by a hash of the headers, the options,
  and `g++ --version`.
- **A file takes about 0.2 seconds with the header, 0.55 without;**
  under the sanitizers, about twice that. Parsing the runtime is most
  of a file's time, so the header pays off past a couple of files.
- **Each `.cpp` on its own,** four at a time, as a user's build would,
  rather than one file including every library's: a library's missing
  include or a clash between two would hide in one big file. Each
  library has its own namespace, so one runner links every library's
  `init.cpp` and runs one by name, as C#'s runner does; each
  `main.cpp` is compiled, and one linked as Kotlin's program.
- **The whole test takes about 10 seconds warm** and 15 cold, for 12
  libraries built three ways (plain, ASan with UBSan, and TSan for the
  threads), most of it the sanitizers' compiles and links.
- **ThreadSanitizer** in GCC 13 fails to start ("unexpected memory
  mapping") about half the time under Linux 6's address randomization;
  a non-PIE binary helps, `setarch -R` or `sysctl vm.mmap_rnd_bits=28`
  fixes it, and where neither is allowed (a sandbox), the test retries.
- **What the package path adds:** none, as Kotlin writes no build files;
  its `temper build` runs `g++ -I.. -std=c++14` on a library's `.cpp`
  files and its dependencies', about a second for `module-state` by
  hand, and a CMake project like Kotlin's tours' (an
  `add_library` of `init.cpp`, an executable of `main.cpp`) configured
  and built in about 1.3 seconds, cold, by hand.

## Lua's fast path, as tried (2026-10-30)

The Lua backend's run test (`crates/temper-be-lua/tests/functional.rs`)
uses no LuaRocks, and what it found, timed by hand with Lua 5.4.6 (three
runs each):

- **Nothing to build.** The runtime is Lua source, copied beside the
  libraries (where Kotlin's `temper.out/lua` has it), and loads in about
  6 milliseconds; Lua compiles each chunk as it's required.
- **One process for every library:** a 7-line runner requires each
  library named on its command line, after a line naming it, catching
  any error as a line too, so the test splits one output by library.
  Kotlin's `LUA_PATH` (`?.lua;?/init.lua`), from the directory holding
  the libraries, is all `require` needs. 14 small libraries take 6 to 7
  milliseconds in one process, against 47 to 73 for a process each
  (`lua LIB/init.lua`, as Kotlin runs one, which the test also does
  once). Requiring a library runs its top level, so tests would be a
  call per test after the require, as Kotlin's luaunit runner makes
  them.
- **One process, not one state per library:** libraries share the
  process's globals, so one that wrote a global could affect the next;
  generated code writes none (LuaLS's global diagnostics check), and
  each library's state is its own chunk's locals. Where isolation
  matters, a runner could load each into a fresh environment
  (`load(…, "t", setmetatable({}, { __index = _G }))`) at no real cost.
- **The checks are cheap too:** `luac -p` on every generated file takes
  about 6 milliseconds. LuaLS, a language server started for one
  `--check`, will be the slow part (not timed: it isn't installed
  here), which only CI pays for now.
- **What the package path adds:** LuaRocks and a rock per library, which
  Kotlin's rockspecs don't yet install usably
  ([kotlin-differences.md](kotlin-differences.md), "Lua rockspecs that
  don't install"); its `temper test` runs each library's tests in a
  process of its own, with luaunit.

## Python's functional tests in bulk, as tried (2026-11-04)

`temper-be-py`'s `runs` started a `python -c` per case that logs (71 of
them), each importing the runtime afresh. In bulk (`TEMPER_PY_BULK=1`
then, the default since 2026-11-05, below), it runs them all in one interpreter instead (`tests/bulk_runner.py`): each
case's projects go first on `sys.path`, its package is imported, its
async work awaited (without shutting the runtime's executor down), and
every module the import added is forgotten, so the next case's
same-named libraries (`modules`, `temper_std`) load fresh. A marker
line before each case splits stdout; an exception prints a marker and
its traceback. Any case whose lines don't match, or that a crash leaves
unfinished, runs again alone, and that result is the one reported, so
failure messages are exactly the per-case path's. All 71 pass in bulk;
none needed to run alone. Cases run as tests (`temper_core.testing`,
writing JUnit XML) and the compile and type checks stay per case.

Measured on `runs` alone, without mypy and pyright, alternating, four
runs each with two other agents busy: 11.9–13.1 s per case, 5.3–6.3 s
in bulk, about 2.2× faster. With the checkers, as in CI, `runs` takes
about 275 s, nearly all mypy and pyright per case, so bulk saves only
about 2% there. Bulk stayed off by default then, as CI barely gained;
with the checks batched, it's on (below).

Carrying it over: **Lua** works the same way (one `luajit` with
`package.loaded` cleared between cases; its runtime has no async
executor), though LuaJIT starts so fast that the win is smaller.
**JS** can import each case's module in one Node process
(`await import()` with a cache-busting query, or a fresh `vm` context
per case), but ES modules can't be unloaded, so memory grows per case;
fine at 72. **Java** would gain most: one JVM with a small runner that
loads each case's classes in its own class loader, `System.out`
redirected per case, instead of a JVM start per case. Batching type checks (one mypy or pyright over every case's
package, with their paths kept apart) is the bigger win for Python's
CI time, but it mixes the cases' diagnostics and needs care with
same-named packages (done, below).

### Batched type checks, and bulk by default (2026-11-05)

Per case, mypy and pyright each started once per checked case (68), and
each reloaded typeshed and the runtime: about 236 s of `runs` with both
on, alone on a quiet machine (the CI run's 432 s was under load). Now
each runs once per batch (`check_types`): every case whose package
names don't clash goes in one batch, a library a case imports (std's,
or `imports-library`'s) shares a batch only with an identical copy (a
digest of its `.py` files), and the one case whose `modules` differs
from the `modules` case's goes in a second. Same options and
strictness; mypy adds only `--show-absolute-path`, and pyright's
`include` lists paths relative to the batch's config (it ignores
absolute ones, with a message, which counts as a failed run).

- **Precision:** each line a batch's run prints is traced to the case
  whose root its file is under; each such case is checked again alone,
  and that message, exactly the per-case path's, is reported (or the
  batch's lines, if it passes alone). A run that fails to start,
  crashes (exit 2 or more), or fails with nothing traced checks every
  case of its batch alone. Tried by hand: an untyped function injected
  into `modules` failed `modules` alone, with both checkers' messages;
  a mypy that exits 2 in batches fell back to per case.
  `checks_traced_to_cases` keeps the tracing honest, without a checker.
- **mypy's cache** is kept per batch (`checks/N/.mypy_cache` under the
  test's target directory), across runs: mypy takes 4.5 s cold, 0.3 s
  warm, and revalidates files by hash, so nothing is weakened.
- **pyright** takes about 3.5 s for both batches; its CLI has no cache,
  and `--threads 2` was slower (2.6 s against 2.0 s for the large
  batch), so it's left as is.
- **Measured** (`runs`, one at a time, one other agent idle): 236 s
  before; 17.4 s after, per case, cold cache; 13.7 s warm; 8.7 s warm in
  bulk; 11.7 s in bulk with a cold cache (`size-and-speed.csv`,
  `funtest-py-runs-checked-s`). The whole CI step, `cargo test -p
  temper-be-py` with checkers required, went from 432 s (under load) to
  8.3 s (`funtest-py-s`).

**Bulk is now the default** (`TEMPER_PY_BULK=0` runs each case alone):
with the checks batched, process start-ups are most of what's left, and
bulk halves them. Results stay as precise, since any case that doesn't
log what it expects is rerun alone and that result reported. What bulk
could hide, a case passing only after another one's leftovers, is
narrow: each case's modules are forgotten before the next, and the
runtime keeps no state a case's output depends on. The per-case path
stays for a new case that misbehaves in a shared interpreter.

**The other backends:** JS's `tsc` already checks every case in one
run. Lua's LuaLS checks the shared directory once, then each case kept
apart (a name clash, tests, or std) once more, about 2 s of its test's
5.3 s; batching those few apart cases would save about a second, so
it waits.

**Proposal for Kotlin Temper's funtests:** keep today's per-case
bring-up path as the reference and the default for a new backend, so
its hello world runs without anything shared; then let a backend
offer a bulk runner as an optimization: the harness writes every
case's output first, starts one process (a Python or Lua interpreter,
one Node, one JVM with a class loader per case) that runs each case
after a marker line, and compares each case's segment with its
expected output. A case that fails, or that a crash cuts short, is
rerun alone on the per-case path, whose result is the one reported,
and a case known not to fit a shared process (init order, logging
setup, threads) is marked to run alone. That keeps results as precise
as today, never makes a backend's first tests depend on bulk runs or
on `temper test`, and turns process start-up, a large share of the
JVM backends' funtest time, into a once-per-run cost.

## C++'s functional tests in bulk (2026-11-05)

`temper-be-cpp`'s `runs` took 213 s alone (266 s for the crate in CI,
under load). Timed by phase, with one other agent's Gradle run nearby:
generating 3 s, the plain build and runs 33 s (158 files), tests 13 s,
host-code libraries 8 s, AddressSanitizer with UBSan 107 s (85 files),
and ThreadSanitizer 49 s (85 files). Per file, with the precompiled
header, loading it is about 0.2 s, and the rest is code generation:
0.2 to 1 s plain, 0.6 to 3.7 s sanitized, mostly the runtime's
templates, instantiated again in every file.

- **ThreadSanitizer builds only what it runs:** the threads use
  `module-state` alone, so it builds that, std if it imports it, and a
  runner of its own: 49 s to under 1 s.
- **The sanitizers' build in bulk** (`compile_bulk`): four `.cpp`s,
  each including about 21 of the files, compiled at once, so templates
  are instantiated once a group. 21 files took 11 s in one compile
  against 59 s one by one; the groups take 13 to 19 s each, about
  650 MB of memory apiece. Only for builds the plain build has checked
  file by file, with the same options and warnings; a group holds at
  most one file of each library, whose modules share a namespace. A
  group that fails is compiled file by file, whose errors are reported,
  naming the file, as alone. Tried by hand: a warning injected into
  `hello-world` failed with its file's own message, and a clash between
  two files fell back and passed (`TEMPER_CPP_BULK=0` builds each file
  alone).
- **A work queue,** not batches of four that wait for their slowest.
- **Measured** (`runs`, warm, other agents busy): 213 s before;
  119 s with the above but each file alone; 71 and 104 s in bulk. The
  CI step, `cargo test -p temper-be-cpp`, went from 266 s (and 163 s
  in the next run) to 104 s (`funtest-cpp-s`); every step of the CI
  run together, from 268 s to 217 s.
- **Not taken:** `-O0` cut a sanitized group's compile by 40%, and
  dropping `-g` by 25%, but `-O1` drives warnings like
  `maybe-uninitialized` and `-g` makes ASan's reports readable. The
  plain build stays file by file, as a user's build, which is what
  makes the warnings per file. ccache isn't installed, and `ld.gold`
  links the ASan runner only 0.1 s faster.

`TEMPER_CXXFLAGS` adds options to every build but ThreadSanitizer's;
the nightly workflow (`.github/workflows/nightly.yml`) uses it to
run everything under ASan and UBSan, and runs the tests under g++ 12
and 14, by `CXX`.

## `tempest test -b`

Built (2026-11-06): `tempest test -b BACKEND` builds the libraries into
`temper.out/BACKEND`, as `tempest build` does, with std's output beside
them as `std`, as Kotlin's, then runs each library's tests with the
language's own tools, reading their results into the interpreter's
summary (`Tests passed: N of M`), so `-b interp` and `-b py` print
alike (each backend crate's `src/tools.rs`, run by
`crates/temper-driver/src/test_run.rs`):

- **Python:** `python3 -m temper_core.testing result.xml` in the
  library's directory, as Kotlin's `RunPython`.
- **JS:** Mocha with its xunit reporter, `@temperlang/core`,
  `@temperlang/std`, and the libraries linked into
  `temper.out/js/node_modules`, as npm's workspace links them.
- **Lua:** the library's `tests/init-test.lua` with LuaUnit beside it.
- **Java:** `javac` on everything against JUnit 5's jars in Maven's
  local repository, and a small runner of JUnit's launcher, as the
  backend's own tests do, rather than Maven, which needs its network.
- **C#:** `dotnet test --logger:junit`, as Kotlin's `RunCSharp`.
- **Rust:** `cargo test --offline`, one target directory for all.
- **C++:** CMake on the `CMakeLists.txt` tempest writes, building the
  library's `LIBRARY-test` target (its tests, std, and the libraries it
  imports), then running it in the
  library's directory, which writes `test-results.xml`, as Kotlin's
  `RunCpp` runs its `g++` build (2026-11-06). Since 2026-11-19 one
  CMake project in `temper.out/cpp/.build` adds every library, so a
  library others use builds once per run, not once per dependent
  (Kelpto's kelpto-core and std were built for each test library);
  where it doesn't configure, each library builds alone in
  `LIBRARY/build`, as before.

Still planned: the fast paths below (one process per backend for all
libraries, cached runtimes), and the package path checked against them.

### Real libraries, nightly (2026-11-06)

Two kinds of runs, kept apart:

- **Every push, and local CI:** `tempest test` and `build` themselves,
  each backend's runner in `test_run.rs`, and `build -v`'s list of gaps,
  on small fixture libraries in `crates/tempest/tests/cli.rs` (one test
  passing and one failing, on every backend whose tools are there), and
  what real libraries needed, distilled into the shared `libraries`
  program and the goldens. Quick, so they run every time.
- **Nightly:** real libraries' tests on every backend each passes on,
  from a list, `.github/nightly-libraries.json` (name, repository, ref,
  and backends), one job per library (`.github/workflows/nightly.yml`,
  "libraries"). Adding a library is a line of data. Where a library
  runs on `py`, its generated packages are then type-checked (mypy and
  pyright, strict, as the Python backend's tests), each count held to
  its allowance in `.github/ci-tools/library-py-baseline.json`, a
  ratchet that only shrinks (`check_library_py.py`).

Meant to grow to hundreds of libraries: keep the list data, and each
job one library with its backends, so the plan job can later shard the
list (a slice per job) or rotate it (a slice per night, all within a
week), and record results per library and backend, without changing how
a library is run. Not built yet: sharding, rotation, results history,
and libraries' own pinned refs beyond `main`.

### The plan (2026-10-30)

The plan, once std translated, reused the backends' harnesses, which
`crates/temper-be-testkit` now shares:

- **Build once with `tempest build`'s layout,** into `temper.out`, so
  the package path stays available and the fast path runs the same
  files.
- **Per backend, the harness's fast path:** Python and Lua run every
  library's tests in one process from `temper.out/BACKEND`, with the
  runtime on `PYTHONPATH` or `LUA_PATH`; JS one Node process, the
  runtime linked into one `node_modules`; Java one `javac` against the
  runtime's classes, cached by hash, and one JVM with a runner of ours;
  C# one `csc` and one `dotnet` runner; C++ `g++` through the runtime's
  precompiled header, or the generated CMake projects; Rust one cargo
  workspace with a kept target directory.
- **Results as JUnit,** as `temper test` reads them, through the same
  summary (`Tests passed: N of M`), so `-b interp` and `-b py` print
  alike.
- **The package path in CI,** checked against the fast path on the same
  libraries, as above.

## For every backend

- **Content-hash caching:** skip generating, building, and testing
  libraries that haven't changed, as Go caches test results. The hashes
  can be the [library cache](library-cache.md)'s.
- **Runtimes built once,** keyed by version and hash, never per run.
- **Backends in parallel,** within a memory budget.
- **The interpreter as tier 0,** the default for quick edit-and-test
  loops.

## Measure first

Time Kotlin's `temper test` per backend on Chromarite, cold and warm,
splitting setup (package tools, dependency resolution) from compiling
and running. The guess is that Maven, `dotnet`, and CMake setup
dominate. Measure only when the machine is otherwise idle, and never
hold up other work for it.

Fast-path ideas that work out are worth proposing for Kotlin's
`temper test` too.
