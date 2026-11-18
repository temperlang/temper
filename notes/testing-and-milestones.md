# Testing, milestones and risks

Part of the [rewrite plan](README.md).

## 14. Testing and checking compatibility

- **What must match:** behavior, error messages and generated public APIs.
  Internal representations, like tokens and trees, needn't. Comparing them
  with Kotlin is a cheap way to find behavior differences early, so we match
  them while that's easy, but a later stage may change them where nothing
  observable changes, noting why.
- **Where Kotlin is flaky, do better.** Tempest has one behavior, ours.
  Each deliberate difference from Kotlin is listed with its reason in
  [kotlin-differences.md](kotlin-differences.md#lexer-and-parser), and in
  `compat/deviations.py`, which the comparison scripts use to report
  those inputs as expected rather than failing. That list goes away when the
  comparisons do. Some Kotlin bugs are copied for now, where they only affect
  broken input, to keep comparisons quiet; they're listed too.
- **Our own tests,** run by `cargo test` without Kotlin Temper, so they last
  past the rewrite: per stage, a few `.cases` files of inputs with expected
  output (for the lexer, inputs from Kotlin's lexer tests plus our own edge
  cases), and properties checked on randomly edited copies of them. Fewer,
  denser files rather than one per case.
- **Fuzzing against Kotlin:** `compat/fuzz_lex.py`, `fuzz_parse.py` and
  `fuzz_ast.py` edit inputs randomly and compare both sides, shrinking any
  difference to a small input to add as a case, and saving the original.
  Real source rarely exercises error paths, so this finds what the corpus
  doesn't. A difference counts as a listed deviation only where it starts
  (`compat/deviations.py`): the first output lines that disagree must be
  about a block quote, a multi-quoted string (to the end of the input if it
  has scriptlet lines, whose blocks may stay open), or what follows a `$`
  error before `{`, or the outputs must match apart from positions. So a
  bug elsewhere in an input with those features is still found. The parse
  and grammar fuzzers also set apart inputs that crash Kotlin and those
  that already differ a step earlier.
- **Benchmarks:** each stage gets a `--bench` mode on both sides and a
  script that alternates them; results go as rows in
  [size-and-speed.csv](size-and-speed.csv) (`compat/measure_size.py
  --record` for sizes and memory), and
  [size-and-speed.md](size-and-speed.md) explains only what rows can't.
- **Oracles:** small Java programs that call Kotlin Temper's jars give
  reference results per file: literate blocks, tokens, and parse trees
  (`compat/oracle/*.java`). Each layer is diffed on every Temper source file
  in the Temper, Chromarite and Ordable repos, and on our test cases' inputs.
- **Functional tests:** the 72 markdown tests, run in-process (interpreter)
  and on every backend, comparing `log` output, with the same per-backend
  skips as Kotlin's `FunctionalTestStatus.kt` to start with. In-process,
  `compat/check_functional.py`'s `KNOWN_GAPS` lists the tests not passed
  yet, each with why (now none: all 72 pass, since 2026-11-02); one
  that starts passing fails the check until it's taken out.
- **Source maps:** the JS backend's maps, as decoded goldens, and read
  by Node's own source map support and stack traces against the Temper
  sources ([tmpl/testing.md](tmpl/testing.md), "JS source maps").
- **Feature builds:** CI's tests without default features build
  `tempest` and `temper-wasm` without their backends (`py`, `rust`,
  `js`, `java`, `csharp`, `cpp`, and `lua` features) and without `net`, and check that each says it
  was built without them; the backends' own tests run either way.
- **Typed expressions:** `uv run compat/count_untyped.py [LIBRARY ...]`
  counts, per file and in total, the expressions typing leaves of
  unknown type, the calls it leaves unresolved, and the `==` uses that
  rely on identity, over the functional tests, std, and the libraries
  given (as Chromarite's and Ordable's checkouts), and lists the fully
  typed functional tests, which tmpl's translation can take
  (notes/typing.md, "Every expression typed"; notes/history/milestone-4.md, slice 7).
- **Translated to tmpl:** `uv run compat/count_tmpl.py` counts the
  functional tests that translate to tmpl with no validator problems,
  and groups the rest by what they first need that the translation
  doesn't cover yet.
- **API diffs:** for each backend, extract the public surface from Kotlin's
  and the new output for the same libraries (functional tests, std,
  Chromarite), and diff them. Every intended change is listed in an allowlist
  with its reason.
- **Library tests:** `tempest test` (crate `tempest`, logic in
  temper-frontend's `project` and `library_tests`) runs a library's
  `test` blocks on the interpreter, finding libraries as Kotlin's `temper
  test` does and printing, as it does, only failures and its summary
  line (`--verbose` lists every test). Chromarite passes 56 of 56
  and Ordable 29 of 29, as with Kotlin's `temper test -b cpp`
  (2026-10-27), and CI runs both from fresh checkouts. That needed two
  fixes: a function type's parameter names, `fn (row: T): Void`, were
  read as uses, and `m[i, j]` on an object didn't call its `get`.
  Differences from Kotlin's discovery: `.gitignore` isn't read (dot
  directories, `temper.out` and `temper.keep` are skipped), files
  outside any library aren't built, and a config is read only for its
  `export let KEY = "VALUE";` lines. Libraries are named as Kotlin
  names them and import each other by name (`import("other-lib/sub")`,
  2026-10-30), each running only its own tests, and two of one name
  are an error. `tempest test -b interp` is the same; on a
  backend, tests run with the language's own tools (test-speed.md,
  2026-11-06). Ordable's tests
  take 14–22 s in a release build, almost all in its property tests
  (tens of thousands of natural comparisons on the interpreter), worth
  profiling with the interpreter's other costs. Std is built in
  (temper-frontend's `stdlib`, from `lib/std/`), for the wasm
  build too; `--std DIR` mounts its directory at std's root instead
  (`Sources::with_std_dir`). `run-temper` stays in
  temper-frontend for the compat scripts, sharing import loading
  (`project::load_imports`); moving it into `tempest` (maybe as `tempest
  run`) is a separate change.
- **`temper.out` as Kotlin's:** `tempest build [-w DIR] [-b
  BACKEND]...` (2026-10-30) writes `temper.out/BACKEND/LIBRARY/…` for
  every library under the work root, with the package files, and each
  backend's runtime beside them, as `temper-core`, as Kotlin copies it
  (built into the backend crates' `runtime::FILES`, checked against the
  crates' `runtime/temper-core`), and a `CMakeLists.txt` per C++ library, which a test
  builds with CMake and runs. `crates/tempest/tests/kotlin_layout.rs`
  compares the tree (every path, and the package files' names and
  versions) with Kotlin's for the functional tests tmpl translated after
  nullable types (24; the 3 that generics added aren't recorded yet), as
  `crates/tempest/tests/kotlin-out.txt` records it (last with Kotlin's
  CLI built from main 0db80cc8, 2026-10-31, which added the C++
  runtime's `module_init.hpp`)
  (`compat/kotlin_out.py` writes it from Kotlin's own output), with the
  intended differences allowed, each with its reason. Dependencies
  between libraries go in every package file as Kotlin writes them, from
  tmpl's `uses` sections (`tests/dependencies.rs`, on hand-written
  tmpl, and, since 2026-10-30, from the frontend's imports too, with
  the shared programs' `imports-library`). Chromarite, Ordable, and
  Hrefine don't build yet: since modules and imports translate
  (2026-10-30), each stops at its own classes; past them, their tests'
  `assert`s are calls of std's `Test`'s methods, and running tests on a
  backend needs std's `testing` in tmpl.
- **Library tours:** build Chromarite and Ordable with tempest and run their
  tours (`demos/tour/` in each, with a hand-written program per language and
  one expected output) unchanged, matching their expected output. That proves
  the generated APIs work for real callers, in every language, so it's the
  test that tempest is a Temper. A backend change that would break them goes
  behind a compatibility mode that keeps Kotlin's API
  ([kotlin-differences.md](kotlin-differences.md), "How far to differ"), so
  tempest never requires changing the tours. If the team adopts an improved
  translation, Chromarite, Ordable, and others update their tours to match,
  after that decision, not as a condition of using tempest.
- **Backend tests, shared from the start:** one set of Temper inputs, each
  with an expected output per backend (or "skip, because …"), run by every
  backend, rather than a separate set per backend as Kotlin Temper has
  (its `*BackendTest.kt` files range from 12 cases for Lua to 64 for Java,
  so most inputs are checked on only some backends). Backend-only cases
  only where a backend truly needs one, which should be rare. Also golden
  files of every backend's generated code for all the functional tests,
  so a codegen change shows up as a diff. The connected-fallback
  functional test (one input, a connected file per backend) is the model.
  Worth recommending to Kotlin Temper too
  ([kotlin-differences.md](kotlin-differences.md)).
- **Other platforms (planned):** CI tests only on Linux, while the
  binaries also ship for macOS and Windows. What differs there: CRLF
  line endings (Git's default checkout on Windows) in lexing, multi-line
  strings, and positions; paths (backslashes, drive letters, mapping
  `-std-/` back to disk, diagnostics); and case-insensitive file systems
  for module names. First, cover these on Linux: a `tests/settle` case
  with CRLF source giving the same results and positions as LF (checking
  how Kotlin handles CRLF first), and `Sources` unit tests with
  Windows-style paths. Then a small job on `windows-latest` and
  `macos-latest`: `cargo test --workspace`, and the built binary running
  `tempest test` on a small library, leaving fmt, clippy, wasm, and the
  functional check to Linux. No `.gitattributes` forcing LF, since
  users' files will have CRLF anyway.
- **One-file downloads, `tempest license`, and `tempest licenses`
  (planned):** each download should hold just the binary, with no
  `COPYRIGHT`, `LICENSE-*`, or `NOTICE` beside it. Instead, as in Kotlin
  Temper, `tempest license` prints tempest's own copyright, licenses,
  and notices (including the Unicode notice), and `tempest licenses`
  prints those of every crate built into that binary. This also fills
  a gap: the downloads now carry only tempest's own license, though the
  binary includes about 25 third-party crates (`ureq`, `rustls`, `ring`,
  and so on), mostly MIT or Apache-2.0 but also ISC, BSD-3-Clause, and
  CDLA-Permissive-2.0 (`webpki-roots`), whose licenses ask for their
  notices to go with binaries. Plan: a uv script that reads
  `cargo metadata` for the binary's own dependencies and features, takes
  each crate's license files from Cargo's local source cache, and writes
  one text file that the binary embeds. Commit that file, and have CI
  check it's current, so builds need no network and a new dependency
  can't slip in without its license. For now, the wasm download keeps
  both texts as files beside it, since it has no command line; revisit
  later (exported functions returning the texts, say) as wasm use
  takes shape. Record the size the texts add to the binary.
- **Semantics and error messages,** compared against Kotlin Temper from
  several sources:
  - **Frontend stage tests** (`frontend/src/commonTest/.../stage-tests/` and
    the frontend unit tests): inputs with expected diagnostics and results.
    Use them for what the language does and what errors say, not for their
    intermediate representations.
  - **Backend tests** (`JsBackendTest`, `RustBackendTest` and the rest):
    expected generated code, which pins down public API and behavior.
  - **REPL tests** in the CLI module: expected REPL output and error text.
  - **Running Kotlin Temper itself,** including `temper repl` scripted
    through stdin, on the same inputs as the new implementation. That makes a
    differential oracle for values, types and messages on any snippet, not
    just existing tests. Small differences in wording are allowed where
    deliberate and listed.
- **Unit tests** in Rust for each crate, written from behavior, borrowing
  cases from Kotlin's tests where they pin down semantics.

### What the parse comparison compares

`compat/check_parse.py` compares what decides behavior: how tokens group
into operators, which tokens the adapters insert or drop, string content
after trimming and joining, the parser's errors, and comments. Both sides
print the same normalized form (`compat/oracle/ParseTree.java`): one line
per node in pre-order, with the operator's name, or a token's kind, byte
span, synthetic flag and text; then errors as message template, operator,
bound, count and byte span; then comments as kind and span. Normalized
away:

- Kotlin's internal objects (stack elements, CST classes) and node spans,
  which are internal; error spans are compared.
- UTF-16 positions, converted to UTF-8 byte offsets.
- The root: Kotlin returns the root's only child when there is one, so we
  print that too.

Tokens are kept in the tree, so the comparison also covers the adapters.

### What the grammar comparison compares

`compat/check_ast.py` compares the grammar step's output
(`compat/oracle/AstTree.java`), on the same inputs plus
`tests/ast/*.cases`: one line per node in pre-order with its kind and byte
span, names with their kind (declared, used, builtin) and text, values with
their type and content, and error nodes; then the grammar step's
diagnostics (level, template, values, span), not the parser's, which
`check_parse.py` covers. As in Kotlin's `ParseStage`, the parse tree is
first split at `;;;` into code and JSON metadata. Normalized away:

- Kotlin's classes (`CallTree`, `ValueLeaf`, ...) and value objects: a value
  is its type name and content, a float its bits in hex, a function its
  name.
- What's inside error trees: Kotlin makes them calls to `error` with the
  unparsable parts as strings; we compare their spans.
- Empty trees' ends: Kotlin may end an empty tree before its start; both
  sides print the end as at least the start, and our spans never invert.
- Positions, where the trees match apart from them: they come from parse
  node spans, which differ by the "spans cover all parts" deviation
  (kotlin-differences.md, "Lexer and parser").
  Exact positions are compared for everything else.

With `--module` (or `--docs`), both sides also do the rest of Kotlin's
`ParseStage` for a library (or documentation): the code wrapped in a block,
call joins rewritten (`rewriteCallJoins`), and the metadata converted to
JSON (`toJsonValue`, called by reflection), printed as one more line, with
the inputs in `tests/module/*.cases` too. Temporary names print as their
hint and their index in order of first appearance, `f#0`.

## 15. Milestones

Milestones 1 to 6, from the literate split to all seven backends, are
done.

7. **Watch, incremental rebuilds and the language server.**

Later, maybe:

- **A library cache** of frontend results ([library-cache.md](library-cache.md)).
- **Tempest in Temper.** Once tempest and Temper are stable, translating
  tempest into Temper would show where Temper falls short for fast,
  allocation-careful code, and each gap would improve Temper itself, not
  just tempest. Likely gaps: unsigned and narrow integers, byte-level
  strings, slices or views, arenas with integer ids, and control over
  copying. Value types as such have hard semantics in many targets; struct
  types stored in byte buffers, as discussed for Temper, might be much
  easier, with struct instances still separately allocated objects, or
  facades over a buffer. Not a priority.
- **A standard Temper C ABI**, which the C++ and Rust backends (and maybe
  a future C99 backend) implement, so native code can back faster
  Python and Lua backends ("fast Python", "fast Lua"), alongside the
  pure ones, which stay useful. Publishing should make the expected
  package variants easy: for Python, platform wheels with native code
  plus a pure wheel as the fallback; for Lua, rocks with and without C
  modules. Publishing and consuming Temper-built libraries should be as
  easy as possible everywhere.
- **`Rc` instead of `Arc` in Rust** for much generated code, once the
  actor and isolation model says what may cross threads, which would
  make Rust output somewhat faster. A hand-converted copy of Ordable's
  generated Rust (Arc to Rc, RwLock to RefCell, Send and Sync bounds
  dropped, kept outside the repo) sorted 20,000 names
  about twice as fast (26 to 14 ms), with natural comparisons 20%
  faster and key building up to twice as fast (2026-10-26).
- **Process isolation**, for an actor model, maybe even a BEAM (Erlang)
  backend. It needs careful global semantics: module-level mutable state
  per process or not at all, and messages that are immutable or copied,
  so no mutable object is shared between processes. Backend-language code
  could still get around it, which may be fine, as a promise about Temper
  code rather than a sandbox. On BEAM, Temper's mutable objects and
  builders would need emulating, which is its own cost. Async and promises
  might map onto messages.

## 16. Risks and open questions

- **Settle loop convergence** with macros that rewrite code: settled for
  today's macros (facts only get added, and a stuck item is forced, the
  earliest first; [design.md](design.md), "Termination and order"). Still
  open for macros that run on types beyond the object literal.
- **Type inference as incremental steps:** solved per call site, which
  has been enough for the functional tests and five real libraries.
- **Semantic fidelity** beyond the functional tests: generated-code details,
  diagnostics, typing edge cases. Mitigated by API diffs and behavior tests
  ported from Kotlin's unit tests.
- **Backend simplicity:** whether shared lowerings absorb enough to keep each
  printer small, especially for C++ and Rust.
- **Two implementations at once** while Kotlin Temper remains the product.
- **Where trailing blocks go:** tempest follows Kotlin's rule, which can
  place a block before a required parameter and move the other
  arguments past it, while Kotlin's tests rely on it. The user prefers
  that a block go only to the last function-typed parameter, and only
  where every parameter after it is optional (2026-10-29); reassess,
  with the team, once tests needn't match ([typing.md](typing.md),
  "Where a trailing block goes").
- **Ruling out reference cycles** ([acyclic.md](acyclic.md)): always
  planned for Temper, never built. Open: top types and open interfaces,
  closures, core's builders, and how much ordinary code it rejects.
