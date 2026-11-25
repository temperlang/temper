# `temper fmt`

Part of the [rewrite plan](README.md); for
[#681](https://github.com/temperlang/temper/issues/681). User docs, and
the spec: [the formatting rules](../docs/tools/fmt.md) and
[the command line](../docs/tools/cli.md#fmt); this note is the design
and census record. Code:
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
| Continuation lines in a header, before its body's `{` (`extends`, `throws`), offset from the header's first line | 4: 2, 2: 1, 0: 1 (4 lines) | 4 | (below) |
| Continuation lines in `( )` whose contents start on the `(` line (`if (a \|\|\n  b) {`), offset from that line | 2: 86%, 0: 12% (176 lines) | 4 | (below) |
| Other continuation lines (at an operator, `orelse` too, or before a word or operand) | 0: 61%, 2: 32%, 4: 2% (516 lines) | 2 | (below) |
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
| Inside a string's `${ }` | of 2,931: none 97.1%, one 1.7%, over several lines 1.2% | none (Tom, 2026-11-24) | 48 |
| After prefix operators and `@` | none 100% | none | 0 |
| Runs of spaces elsewhere | 1 case | one space | 1 |
| Before a trailing comment | one 84%, aligned 15% | keep as written | 0 |
| `//` then a space | 99.9% | keep (comment text) | 0 |
| Trailing space | 1 line | none | 1 |
| Blank line after a line ending `{` | none 99.9% | keep as written | 0 |
| Blank line before a line starting a close bracket | none 100% | keep as written | 0 |
| Blank line after a multi-line statement | none 75%, one 25% | keep as written | 0 |
| Blank lines in a row | at most one | at most one | 0 |
| Trailing comma, multi-line `[ ]` | yes 99.6% | as written: it asks for one item per line ([lists](#lists-the-author-decides)) | (below) |
| Trailing comma, multi-line `( )` | yes 63% | as written | (below) |
| Trailing comma, multi-line `{ }` with commas | no 72% | as written | (below) |
| Multi-line `( )` lists | tall 49%, hugged 50% | by the author's line break and trailing comma | (below) |
| Multi-line `[ ]` lists | tall 99.8% | the same | (below) |
| Block arguments | trailing block 97%, `fn` in `( )` 3% | not changed (that's meaning) | - |

**`temper fmt` itself** (`--temper`, over copies, with lib/ and test
programs already formatted) changes 714 of 81,110 lines (0.9%, prose
included): 13 in core and std (3 files), 123 in test programs, 143 in
docs examples, and 1 to 205 in each library (Ordable 1, Argmas 16,
Chromarite 19, Hrefine 74, Kelpto 120, Wherewith 205). Before `( )`
continuations went to four spaces it changed 548: that rule moves 166
lines, 148 of them Wherewith's tests' `assert(a ==\n  "...")`. Before
lib/ was formatted, it changed 645.
Indenting every continuation line four spaces, and removing blank lines
after `{` and before `}`, changed 895; Black-style wrapping at 100
columns (an earlier commit) 758; with neither list rules nor
continuation indent, the first slice changed 189. It formats every
library file; it leaves alone one deliberately broken test program and
22 docs blocks that aren't whole programs (REPL transcripts, fragments).

Notes on the numbers:

- **Interpolations** are tight, `"hi, ${name}"`, as in Prettier's
  template literals, Kotlin, and Python's f-strings, unlike `{ a: 1 }`
  (Tom, 2026-11-24). By group: core+std 14 tight, 1 padded; tests 936,
  42, and 30 over several lines; docs 77, 8 and 1; and the libraries
  all tight but Wherewith's 3 over several lines (Argmas 189,
  Chromarite 44, Hrefine 221, Kelpto 447, Ordable 29, Wherewith 889).
  Tightening adds 43 changed lines (1 in core and std, 35 in tests, 7
  in docs), none in the libraries. One over several lines
  (`"${\n  a +\n    b\n}"`, almost always a line break right after
  `${`) keeps its line breaks, its lines laid out by the usual rules
  as in any bracket; only space on the `${` or `}` line, next to them,
  goes (`${ f(\n  a\n) }` becomes `${f(\n  a\n)}`), so one rule
  covers both.
- **Line width:** there's none. It was dominated by data: Hrefine and
  Kelpto keep Unicode and entity tables on very long lines (up to 4,780
  columns), and in core and std only 8 lines were over 100 columns.
- **The indentation model** (below) matches 99.8% of lines. The 104
  that differ are mostly hand-aligned expressions in std's `temporal`
  and `json`, a 3-space slip in `core.temper`, and some test inputs.
- **Continuation lines** are indented one step, two spaces, wherever
  they break: at an operator or punctuation (`a &&`, `.b`, `orelse`), or
  before a word or operand (`new\n  Foo()`). Today 53% have none
  (chained `&&` in conditions) and 41% two. The exception is any
  header, a class's, interface's, function's or function literal's,
  from its first token to the `{` of its body: its continuation lines
  (`class C\n    extends D {`, `let f(): T\n    throws Bubble {`), in
  brackets too (`run(fn (): T\n    throws Bubble {`), get two steps,
  four spaces, from the line the header starts on, to tell them from
  the body, which indents one step from that line (Tom, 2026-11-24).
  Lines in the header's own brackets (its parameters, type parameters)
  follow the normal rules: indenting them from the header's first line
  could put them left of their own statement, and there's no body
  to tell them from. A line starting a header (`let g =\n  fn ...`)
  continues what's before it, once. The parse tree decides what's a
  header. Only 4 lines are headers' continuations today. Four spaces for all continuation
  lines, as in Java and Kotlin, changed far more (157 lines in
  Wherewith, 117 in Kelpto, against 3 and 69; see
  [the numbers](#the-numbers)).
- **In parentheses whose contents start on the `(` line**, continuation
  lines get four spaces too, from that line (Tom, 2026-11-24), to tell a
  condition from the body after it: `if (a ||\n    b) {\n  c`. Temper's
  `if`, `while` and `for` are calls with trailing blocks, not special
  syntax, so the rule is structural and applies to every `( )`
  (`foo(a ||\n    b)`, `for (var i = 0;\n    i < n;`, `do { } while (a
  ||\n    b)`), not only those followed by a block: the simplest rule
  to state, and the parse tree isn't needed. `[ ]` keep two
  (`list[a +\n  b]`). With a line break right after `(`, the contents
  start a line of their own and their continuations indent two from it,
  as anywhere (`if (\n  a ||\n    b\n) {`). Brackets opened on a
  four-space line indent their contents from it, so
  `1 <= day && (\n  if ...` nests from the `1 <= day` line and `)) {`
  returns to it. Today 176 such lines: 86% indented two, 12% none.
- **`fmt` follows the parse tree:** a line starting with `orelse` after
  a line ending with `}` (`let x = do { f() }\norelse 7;`) continues the
  statement, by design: a `}` ending a line ends the statement unless the
  next token can only continue an expression, as infix-only operators
  like `.`, `?.`, `orelse`, `&&` and `==` can ([automatic semicolon
  insertion](../docs/language/syntax.md#automatic-semicolon-insertion)).
  The other way, a `{ x }` on the line after a whole call `f(a)` is a
  statement of its own (the parser inserts a `;` between them), so it
  starts a statement, not a continuation: nodes with only inserted
  tokens between them start statements as juxtaposed ones do.
- **Supertype lists:** a comma between supertypes (`extends A,\n B`)
  continues its header, four spaces from its first line, where other
  commas outside brackets end a statement.
- **A line starting with `;` or `,`**, as after a multi-quoted string
  (`let s = """\n  "text\n;`), lines up with the start of what it ends
  (the statement, or the list's item), as a close bracket lines up with
  the line it opened on: it ends the statement rather than continuing
  it.
- **A condition over several lines** follows its line break after
  `if (`. With one, the first operand starts a line of its own and the
  rest continue it (`if (\n  a &&\n    b\n) {`); without, the rest
  continue the `if` line four spaces in (`if (a &&\n    b) {\n  c`), as
  above.
- **`for` headers** written `for (var i = 0;\n  i < n;\n  i += 1) {`,
  with the first clause on the `for (` line, are continuation lines
  too: a `;` in `( )` ends a statement only if the contents start on a
  line of their own (`for (\n  a;\n  b;\n) {`, each clause then indented
  one level). Broken after `;`, the later clauses are indented four
  spaces, as `if (a &&\n    b) {` is; their line breaks stay.
- **Blank lines** are the author's, after `{` and before `}` too (std
  json's `) extends JsonSyntaxTree {` then a blank line); only runs of
  them become one, and there are none at a file's start. A comment is
  never code: `class A {} // x {` doesn't end with `{`.
- **Docs examples aligned in columns** (`builtins.md`'s `""       ==
  cat()             &&` chains) get the normal rules: one space around
  operators and continuation lines indented two. Space before `<` stays
  as written (it decides angle brackets). Those kept by hand use
  [`// fmt: off`](#design) or the script's `<!-- fmt: off -->`.
- The literate line numbers the script's `--samples N` prints count
  lines of the joined code blocks, not of the file.

## Lists: the author decides

Bracketed lists (arguments, parameters, list and object literals, and
type arguments: `( )`, `[ ]`, `{ }` and `< >` around a comma list of
two or more items, or one with a trailing comma, and a single item in
`( )`, `[ ]` or `< >`) are laid out as their author asks, with no line
width:

1. **No line break right after the open bracket:** the list goes on one
   line, however long (`f(a,\n  b)` becomes `f(a, b)`; items over
   several lines: [below](#lists-the-author-decides)).
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

**Items over several lines.** An item may span lines (a block, an
inner list, an object, a `+` continued on the next line); the rules
lay out the items in sequence, the same way whatever lines each takes:

1. Each item starts right after the previous one's last line, after
   `, `; the close bracket follows the last item's last line.
2. The same, starting on the indented line after the open bracket, with
   the close bracket on a line of its own.
3. Each item starts a line of its own (`}, {` between two objects
   becomes `},\n  {`).

An item's interior follows its own rules, recursively: its lists their
own line breaks, its blocks and continuation lines as anywhere. Its
contents indent one step from the line its bracket opens on (or the line
starting that statement), and its close bracket returns to that line,
as everywhere; so in rule 1 an item's lines indent from the list's first
line, and in rule 2 from the line the item starts on:

```temper inert
foo(fn {                        foo(fn {
  body();                         body();
},                     becomes  }, b, [
  b, [                            first,
    first,                        second,
    second,                     ]);
  ]);                           let nested = outer(short, inner(
let nested = outer(short,         first, second
  inner(                        ));
    first, second
  ));
```

Some places keep a list's line breaks as written:

- **Comments between items** (before an item's first token or after its
  last): a line comment can't be joined, and a comment's place among
  the items is the author's. Comments inside an item are the item's.
- **An item that must end its line**: a multi-quoted string ends at its
  line's end, so what follows can't join its last line. Moving the next
  item (and its comma) to a line of its own would follow no rule people
  write, so when in doubt, it's left out.
- **A line break before a comma**, and code split between literate
  code blocks.
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
rule that the output with it doesn't have). With lib/ and test
programs already formatted (before, core+std changed 67 lines):

| Group | Lines | `fmt` changes | List rules (data lists too) | Continuation indent |
|---|---|---|---|---|
| core+std | 4,592 | 13 | 0 (2) | 13 |
| tests | 10,480 | 123 | 13 (85) | 7 |
| docs (55 of 70 files whole) | 14,153 | 47 | 0 (0) | 32 |
| Argmas | 7,225 | 16 | 16 (23) | 0 |
| Chromarite | 3,807 | 19 | 18 (188) | 0 |
| Hrefine | 12,902 | 74 | 63 (590) | 9 |
| Kelpto | 24,934 | 117 | 47 (184) | 70 |
| Ordable | 3,169 | 1 | 0 (415) | 1 |
| Wherewith | 11,841 | 203 | 54 (78) | 149 |

What changes: lists written one item per line without a trailing comma
hug (std's `json` signatures, `afterSubstring(\n  string, inString,
substring\n)`); lists written a few items per line with a trailing
comma go one per line (Kelpto's and Hrefine's long signatures and
`tree.add(...)` calls); lists with no break after the bracket join.
Continuation lines move most in Wherewith, Kelpto, and core and std:
`( )` continuations from two spaces to four (Wherewith's
`assert(a ==\n    "...")`, std's `if (a ||\n    b) {`), lines after an
operator written with no extra indent (`if (\n  a ||\n  b\n)`, which
becomes `a ||\n    b`), and class headers' `extends` lines (four).
Items over several lines and single items change almost nothing:
authors already write `foo(a, b, fn {` and `outer(x, inner(`, and
nowhere write a list (without a trailing comma) with a multi-line item
before its last; formatting with the general rule for multi-line items
gives byte for byte the output of the earlier rule allowing only a last
one ending with a bracket. The changes are
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
  spacing rules, keyed on the two tokens' roles and their operators'
  spacing, declared in the operator table (`operator::Spacing`: one
  space, none, none before and one after, or as written; docs/tools/fmt.md's
  table is checked against it); across lines, the line breaks (blank
  lines capped), then the indentation. Every line's indentation is
  computed by the same rules; then one decision,
  `Formatter::stays_as_written`, keeps some as written (`// fmt: off`
  regions, left of the margin, tabs in markdown or a string's line, a
  fence-like line, a leading `<`, a line that would join a list item,
  a block comment that can't move). A kept line still starts or
  continues statements, as its indentation as written.
  Space next to comments, inside strings (and `\u{...}` escapes), and
  before `<` (whether `<` follows space decides whether it may be an
  angle bracket) is copied, and any change is checked by lexing the two
  tokens with the new space, after a value and after an operator (`/`
  divides after one and starts a regex after the other), so `- -x`,
  `a b`, `1 .x` or `x /  .y` never join.
- **Lists** are the exception (above): the formatter finds them in the
  tree and decides each one's layout, inner lists first (so an outer
  list knows which items span lines), from its trailing
  comma and the line break after its open bracket. Then, in the same
  pass that sets the space between tokens, it adds or removes line
  breaks only at each list's own places (after the open bracket, after
  a comma, before the close bracket). The lines it adds indent as any
  line there would. Type arguments' and parameters' `< >` (the tree
  tells) count as brackets for indentation, empty ones holding a comment
  too; other `<`s don't, as they may be less-than.
- **Indentation** is computed in one pass with a stack of open brackets,
  each remembering the line its contents indent from: the bracket's own
  line if that line started at the bracket's depth, or else the line
  that started the statement at that depth. Contents get that line's
  indentation (as formatted) plus two spaces; a line starting with a
  close bracket lines up with it. So `foo(a, fn {`, `.map { x =>` on a
  chain's continuation line, `class C\nextends D {`, and the `{` ending
  a `for (` header split over lines each indent their contents one
  level, as the libraries do. A line continuing a statement is indented
  from the statement's first line, two spaces, or four from a header's
  first line at the header's own depth (above; the tree marks headers'
  tokens, after a class's, interface's, function's or function
  literal's first token, to its body's `{`), or four from a `(`'s line
  when it continues the statement starting there (each line and open
  bracket remembers the line it's on): the code before
  didn't end with `;`, `;;` (a block's signature), `{`, `}`, `,`,
  `=>` or a label's `:` (in `( )` and `[ ]`, `,`,
  or `;` if their contents start on a line of their own, as in
  `for (\n  a;\n  b;`), or it starts with an infix operator, as `.`,
  `?.` or `&&` (the tree tells). One starting with `;`, `;;` or `,` lines up
  with the statement's first line. Which token is before a line, or
  first on it, is always code: comments are skipped. But a line
  starting a node right after another (statements without a `;`
  between them, `f()\ng()`, which covers a declaration after a
  decorator line like `@connected`) starts a statement, and a comment
  line goes with the code after it.
  A bracket opened on a continuation line indents its contents from that
  line (`.map { x =>`, `&& (` in a condition), but a header's body's
  `{` from the header's first line, so a class body indents one level
  from `class`, and a function literal's from the line its header
  starts on. A line starting with a close bracket continues its bracket's
  line (`} else {`).
- **Comments** never move between tokens, so they can't be lost or
  reattached: a comment on its own line is indented like the code
  around it, a trailing comment keeps its space. A multi-line block
  comment's later lines move with its first (doc comments' ` * ` lines),
  and only if all of them can, but a doc comment's (`/** ... */`) star
  gutter lines up one column past its `/`, as formatted (the usual
  ` * ` frame), with what follows each `*` as written. A comment has a
  gutter when every line after its first that isn't blank starts with
  `*` after its indentation, its closing `*/` included; in one without,
  `*` lines are text like any other (bullets, say) and move with the
  rest, as their nesting would otherwise be lost. A gutter after a tab
  on its first line stays as written, as spaces can't match a tab's
  width. No stars are added or removed, and no lines joined or split. Before, `*` lines only moved
  with the first line too, so stars written off that column stayed off
  it (`  *` under `/**`). Only that column changes,
  so the framing's other choices (a `/**` line of its own, stars on
  every line) stay the author's. A comment whose lines can't all move
  (one would go left of the margin, or is indented with a tab, where
  moving would mix tabs and spaces) stays where it is, its first line's
  indentation too, keeping its lines' indentation relative to each
  other, which the safety check compares (an ASCII diagram mustn't come
  apart). Indentation is spaces and tabs only: other Unicode space (an
  ideographic space, NBSP) is the comment's text, and moves with it.
  Likewise a multi-quoted string's line indented with tabs stays as
  written; others move with the line the string starts on. Trailing space goes from every
  line of a comment, as from code: "trailing spaces go" with no
  exception to explain, and it's never meaningful.
- **`// fmt: off` regions:** lines after a
  `// fmt: off` comment on a line of its own, to a `// fmt: on` on a line
  of its own, keep their indentation, space, line breaks and trailing
  space as written; the formatter still tracks their brackets, and lists
  with a place in a region are laid out as written. The spelling is
  Black's and ruff's (`# fmt: off`) with Temper's comment, shorter than
  clang-format's `// clang-format off`. Without a `// fmt: on`, the
  region ends where the brackets around the `off` close, at the end of
  its block, much as Black and ruff end one with its indented block: to the end of the file would surprise in a
  function, and requiring `on` would make a missing one an error for no
  gain. In `.temper.md`, regions end with their code block, as cells do
  in notebooks, though blocks continue each other's brackets. The two
  comments themselves indent like any. Comments after code on their
  line, `/* fmt: off */`, `//fmt: off`, and other spellings don't count
  (one spelling to look for and document), and a `// fmt: on` with no
  region to end is an ordinary comment.
- **Multi-quoted strings:** their content lines move with the line that
  opened the string; the margin's indentation is incidental, so their
  values don't change.
- **Literate files:** only Temper code blocks are edited, each indented
  from its block's margin (the fence's indentation, or four columns past
  its list items' for indented code); blocks continue each other's
  brackets, as the compiler reads them, so a class split over several
  blocks indents consistently. Blank lines at a fenced block's start
  and end collapse to one, as elsewhere (those are the block's, not
  prose). Prose, including trailing space, is never
  touched, nor are lines indented with tabs (markdown counts tabs to the
  next multiple of four, as the margins do) or code lines starting like
  a fence (```` ``` ```` or `~~~`, which moving could make close their
  block; found by fuzzing). No line is indented left
  of its margin, even under a line kept there. The first line of
  indented code right after a list item (`*     g();`, a blank line,
  then `    h();` inside `g`'s brackets) stays left of the item's
  content column, as written if formatting would reach it: there it
  would continue the item, changing the blocks (fuzzing; the markdown
  scanner reports that column).
- **Safety:** input with lexing, bracket or parse errors, an unclosed
  block comment, string or `${` is left alone, with the first error.
  After formatting, the result is parsed again and must have the same
  tokens (commas included), comments (ignoring trailing space and the
  indentation their lines share), parse tree and markdown outside code;
  otherwise `fmt` refuses, as a bug. The tests don't rest on that
  check alone: the repository and fuzzing tests check with their own
  code that only space changed (the text without space is the same, the
  tokens lexed again are, with comments' relative indentation, and
  `// fmt: off` regions' lines are byte for byte), and that a quarter
  of fuzzed inputs at least still parse, so refusing them all can't
  pass. Push CI fuzzes with a fixed seed; nightly.yml's `fmt-fuzz`
  with a new one each night.
- **Line breaks and space-only files:** a file's line breaks stay its
  own; the ones `fmt` adds, and the one at the end, are CRLF if the file
  has any (mixed files keep each gap's own). A lone `\r` (not before
  `\n`), a line break to some editors and language clients but not to
  Temper's lexer, is refused with its line (`FmtError::Unsupported`):
  the simpler safe choice than counting it as a line break everywhere,
  which would also have to change the lexer's and the language server's
  line counting. A `.temper` file of only
  space becomes empty, as one with code has no blank lines at its start
  or end.
- **Idempotence:** every rule depends on the tokens and on indentation
  the formatter computed itself, not on the input's (except line breaks
  as written, which the list rules keep where they decide), so formatted
  code formats to itself. Tests check it over every Temper file in the
  repository and randomly edited inputs (with lists' line breaks and
  trailing commas added and removed).
- **Speed:** one lex and parse, a linear pass, and one more parse for
  the check. Linear in the source, long lines and deep nesting too:
  where each token's line starts, and how many tokens before each are
  kept or after prose, are found in one pass beforehand, not by looking
  back per token, which took quadratic time on a 600 KiB line (40k
  statements: 8 s, now well under one). The parser's check for a
  follower (`extends A, B`) counted a long comma list's tokens at each
  comma, which made 80k-item lists quadratic too. Headers nested in
  headers (a function literal as a default value in a function's
  parameters, thousands deep) were quadratic as each token was assigned
  to every enclosing header; now one sweep with a stack of open headers
  assigns each token its innermost once.
  `formatting_time_grows_linearly` checks that twice the code takes
  under three times as long, in debug. Finding statements, lists and operators' roles in the tree
  isn't slow: each is one walk over the tree, no searching. In release,
  with the machine quiet, `core.temper` (50 KiB) takes 6 to 8 ms, of
  which its two parses are 3.5 to 5 ms, so the formatter's own pass,
  tree walks included, is 2 to 3 ms; `json.temper.md` (43 KiB) 9 to 10
  ms (parses 5 to 6). The list rules cost about a millisecond at most
  (timed with and without them), and the rules for multi-line items
  and single items nothing measurable (the CLI with and without,
  back to back, `--check`, process start included: core 16 to 25 ms
  and json 30 to 38 ms either way). The machine is noisy;
  `format_speed` in `tests/fmt.rs` (ignored by default) measures them.
  Through the language server, a formatting request for them answers in
  6 to 30 ms (`format_speed` in `crates/temper-lsp/tests/lsp.rs`).
- **Language server:** `textDocument/formatting` and `rangeFormatting`
  format the open text, reusing its unit's parse when that's current,
  and answer with the runs of changed lines (Myers' diff), all of them
  or those within a range's lines (a range ending at a line's
  character 0, as a selection of whole lines does, leaves that line
  out). A run that keeps its number of lines is clipped to the range
  line for line; one that joins or splits lines is taken only if the
  range holds all of it, so `formatOnSaveMode: "modifications"` never
  touches lines outside what changed. Past 500 changed lines the diff
  stops and everything between the unchanged ends is one run. Edits'
  positions are line starts, so only an edit to the end of a last line
  without a line break counts UTF-16 units. A document formatting
  refuses gets no edits and no error. One that doesn't parse gets
  nothing more, as its syntax error is already a diagnostic (Tom,
  2026-11-24); the surprising refusals, a lone `\r` or a bug, get a
  `window/showMessage` warning saying why, once per version of the
  document, so saving again doesn't nag. The VS Code
  extension formats on save by default (`editor.formatOnSave` for
  `[temper]` and `[temper-markdown]`).
- **Writing files:** each is written whole or not at all (a new hidden
  file beside it, with its permissions, synced, then renamed over it,
  and removed on any error), so a full disk or a kill mid-run leaves
  the original. A link named on the command line has its target
  replaced, staying a link. Files with other hard links (Unix), or in a
  directory where no file can be added, are written in place, as a new
  file would split the links or can't be made; read-only files are an
  error, as before. On Windows `rename` replaces too, but fails if
  another program holds the file open without sharing deletes. There's
  no check that the file didn't change between reading and replacing
  (gofmt and rustfmt don't either). A panic, a bug, costs only its
  file: the CLI catches it, as the language server does, reports it,
  and goes on, failing at the end.
- **The walk** shares the library scanner's ignore and skip rules
  (`project::{dir_ignores, skipped, scanned_dir, ignores_above}`), reads
  the ignore files above the directory named up to its git repository's
  root as `build` does, and skips symbolic links entirely (`build`
  follows links to files), so `temper fmt` in a checkout never writes
  outside it. It lists directories itself, as `Sources` doesn't say
  what's a link. A directory named that `--ignorefile` lists is skipped
  (not the ignore file's own), and a missing `--ignorefile` is an error
  whatever's named.

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
breaks and trailing commas, items over several lines in any position
following the same rules (recursively), and comments between items,
items that must end their line, and data lists kept; `for` headers and
aligned docs examples by the normal rules;
continuation lines indented two spaces, four in any header and in
`( )` whose contents start on the `(` line, lines starting with `;` or
`,` lined up with their statement,
blank lines kept (runs collapsed; Tom's reviews of trial reformats,
2026-11-24); interpolations tight (`${x}`);
golden cases (`tests/fmt/*.cases`),
idempotence and tree-preservation tests over the repository's Temper
files and randomly edited inputs; refusal for code that doesn't parse;
language server formatting, on save by default in VS Code; lib/ and
test programs formatted; `--ignorefile` (the repository's
`.fmtignore`: code that doesn't parse on purpose); the docs' examples
by `compat/fmt_doc_examples.py` (plain Markdown isn't `fmt`'s:
`<!-- fmt: off -->` keeps hand-aligned tables and spacing examples);
both checked in CI's lint job; `// fmt: off` regions (the script also
honors one as a block's first line), and doc comments' `*` lines lined
up past their `/`; and fuzzing's finds formatted rather than refused (a
`/` before a spaced `.`, `<</` at the end of a file, which now lexes as
before a line break, indented code after a list item, and empty `< >`
holding a comment).

Left out of this RFC and PR (Tom, 2026-11-24):

- Markdown prose in `.temper.md`, left exactly as written. Formatting
  it (rewrapping paragraphs, normalizing lists and emphasis) is a
  Markdown formatter's job; should `fmt` call one, or leave prose to
  editors?
- Doc comments' framing beyond their `*` column: adding or removing
  stars, moving `/**` or `*/` to lines of their own, rewrapping their
  text.

The pre-PR tests review (2026-11-24) found and this branch fixed: a CRLF
file without a final line break got a `\n`; the docs' examples check
passed whatever `temper fmt` did (it now checks the exit status, the
error lines and a canary block, with a self-test in CI); the directory
walk followed links to directories, looping; quadratic time on long
lines and deep nesting; tabs after keywords kept, trailing space after
comments kept, a space-only file kept, blank lines at a code block's
edges kept, `{a:1}` left tight; `fmt --check | head` panicking; a range
ending at character 0 taking the next line; and `UPDATE_SNAPSHOTS=0`
updating goldens.

The pre-PR code review (2026-11-24) found and this branch fixed: a
panic on comment lines indented with Unicode space (and a panic or
error in one file stopping the CLI's run); writes that weren't atomic;
the walk writing through links to files outside the root; `extends
A,\n B` and a block after a call on its own line indented wrongly; `*`
bullets in gutterless doc comments flattened; tab-indented comments and
strings drifting; lone `\r`s; ignore-file gaps; nested headers'
quadratic time; silent refusals and range edits past the range in the
language server; and the docs script's CRLF pages and mixed
indentation. The VS Code extension's server settings are now
machine-scoped (a workspace can't pick the binary).

Next: the docs' `<!-- fmt: off -->` blocks could move to a
`// fmt: off` first line, if wanted.
