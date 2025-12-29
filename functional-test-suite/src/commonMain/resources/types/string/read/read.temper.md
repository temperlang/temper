# String Read Test

Exercise extension methods providing access to string code units in various
encodings.

## Setup

Greek word used by Markus Kuhn's string decoder test suite
plus Greek Zero Sign.

    reportSliceInfo("κόσμε\u{1018A}");

But test in a function so we can also review for other strings.

    let reportSliceInfo(s: String): Void {
      let start = String.begin;
      let end = s.end;

## Counting code-points

Counting code-points is not guaranteed fast, but checking that we have
a certain number is efficient.

      let count = s.countBetween(start, end);
      console.log("Count of code-points for `${s}`: ${count}");

      console.log("Has at least 0: ${s.hasAtLeast(start, end, 0)}");
      console.log("Has at least 1: ${s.hasAtLeast(start, end, 1)}");
      console.log("Has at least 2: ${s.hasAtLeast(start, end, 2)}");
      console.log("Has at least 3: ${s.hasAtLeast(start, end, 3)}");
      console.log("Has at least 4: ${s.hasAtLeast(start, end, 4)}");
      console.log("Has at least 5: ${s.hasAtLeast(start, end, 5)}");
      console.log("Has at least 6: ${s.hasAtLeast(start, end, 6)}");
      console.log("Has at least 7: ${s.hasAtLeast(start, end, 7)}");
      console.log("Has at least 8: ${s.hasAtLeast(start, end, 8)}");

```log
Count of code-points for `κόσμε𐆊`: 6
Has at least 0: true
Has at least 1: true
Has at least 2: true
Has at least 3: true
Has at least 4: true
Has at least 5: true
Has at least 6: true
Has at least 7: false
Has at least 8: false
```

## Iteration

Now iterate through them.

      console.log("code-points");
      for (let cp of s) {
        console.log("  ${cp.toString(16)}");
      }
    }

```log
code-points
  3ba
  1f79
  3c3
  3bc
  3b5
  1018a
```

## Length on single-byte chars

Checking on code points that require multiple chars in some encodings actually
let some issues through, so check purely single-byte chars also.

    reportSliceInfo("Hi");

Results are shorter for this one.

```log
Count of code-points for `Hi`: 2
Has at least 0: true
Has at least 1: true
Has at least 2: true
Has at least 3: false
Has at least 4: false
Has at least 5: false
Has at least 6: false
Has at least 7: false
Has at least 8: false
code-points
  48
  69
```

## Comparing string indices

Indices can be compared.

    let compareEnds(message: String): Void {
      let start = String.begin;
      let end = message.end;
      console.log("start < end? ${start < end}");
      console.log("start > end? ${start > end}");
      console.log("start == start? ${start == start}");
      console.log("end < end? ${end < end}");
      console.log("end <= end? ${end <= end}");
    }
    compareEnds("Hi");

```log
start < end? true
start > end? false
start == start? true
end < end? false
end <= end? true
```

## Converting code points back into Strings

We can convert individual integer code points or lists of them.

    let fromCodeNoInline(code: Int): String throws Bubble {
      console.log("From 0x${code.toString(16)}");
      String.fromCodePoint(code)
    }
    let fromCodesNoInline(codes: Listed<Int>): String throws Bubble {
      console.log("From codes");
      String.fromCodePoints(codes)
    }

The first one can inline in the front end, but the others shouldn't.

    console.log(String.fromCodePoint(0x1F30D));
    console.log(fromCodeNoInline(0x1F30D));
    console.log(fromCodeNoInline(0x110000) orelse "nope");
    console.log(fromCodeNoInline(0xD800) orelse "no to surrogate");
    console.log(fromCodesNoInline([0x1F43B, 0x200D, 0x2744, 0xFE0F]));
    console.log(fromCodesNoInline([0xD800, 0xDC00]) orelse "no to surrogates");

```log
🌍
From 0x1f30d
🌍
From 0x110000
nope
From 0xd800
no to surrogate
From codes
🐻‍❄️
From codes
no to surrogates
```

## Parsing Int values

Don't bother to check past limits for now, since limits vary by backend.

    let checkParse(string: String, radix: Int? = null): Void {
      let converted = when (radix) {
        // Allow either explicit radix or not for varied testing.
        is Int -> string.toInt32(radix);
        else -> string.toInt32();
      }.toString() orelse "failed";
      console.log("Parse ${string} -> ${converted}");
    }

Pass in explicit null for the radix for now because of a limitation in our C\#
backend. At some point at least, we've generated the above as a function value
assignment instead of a simple function definition.

TODO Support optional parameters in C\# function values through `delegate` types?

    checkParse("-1", null);
    checkParse("2", null);

Allow surrounding whitespace since JSON parsers do.

    checkParse(" 2 ", null);

But none of these are good full base-10 ints.

    checkParse("2.0", null);
    checkParse("2a", null);
    checkParse("0x10", null);
    checkParse("totally", null);

But we do allow explicit base/radix for parsing, including some that aren't
common bases, just to make sure we've supported those cases:

    checkParse("2a", 16);
    checkParse("20", 7);
    checkParse("20", 17);
    checkParse("20", 37); // but only through base 36

```log
Parse -1 -> -1
Parse 2 -> 2
Parse  2  -> 2
Parse 2.0 -> failed
Parse 2a -> failed
Parse 0x10 -> failed
Parse totally -> failed
Parse 2a -> 42
Parse 20 -> 14
Parse 20 -> 34
Parse 20 -> failed
```

## Substrings

String indices let us refer to positions in a string without random access.
Given a string like `foo-bar`, we can `slice` out substrings.

    do {
      let s = "foo-bar";
      let after1 = s.next(String.begin);
      let after2 = s.next(after1);
      let after3 = s.next(after2);
      let after4 = s.next(after3);

Also check stepping multiple, both forward and backward. Also stepping 0.

      let after4b = s.step(String.begin, 4);
      let before1 = s.prev(after4b);
      let before3 = s.step(before1, -2);
      let before3b = s.step(before3, 0);

And we shouldn't go past bounds.

      let wayBefore = s.step(before3, -100);
      let wayAfter = s.step(before3, 100);

      console.log("s[3:]  -> ${ s.slice(after3, s.end) }");
      console.log("s[:3]  -> ${ s.slice(String.begin, after3) }");
      console.log("s[1:4] -> ${ s.slice(after1, after4) }");
      console.log("s[:4]  -> ${ s.slice(String.begin, after4) }");
      console.log("s[1:4] -> ${ s.slice(before3b, after4b) }");
      console.log("s[-many:many] -> ${ s.slice(wayBefore, wayAfter) }");
    }

```log
s[3:]  -> -bar
s[:3]  -> foo
s[1:4] -> oo-
s[:4]  -> foo-
s[1:4] -> oo-
s[-many:many] -> foo-bar
```

## Finding substrings

While regexes allow for more advanced searching, sometimes we just want to find
a substring index.

    let reportFound(
      label: String,
      haystack: String,
      needle: String,
      start: StringIndex = String.begin,
    ): StringIndex {
      let index = haystack.indexOf(needle, start);
      when (index) {
        is StringIndex -> do {
          let before = haystack.slice(hay.prev(start), index);
          console.log("${label}: ${before}...");
          haystack.next(index) // for searching after
        }
        else -> do {
          console.log("${label}: (nope)");
          String.begin // presumed unused
        }
      }
    }

    let needle = "time";
    let hay = "It was the best of times, it was the worst of times, ...";
    let after1 = reportFound("found 1", hay, needle);
    let after2 = reportFound("found 2", hay, needle, after1);
    reportFound("found 3", hay, needle, after2);

```log
found 1: It was the best of ...
found 2: times, it was the worst of ...
found 3: (nope)
```

Also check edge cases.

    let hay2 = "time comes for us all";
    let hay3 = "in the nick of time";
    let hay4 = "tim";

    reportFound("hay 2", "time comes for us all", needle); // start
    reportFound("hay 3", "in the nick of time", needle); // end
    reportFound("hay 4", "my name is tim", needle); // incomplete at end
    reportFound("hay 5", "tim", needle); // shorter than needle

```log
hay 2: ...
hay 3: in the nick of ...
hay 4: (nope)
hay 5: (nope)
```
