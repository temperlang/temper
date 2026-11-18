# 10. Where tests go

A cheat sheet for adding tests, for readers who know Kotlin Temper's
test layout. Most of this compiler's tests are goldens: an input, and the
output it should give, checked in beside it.

## Kotlin's places, and this compiler's

| In Kotlin Temper | Here |
|---|---|
| Lexer and parser tests | `.cases` files in `temper-syntax`: `tests/*.cases` for tokens ([`lex.rs`](../../crates/temper-syntax/tests/lex.rs)), `tests/parse/` for trees, `tests/ast/` for the grammar step, and `tests/module/` for what comes after it. `compat/check_lex.py`, `check_parse.py`, and `check_ast.py` compare the same inputs with Kotlin Temper |
| Stage tests (`frontend/src/commonTest/resources/.../stage-tests/`) | `.cases` files in `temper-frontend`'s `tests/settle/` ([`settle.rs`](../../crates/temper-frontend/tests/settle.rs)): names, macros, and builtins; typing in `types.cases`, `class-types.cases`, `flow.cases`, and `fields-first.cases`; the interpreter in `run.cases`. A new `.cases` file there is picked up as it is |
| The functional test suite (`functional-test-suite/src/commonMain/resources/`) | The same tests, begun as Kotlin's, in [`temper-funtests`'s `cases/`](../../crates/temper-funtests/cases/config.temper.md), which that crate reads. `compat/check_functional.py` runs them all on the interpreter |
| A backend's functional tests (`PyFunctionalTest` and the like) | The functional tests a backend covers, by the groups in [`temper-be-testkit`](../../crates/temper-be-testkit/src/lib.rs) (`CLASSES`, `NULLABLES`, …), with a golden per test in the backend crate's `tests/goldens/` |
| Backend unit tests, Temper in and target code out (`PyTranslatorTest` and the like) | A shared program in [`temper-be-testkit/programs/`](../../crates/temper-be-testkit/programs/triple.temper.md), which the backends translate, run, and check; or, for one language's edge cases, a program in that backend's own `tests/programs/` (Java, JS, and Rust have some) |
| TmpL tests (`be/src/commonTest/.../tmpl/`) | tmpl goldens: `temper-frontend`'s `tests/to_tmpl/` ([`to_tmpl.rs`](../../crates/temper-frontend/tests/to_tmpl.rs)), one per covered functional test, and the testkit's `tests/goldens/NAME.tmpl`, one per shared program. Hand-written tmpl goes in `temper-tmpl`'s `tests/text/*.cases` ([`text.rs`](../../crates/temper-tmpl/tests/text.rs)) |
| The runtimes' own tests | Kept in each backend crate's `runtime/`; CI runs the Rust runtime's |
| Language server tests | `temper-analysis`'s [`analysis.rs`](../../crates/temper-analysis/tests/analysis.rs) for queries, and `temper-lsp`'s [`lsp.rs`](../../crates/temper-lsp/tests/lsp.rs) for a scripted session; the VS Code extension's in `editors/vscode/` ([its tests](detail/09-language-server.md#tests)) |
| (none) | The playground: `temper-wasm`'s [`examples.rs`](../../crates/temper-wasm/tests/examples.rs), one line per example, and a smoke test in a browser ([its tests](detail/08-playground.md#tests)) |
| (none) | The REPL: transcripts in `tests/repl/*.cases` in `temper-frontend` and `temper-driver`, and the docs' transcripts ([`repl_docs.rs`](../../crates/temper-frontend/tests/repl_docs.rs)) |
| `temper test` on libraries | Small fixture libraries in the `tempest` crate's [`cli.rs`](../../crates/tempest/tests/cli.rs), on every backend; real libraries' tests nightly, from the list in `.github/nightly-libraries.json` |

Unit tests of one module stay in its source file, under `#[cfg(test)]`,
as Rust has them.

## Which to pick

- **A syntax or settle change:** a case in the matching `.cases` file.
  It shows the tree, the diagnostics, and, for settle cases, what
  running it logs.
- **Language behavior that every backend must share:** a shared program.
  Prefer extending an existing one to adding a new one, since each new
  program costs time on every backend. Its ```` ```log ```` blocks say
  what it logs, and may name backends (```` ```log cpp java ````) or
  leave them out (```` ```log ~lua ````). The testkit checks that the
  interpreter logs the same. A new program also goes in the testkit's
  `PROGRAMS` list, which six backends run as it is; Python runs those
  in its own list, in
  [`temper-be-py`'s `tests/functional.rs`](../../crates/temper-be-py/tests/functional.rs).
- **A functional test:** add a directory under
  `crates/temper-funtests/cases/` whose main file is named after it
  (`algos/fibonacci/fibonacci.temper.md`), and its `import` in
  `cases/config.temper.md`. A backend runs
  a functional test once it's in one of the testkit's groups.
- **Something one backend gets wrong:** a program in that backend's
  `tests/programs/`, or a shared program if the others could get it
  wrong too.

## Goldens

Run with `UPDATE_SNAPSHOTS=1` to write new or changed output, then
review the diff:

    UPDATE_SNAPSHOTS=1 cargo test -p temper-be-py

`REPL_DOCS_FIX=1` does the same for the docs' REPL transcripts.

## Running some of the tests

    cargo test -p temper-frontend --test settle   # one crate's test file
    cargo test -p temper-be-js                    # one backend
    TEMPER_CASES=casts-as-expr cargo test -p temper-be-js   # a few functional tests
    uv run compat/check_functional.py algos/fibonacci        # one functional test, on the interpreter

A backend's tests run the generated code with the language's own tools
and checkers: mypy and pyright, `tsc` and Mocha, LuaLS, and the
compilers. A missing tool skips its test with a message. CI sets
`TEMPER_REQUIRE_CHECKERS`, so there a missing tool fails instead; set
it locally to be sure nothing was skipped. Tools are found on `PATH`,
or by an environment variable named in each crate's
`tests/functional.rs` (such as `MYPY` or `TSC`).

[CI and supply chain](../contributing/ci.md) has the full table of
what CI runs, and the tool versions it pins. Each stage's page in
[`detail/`](README.md#going-deeper) lists that stage's tests, with what
they don't cover yet.
