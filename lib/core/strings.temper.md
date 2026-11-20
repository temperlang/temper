# Strings

Text: `String`, positions within strings, as `StringIndex`, and
`StringBuilder` for composing strings.

*String* is a chunk of textual content.

⎀ syntax/StringExpr -heading

To encourage code with repeatable behavior, strings do not have lengths; the length of a string
depends on the kind of characters, which differ across programming languages.
See the documentation for [string indices][snippet/type/StringIndex] to see how to compare
character counts and understand more about semantic consistency of string operations.

String values have a syntax similar to JavaScript, but instead of using backticks `` ` `` for
multi-line strings with interpolation, use triple-quotes: `"""` for multi-line strings.

⎀ syntax/multi-quoted-strings -heading

⎀ syntax/EscapeSequences

⎀ syntax/string/incidental-space-removal

⎀ syntax/string/interpolation -heading

    @connected
    @mayDowncastTo(false) // Confusable with scalars on many dynlang backends, and dates in older PLs
    export class String extends MapKey<String> {
      private let content: String;
      private constructor();

      /**
       * Convert the single code point to a string if it's valid. Specifically, it
       * must be a Unicode scalar value, which is between 0 and 0x10FFFF, inclusive,
       * except for the surrogate range 0xD800 through 0xDFFF.
       */
      @connected
      public static fromCodePoint(codePoint: Int): String throws Bubble;

      /** Convert the list of code points to a string if they're valid. */
      @connected
      public static fromCodePoints(codePoints: Listed<Int>): String throws Bubble;

      /** True iff this string has no code-points. */
      @connected
      public get isEmpty(): Boolean { content == "" }

      /**
       * A string index that works for the start of any string.
       */
      @connected
      public static let begin: StringIndex;

      /**
       * The index at the end of this string, just past the last character if any.
       *
       * ```temper
       * // An empty string's end is the same as its start.
       *    "".end.compareTo(String.begin) == 0 &&
       *
       * // Any other string's end is after its start.
       * "foo".end.compareTo(String.begin) >  0
       * ```
       */
      @connected
      public get end(): StringIndex;

      /**
       * The integer value of the codepoint at the given index or panics if there is no such value.
       *
       * This may produce an unexpected result if index was derived from operations involving
       * a different string.  For example, within a language that uses UTF-16 surrogates to
       * represent supplementary code points, using a string index from another string might
       * point into the middle of a code-point in this string causing this method to return
       * the integer value of the trailing surrogate instead of the whole code point.
       *
       * ```temper
       * "foo"[String.begin] == char'f'
       * ```
       */
      @connected
      public get(index: StringIndex): Int;

      /**
       * True when index points to a character in this string.  False if it is out of bounds.
       *
       * ```temper
       * !"".hasIndex(String.begin) && "foo".hasIndex(String.begin)
       * ```
       */
      @connected
      public hasIndex(index: StringIndex): Boolean;

      /**
       * The first index of *target* if found, starting from *start*.
       */
      @connected
      public indexOf(
        target: String,
        start: StringIndex = String.begin,
      ): StringIndexOption {
        // This implementation is inefficient but correct.
        let targetLength = target.countBetween(String.begin, target.end);
        var index = start;
        var endGuess = step(index, targetLength);
        let fullEnd = end;
        starts: while (true) {
          if (slice(index, endGuess) == target) {
            return index;
          }
          if (endGuess == fullEnd) {
            // Only break at the end *after* we've checked it.
            break starts;
          }
          index = next(index);
          endGuess = next(endGuess);
        }
        return StringIndex.none;
      }

      /**
       * An index for the next code-point after the one pointed to by index in this string.
       * Returns *end* if there is no such code-point.
       *
       * ```temper
       * let s = "abc";
       * let i = s.next(String.begin);
       *
       * s[i] == char'b'
       * ```
       */
      @connected
      public next(index: StringIndex): StringIndex;

      /**
       * An index for the code-point that preceded the one pointed to by index in this string.
       * Returns *begin* if there is no such code-point.
       *
       * ```temper
       * let s = "abc";
       * let i = s.prev(s.end);
       *
       * s[i] == char'c'
       * ```
       */
      @connected
      public prev(index: StringIndex): StringIndex;

      /**
       * An index for *by* steps away from the given *index*, either positive/forward or
       * negative/backward. If forward but there's no such code-point, returns *end*, or
       * else *begin* for no such code-point when stepping backward.
       */
      @connected
      public step(index: StringIndex, by: Int): StringIndex {
        var newIndex = index;
        if (by >= 0) {
          // Forward with next.
          steps: for (var i = 0; i < by; i += 1) {
            let oldIndex = newIndex;
            newIndex = next(newIndex);
            if (newIndex == oldIndex) {
              break steps;
            }
          }
        } else {
          // Backward with prev.
          steps: for (var i = 0; i > by; i -= 1) {
            let oldIndex = newIndex;
            newIndex = prev(newIndex);
            if (newIndex == oldIndex) {
              break steps;
            }
          }
        }
        newIndex
      }

      /**
       * True if there are at least minCount code-points between begin and end (exclusive).
       * Zero if begin is at or past end.
       *
       * `s.hasAtLeast(begin, end, n)` is at least as efficient as
       * `s.countBetween(begin, end) >= n` and better in many situations.
       *
       * When the native string representation is UTF-8, and the number of
       * octets between begin and end is minCount×4 or greater, *hasAtLeast*
       * requires no counting.
       *
       * When the native string representation is UTF-16, and the number of
       * code units between begin and end is minCount×2 or greater, *hasAtLeast*
       * requires no counting.
       *
       * At no point is it worse than O(min(n, minCount)) where n is the actual
       * count between begin and end.
       *
       * ```temper
       * let s = "abcdefghijklmnopqrstuvwxyz";
       * let i = s.next(String.begin); // Points at 'b'
       * let j = s.prev(s.end);        // Points at 'y'
       *
       * // Between i and j in s are 26-2, 24 letters.
       *
       *  s.hasAtLeast(i, j, 4)  && // Fast path for all native string encodings
       *  s.hasAtLeast(i, j, 24) && // Might require counting
       * !s.hasAtLeast(i, j, 25) && // Might require counting
       * !s.hasAtLeast(i, j, 100)   // Fast path too
       * ```
       */
      @connected
      public hasAtLeast(begin: StringIndex, end: StringIndex, minCount: Int): Boolean;

      /**
       * The count of codepoints between *begin* and *end*.
       * On most backends, this is O(n) on the output size.
       * Use [snippet/type/String/method/hasAtLeast] instead when trying to test that
       * there are at least a number of codepoints as it can more efficient for many
       * strings.
       *
       * ```temper
       * let s = "abcdefghijklmnopqrstuvwxyz";
       * let i = s.next(String.begin); // Points at 'b'
       * let j = s.prev(s.end);        // Points at 'y'
       *
       * s.countBetween(i, j) == (26 - 2)
       * ```
       */
      @connected
      public countBetween(begin: StringIndex, end: StringIndex): Int;

      /**
       * The string containing all and only the code points between the given
       * indices in this string in order.
       * If begin > end, returns the empty string.
       *
       * ```temper
       * let s = "tsubo";
       * let i = s.next(String.begin);
       * let j = s.prev(s.end);
       *
       * "sub" == s.slice(i, j)
       * ```
       */
      @connected
      public slice(begin: StringIndex, end: StringIndex): String;

      /** Where splitting by "" returns each code point as a separate string. */
      @connected
      public split(separator: String): List<String>;

      /**
       * Invokes body with each code point value in order.
       *
       * ```temper
       * for (let codePoint: Int of "foo-bar") {
       *   console.log("U+${codePoint.toString(16)}");
       * }
       * //!outputs "U+66"
       * //!outputs "U+6f"
       * //!outputs "U+6f"
       * //!outputs "U+2d"
       * //!outputs "U+62"
       * //!outputs "U+61"
       * //!outputs "U+72"
       * ```
       */
      @connected
      public forEach(@inlineUnrealizedGoal let body: fn (Int): Void): Void {
        for (var index = String.begin; this.hasIndex(index); index = this.next(index)) {
          let codePoint: Int = this[index];
          body(codePoint);
        }
      }

      /** Supports numeric JSON format plus Infinity and NaN. */
      @connected
      public toFloat64(): Float64 throws Bubble;

      /** Supports integer JSON format, plus any radix 2 through 36. */
      @connected
      public toInt32(radix: Int = 10): Int throws Bubble;

      /** Supports integer JSON format, plus any radix 2 through 36. */
      @connected
      public toInt64(radix: Int = 10): Int64 throws Bubble;

      @connected
      public toString(): String { content }

      @connected
      @operator("==")
      public eq(other: String): Boolean;
    }

*StringIndexOption* is a [string index][snippet/type/StringIndex], or a similar value
that idiomatically represents [no string index][snippet/type/StringIndex/static/none].

String helpers like *indexOf* in many languages can return an integer
like value which is either a position within the string or an integer
value that represents failure to find a match like -1.

*StringIndexOption* combines those two options in a type-safe way
that typically translates to an idiomatic integer.

    @imu
    @connected
    export sealed interface StringIndexOption extends Equatable<StringIndexOption> {
      /**
       * Sorts [snippet/type/StringIndex/static/none] before every valid string index.
       *
       * Comparing string indices derived from different strings produces a behaviour
       * that is stable within the language but which may differ across translation targets
       * with different internal string representations.
       */
      @operator("<=>")
      @connected
      public compareTo(other: StringIndexOption): Int;

      @operator("==")
      @connected
      public eq(other: StringIndexOption): Boolean;
    }

*NoStringIndex* represents the failure to find a string index.

As noted in [snippet/type/StringIndexOption], this is an integer-like type
that represents the failure to find a valid string index.

    @connected
    export class NoStringIndex extends StringIndexOption {
      @connected
      private constructor() {}
    }

*StringIndex* represents a position within a string, at a character or at its end.

String indices translate to integral types (usually `int` or `size_t`) but the exact integer
value for any notional index may differ from language to language.

Strings are similar to arrays of "characters" in many languages, but the kind of character
differs from language to language.

For example a string like "κόσμε𐆊" can be represented in multiple ways.

<table border=1><tr><th>↓Encoding/<div style="float:right">Glyph→</div></th><th colspan="2">κ<th colspan="2">ό</th><th colspan="3">σ</th><th colspan="2">μ</th><th colspan="2">ε</th><th colspan="4">𐆊</th></tr>
<tr><th>UTF-8</td><td>ce</td><td>ba</td><td>e1</td><td>bd</td><td>b9</td><td>cf</td><td>83</td><td>ce</td><td>bc</td><td>ce</td><td>b5</td><td>f0</td><td>90</td><td>86</td><td>8a</td></tr>
<tr><th>UTF-16</th><td colspan="2">3ba</td><td colspan="3">1f79</td><td colspan="2">3c3</td><td colspan="2">3bc</td><td colspan="2">3b5</td><td colspan="2">d800</td><td colspan="2">dd8a</td></tr>
<tr><th>UTF-32</th><td colspan="2">3ba</td><td colspan="3">1f79</td><td colspan="2">3c3</td><td colspan="2">3bc</td><td colspan="2">3b5</td><td colspan="4">1018a</td></tr>
</table>

Go, and Rust and many C and C++ codebases use UTF-8 which can have between one to four bytes
for a code-point.  The start of the n-th code-point could be anywhere between the n-th byte
and the 4*n-th byte.  Figuring out exactly where involves examining n bytes, an O(n) operation.

C# and Java use UTF-16 which represent most code-points using one 2-byte code unit, but
may use a pair of "surrogates."

Python3 presents strings as code-points but uses tricks internally to use fewer than
3-bytes per code-point of storage for most strings.

As can be seen above, O(1) random access won't work across languages.  The *StringIndex* type
allows an efficient representation of a position within a string.  Each *StringIndex*
is either the start of a string or is derived from an operation that takes into account the
string content.  This lets Temper provide consistent, efficient string operations regardless
of the native character size.

    @connected
    export class StringIndex extends StringIndexOption {
      /** The singleton [snippet/type/NoStringIndex] value */
      @connected
      public static let none: NoStringIndex;

      private offset: Int;
      private constructor(offset: Int) { this.offset = offset; }
    }

*StringBuilder* allows composing a string by appending substrings at the
end.

```temper inert
let stringBuilder = new StringBuilder();
stringBuilder.append("Hello");
console.log(stringBuilder.toString()); //!outputs "Hello"
stringBuilder.append(", World!");
console.log(stringBuilder.toString()); //!outputs "Hello, World!"
stringBuilder.clear();
stringBuilder.append("Goodbye.");
console.log(stringBuilder.toString()); //!outputs "Goodbye."
```

    @connected
    export class StringBuilder {
      private let substrings: ListBuilder<String>;

      @connected
      public constructor(): Void {
        this.substrings = new ListBuilder<String>();
      }

      /** You can append a string at the end. */
      @connected
      public append(substring: String): Void {
        substrings.add(substring);
      }

      /**
       * A single code point can be appended.  If it's not a valid
       * [Unicode scalar value](https://www.unicode.org/glossary/#unicode_scalar_value),
       * the call bubbles.  This operation can be more efficient on some backends
       * than making a temporary string from a code point just for appending.
       */
      @connected
      public appendCodePoint(cp: Int): Void throws Bubble {
        append(String.fromCodePoint(cp));
      }

      /**
       * *appendBetween* can be connected to a function that appends a substring
       * without creating a new backing array for the substring.
       */
      @connected
      public appendBetween(
        source: String,
        begin: StringIndex,
        end: StringIndex,
      ): Void {
        var limit = end;
        let sourceEnd = source.end;
        // Do not loop infinitely if chosen end is past the source end
        if (limit > sourceEnd) { limit = sourceEnd; }

        for (var index = begin;
            index < limit;
            index = source.next(index)) {
          appendCodePoint(source[index]) orelse panic();
        }
      }

      /**
       * *clear()* removes any previously appended content, returning this
       * *StringBuilder* to its newly created state.
       *
       * This *StringBuilder* may be reused after clearing.
       */
      @connected
      public clear(): Void {
        this.substrings.clear();
      }

      /**
       * The index at the end of the string built by this string
       * as it stands right now.  It will still be the index after
       * the previously appended content even if more content is appended.
       * The end index is invalidated by any subsequent calls to [clear].
       *
       * ```temper
       * let stringBuilder = new StringBuilder();
       * stringBuilder.append("Hello");
       * let afterHello = stringBuilder.end;
       * stringBuilder.append(", World!");
       * let str = stringBuilder.toString();
       * // Now I can use afterHello with str.
       * console.log(str.slice(String.begin, afterHello)); //!outputs "Hello"
       * console.log(str.slice(afterHello, str.end)); //!outputs ", World!"
       *
       * // ⚠️ Do not use afterHello after calling clear.
       * stringBuilder.clear();
       * // stringBuilder.toString()[afterHello] would panic!
       * ```
       */
      @connected
      public get end(): StringIndex {
        let substrings = this.substrings;
        when (substrings.length) {
          0 -> String.begin;
          1 -> substrings[0].end;
          else -> do {
            // Compact and test
            // Ideal if these things happen atomically.
            let s = toString();
            substrings.clear();
            substrings.add(s);
            s.end
          }
        }
      }

      @connected
      public toString(): String {
        this.substrings.join("") { (substring: String): String => substring }
      }
    }
