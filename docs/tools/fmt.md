# Formatting rules

`temper fmt` ([command line](cli.md#fmt)) lays out Temper code in one
standard style. It has no options and no line width: it sets the
spaces, and keeps your line breaks, except in lists.

## Spacing

One space goes around operators, after commas, semicolons, and colons
in types and object literals, and inside `{ }`; none goes inside `( )`
and `[ ]`, or between a called name and `(`. Runs of spaces and tabs
become one.

```temper inert
let x:Int=f( a ,b )+1;          // before
let x: Int = f(a, b) + 1;       // after
if(ok){go()}                    // before
if (ok) { go() }                // after
let p = {x:1, y :2};            // before
let p = { x: 1, y: 2 };         // after
```

No space goes just inside a string's `${ }`, as in Kotlin, Python's
f-strings, and JavaScript's template literals; one over several lines
keeps its line breaks.

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
ends the call, so the block isn't its argument).

```temper inert
let ready = loaded &&
            checked;     // before
let ready = loaded &&
  checked;               // after
```

A continuation line indents four spaces instead, to stand apart from
what follows: in a header (a class's, interface's, function's, or
function literal's, before the `{` of its body), from the line the
header starts on; and in parentheses whose contents start on the `(`
line, as a condition's do, from that line (break right after the `(`,
and they indent two from their own first line, as above). A comma
between supertypes (`extends A,` then `B` on the next line) continues
the header too. Brackets
opened on such a line indent their contents from it, but a body from its
header's first line.

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
```

## Blank lines

Blank lines stay as you wrote them, but never more than one in a row
(at a `.temper.md` code block's start and end too), and none at the
start of a file. Trailing spaces go, after comments
too, and a file ends with one line break, unless it has nothing but
space: then it's empty. A file's line breaks stay its own: in a file
with CRLF line breaks, the ones `fmt` adds are CRLF too.

```temper inert
let a = 1;


let b = 2;   // before: two blank lines

let a = 1;

let b = 2;   // after: one
```

## Lists

Lists are arguments, parameters, list and object literals, and type
arguments, and a single item in parentheses or brackets, as in
`(a + b)`. Your line breaks and trailing comma choose their layout:

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
directories, and what `.gitignore` and `.temperignore` files list, in
it and above it up to its git repository's root, as `build` does. It
skips symbolic links too, to files as to directories, so a link in a
checkout can't make it write outside; a link named on the command line
formats the file it links to. To
keep more as written, such as a golden test's input or code that
doesn't parse on purpose, list it in a file of gitignore patterns,
relative to that file's directory, and name it with `--ignorefile`
([ignore files](cli.md#ignore-files)). A directory named that it lists
is skipped too (but not its own directory, where its patterns start); a
file named on the command line is formatted all the same. For a check
in CI, with this
`.fmtignore`:

```gitignore
# Golden inputs, kept as written
tests/inputs/
# Code that doesn't parse, on purpose
/examples/errors.temper.md
```

```sh
temper fmt --check --ignorefile .fmtignore
```
