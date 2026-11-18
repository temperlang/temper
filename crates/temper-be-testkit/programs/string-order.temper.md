# String order

Temper orders strings by code point. Strings stored as UTF-16 (Java,
C#, and JavaScript) need care there: a character above U+FFFF is a
surrogate pair, whose first unit is U+D800 to U+DBFF, so comparing
units puts it before a character from U+E000 to U+FFFF, though its code
point is greater. Each line compares two strings with `<=>`, `<`, `<=`,
`>`, and `>=`: characters on either side of that range, a difference
after an equal prefix, two pairs that differ only in their second unit,
a string that's a prefix of the other, the empty string, and ASCII,
whose `<=>` is still -1, 0, or 1, as `Float64`'s is, last. The log is
what the interpreter gives.

    let show(a: String, b: String): String {
      "${(a <=> b).toString()} ${(a < b).toString()} ${(a <= b).toString()} ${(a > b).toString()} ${(a >= b).toString()}"
    }
    console.log(show("\u{FF61}", "\u{1F600}"));
    console.log(show("\u{1F600}", "\u{FF61}"));
    console.log(show("\u{E000}", "\u{10000}"));
    console.log(show("\u{FFFF}", "\u{10FFFF}"));
    console.log(show("a\u{FF61}b", "a\u{1F600}"));
    console.log(show("\u{1F600}", "\u{1F601}"));
    console.log(show("\u{1F600}", "\u{1F600}"));
    console.log(show("\u{1F600}", "\u{1F600}a"));
    console.log(show("", "\u{1F600}"));
    console.log(show("", ""));
    console.log(show("z", "a"));
    console.log(show("abc", "abd"));

    let cmp(a: Float64, b: Float64): Int { a <=> b }
    console.log("${cmp(2.5, 1.0).toString()} ${cmp(1.0, 2.5).toString()} ${cmp(1.5, 1.5).toString()}");

```log
-1 true true false false
1 false false true true
-1 true true false false
-1 true true false false
-1 true true false false
-1 true true false false
0 false true false true
-1 true true false false
-1 true true false false
0 false true false true
1 false false true true
-1 true true false false
1 -1 0
```
