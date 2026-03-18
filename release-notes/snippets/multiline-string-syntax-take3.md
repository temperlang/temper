### 🚨Breaking change: Multiline string syntax (yes, again)

Early on, Temper used Python-esque triple quoted string syntax but
with rules for stripping incidental spaces from the beginning and ends
of lines.

```temper inert
let s = """
  Two spaces stripped per line
  Including here
  Can't have """ here
  """;
```

A subsequent 🚨breaking change introduced multi-quoted string syntax
that uses margin characters and to allow embedding statement fragments
using `{: ... :}`.

With margin characters, there's no need for an end delimiter.

```temper inert
let a = """
  "I am the Count who loves to ${action}!
  "{: for (let number of numbers) { :}
      " ${number}! Ha HA ha.
  "{: } :}
  ;
```

Unfortunately, that scheme was visually noisy: `{: } :}` is 7
characters for a payload of one single-character token.
And the rules around stripping newlines were confusing.

The new, final, multi-quoted string expression syntax now leans more
heavily on margin characters.

There are now three margin characters:

- The double quote (`"`) and tilde (`~`) margin characters precede
  literal character content, escape sequences, and interpolations.
  See below for why there are two.
- The colon margin character precedes lines of statement fragment
  tokens.

```temper
let numbers = ["Zero", "One", "Two", "Three"];
// Starting at zero because the Count is a vampire, not a monster.
let action = "count";

let a = """
  ~I am the Count who loves to ${action}!
  : for (let number of numbers) {
      ~ ${number}! Ha HA ha.
  : }
  ;
```

Spaces (and tabs) are still stripped from the ends of lines, so use
`${}` if you need a line to end with spaces.

But the `~` and `"` margin characters give fine-grained control over
newline stripping via one simple rules: `"` lines have them, `~` lines
don't.

#### Migration Guide

To migrate old style complex string expressions to new style, keep in
mind two things.

1. The old style dropped any newline at the end of the last line.
   If you want the last line to drop its newline *now*, use the
   `~` margin character instead of `"`.
2. `{: ... :}` statement fragments need to be on their own lines,
   without the delimiters, and with a `:` margin character instead
   of `"`.
   if you have lines that mix statement fragments and character data,
   separate the parts onto separate lines with the right margin
   character, and use `~` instead of `"` for character data lines
   that were followed by other stuff before a newline.
