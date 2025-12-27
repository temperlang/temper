# String Indices

String indices and string index options can be compared and
used to retrieve parts of strings.

String scanning routines often return a *StringIndexOption*.

    let indexOfChar(s: String, c: Int): StringIndexOption {
      var i = String.begin;
      while (s.hasIndex(i)) {
        if (s[i] == c) {
          return i;
        }
        i = s.next(i);
      }
      return StringIndex.none;
    }

First, comparing indices within the same strings compares
by position.

    let comparePositions(s: String, a: Int, b: Int): Void {
      let delta = indexOfChar(s, a)
        .compareTo(indexOfChar(s, b));
      let relPos = if (delta < 0) {
        "before"
      } else if (delta == 0) {
        "at"
      } else {
        "after"
      };
      console.log(
        "'${
          String.fromCodePoint(a) orelse "?"
        }' is ${relPos} '${
          String.fromCodePoint(b) orelse "?"
        }' in \"${s}\"."
      );
    }

    comparePositions("cookie", char'c', char'i');
    comparePositions("cookie", char'k', char'o');
    comparePositions("cookie", char'k', char'k');
    comparePositions("cookie", char'x', char'c');
    comparePositions("cookie", char'e', char'x');
    comparePositions("cookie", char'x', char'y');

```log
'c' is before 'i' in "cookie".
'k' is after 'o' in "cookie".
'k' is at 'k' in "cookie".
'x' is before 'c' in "cookie".
'e' is after 'x' in "cookie".
'x' is at 'y' in "cookie".
```

*StringIndexOption* supports *is* RTTI checks and *as* conversions.
We can define predicates for *as* and *is* by checking whether
*indexOfChar*'s output is *NoStringIndex* or *StringIndex*.

    let has0(s: String, c: Int): Boolean {
      indexOfChar(s, c) is StringIndex
    }

    let has1(s: String, c: Int): Boolean {
      let i = indexOfChar(s, c);
      i is StringIndex
    }

    let has2(s: String, c: Int): Boolean {
      let i = indexOfChar(s, c);
      !(i is NoStringIndex)
    }

    let has3(s: String, c: Int): Boolean {
      let i = indexOfChar(s, c);
      do { i as StringIndex; true } orelse false
    }

    let has4(s: String, c: Int): Boolean {
      let i = indexOfChar(s, c);
      do { i as NoStringIndex; false } orelse true
    }

Now, we'll build a table of outputs by looking for several characters
in "cookie."

    let s = "cookie";
    for (let c of [char'o', char'i', char'z']) {
      console.log(
        "'${String.fromCodePoint(c) orelse "?"}' in \"${s}\": ${
          if (has0(s, c)) { "y" } else { "n" }
        }, ${
          if (has1(s, c)) { "y" } else { "n" }
        }, ${
          if (has2(s, c)) { "y" } else { "n" }
        }, ${
          if (has3(s, c)) { "y" } else { "n" }
        }, ${
          if (has4(s, c)) { "y" } else { "n" }
        }"
      );
    }

```log
'o' in "cookie": y, y, y, y, y
'i' in "cookie": y, y, y, y, y
'z' in "cookie": n, n, n, n, n
```

And we shouldn't ever be able to access the char at `end`. This panics, so don't
try it.

    // let message = "hi";
    // // TODO How to duplicate this issue when bad indexing now panics?
    // // And I wanted to say this, but it's broken right now. See:
    // // - https://github.com/temperlang/temper/issues/156
    // // let end = String.fromCodePoint(message[message.end]) orelse "nope";
    // let end = message[message.end];
    // console.log("Char at end? ${end}");
