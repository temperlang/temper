# tmpl: testing

Part of the [tmpl design](README.md).

## Level 1: Testing

Proposed, in order of when they're worth building:

- **Round trips,** on every tmpl the tests produce: text to model to
  text is identical; model to binary to model is equal; text with spans
  stripped reads back equal apart from spans.
- **The validator everywhere:** after the frontend's translation and
  after each pass, in tests and debug builds.
- **Golden tests** in `tests/tmpl/*.cases`, as `tests/settle` does:
  Temper source to starting tmpl, and starting tmpl to lowered tmpl per
  profile, compared as canonical text without spans.
- **Shared backend tests** (kotlin-differences.md): one set of inputs
  with expected output per backend, including the known Kotlin bugs
  (Boolean `==`, initialization order, Rust's checked index, C++ forward
  declarations, module `var`s under threads).
- **Fuzzing:** translate mutated inputs as `mutated_inputs_settle` does,
  checking that translation never panics, output validates, and passes
  are deterministic; and random valid tmpl from the schema, lowered
  under random profiles.
- **A tmpl interpreter, later** (../history/milestone-3.md): run a program before
  and after lowering and compare output, and compare with the
  frontend's interpreter on the functional tests.

## Level 3: Golden tests

Wanted by the user (2026-10-29): golden tests of both the canonical
text and the view, which leaves out derivable detail. The prettier and
more stable the names, the more stable the diffs. Proposed layout,
with the `=== name` cases and `UPDATE_SNAPSHOTS=1` that the lexer's
and settle tests use:

- **`crates/temper-tmpl/tests/text/*.cases`:** hand-written tmpl, then
  `--- canonical` (what the writer gives back), `--- view`, and
  `--- problems` (validator output, `RULE @node: message`). Every case
  also round trips: read then write gives the canonical text, and
  write then read gives an equal program. As built (2026-10-29,
  Proposed): a section shows only what isn't the default, so
  `--- canonical` only where the input isn't canonical already, and
  `--- problems` only where there are some; `--- error` shows why
  text doesn't read. `--- view` comes with the view. `basics.cases`
  holds valid programs (formats.md's example among them),
  `problems.cases` the validator's rules, and `errors.cases` the
  reader's. With core's declarations (2026-10-29), `tests/qname.rs`
  checks qnames against Kotlin's examples and their derivation, and
  `tests/core_fixture.rs` checks `tests/fixtures/core.tmpl`, a `uses`
  section for core's members, against `lib/core/core.temper`. Beside them, `tests/model.rs` tests what text can't
  express (broken structure, spans), and `tests/round_trip.rs`
  generates random valid programs, which must validate without
  problems and round trip (`TMPL_FUZZ_SEED`, `TMPL_FUZZ_ROUNDS`).
- **`crates/temper-tmpl/tests/lower/*.cases`:** starting tmpl, then per
  variant `--- py` (lowered canonical text), `--- py view`, and
  `--- py names`: each declaration whose output name isn't its source
  name, or that's compiler-made, as `~count#9 int32`.
- **`crates/temper-frontend/tests/tmpl/*.cases`:** Temper source, then
  `--- tmpl` (starting canonical text) and `--- view`, adding lowered
  sections as backends arrive.
- **The suffix count:** a golden file listing every suffixed name per
  variant across the functional tests and std, with the total, so a
  change that adds one shows in review. Chromarite and Ordable are
  counted the same way by a compat check against their checkouts.

Spans are stripped from goldens; a separate test checks P1 and that
lowering keeps every source node's span.

### Backends: goldens, runs, and type checkers

As built with the Python backend (2026-10-30):

- **Goldens:** each backend's output for the functional tests that tmpl
  covers, one file per test, as Kotlin's `…BackendTest` goldens
  (`crates/temper-be-py/tests/goldens/algos-fibonacci.py`), accepted
  with `UPDATE_SNAPSHOTS=1`; layout files are checked by the test.
- **Runs:** each generated program runs against its runtime library and
  must log what the functional test expects of that backend
  (`temper-funtests` honors the log blocks' backend selectors).
- **Type checkers** (the user's direction, 2026-10-30): every backend's
  generated code is checked by that language's checker in its tests,
  automatically, never as a separate manual step. Compiled languages get
  this from their compilers; JS uses TypeScript's, and Lua LuaLS's
  (both below). For Python: pyright in strict mode, locally (where on
  `PATH`) and in CI, and mypy in strict mode too, in CI, where both are
  installed at their latest versions each run, since the two sometimes
  disagree; locally mypy is optional (`MYPY` names one). A test skips a
  missing checker with a message, except in CI, where
  `TEMPER_REQUIRE_CHECKERS` makes a missing one fail.
- **Rust** (2026-10-30): the compiler, by building every generated
  crate, and clippy, with `-D warnings` but for three lints that fire
  on what the functional tests do on purpose (`self_assignment` for
  `zero = zero` and `unnecessary_min_or_max` for `1.min(3)`, which
  Kotlin's output trips too, and `disallowed_names` for a local named
  `foo`, which Kotlin's output avoids only by numbering every name).
  The crates build offline in one cargo workspace with one target
  directory, kept under `CARGO_TARGET_TMPDIR` so builds are
  incremental, against the runtime, `temper-core`, and its `Cargo.lock`;
  `temper-core`'s one dependency, `indexmap`, must be in cargo's cache
  (CI fetches it first). The goldens are also checked to be as rustfmt
  lays them out. A missing cargo, clippy, rustfmt, or `indexmap` skips
  with a message, or fails with `TEMPER_REQUIRE_CHECKERS`. The shared
  programs (`crates/temper-be-testkit/programs/`, the Rust backend's
  own until 2026-10-30) cover what the functional tests don't yet; one
  is hand-written tmpl, for constructs the frontend doesn't translate
  yet.
- **JS** (2026-10-30): each generated package runs under Node (`node`,
  or `NODE`) against the runtime, `@temperlang/core`
  (`crates/temper-be-js/runtime/temper-core`), copied into a `node_modules`
  beside the packages, and TypeScript's checker, `tsc --checkJs --allowJs
  --noEmit --strict --module nodenext`, checks all of them in one run
  (`tsc`, or `TSC`), with JSDoc as their only annotations. It checks
  them against declarations (`.d.ts`) that `tsc` generates from the
  runtime's own JSDoc first, with `--skipLibCheck`, since those
  declarations don't all check themselves (`Pair` is declared twice, and
  `Union` is undeclared). A test that runs as tests (`testing/asserts`)
  runs with Mocha (`mocha`, or `MOCHA`), against std's
  `@temperlang/std` in the same `node_modules`. CI installs the latest
  TypeScript and Mocha 11 with npm, on Node 24. To consider later
  (2026-11-03): Node's built-in runner (`node:test`, stable since
  Node 20, with `--test-reporter=junit`) in place of Mocha, so tests
  need no npm package. It changes the generated test code and
  `package.json`, so with the team and Kotlin's JS together. Bun and
  Deno run `node:test` only partly (Bun lacks mocks, snapshots, and
  timers; Deno's shim reportedly lacks `beforeEach`/`afterEach`), so
  plain `describe`/`it`/`assert` would be the portable subset. A
  missing Node or `tsc` skips with a message, or fails
  with `TEMPER_REQUIRE_CHECKERS`. Besides the 7 functional tests, the
  JS backend runs the shared programs
  (`crates/temper-be-testkit/programs/`), with goldens of its own.
- **JS source maps** (2026-10-30): goldens of each program's map,
  decoded, a line per mapping with the generated and Temper text there
  (`tests/goldens/*.map.txt`), with each mapping checked to land on
  matching Temper; then, in the run test, Node loads each module with
  `--enable-source-maps` and looks up every mapping with its own source
  map support (`findSourceMap`), which must agree and find a source
  file that exists; and an error thrown in a generated function must
  have a stack trace pointing at its Temper line and column. CI runs
  these with the run test, on its Node 24, so a missing Node fails
  there.
- **Java** (2026-10-30): the compiler is the checker. Without Maven,
  `javac` (or `JAVAC`) compiles the runtime, `temper-core`
  (`crates/temper-be-java/runtime/temper-core`) once, for Java 8 as its
  `pom.xml` says, into `CARGO_TARGET_TMPDIR`, kept until its sources or
  the JDK change; then every generated package and a harness that runs
  `module-state` from 8 threads, in one `javac` run, with `--release 17
  -Xlint:all -Xdoclint:all,-missing -Werror`: every lint an error, and
  every Javadoc check but for missing comments, which Kotlin's `pom.xml`
  leaves out of `javadoc`'s too. No lint is allowed, and Kotlin's own
  output passes the same flags. Then `java` (or `JAVA`) runs each
  program's main class. CI sets up Temurin 21. A missing `javac` or
  `java` skips with a message, or fails with `TEMPER_REQUIRE_CHECKERS`.
  Besides the 7 functional tests, it runs the Rust backend's programs,
  as JS does. A test that runs as tests (`testing/asserts`) compiles
  with std and JUnit 5.9.2's jars from Maven's cache (CI fetches them
  with `mvn dependency:get`) and runs through JUnit's launcher
  (2026-11-03).
- **C#** (2026-10-30): the compiler is the checker, with the .NET
  analyzers. Without MSBuild or NuGet, Roslyn's `csc` (from the newest
  .NET 6 SDK that `dotnet`, or `DOTNET`, lists) compiles the runtime,
  `TemperLang.Core` (`crates/temper-be-csharp/runtime/temper-core`) once, as
  its `.csproj` does for .NET 6, into `CARGO_TARGET_TMPDIR`, kept until
  its sources or the SDK change; then every generated library's sources
  in one `csc` run, against .NET 6's reference assemblies, with C# 10,
  `-nullable:enable -warn:9999 -warnaserror+`, and the SDK's .NET
  analyzers with every rule a warning (`AnalysisMode` `All`), but
  CA2211, which Kotlin's public static fields for exported `var`s break.
  So culture-sensitive calls (CA1304, CA1305, CA1307, CA1309, CA1310)
  are errors. Kotlin's `Logging.cs`, which every library gets, passes
  too. Then one generated `Program.cs` as its own program, and a runner
  that calls each library's `Program.cs` (renamed, so they share one
  program), and `module-state` from 8 threads; `dotnet` runs them under
  `sv-SE`, whose minus sign is U+2212, so a number formatted by the
  current culture shows. The runtime's one dependency,
  `Microsoft.Extensions.Logging` 8.0.0, comes from NuGet's cache, never
  the network; CI restores a copy of the runtime's project to put it
  there. A missing SDK, targeting pack, or package skips with a
  message, or fails with `TEMPER_REQUIRE_CHECKERS`. Besides the 7
  functional tests, it runs the Rust backend's programs, and one of its
  own, `hiding`: names that would hide the classes the translator names
  (`Math`, `Core`, `Support`), which it qualifies from `global::`, a
  parameter that's a C# keyword (`@object`), and `Boolean`'s `<`. A
  library's root namespace can hide classes in its dependents too, which
  waits for imports; one assembly for every library can't test it. A
  generated
  library's own projects also build and run with `dotnet build`,
  offline against the cache (`--source ~/.nuget/packages`), with
  `TemperLang.Core`'s project copied beside it (tried by hand, not in
  the tests). A test that runs as tests compiles with std, as strictly,
  against MSTest.TestFramework 3.1.1 from NuGet's cache (CI restores it
  with the runtime's packages), and a small reflection runner calls its
  `[TestMethod]`s (2026-11-03).
- **Every backend on every platform we ship binaries for** (planned,
  2026-11-04, the user's call): Linux x64 and arm64, macOS arm64, and
  Windows x64, each running every backend's functional tests with the
  most common toolchain versions there, and later more compilers or
  versions per platform where tempest is flexible enough. For C++: g++
  and Clang on Linux, Apple Clang on macOS, and MSVC on Windows, which
  matters most there (`cl /std:c++14 /W4 /WX`, again under
  `/fsanitize=address` and with `/RTC1` in a debug build; MSVC has no
  UBSan or TSan, so Linux keeps those), maybe `clang-cl` too. Plus
  tempest's own tests on each, for paths and line endings in the CLI.
  Not all at once: the most common toolchain per backend per platform
  first, with MSVC early. A missing toolchain skips locally, as a
  missing `g++` does.
- **C++** (2026-10-30): the compiler is the checker, with sanitizers.
  Without CMake, `g++` (or `CXX`) compiles every generated `.cpp`
  (each library's `init.cpp` and `main.cpp`, and a runner) against the
  header-only runtime, `temper-core` (`crates/temper-be-cpp/runtime`), with
  `-std=c++14`, as Kotlin's, and `-Wall -Wextra -Wpedantic -Werror`
  plus 33 more warnings (the test's `STRICT`), through a precompiled
  header of the runtime per set of options, kept in `CARGO_TARGET_TMPDIR`
  until the runtime, options, or `g++` change. The runtime is a system
  directory there, so four warnings it breaks (`-Wshadow`,
  `-Wsign-conversion`, `-Wuseless-cast`, `-Wfloat-equal`) apply only to
  generated code; building the header first checks the runtime itself
  with the rest. Not on: `-Weffc++`, older advice that the runtime
  breaks too. Then one library's `main.cpp` linked as Kotlin's program
  is, and a runner of every library's init and of `module-state` from
  8 threads; then all again under `-fsanitize=address,undefined
  -fno-sanitize-recover=all`, and the threads under
  `-fsanitize=thread` (without PIE, retried while TSan crashes silently
  as it starts, or can't map its shadow memory under Linux 6's address
  randomization; CI narrows it
  with `sysctl vm.mmap_rnd_bits=28`). Any sanitizer report fails the
  test. A missing `g++` skips with a message, or fails with
  `TEMPER_REQUIRE_CHECKERS`. Besides the 7 functional tests and the
  Rust backend's programs, one of its own, `hiding`: names C++
  reserves or the translator prints (`std`, `core`, `int32_t`,
  `explicit`, `M_PI`), `Boolean`'s `<`, a trigraph and a NUL in
  strings, and shifts past the width. A library also builds by hand
  with CMake, as a user's project would (not in the tests). `strict`
  (2026-11-09) compiles every generated `.cpp` again, not linked, at
  `-O2` with `STRICT` by `g++-12` and `g++-14` (those installed, or
  `TEMPER_STRICT_CXX`'s list), as release builds would, and the newest
  GCC at `-O3` too: `-O1` misses some of GCC's path-following warnings,
  such as a null upcast's `-Wnull-dereference`, and only `-O3` showed
  libstdc++'s `vector<bool>` `-Warray-bounds`. CI adds clang++ with its own set
  (`CLANG_STRICT`), which only CI checks.
- **Lua** (2026-10-30): no compiler, so the checks are a run, a parse,
  and a type checker. With `lua` (or `LUA`), from one directory holding
  every generated library and a copy of the runtime, `temper-core`
  (`crates/temper-be-lua/runtime/temper-core`), with Kotlin's `LUA_PATH`
  (`?.lua;?/init.lua`) and no LuaRocks, one process requires each
  library in turn, after a line naming it, and each must log what the
  test expects of `lua`; `fibonacci` also runs alone, as Kotlin's
  `temper run` runs a library (`lua fibonacci/init.lua`); and a script
  calls `module-state`'s `tick()` and reads its exports back, which must
  show the `var`s' current values. `luac -p` (or `LUAC`) parses every
  generated file, if there's a `luac` (a missing one only skips). The
  checker is LuaLS (`lua-language-server --check`, or `LUALS`), at its
  `Warning` level, with the type-check, LuaCATS, balance, and globals
  diagnostics on for every file, against the generated `---@param`,
  `---@return`, and `---@type` annotations and a typed view of the
  runtime (`tests/luals/temper-core.lua`, a `---@meta` file standing in
  for `temper-core`, which has no annotations and fills its table in a
  loop LuaLS can't follow; a test checks the runtime defines everything
  in it; the `missing-local-export-doc` lint is off until tmpl carries
  docs). LuaLS 3.19.2 passes locally (named by `LUALS`) and in CI,
  which installs that release and Lua 5.4.6 (Ubuntu 24.04's `lua5.4`),
  and runs the programs again under LuaJIT 2.1 (`LUA` names the
  interpreter).
  A missing `lua` or LuaLS skips with a
  message, or fails with `TEMPER_REQUIRE_CHECKERS`. Besides the 7
  functional tests and the Rust backend's four programs, three of its
  own: `hiding` (checked against the interpreter), `repeats` (tmpl, for
  jumps the frontend doesn't translate yet), and `locals` (generated:
  221 module-level declarations, past Lua's 200 locals, so the overflow
  table runs).
- **The harnesses' shared parts** (noted 2026-10-30): each backend's
  `tests/functional.rs` repeats `COVERED`, `PROGRAMS`, `Case`,
  `cases()`, `program()`, `generate()`, `with_stack`, the goldens'
  update loop, the hash-stamped runtime cache, the skip-or-require
  check, and the 8-thread `module-state` expectation; C++'s is the
  sixth copy, and adds a parallel compile step and sanitizer modes the
  others could use too. Lua's is the seventh, and adds what others could
  share as well: the one-process runner that splits its output by
  library, a check of a backend's own programs against the interpreter
  (Rust's has one for its programs), a generated program, a typed view
  of a runtime for a checker, and `copy_dir` and the tool lookup, which
  JS's has too. Factoring them into a dev-only crate is a restructuring
  change of its own, next with the CLI and `temper.out` alignment.
  Done (2026-10-30), as `crates/temper-be-testkit`, a dev-dependency of
  every backend: `COVERED`, the shared programs (moved to its
  `programs/`, with their check against the interpreter, in its
  `tests/programs.rs`), `Case`, `cases()`, `program()`, `interpret()`,
  `with_stack`, `Goldens` (the update loop), `tool` and `missing` (the
  skip-or-require check), `cached` (the hash-stamped runtime cache),
  `check_log`, `threads_expected`, `copy_dir`, `write_files`, and
  `files_with`. Each backend keeps its own programs, checkers, runners,
  and layout checks. Not shared yet: Lua's one-process runner and
  C++'s parallel compile and sanitizer modes, which only one backend
  uses so far.
- **Modules, libraries, and tests** (2026-10-30): three shared
  programs, checked against the interpreter like the rest, whose tmpl
  is golden too (`crates/temper-be-testkit/tests/goldens/*.tmpl`), so
  what backends start from shows in review. `modules` is a library of
  three modules in directories (`modules/helpers/deep`), importing
  functions, a `let`, and a `var` (copied when imported), with an alias
  and a name an import also has; `imports-library` imports `modules` by
  its name, so a backend's run builds both and checks their package
  files' dependency; and `testing` has tests, whose form each backend's
  goldens show, but which can't run until std's `testing` translates,
  so runs check only that the library loads (and, where cheap, that the
  test files parse: Python's `py_compile`). `Case::libraries` and
  `testkit::libraries` give a case the libraries it may import. Per
  backend: Python writes both libraries on `PYTHONPATH` and gives mypy
  and pyright both; Rust builds them in its one workspace, and builds and
  runs `testing`'s tests with `cargo test` against a stand-in
  `temper-std` crate (`init` and `testing::Test`'s `new` and
  `soft_fail_to_hard`), with clippy on `--all-targets`; JS puts the
  imported library in `node_modules`, where tsc checks it, and parses
  tests with `node --check`; Java and C# compile every library in one
  `javac` or `csc` run, leaving test sources out; C++ builds every
  program in one directory, then a library with tests again with
  `-DTEMPER_TESTS` and its `test.cpp` against a stand-in
  `std/testing.hpp` (since 2026-11-03, std's own, its JUnit XML compared
  with `expectedFailures`), whose JUnit XML must show no failures; Lua runs an
  importing library in a process of its own, and `testing`'s tests
  under a LuaUnit stand-in, with luac and LuaLS on the tests files.
- **Parameters' defaults** (2026-11-01): the testkit's `DEFAULTS`
  list (`classes/property-order`, whose default has statements) and a
  shared library of two modules, `defaults` (in its own directory, so
  in `LIBRARIES`), checked against the interpreter, with its tmpl
  golden: defaults left out, given, and null, one reading an earlier
  parameter and one that runs only when needed (a call that counts),
  nullable parameters defaulting to null and to a value, methods,
  static methods, a header's and a constructor's own defaults, an
  object literal that leaves a property out, and another module's
  function and exported class (Rust's and Java's builders). Every
  backend runs both with goldens, under its checkers, as for function
  values; the text cases add `coalesce` and N2's problems.
- **Core's other types** (2026-11-02): the testkit's `CORE_TYPES`
  list, nine functional tests that use string indices and
  `StringIndexOption` (`is`, `as`, and comparisons), `StringBuilder`,
  `Empty`, `Deque`, `DenseBitVector`, and more of `String`'s and
  `Float64`'s members (`split`, `countBetween`, `near` with named
  tolerances, `pi`, and the math functions), with their to_tmpl goldens
  in the frontend's `tests/to_tmpl/`. Every backend runs them with
  goldens, under its checkers, as for defaults. The core fixture's test
  now reads core's declarations whose parameters span lines (`near`,
  `indexOf`, `appendBetween`).
- **String order** (2026-10-30): the Rust backend's `string-order`
  program compares strings around the UTF-16 surrogate range, after
  equal prefixes, and with the empty string, with `<=>`, `<`, `<=`,
  `>`, and `>=`; a test there checks its log against the interpreter,
  and every backend's run test runs it, Python's included.
- **Wasm** (2026-10-30): `temper-wasm`'s `generate` matches each
  backend's goldens for subset A, natively; a real wasm build was run
  under Node by hand, not in the tests.

### Binary goldens (planned with the binary format)

Proposed (2026-10-29), from the user's question. Besides round trips,
check in a few small binary goldens, so an accidental change to the
format's bytes shows in review, and its version must be raised:

- **Kept small:** a handful of cases covering every section, not every
  text golden.
- **Readable failures:** a mismatch shows a diff of a structured dump
  (`tmpl dump`: each section with its offset and length, then its
  decoded records) of expected against actual, plus the first byte that
  differs, never raw bytes alone. The dump is also checked in beside
  each binary golden, so reviewers see what changed.
- **Compatibility:** binaries from earlier format versions stay as
  fixtures that newer readers must still read, or reject with a clear
  version error, as versioning decides.
