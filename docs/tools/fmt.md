# Formatting rules

`temper fmt` ([command line](cli.md#fmt)) lays out Temper code in one
standard style. It has no options and no line width: it sets the
spaces, and keeps your line breaks, except in lists.

## Spacing

One space goes around operators, after commas, semicolons and type
colons, and inside `{ }`; none goes inside `( )` and `[ ]`, or between a
called name and `(`.

```temper inert
let x:Int=f( a ,b )+1;          // before
let x: Int = f(a, b) + 1;       // after
if(ok){go()}                    // before
if (ok) { go() }                // after
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
statement's first line.

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
and they indent two from their own first line, as above). Brackets
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

Blank lines stay as you wrote them, but never more than one in a row,
and none at the start of a file. Trailing spaces go, and a file ends
with one line break.

```temper inert
let a = 1;


let b = 2;   // before: two blank lines

let a = 1;

let b = 2;   // after: one
```

## Lists

Lists are arguments, parameters, list and object literals, and type
arguments. Your line breaks and trailing comma choose their layout:

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
- Comments' text (a comment on its own line indents like the code
  around it). In a doc comment over several lines, `/** ... */`, each
  line starting with `*` lines up one column past the `/`, with what
  follows the `*` as written.
- Strings.
- Prose in `.temper.md` files: only Temper code blocks change.

## Turning it off

Lines between a `// fmt: off` and a `// fmt: on`, each a comment on a
line of its own, stay exactly as written, as for a table aligned in
columns; the two comments indent like any other. Without a `// fmt: on`,
the region ends where the brackets around it close (the end of its
block), or at the end of the file or, in `.temper.md`, of its code
block.

```temper inert
// fmt: off
let sizes = [
  "small",   1,
  "large", 100,
];
// fmt: on
```

## What it refuses

Code that doesn't parse is left as written, with an error. `fmt` also
checks its own work: if formatting would change a file's tokens,
comments, or parse tree, it leaves the file alone and says so.

## What it leaves out

Under a directory, `fmt` skips `temper.out`, hidden directories, and
what `.gitignore` and `.temperignore` files list, as `build` does. To
keep more as written, such as a golden test's input or code that
doesn't parse on purpose, list it in a file of gitignore patterns,
relative to that file's directory, and name it with `--ignorefile`
([ignore files](cli.md#ignore-files)); a file named on the command
line is formatted all the same. For a check in CI, with this
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
