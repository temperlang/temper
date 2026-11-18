# How the compiler works

This is Temper's compiler, written from scratch in Rust. It reads
Temper source, works out what every name means and what type every
value has, and then writes the same program in seven languages: C++,
C#, Java, JavaScript, Lua, Python, and Rust. It can also run a program
directly, with a small interpreter, which is how `temper test` and the
playground's Run button work. The whole compiler is one small binary,
and the same code builds to wasm for the browser.

These pages walk through it one stage at a time. Each is short, follows
one tiny program through its stage, and ends with links into the code.
They cover the compiler's architecture, not the language: for Temper
itself, see the [language reference](../language/README.md).

## The big picture

```mermaid
flowchart LR
  src["Temper source"] --> syntax["Syntax: tokens and trees"]
  syntax --> settle["Settle loop: names, macros, types"]
  settle --> interp["Interpreter: run and test"]
  settle --> totmpl["to_tmpl"]
  totmpl --> tmpl["tmpl: the backend IR"]
  tmpl --> lower["Shared lowerings and naming"]
  lower --> backends["Seven backends"]
  backends --> files["Source and package files"]
```

Only three data structures carry the program from start to end:

- **Tokens**, from the lexer: a kind and a byte span each.
- **The tree**, one arena per library. Parsing builds it, and the
  settle loop rewrites it in place until every node is one of a small
  set of core constructs.
- **tmpl**, the backend IR: a settled, typed, fully explicit program
  that a backend can translate without asking the frontend anything.

**Errors are values** at every stage. A syntax error, an undeclared
name, or a type that doesn't fit is reported and becomes an error node,
and everything around it carries on: it's typed, run, and translated.
The error fails only where it runs, so one mistake never hides the rest
of a library's diagnostics, tests, or generated code.

## The crates

```mermaid
flowchart LR
  syntax["temper-syntax"] --> frontend["temper-frontend"]
  tmplc["temper-tmpl"] --> frontend
  tmplc --> be["temper-be-* (seven)"]
  frontend --> driver["temper-driver"]
  frontend --> analysis["temper-analysis"]
  be --> driver
  analysis --> lsp["temper-lsp"]
  analysis --> wasm
  driver --> cli["tempest (the temper command)"]
  lsp --> cli
  driver --> wasm["temper-wasm (playground)"]
```

| Crate | Its job |
|---|---|
| [`temper-syntax`](../../crates/temper-syntax/src/lib.rs) | Source to trees: literate files, lexing, parsing, the grammar step |
| [`temper-frontend`](../../crates/temper-frontend/src/lib.rs) | Trees to a settled library: the settle loop, typing, the interpreter, and translation to tmpl |
| [`temper-analysis`](../../crates/temper-analysis/src/lib.rs) | Editor queries over a settled library: diagnostics, hover, definition, completion, references, rename, outlines, and signature help |
| [`temper-tmpl`](../../crates/temper-tmpl/src/lib.rs) | tmpl itself, plus the lowerings and naming that every backend shares, and what a backend is to tools ([`Backend`](../../crates/temper-tmpl/src/backend.rs)), with the helpers their test runners share ([`tools`](../../crates/temper-tmpl/src/tools.rs)) |
| `temper-be-*` | One crate per language: tmpl in, source files out, and natively, how the language runs tests (its `tools` module). Each depends only on `temper-tmpl` |
| [`temper-driver`](../../crates/temper-driver/src/lib.rs) | The registry of backends, the one compile path, `build`, and running tests with any backend's tools |
| [`temper-lsp`](../../crates/temper-lsp/src/lib.rs) | The language server, `temper lsp` |
| [`tempest`](../../crates/tempest/src/main.rs) | The `temper` command: argument parsing and output, for `build`, `test`, `lsp`, and one backend's files |
| [`temper-wasm`](../../crates/temper-wasm/src/lib.rs) | The compiler as wasm, for the playground |

## Reading order

1. [Syntax](01-syntax.md): from text to tokens to trees.
2. [The settle loop](02-settle-loop.md): names and macros, as work
   items that wait on facts.
3. [Typing](03-typing.md): types as more facts in the same loop, then
   checks.
4. [The interpreter](04-interpreter.md): running a settled library.
5. [tmpl](05-tmpl.md): the backend IR, and the lowerings backends share.
6. [Backends](06-backends.md): tmpl in, C++, C#, Java, JavaScript, Lua,
   Python, or Rust out.
7. [The `temper` command](07-cli.md): `build` and `test`, and running
   tests with each language's own tools.
8. [Wasm, the playground, watch, and a language server](08-playground.md):
   the same compiler in a browser, and in an editor.
9. [The language server and editors](09-language-server.md): `temper
   lsp`, its queries, and the VS Code extension.
10. [Where tests go](10-tests.md): a cheat sheet for adding tests, for
    readers who know Kotlin Temper's.

## The running example

Every page follows this program, `triple.temper`:

```temper
let triple(n: Int): Int { n * 3 }
console.log("triple(14) = ${triple(14)}");
```

Run, it prints `triple(14) = 42`. By the end, you'll have seen it as
tokens, as a tree, settled, typed, as tmpl, and in all seven languages.
It's one of the [playground](../../site/playground/README.md)'s examples,
and a shared test program
([`triple.temper.md`](../../crates/temper-be-testkit/programs/triple.temper.md)),
so the outputs these pages show are goldens that the tests keep
current: its [tmpl](../../crates/temper-be-testkit/tests/goldens/triple.tmpl),
and its [Python](../../crates/temper-be-py/tests/goldens/triple.py), for
one. For a bigger example, the functional test `algos/fibonacci` has
goldens at every stage, such as its
[tmpl](../../crates/temper-frontend/tests/to_tmpl/algos-fibonacci.tmpl).

## Going deeper

Each page has a companion in [`detail/`](detail/) that goes a level
further: every important decision with its alternatives, pros and cons,
and reasons (and how Kotlin Temper does it), the main types and
functions, the tests that pin it, and known weaknesses:
[syntax](detail/01-syntax.md),
[the settle loop](detail/02-settle-loop.md),
[typing](detail/03-typing.md),
[the interpreter](detail/04-interpreter.md),
[tmpl](detail/05-tmpl.md),
[backends](detail/06-backends.md),
[the command and driver](detail/07-cli.md),
[the playground](detail/08-playground.md), and
[the language server and editors](detail/09-language-server.md).

The design notes explain the why: [notes/README.md](../../notes/README.md)
for the goals, [notes/design.md](../../notes/design.md) for the whole
design, [notes/typing.md](../../notes/typing.md) for typing, and
[notes/tmpl/](../../notes/tmpl/README.md) for tmpl. The Kotlin
compiler's own backend guide and output grammars are kept in
[kotlin/](kotlin/README.md).

Links to code go to a file and name the type or function to look for
there. CI checks that each file still defines what its link names, and
that every link to a page or heading still leads somewhere
(`uv run compat/check_anchors.py`).
