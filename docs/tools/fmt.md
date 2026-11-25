# Formatting rules

`temper fmt` ([command line](cli.md#fmt)) lays out Temper code in one
standard style. It has no options and no line width: it sets the
spaces, and keeps your line breaks, except in lists.

## Spacing

One space goes around operators, after commas, semicolons, and colons
in types and object literals, and inside `{ }`; none goes inside `( )`
and `[ ]`, or between a called name and `(`, except after `if`,
`while`, `for`, `when`, `catch`, and `fn`, which take one (`if (ok)`,
but `f(ok)`), whatever they name. Runs of spaces and tabs become one.

```temper inert
let x:Int=f( a ,b )+1;          // before
let x: Int = f(a, b) + 1;       // after
if(ok){go()}                    // before
if (ok) { go() }                // after
let p = {x:1, y :2};            // before
let p = { x: 1, y: 2 };         // after
```

Each operator's spacing is declared in the parser's operator table:
around an infix operator, after a prefix one, inside brackets. Followers
space as themselves (the `,` in `extends A, B`), and an object literal's
`:` as a type's. Space before `<` always stays as written, as it decides
whether `<` may be an angle bracket.

| Spacing | Operators |
|---|---|
| One space around, or inside | `=>`, `->`, `=`, `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `^=`, `\|=`, `&&=`, `\|\|=`, `<<=`, `>>=`, `>>>=`, `??`, `\|\|`, `&&`, `==`, `!=`, `===`, `!==`, `=~`, `!~`, `<`, `<=`, `>`, `>=`, `\|`, `^`, `&` (infix), `<<`, `>>`, `>>>`, `+` (infix), `-` (infix), `*` (infix), `/`, `%`, `~` (infix), `**`, `{ }`, `\{ }` |
| None around, after, or inside | `@`, `< >`, `++` (prefix), `--` (prefix), `+` (prefix), `-` (prefix), `!` (prefix), `~` (prefix), `&` (prefix), `*` (prefix), `...`, `.`, `?.`, `( )`, `[ ]`, `\`, `\( )`, `${ }` |
| None before, one after | `;`, `,`, `:` (type) |
| As written (a run of spaces and tabs becomes one) | `;;;`, `;;`, `:` (marker), `given`, `return`, `throw`, `yield`, `of`, `:` (label), `case`, `orelse`, `extends`, `implements`, `forbids`, `supports`, `throws`, `in`, `instanceof`, `is`, `as`, `::`, `await`, `++` (postfix), `--` (postfix), `!` (postfix), `?`, `..`, `\u{ }`, `new` |

No space goes just inside a string's `${ }`, as in Kotlin, Python's
f-strings, and JavaScript's template literals; one over several lines
keeps its line breaks. Quasi-quotes space, indent, and lay out lists as
their plain brackets do (`\(a + b)`, `\{ f(x) }`). A string's `\u{ }`
is the string's own text, where a space would be part of a code point
(an error), so it stays exactly as written.

```temper inert
"hi, ${ name }, ${ f({ a: 1 }) }"   // before
"hi, ${name}, ${f({ a: 1 })}"       // after
```

## Indentation and continuations

Each bracket level indents two spaces.

```temper inert
if (ok) {
go();       // before
}
if (ok) {
  go();     // after
}
```

A line that continues a statement indents two spaces from the
statement's first line. One that starts a statement doesn't, as a
`{ x }` block on the line after a whole call `f(a)` does (the line break
ends the call, so the block isn't its argument), or the statement on the
line after a label (`outer:`). A line starting with `;`, `;;`, or `,`,
as after a multi-quoted string, lines up with the statement or item it
ends, as a close bracket lines up with its open bracket's line.

```temper inert
let ready = loaded &&
            checked;     // before
let ready = loaded &&
  checked;               // after
outer:
  for (;;) { f() }       // before
outer:
for (;;) { f() }         // after
```

Two kinds of continuation line indent four spaces instead, to stand
apart from what follows:

- In a header (a class's, interface's, function's, or function
  literal's, before the `{` of its body), from the line the header
  starts on. A comma between supertypes (`extends A,` then `B` on the
  next line) continues the header too.
- In parentheses whose contents start on the `(` line, as a
  condition's do, from that line. With a line break right after the
  `(`, the contents start a line of their own, and their continuations
  indent two from it, as above.

Brackets opened on such a line indent their contents from it, but a
header's body indents from the header's first line.

```temper inert
let parse(text: String): Int
throws Bubble {          // before
  0
}
let parse(text: String): Int
    throws Bubble {      // after
  0
}
run(fn (x: Int): Int
    throws Bubble {      // after, in brackets too
  x
});
if (ready &&
  checked) {             // before
  go();
}
if (ready &&
    checked) {           // after
  go();
}
if (
  ready &&
    checked              // after, with a break after `(`
) {
  go();
}
```

## Blank lines

Blank lines stay as you wrote them, but never more than one in a row
(at a `.temper.md` code block's start and end too), and none at the
start of a file. Trailing spaces go, after comments too, and a file
ends with one line break, unless it has nothing but space: then it's
empty. A file's line breaks stay its own: in a file
with CRLF line breaks, the ones `fmt` adds are CRLF too.

```temper inert
let a = 1;


let b = 2;   // before: two blank lines

let a = 1;

let b = 2;   // after: one
```

## Lists

Lists are arguments, parameters, list and object literals, and type
arguments. Grouping parentheses, as in `(a + b)`, are lists too, of one
item, as is an index in brackets, as in `items[i]`. Your line breaks and trailing comma choose their layout:

- No line break after the open bracket: all on one line.
- A line break after the open bracket: the items together on one line
  of their own.
- A trailing comma: one item per line.

```temper inert
f(a,
  b);        // before
f(a, b);     // after

f(
  a,
  b
);           // before
f(
  a, b
);           // after

f(a, b,);    // before
f(
  a,
  b,
);           // after
```

`fmt` never adds or removes a comma, so to put a list back on one line,
delete its trailing comma. An item may span lines; the next item starts
on the line it ends.

```temper inert
foo(fn {
  body();
},
  b);        // before
foo(fn {
  body();
}, b);       // after
```

A list with a comment between items, only literals (a table of data), or
a multi-quoted string as an item keeps its line breaks.

## What it never changes

- Line breaks outside lists.
- Comments' text, apart from trailing spaces. A comment on its own line
  indents like the code around it, its lines moving together, unless
  that would take one left of the margin, or one of its lines is
  indented with tabs (moving would mix tabs and spaces): then it stays
  where it is. A doc comment over several lines, `/** ... */`, with a
  star gutter (every line after the first that isn't blank starts with
  `*`, the closing `*/` too) has its `*` lines one column past the `/`,
  with what follows each `*` as written. In one without a gutter, lines
  starting with `*`, such as bullets, move with the rest.
- Strings. A multi-quoted string's lines move with the line it starts
  on, but lines indented with tabs stay as written.
- Prose in `.temper.md` files: only Temper code blocks change.

## Turning it off

Lines between a `// fmt: off` and a `// fmt: on`, each a comment on a
line of its own, stay exactly as written, as for a table aligned in
columns; the two comments indent like any other. Without a `// fmt: on`,
the region ends where the brackets around it close (the end of its
block), or at the end of the file or, in `.temper.md`, of its code
block. Only those exact comments count: `//fmt: off` and
`/* fmt: off */` don't, and a `// fmt: on` with no region to end is an
ordinary comment.

```temper inert
// fmt: off
let sizes = [
  "small",   1,
  "large", 100,
];
// fmt: on
```

## What it refuses

Code that doesn't parse is left as written, with an error, as is a
file with a carriage return not followed by a line feed (a line break to
some editors but not to Temper; `fmt` takes `\n` and `\r\n`). `fmt`
also checks its own work: if formatting would change a file's tokens,
comments, or parse tree, it leaves the file alone and says so. A bug
that stops formatting a file costs only that file: `fmt` reports it and
goes on.

Each file is written whole or not at all: the formatted text goes to a
new file beside it, with its permissions, which then replaces it. A
file with other hard links is written in place instead, so they all
see the change, as is one in a directory where no file can be added. A
read-only file is an error.

## What it leaves out

Under a directory, `fmt` skips `temper.out`, `temper.keep`, hidden
directories, and what `.gitignore` and `.temperignore` files list, as
`build` does ([ignore files](cli.md#ignore-files)). It skips symbolic
links too, to files as to directories, so a link in a checkout can't
make it write outside; a link named on the command line formats the
file it links to.

To keep more as written, such as a golden test's input or code that
doesn't parse on purpose, list it in a file of gitignore patterns and
name it with `--ignorefile`. A directory named that it lists is skipped
too (but not its own directory, where its patterns start); a file named
on the command line is formatted all the same. For a check in CI, with
this `.fmtignore`:

```gitignore
# Golden inputs, kept as written
tests/inputs/
# Code that doesn't parse, on purpose
/examples/errors.temper.md
```

```sh
temper fmt --check --ignorefile .fmtignore
```

## In editors

The [language server](editors.md) formats with the same code, a whole
document or the lines of a range, and the VS Code extension formats on
save by default. To turn that off, set the standard
`editor.formatOnSave` to `false` for `[temper]` (and
`[temper-markdown]`, for `.temper.md` files) in your settings. In
Restricted Mode, for a workspace you haven't trusted, the extension
doesn't run, so neither does formatting.

When it refuses a file, it leaves it as written and says why, except
for code that doesn't parse, whose syntax error already shows as a
diagnostic.
