# `temper fmt`

Part of the [rewrite plan](README.md); for
[#681](https://github.com/temperlang/temper/issues/681). User docs:
[the command line](../docs/tools/cli.md#fmt). Code:
`crates/temper-syntax/src/fmt.rs`, tests in
`crates/temper-syntax/tests/fmt.rs` and `tests/fmt/`.

Goals: one layout with no options; fast enough to run on every save
(tens of milliseconds a file, from one file's syntax tree); never
changes meaning; idempotent; comments never lost or moved; code that
doesn't parse left alone; in `.temper.md`, only Temper code blocks
change.

## Census: how Temper is laid out today

`compat/fmt_census.py` measures the candidate rules over the compiler's
`lib/` (core and std), every Temper file under `crates/` (mostly
tests), the docs' ```` ```temper ```` examples, and six libraries
(Argmas, Chromarite, Hrefine, Kelpto, Ordable, Wherewith): 59,522 code
lines, using temper-syntax's own lexer and parser. Each rule below is
the dominant style; "lines changed" is what applying it changes.

| Rule | Today | Proposed | Lines changed |
|---|---|---|---|
| Indent characters | spaces 100% | spaces | 0 |
| Indent step after an open bracket | 2: 99.6%, 4: 0.2% | 2 | (in the next row) |
| Indentation, whole model (below) | matches 99.8% | the model | 104 (0.2%) |
| Continuation lines, offset from the statement's first line | 0: 53%, 2: 41%, other 6% | keep as written (for now) | 0 |
| `{` on the line of what it follows | 100% | same line (kept: line breaks stay) | 0 |
| `} else` on one line | 99.5% | one line (kept: line breaks stay) | 0 |
| Space around infix operators | one 99.8% | one | 62 |
| Label and object colons (`a: 1` in `{ }`) | one 50%, none 50% | keep as written | 0 |
| Space before `<` | none 61%, one 39% (type arguments, comparisons) | keep as written (it decides angle brackets) | 0 |
| `.` and `?.` | none 100% | none | 2 |
| `,` | none before 100%, one after 100% | same | 11 |
| `;` | none before 100%, one after on a line 99.9% | same | 5 |
| Type colon `x: T` | none before 100%, one after 99.9% | same | 11 |
| Inside `( )`, `[ ]`, `< >` | none 100%, 99.8% | none | 13 |
| `name(` in calls | none 100% | none | 0 |
| `if (`, `while (`, `for (`, `when (`, `catch (` | one 100% | one | 1 |
| `fn (` | one 92% | one | 8 |
| Before `{` (but `({`, `${`) | one 100% | one | 0 |
| Inside `{ }` on one line | one 97% (the rest: `{}`, `{Date}`) | one; `{}` as written | 34 |
| After prefix operators and `@` | none 100% | none | 0 |
| Runs of spaces elsewhere | 1 case | one space | 1 |
| Before a trailing comment | one 84%, aligned 15% | keep as written | 0 |
| `//` then a space | 99.9% | keep (comment text) | 0 |
| Trailing space | 1 line | none | 1 |
| Blank line after a line ending `{` | none 99.9% | none | 5 |
| Blank line before a line starting a close bracket | none 100% | none | 4 |
| Blank line after a multi-line statement | none 75%, one 25% | keep as written | 0 |
| Blank lines in a row | at most one, but for a handful | at most one | few |
| Trailing comma, multi-line `[ ]` | yes 99.6% | yes (later) | 2 |
| Trailing comma, multi-line `( )` | yes 63% | open | - |
| Trailing comma, multi-line `{ }` with commas | no 72% | open | - |
| Multi-line `( )` lists | tall 49%, hugged 50% | open | - |
| Multi-line `[ ]` lists | tall 99.8% | tall (later) | - |
| Block arguments | trailing block 97%, `fn` in `( )` 3% | not changed (that's meaning) | - |
| Line width | p50 40, p90 103, p99 955 | no width rule yet | - |

**`temper fmt` itself** (`--temper`, over copies) changes 189 of 81,019
lines (0.2%, prose included): 23 in core and std (4 files), 105 in test
programs, 55 in docs examples, and 0 to 2 in each library (Argmas and
Ordable none). It formats every library file; it leaves alone one
deliberately broken test program and 22 docs blocks that aren't whole
programs (REPL transcripts, fragments).

Notes on the numbers:

- **Line width** is dominated by data: Hrefine and Kelpto keep Unicode
  and entity tables on very long lines (up to 4,780 columns). In
  core and std only 8 lines are over 100 columns, and 108 over 80. A
  width rule would need a way to leave tables alone (as rustfmt's
  `#[rustfmt::skip]`), so the first rules don't break lines at all.
- **The indentation model** (below) matches 99.8% of lines. The 104
  that differ are mostly hand-aligned expressions in std's `temporal`
  and `json`, a 3-space slip in `core.temper`, and some test inputs.
- **Continuation lines** split between no extra indent (class headers
  like `class C\nextends D {`, chained `&&` in conditions, `for`
  headers) and two spaces (57% of lines after a binary operator, 67%
  of lines starting with one), so the first slice keeps each
  continuation line's offset from its statement's first line. That
  still fixes them when the statement itself moves.
- The literate line numbers the script's `--samples N` prints count
  lines of the joined code blocks, not of the file.

## Design

**Keep the line breaks; rewrite the space between tokens.** This is
gofmt's approach rather than Prettier's or rustfmt's. Line breaks
matter in Temper: a `}` ending a line or a `{` starting one can end a
statement (semicolon insertion), so moving them risks meaning, and
keeping them makes the result predictable for people and diffs. The
census shows authors already agree on almost everything else.

- **Input:** the parse result (`parse::Parsed`), which keeps every
  lexer token, including comments, spaces and line breaks
  (`Parsed::lexed`), and flags synthetic tokens (inserted semicolons,
  closers, call joins), which the formatter skips. The parse is the only
  input: no names, types or other files. No separate lossless
  tree is needed: whitespace and comments sit between tokens, and the
  tree gives each operator token its role (infix, prefix, open or close
  bracket, separator) for spacing.
- **Output:** the tokens in order, each gap rewritten: on a line, by the
  spacing rules, keyed on the two tokens' roles and operators; across
  lines, the line breaks (blank lines capped), then the indentation.
  Space next to comments, inside strings (and `\u{...}` escapes), and
  before `<` (whether `<` follows space decides whether it may be an
  angle bracket) is copied, and any change is checked by lexing the two
  tokens with the new space, so `- -x`, `a b` or `1 .x` never join.
- **Indentation** is computed in one pass with a stack of open brackets,
  each remembering the line its contents indent from: the bracket's own
  line if that line started at the bracket's depth, or else the line
  that started the statement at that depth. Contents get that line's
  indentation (as formatted) plus two spaces; a line starting with a
  close bracket lines up with it. So `foo(a, fn {`, `.map { x =>` on a
  chain's continuation line, `class C\nextends D {`, and the `{` ending
  a `for (` header split over lines each indent their contents one
  level, as the libraries do. A line continuing a statement (the code
  before didn't end with `;`, `{`, `}`, `,` or `=>`, or `,` in `( )` and
  `[ ]`; or it starts with `.` or `?.`) keeps its offset from the
  statement's first line, as written. A line after a decorator line
  (`@connected`) starts a statement.
- **Comments** never move between tokens, so they can't be lost or
  reattached: a comment on its own line is indented like the code
  around it, a trailing comment keeps its space. A multi-line block
  comment's later lines move with its first (doc comments' ` * ` lines),
  and only if all of them can.
- **Multi-quoted strings:** their content lines move with the line that
  opened the string; the margin's indentation is incidental, so their
  values don't change.
- **Literate files:** only Temper code blocks are edited, each indented
  from its block's margin (the fence's indentation, or four columns past
  its list items' for indented code); blocks continue each other's
  brackets, as the compiler reads them, so a class split over several
  blocks indents consistently. Prose, including trailing space, is never
  touched, nor are lines indented with tabs (markdown counts tabs to the
  next multiple of four).
- **Safety:** input with lexing, bracket or parse errors, an unclosed
  block comment, string or `${` is left alone, with the first error. After formatting, the result is parsed again and must have the
  same tokens, comments (ignoring each line's indentation), parse tree
  and markdown outside code; otherwise `fmt` refuses, as a bug.
- **Idempotence:** every rule depends on the tokens and on indentation
  the formatter computed itself, not on the input's (except the kept
  continuation offsets, which are relative), so formatted code formats
  to itself. Tests check it over every Temper file in the repository and
  randomly edited inputs.
- **Speed:** one lex and parse, a linear pass, and one more parse for
  the check. In release, the largest files take 4 ms (`core.temper`,
  50 KiB) to 7 ms (`json.temper.md`, 43 KiB); `format_speed` in
  `tests/fmt.rs` (ignored by default) measures them.

### Alternatives

- **A layout engine** (Wadler/Prettier documents with groups and
  indentation, picking breaks to fit a width). Needed for long-line
  breaking and for normalizing multi-line lists (tall vs hugged,
  trailing commas); it can be added later for just those constructs,
  inside the same token-gap frame, once the census questions above are
  decided. Doing everything through it from the start would rewrite far
  more lines than the census says is needed, and would have to respect
  semicolon insertion at every break.
- **Reprinting from the AST** loses comments and layout the tree
  doesn't keep; the parse result's tokens make that unnecessary.

## Status and next steps

Done (first slice): `temper fmt [PATH]...` and `--check`; the rules
above marked as proposed, except trailing commas and the multi-line
list shapes; golden cases, idempotence and tree-preservation tests
over the repository's Temper files and randomly edited inputs; refusal
for code that doesn't parse. The repository's own files aren't
reformatted yet.

Next:

1. Language server document formatting, on by default on save in the
   VS Code extension, reusing the open document's parse.
2. Format lib/ and docs in one commit of its own, listed in
   `.git-blame-ignore-revs`; then `temper fmt --check` in CI.
3. Decide the open questions: continuation indent (0 or 2, maybe by
   kind: 2 after a binary operator, 0 for `extends`), multi-line list
   shape and trailing commas, a line width and how tables opt out.
4. Doc comments' framing (`/** … */`), if wanted.
