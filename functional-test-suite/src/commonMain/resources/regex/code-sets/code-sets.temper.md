# Regex Code Sets Test

Some backends rewrite code sets, so check that classes keep the ends of
their ranges, and that a negated set reaching the end of Unicode works.
Regex literals don't take `\u{…}` escapes, so build the latter. The
regex match test has the negated sets that work everywhere.

This isn't part of the regex match test, since Lua's regex runtime fails
on these sets and hangs on the negated range up to U+10FFFF.

    console.log("digits: ${/[\d]+/.find("x09y").full.value}");
    console.log("word: ${/[\w]+/.find("-azAZ09_-").full.value}");
    console.log("not word: ${/[^\w]+/.find("a-9z").full.value}");
    let notHigh =
      oneOrMore(new CodeSet([new CodeRange(0x7f, 0x10ffff)], true)).compiled();
    console.log("not high: ${notHigh.find("é-a🌍").full.value}");

```log
digits: 09
word: azAZ09_
not word: -
not high: -a
```

## Import

    let { ... } = import("std/regex");
