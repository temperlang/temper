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

A line that continues a header (a class's, interface's, function's, or
function literal's, up to the `{` of its body) indents four spaces from
the line the header starts on, so it stands apart from the body. Lines
inside the header's own brackets, like its parameters, indent as any
others do.

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
  around it).
- Strings.
- Prose in `.temper.md` files: only Temper code blocks change.

## What it refuses

Code that doesn't parse is left as written, with an error. `fmt` also
checks its own work: if formatting would change a file's tokens,
comments, or parse tree, it leaves the file alone and says so.
