# `temper fmt`

Part of the [rewrite plan](README.md); for
[#681](https://github.com/temperlang/temper/issues/681). User docs:
[the command line](../docs/tools/cli.md#fmt). Code:
`crates/temper-syntax/src/fmt.rs`, tests in
`crates/temper-syntax/tests/fmt.rs` and `tests/fmt/`.

Goals: one layout with no options and no line width (the author decides
where lines break); fast enough to run on every save
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
| Continuation lines, offset from the statement's first line | 0: 53%, 2: 41%, other 6% | 4 (twice the indent step) | (below) |
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
| Trailing comma, multi-line `[ ]` | yes 99.6% | as written: it asks for one item per line ([lists](#lists-the-author-decides)) | (below) |
| Trailing comma, multi-line `( )` | yes 63% | as written | (below) |
| Trailing comma, multi-line `{ }` with commas | no 72% | as written | (below) |
| Multi-line `( )` lists | tall 49%, hugged 50% | by the author's line break and trailing comma | (below) |
| Multi-line `[ ]` lists | tall 99.8% | the same | (below) |
| Block arguments | trailing block 97%, `fn` in `( )` 3% | not changed (that's meaning) | - |

**`temper fmt` itself** (`--temper`, over copies) changes 895 of 81,110
lines (1.1%, prose included): 69 in core and std (4 files), 132 in test
programs, 148 in docs examples, and 6 to 214 in each library (Ordable
6, Argmas 18, Chromarite 20, Hrefine 123, Kelpto 165, Wherewith 214).
With Black-style wrapping at 100 columns instead (the previous commit),
it changed 758 (51, 131, 63; Ordable 3, Chromarite 46, Argmas 64,
Hrefine 73, Kelpto 142, Wherewith 185); with neither list rules nor
continuation indent, the first slice changed 189. It formats every
library file; it leaves alone one deliberately broken test program and
22 docs blocks that aren't whole programs (REPL transcripts, fragments).

Notes on the numbers:

- **Line width:** there's none. It was dominated by data: Hrefine and
  Kelpto keep Unicode and entity tables on very long lines (up to 4,780
  columns), and in core and std only 8 lines were over 100 columns.
- **The indentation model** (below) matches 99.8% of lines. The 104
  that differ are mostly hand-aligned expressions in std's `temporal`
  and `json`, a 3-space slip in `core.temper`, and some test inputs.
- **Continuation lines** split between no extra indent (class headers
  like `class C\nextends D {`, chained `&&` in conditions, `for`
  headers) and two spaces (57% of lines after a binary operator, 67%
  of lines starting with one). Neither tells a continuation line from
  the block inside it (`if (a &&\n  b) {\n  c`), so they're indented
  twice, four spaces, as in Java and Kotlin; the [numbers](#the-numbers)
  say what that changes.
- **`for` headers** written `for (var i = 0;\n    i < n;\n    i += 1) {`,
  with the first clause on the `for (` line, are continuation lines
  too: a `;` in `( )` ends a statement only if the contents start on a
  line of their own (`for (\n  a;\n  b;\n) {`, each clause then indented
  one level), so here the later clauses are indented four spaces from
  the `for` line, and the loop's body two. That tells the header from
  the body, as in `if (a &&\n    b) {`; their line breaks stay.
- **Docs examples aligned in columns** (`builtins.md`'s `""       ==
  cat()             &&` chains) get the normal rules: one space around
  operators and continuation lines indented four. Space before `<` stays
  as written (it decides angle brackets). There's no `// fmt: off`.
- The literate line numbers the script's `--samples N` prints count
  lines of the joined code blocks, not of the file.

## Lists: the author decides

Bracketed lists (arguments, parameters, list and object literals, and
type arguments: `( )`, `[ ]`, `{ }` and `< >` around a comma list of
two or more items, or one with a trailing comma, and a single item in
`( )`, `[ ]` or `< >`) are laid out as their author asks, with no line
width:

1. **No line break right after the open bracket:** the list goes on one
   line, however long (`f(a,\n  b)` becomes `f(a, b)`).
2. **A line break right after the open bracket, and no trailing
   comma:** the items go together on one indented line, and the close
   bracket on a line of its own (`f(\n  a, b\n)`).
3. **A trailing comma:** one item per line, each with its comma, and the
   close bracket on a line of its own.

`fmt` never adds or removes a comma: rule 3's trailing comma is already
there, and going from one item per line to one line, or to a hugged
line, is the author's to ask for by deleting it. So the layout always
says what was asked for, and formatting is idempotent by construction.
Each list follows its own line breaks, inner lists too: in
`build(\n  rows(a,\n    b),\n  c,\n)` the outer list goes one item per
line and `rows(a, b)` joins. Lists are laid out inner ones first, so an
outer list sees whether its items span lines as formatted.

A single item follows the same rules: `f(\n  x\n)` stays hugged,
`f(\n  x)` gets its `)` on a line of its own, `f(x\n)` joins, and
`f(x,)` goes one per line. Single items in `{ }` don't count: they may
be a block's one statement.

**Items over several lines.** The rules speak of items on one line,
with two exceptions:

- **The last item may span lines if it ends with a close bracket** (a
  block argument, an inner list, an object literal, a call): rule 1 or
  2 applies to everything before it, and the last item keeps its own
  layout (its own lists following their own rules), with the list's
  close bracket after its last line:

  ```temper inert
  foo(a,                          foo(a, b, fn {
    b, fn {                         body();
      body();          becomes    });
    });                           let nested = outer(short, inner(
  let nested = outer(short,         first, second
    inner(                        ));
      first, second
    ));
  ```

  Contents indent one step from the line their statement starts on, and
  the close brackets return to it, as everywhere. An item not ending
  with a bracket (`a +\n    b`, a multi-quoted string, which must end
  its line) keeps the list as written.
- **With a trailing comma, any item may span lines** (rule 3): each
  item starts a line of its own, whatever its own layout, so `}, {`
  between two objects becomes `},\n  {`.

Otherwise these keep their line breaks as written:

- **Comments between items**: a line comment can't be joined, and a
  comment's place among the items is the author's. (Comments inside a
  last item, or inside any item of a list with a trailing comma, are the
  item's.)
- **Another item over several lines**: a block or a list that isn't
  last, as in `foo(fn {\n  ...\n}, b)`. Joining what follows its last
  line, or breaking before its first, has no layout everyone writes,
  so when in doubt, it's left out.
- **A list in a string's `${ }`**.
- **Data**: a list whose items (two or more) are all literals (numbers, `true`,
  `false`, `null`, strings without holes, `char'a'`, negated numbers) or
  lists or calls of them (`new Pair("a", 1)`). Tables are written a few
  values per line: `[\n  0x41, 0x42,\n  0x43, 0x44\n]` would join onto
  one line by rule 2 (or by rule 1, without the first break), and
  explode to one value per line by rule 3. So data lists keep their
  rows. Without this exception the list rules change 590 lines in
  Hrefine, 415 in Ordable, 188 in Chromarite, 184 in Kelpto and 86 in
  tests (against 63, 0, 18, 47 and 14); see below.

**Only lists and continuation lines:** other line breaks stay. A line
break inside a list's brackets, after its commas, can't end a
statement, so moving it never changes meaning; elsewhere it could.

### Why no width

Black, Prettier and rustfmt pick line breaks to fit a width, which
rewrites lines whenever a name's length changes and needs exceptions
(data tables, magic trailing commas, opt-out comments) where the width
gets it wrong. Here the author's two choices, a line break after the
open bracket and a trailing comma, pick among the three layouts people
already use, and `fmt` only makes them consistent. Lines can be long:
that's the author's to fix, by breaking after the bracket.

### The numbers

`rules_census` in `crates/temper-syntax/tests/fmt.rs` counts, over the
repository and the six libraries, the lines `fmt` changes and the lines
each rule for line breaks changes (a line of the output without the
rule that the output with it doesn't have):

| Group | Lines | `fmt` changes | List rules (data lists too) | Continuation indent |
|---|---|---|---|---|
| core+std | 4,594 | 69 | 6 (8) | 42 |
| tests | 10,478 | 132 | 14 (86) | 22 |
| docs (54 of 69 files whole) | 14,010 | 48 | 0 (0) | 33 |
| Argmas | 7,225 | 18 | 16 (23) | 2 |
| Chromarite | 3,807 | 20 | 18 (188) | 0 |
| Hrefine | 12,902 | 123 | 63 (590) | 58 |
| Kelpto | 24,934 | 165 | 47 (184) | 117 |
| Ordable | 3,169 | 6 | 0 (415) | 6 |
| Wherewith | 11,841 | 212 | 54 (78) | 157 |

What changes: lists written one item per line without a trailing comma
hug (std's `json` signatures, `afterSubstring(\n  string, inString,
substring\n)`); lists written a few items per line with a trailing
comma go one per line (Kelpto's and Hrefine's long signatures and
`tree.add(...)` calls); lists with no break after the bracket join.
Continuation lines move most in Wherewith and Kelpto, whose `if (`
conditions over several lines indented their later lines by 0 or 2.
Multi-line last items and single items change almost nothing: authors
already write `foo(a, b, fn {` and `outer(x, inner(`. The changes are
std's `temporal` (`&& (\n  if ... }))` gets its `)` on a line of its
own, rule 2 for the single item) and Hrefine's `serializeUrl(` call,
whose trailing comma now puts its items one per line around a
multi-line `if` item.

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
  tree and decides each one's layout, inner lists first (so an outer
  list knows which items span lines), from its trailing
  comma and the line break after its open bracket. Then, in the same
  pass that sets the space between tokens, it adds or removes line
  breaks only at each list's own places (after the open bracket, after
  a comma, before the close bracket). The lines it adds indent as any
  line there would. A laid-out list's `< >` count as brackets for
  indentation; other `<`s don't, as they may be less-than.
- **Indentation** is computed in one pass with a stack of open brackets,
  each remembering the line its contents indent from: the bracket's own
  line if that line started at the bracket's depth, or else the line
  that started the statement at that depth. Contents get that line's
  indentation (as formatted) plus two spaces; a line starting with a
  close bracket lines up with it. So `foo(a, fn {`, `.map { x =>` on a
  chain's continuation line, `class C\nextends D {`, and the `{` ending
  a `for (` header split over lines each indent their contents one
  level, as the libraries do. A line continuing a statement is indented
  four spaces from the statement's first line: the code before didn't
  end with `;`, `{`, `}`, `,` or `=>` (in `( )` and `[ ]`, `,`, or `;`
  if their contents start on a line of their own, as in
  `for (\n  a;\n  b;`), or it starts with `.` or `?.`. But a line
  starting a node right after another (statements without a `;`
  between them, `f()\ng()`) or after a decorator line (`@connected`)
  starts a statement, and a comment line goes with the code after it.
  A bracket opened on a continuation line indents its contents from that
  line (`.map { x =>`), but on a line continuing a declaration's header
  (a word not after an operator, like `extends` or `throws`) from the
  declaration's first line, so a class body indents one level from
  `class`. A line starting with a close bracket continues its bracket's
  line (`} else {`).
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
  next multiple of four, as the margins do). No line is indented left
  of its margin, even under a line kept there.
- **Safety:** input with lexing, bracket or parse errors, an unclosed
  block comment, string or `${` is left alone, with the first error.
  After formatting, the result is parsed again and must have the same
  tokens (commas included), comments (ignoring each line's
  indentation), parse tree and markdown outside code; otherwise `fmt`
  refuses, as a bug.
- **Idempotence:** every rule depends on the tokens and on indentation
  the formatter computed itself, not on the input's (except line breaks
  as written, which the list rules keep where they decide), so formatted
  code formats to itself. Tests check it over every Temper file in the
  repository and randomly edited inputs (with lists' line breaks and
  trailing commas added and removed).
- **Speed:** one lex and parse, a linear pass, and one more parse for
  the check. Finding statements, lists and operators' roles in the tree
  isn't slow: each is one walk over the tree, no searching. In release,
  with the machine quiet, `core.temper` (50 KiB) takes 6 to 8 ms, of
  which its two parses are 3.5 to 5 ms, so the formatter's own pass,
  tree walks included, is 2 to 3 ms; `json.temper.md` (43 KiB) 9 to 10
  ms (parses 5 to 6). The list rules cost about a millisecond at most
  (timed with and without them), and the rules for multi-line last
  items and single items nothing measurable (the CLI with and without,
  back to back, `--check`, process start included: core 16 to 25 ms
  and json 30 to 38 ms either way). The machine is noisy;
  `format_speed` in `tests/fmt.rs` (ignored by default) measures them.
  Through the language server, a formatting request for them answers in
  6 to 30 ms (`format_speed` in `crates/temper-lsp/tests/lsp.rs`).
- **Language server:** `textDocument/formatting` and `rangeFormatting`
  format the open text, reusing its unit's parse when that's current,
  and answer with the runs of changed lines (Myers' diff), all of them
  or those on a range's lines. A document that doesn't parse gets no
  edits and no error. The VS Code extension formats on save by default
  (`editor.formatOnSave` for `[temper]` and `[temper-markdown]`).

### Alternatives

- **A layout engine** (Wadler/Prettier documents with groups and
  indentation, picking breaks to fit a width) for everything. The
  previous commit wrapped lists Black-style at 100 columns with that
  idea; dropped for the author's choices ([why](#why-no-width)). Doing
  everything through it would rewrite far more lines than the census
  says is needed, and would have to respect semicolon insertion at
  every break.
- **Reprinting from the AST** loses comments and layout the tree
  doesn't keep; the parse result's tokens make that unnecessary.

## Status and next steps

Done: `temper fmt [PATH]...` and `--check`; the rules above marked as
proposed; lists, single items too, laid out by the author's line
breaks and trailing commas, with multi-line last items (and any item
with a trailing comma) following the rules, and comments between
items, other multi-line items and data lists kept; `for` headers and
aligned docs examples by the normal rules;
continuation lines indented twice; golden cases (`tests/fmt/*.cases`),
idempotence and tree-preservation tests over the repository's Temper
files and randomly edited inputs; refusal for code that doesn't parse;
language server formatting, on save by default in VS Code. The
repository's own files aren't reformatted yet.

Next:

1. Format lib/ and docs in one commit of its own, listed in
   `.git-blame-ignore-revs`; then `temper fmt --check` in CI.
2. Doc comments' framing (`/** … */`), if wanted.
3. A `// fmt: off` region, if hand-aligned code ever needs one.
4. Markdown prose in `.temper.md`: left exactly as written. Formatting
   it (rewrapping paragraphs, normalizing lists and emphasis) is a
   Markdown formatter's job; should `fmt` call one, or leave prose to
   editors?
