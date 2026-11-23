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
| Trailing comma, multi-line `[ ]` | yes 99.6% | when one per line ([wrapping](#wrapping-lists)) | (below) |
| Trailing comma, multi-line `( )` | yes 63% | when one per line | (below) |
| Trailing comma, multi-line `{ }` with commas | no 72% | when one per line | (below) |
| Multi-line `( )` lists | tall 49%, hugged 50% | Black's: one line, else hugged, else tall | (below) |
| Multi-line `[ ]` lists | tall 99.8% | the same | (below) |
| Block arguments | trailing block 97%, `fn` in `( )` 3% | not changed (that's meaning) | - |
| Line width | p50 40, p90 103, p99 955 | 100, for lists only | (below) |

**`temper fmt` itself** (`--temper`, over copies) changes 758 of 81,019
lines (0.9%, prose included): 51 in core and std (4 files), 131 in test
programs, 63 in docs examples, and 3 to 185 in each library (Ordable 3,
Chromarite 46, Argmas 64, Hrefine 73, Kelpto 142, Wherewith 185).
Without wrapping lists, the first slice changed 189 lines (23 in core
and std, 105 in tests, 55 in docs, 0 to 2 in each library). It formats
every library file; it leaves alone one deliberately broken test
program and 22 docs blocks that aren't whole programs (REPL
transcripts, fragments).

Notes on the numbers:

- **Line width** is dominated by data: Hrefine and Kelpto keep Unicode
  and entity tables on very long lines (up to 4,780 columns). In
  core and std only 8 lines are over 100 columns, and 108 over 80. So
  only lists wrap, and lists of literals keep their lines
  ([wrapping lists](#wrapping-lists)).
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

## Wrapping lists

Bracketed lists wrap as Python's [Black](https://black.readthedocs.io/en/stable/the_black_code_style/current_style.html#line-length)
does, at a width of 100 columns (from the code block's margin in
markdown):

- **Which lists:** arguments, parameters, list and object literals, and
  type arguments (`( )`, `[ ]`, `{ }` and `< >` around a comma list):
  two or more items, or one with a trailing comma. A single item never
  wraps (`f(g(...))` keeps `f(g(` together, and `g`'s list wraps).
- **Layouts:** on one line if the list fits, with the rest of its line
  up to where the line may break next (the end of the line, or just
  after the next list's open bracket); else its items together on one
  line of their own between the brackets' lines ("hugged"); else one
  item per line, with a trailing comma added. Lists are decided
  outermost first, as the formatter reaches them, so inner lists decide
  on the lines their outer list gives them.
- **Magic trailing comma:** a trailing comma as written keeps one item
  per line (and keeps the lists around it as written), so an author can
  ask for a tall list, and wrapping is idempotent: a list made tall gets
  the comma that keeps it so. As in Black, a list made tall stays so
  when it later shrinks, until the comma is deleted.
- **Kept as written:** a list with a comment inside, or a line break
  inside an item (a block argument like `foo(a, fn {`, a multi-line
  string, a list kept tall inside it), a list in a string's `${ }`, and
  **data**: a list whose items are all literals (numbers, `true`,
  `false`, `null`, strings without holes, `char'a'`, negated numbers) or
  lists or calls of them (`new Pair("a", 1)`). That keeps tables of
  test cases and Unicode data as their authors wrote them, one row per
  line however long, with no opt-out comment needed.
- **Only lists:** other long lines stay long. A line break inside a
  list's brackets, after its commas, can't end a statement, so wrapping
  there never changes meaning; elsewhere it could.
- **Safety:** the check after formatting allows only trailing commas
  added or removed (tokens, and the tree's comma lists); `fmt` only adds
  them.

**The numbers** (`wrap_census` in `crates/temper-syntax/tests/fmt.rs`,
over the repository and the six libraries; a line changed is one of the
first slice's output the wrapped output doesn't have):

| Width | Lines over it now: core+std | Lines wrapping changes: core+std, tests, Argmas, Chromarite, Hrefine, Kelpto, Ordable, Wherewith |
|---|---|---|
| 80 | 115 | 20, 38, 200, 91, 151, 470, 11, 379 |
| 88 (Black's) | 63 | 18, 26, 130, 65, 113, 294, 7, 288 |
| **100** | 9 | 28, 26, 62, 44, 71, 141, 3, 184 |
| 120 | 4 | 40, 28, 27, 23, 61, 60, 1, 75 |

Of 4,591 code lines in core and std, 10,477 in tests and 63,782 in the
libraries; docs' examples change 0 to 1 lines (54 of 69 docs files
parse whole). **100** is proposed: core and std have only 9 lines over
it, and most of their 28 changed lines join parameter lists that
`json` wraps by hand at 80 (`let parseJsonValue(\n  sourceText: ...,\n):`)
but fit in 100; at 88 the libraries change about twice as many lines
and core and std still have 63 lines over the width; 120 joins more of
core's hand-wrapped lists. A narrow width gains little when only lists
wrap.

**Data lists** are what makes any width workable: wrapping them too
changes 2,069 lines in Hrefine, 1,593 in Kelpto, 418 in Ordable and 98
in tests at width 100 (against 71, 141, 3 and 26), exploding table rows
one value per line. Other choices considered: keeping lists already
written one item per line (the magic comma does that for 99.6% of tall
`[ ]` lists, and it doesn't help one-line tables); filling lines with
as many items as fit, as Prettier does for number arrays (rewraps
tables' rows; maybe later, for data lists over the width only); an
opt-out comment like `// temper fmt: off` (more to learn; not needed
yet).

What changes, in the libraries: long calls and signatures wrap
(Argmas's 17-argument `addParam` calls go one per line, Kelpto's long
`renderSafe(...)` calls hug), and lists written hugged *with* a trailing
comma go one per line (a few in Kelpto). A list inside a one-line block
wraps where it is (`do { editAll(\n  ...\n); "accepted" }`), as Black
would.

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
- **Lists** are the exception (above): the formatter finds them in the
  tree, measures each gap and token's width on one line, decides
  which lists may wrap (inner first), and then, in the same pass that
  sets the space between tokens, decides each list's layout as it
  reaches the open bracket, adding or removing line breaks only at the
  list's own places (after the open bracket, after a comma, before the
  close bracket). The lines it adds indent as any line there would. A
  wrapped list's `< >` count as brackets for indentation; other `<`s
  don't, as they may be less-than.
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
  and markdown outside code, apart from trailing commas; otherwise
  `fmt` refuses, as a bug.
- **Idempotence:** every rule depends on the tokens and on indentation
  the formatter computed itself, not on the input's (except the kept
  continuation offsets, which are relative, and line breaks inside
  lists, which a trailing comma or the width decides the same way
  again), so formatted code formats to itself. Tests check it over every
  Temper file in the repository and randomly edited inputs, at width
  100 and at 30, where most lists wrap; the wrapping cases hold at
  widths 90 and 110 too.
- **Speed:** one lex and parse, a linear pass, and one more parse for
  the check. In release, the largest files take 4 to 9 ms (`core.temper`,
  50 KiB) and 7 to 13 ms (`json.temper.md`, 43 KiB), wrapping adding
  about a tenth (the machine is noisy); `format_speed` in `tests/fmt.rs`
  (ignored by default) measures them. Through the language server, a
  formatting request for them answers in 6 to 30 ms (`format_speed` in
  `crates/temper-lsp/tests/lsp.rs`).
- **Language server:** `textDocument/formatting` and `rangeFormatting`
  format the open text, reusing its unit's parse when that's current,
  and answer with the runs of changed lines (Myers' diff), all of them
  or those on a range's lines. A document that doesn't parse gets no
  edits and no error. The VS Code extension formats on save by default
  (`editor.formatOnSave` for `[temper]` and `[temper-markdown]`).

### Alternatives

- **A layout engine** (Wadler/Prettier documents with groups and
  indentation, picking breaks to fit a width) for everything. Lists
  wrap with the same idea (groups decided outermost first, a group flat
  if it fits up to the next possible break) inside the token-gap frame;
  doing everything through it would rewrite far more lines than the
  census says is needed, and would have to respect semicolon insertion
  at every break.
- **Reprinting from the AST** loses comments and layout the tree
  doesn't keep; the parse result's tokens make that unnecessary.

## Status and next steps

Done: `temper fmt [PATH]...` and `--check`; the rules above marked as
proposed; wrapping lists at 100 columns, with the magic trailing comma
and data lists kept; golden cases (`tests/fmt/*.cases`), idempotence
and tree-preservation tests over the repository's Temper files and
randomly edited inputs; refusal for code that doesn't parse; language
server formatting, on save by default in VS Code. The repository's own
files aren't reformatted yet.

Next:

1. Format lib/ and docs in one commit of its own, listed in
   `.git-blame-ignore-revs`; then `temper fmt --check` in CI.
2. Open questions: continuation indent (0 or 2, maybe by kind: 2 after
   a binary operator, 0 for `extends`); whether data lists over the
   width should fill lines; whether lists inside one-line blocks
   should wrap.
3. Doc comments' framing (`/** … */`), if wanted.
4. Markdown prose in `.temper.md`: left exactly as written. Formatting
   it (rewrapping paragraphs, normalizing lists and emphasis) is a
   Markdown formatter's job; should `fmt` call one, or leave prose to
   editors?
