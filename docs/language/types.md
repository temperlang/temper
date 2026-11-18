# Types and Values

Simple values look similar to values from JavaScript, Java, C#.

Type names consistently start with a capital letter though:

* `Boolean`&check; ; not `boolean`&cross;
* `Int`&check; ; not `int`&cross;
* `String`&check; ; not `string`&cross;

Value names, like lower-variable names,
start with a lower-case letter: `false`&check; ; not `False`&cross;.

## *class Boolean*

A *Boolean* is a truth value that may be used in `if` and loop conditions.

```ebnf
BooleanLiteral := "false" | "true"
```

*Boolean* `extends` [*AnyValue*](#interface-anyvalue), [*Equatable*](#interface-equatable)&lt;[*Boolean*](#class-boolean)&gt;

### methods

#### `Boolean.constructor`

`:` `fn(`[*Boolean*](#class-boolean)`):` [*Void*](#class-void)

#### `Boolean.toString`

`:` `fn(`[*Boolean*](#class-boolean)`):` [*String*](#class-string)

For [*false*](builtins.md#false), the string `"false"`.
For [*true*](builtins.md#true), the string `"true"`.

#### `Boolean.eq`

`:` `fn(`[*Boolean*](#class-boolean), [*Boolean*](#class-boolean)`):` [*Boolean*](#class-boolean)

## *class Int32*

*Int32* is the default, general-use signed integer value type in Temper.
Because it is so frequently used, the alias *Int* is encouraged in source
code.

### Int32 Syntax Examples
Integers can be runs of decimal digits.

```temper
123
// ✅ 123
```

Zero is a valid number, but C-style octal literals (with zero padding) would be a source
of confusion.

```temper
0
// ✅ 0
```

```temper
let hundred = 100;
let ten     = 010; // <!-- No
let one     = 001; // <!-- Still no
// ❌ Malformed number!, Malformed number!
```

You can't use commas in a number literal to make large numbers readable,
but you can use an underscore to separate digit groups.

```temper
let commas =      [123,456,789];  // Commas separate list elements
let underscores = [123_456_789];  // Underscores make long numbers easier to read

//!outputs  "commas has 3 elements"
console.log("commas has ${commas.length} elements");
//!outputs  "underscores has 1 element"
console.log("underscores has ${underscores.length} element");
// ✅
```

Exponential notation is fine for floating point values, but not for integers.

```temper
1e2 == 100.0
// ✅
```

And feel free to use a base like hexadecimal or binary when that fits what you're
modeling.

```temper
0x10 == 16 && // Hex
0b10 ==  2 && // Binary
0o10 ==  8
// ✅
```

*Int32* `extends` [*AnyValue*](#interface-anyvalue), [*MapKey*](#interface-mapkey)&lt;[*Int32*](#class-int32)&gt;

### properties

#### `Int32.signum`

`:` [*Int32*](#class-int32)

`-1` if `this < 0`, `0` if `this == 0`, or otherwise `1`.

### methods

#### `Int32.constructor`

`:` `fn(`[*Int32*](#class-int32)`):` [*Void*](#class-void)

#### `Int32.toFloat64`

`:` `fn(`[*Int32*](#class-int32)`):` [*Float64*](#class-float64)

#### `Int32.toInt64`

`:` `fn(`[*Int32*](#class-int32)`):` [*Int64*](#class-int64)

#### `Int32.toString`

`:` `fn(`[*Int32*](#class-int32), [*Int32*](#class-int32)&#63; \= \.\.\.`):` [*String*](#class-string)

Supports radix 2 through 36. Doesn't prefix `+` for positive.

#### `Int32.min`

`:` `fn(`[*Int32*](#class-int32), [*Int32*](#class-int32)`):` [*Int32*](#class-int32)

#### `Int32.max`

`:` `fn(`[*Int32*](#class-int32), [*Int32*](#class-int32)`):` [*Int32*](#class-int32)

#### `Int32.succ`

`:` `fn(`[*Int32*](#class-int32)`):` [*Int32*](#class-int32)

`this + 1`, the successor value used by [`++` operator: increment](builtins.md#-operator-increment).

#### `Int32.pred`

`:` `fn(`[*Int32*](#class-int32)`):` [*Int32*](#class-int32)

`this - 1`, the predecessor value used by [`--` operator: decrement](builtins.md#---operator-decrement).

#### `Int32.eq`

`:` `fn(`[*Int32*](#class-int32), [*Int32*](#class-int32)`):` [*Boolean*](#class-boolean)

## *class Int64*

*Int64* is a double wide, signed integer type.

Possible *Int64* values range from -2<sup>63</sup> to (2<sup>63</sup> - 1).

`i64` after a decimal or hex literal makes it an [*Int64*](#class-int64).

```temper
let my64: Int64 = 1i64;
1i64 * 0x2i64 == 2i64
// ✅
```

*Int64* `extends` [*AnyValue*](#interface-anyvalue), [*Equatable*](#interface-equatable)&lt;[*Int64*](#class-int64)&gt;

### methods

#### `Int64.constructor`

`:` `fn(`[*Int64*](#class-int64)`):` [*Void*](#class-void)

#### `Int64.toFloat64`

`:` `fn(`[*Int64*](#class-int64)`):` [*Float64*](#class-float64) `throws` [*Bubble*](#class-bubble)

Bubbles if the result isn't within the exponent error of the original.

#### `Int64.toFloat64Unsafe`

`:` `fn(`[*Int64*](#class-int64)`):` [*Float64*](#class-float64)

If outside bounds, returns a backend-dependent value. Use fuzz testing.

#### `Int64.toInt32`

`:` `fn(`[*Int64*](#class-int64)`):` [*Int32*](#class-int32) `throws` [*Bubble*](#class-bubble)

Bubbles if the result isn't within bounds.
TODO Should this panic instead since this is an easy check?
TODO But many others cost more to check the panic on fail.

#### `Int64.toInt32Unsafe`

`:` `fn(`[*Int64*](#class-int64)`):` [*Int32*](#class-int32)

If outside bounds, returns a backend-dependent value. Use fuzz testing.
TODO Ensure wrapping here since we do that elsewhere?
TODO For symmetry, define consistent semantics for all lossy conversions?

#### `Int64.toString`

`:` `fn(`[*Int64*](#class-int64), [*Int32*](#class-int32)&#63; \= \.\.\.`):` [*String*](#class-string)

Supports radix 2 through 36. Doesn't prefix `+` for positive.

#### `Int64.min`

`:` `fn(`[*Int64*](#class-int64), [*Int64*](#class-int64)`):` [*Int64*](#class-int64)

#### `Int64.max`

`:` `fn(`[*Int64*](#class-int64), [*Int64*](#class-int64)`):` [*Int64*](#class-int64)

#### `Int64.succ`

`:` `fn(`[*Int64*](#class-int64)`):` [*Int64*](#class-int64)

`this + 1i64`, the successor value used by [`++` operator: increment](builtins.md#-operator-increment).

#### `Int64.pred`

`:` `fn(`[*Int64*](#class-int64)`):` [*Int64*](#class-int64)

`this - 1i64`, the predecessor value used by [`--` operator: decrement](builtins.md#---operator-decrement).

#### `Int64.eq`

`:` `fn(`[*Int64*](#class-int64), [*Int64*](#class-int64)`):` [*Boolean*](#class-boolean)

## *class Float64*

A *Float64* is an [IEEE-754 64-bit (aka double-precision) floating point
number][FP64].

#### Syntax for *Float64Literal*

```ebnf
Float64Literal := ((whole number: [0-9]+*("_")) "." (fraction: [0-9]++("_")) (exponent: ("E" | "e") (() | "+" | "-") [0-9]++("_"))? | (whole number: [0-9]+*("_")) (exponent: ("E" | "e") (() | "+" | "-") [0-9]++("_"))) (suffix: ("D" | "d")+)? | (whole number: [0-9]+*("_")) (suffix: ("D" | "d")+)
```

### Float64 Syntax Examples

A number with a decimal point is a Float64.

```temper
123.456
// ✅ 123.456
```

You can make big numbers more readable by separating digits with underscore(`_`).

```temper
123_456.789 == 123.456_789e3
// ✅
```

A number with an exponent is a Float64 even if it does not have a decimal point.
The exponent follows the letter 'e', either upper or lower-case.

```temper
123e2 == 12_300.0 &&
123e2 == 123E2
// ✅
```

Exponents may have a sign.

```temper
125e+2     == 12_500.0 &&
1.25e+2    == 125.0 &&
125e-2     == 1.25 &&
125e-0_002 == 1.25  // Exponents are rarely big, but you may break them up.
// ✅
```

An integer-like number with a decimal suffix is a Float64.

```temper
1F64 == 1.0 &&
1f64 == 1.0
// ✅
```

Unlike in C, digits are required after a decimal point.

<!-- known: I10 -->
```temper
1.
// ❌ Operator Dot expects at least 2 operands but got 1!
```

> **Note:** Temper accepts `1.`, as a number followed by a dot ([inconsistencies](../inconsistencies.md#i10-examples-that-dont-run-as-marked)).

Which allows Temper to more flexibly combine numbers with class-use syntax.

```temper
64.toString(16 /* hex radix */) == "40"
// That '.' is not a decimal point, so that's an integer.
// ✅
```

Temper also does not recognize all C's number suffixes.

```temper
1F
// ❌ Malformed number!
```

[FP64]: https://en.wikipedia.org/wiki/Double-precision_floating-point_format

*Float64* `extends` [*AnyValue*](#interface-anyvalue), [*Equatable*](#interface-equatable)&lt;[*Float64*](#class-float64)&gt;

### methods

#### `Float64.constructor`

`:` `fn(`[*Float64*](#class-float64)`):` [*Void*](#class-void)

#### `Float64.toInt32`

`:` `fn(`[*Float64*](#class-float64)`):` [*Int32*](#class-int32) `throws` [*Bubble*](#class-bubble)

Truncates toward zero. Bubbles if the value isn't within 1 of Int32 limits.

#### `Float64.toInt32Unsafe`

`:` `fn(`[*Float64*](#class-float64)`):` [*Int32*](#class-int32)

If outside bounds, returns a backend-dependent value. Use fuzz testing.

#### `Float64.toInt64`

`:` `fn(`[*Float64*](#class-float64)`):` [*Int64*](#class-int64) `throws` [*Bubble*](#class-bubble)

Truncates toward zero. Bubbles if the value isn't within plus or minus
0x1f_ffff_ffff_ffff, inclusive.

#### `Float64.toInt64Unsafe`

`:` `fn(`[*Float64*](#class-float64)`):` [*Int64*](#class-int64)

If outside bounds, returns a backend-dependent value. Use fuzz testing.

#### `Float64.toString`

`:` `fn(`[*Float64*](#class-float64)`):` [*String*](#class-string)

#### `Float64.abs`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.acos`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.asin`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.atan`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.atan2`

`:` `fn(`[*Float64*](#class-float64), [*Float64*](#class-float64)`):` [*Float64*](#class-float64)

The y coordinate is this object.

#### `Float64.ceil`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.cos`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.cosh`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.exp`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.expm1`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.floor`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.log`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.log10`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.log1p`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.max`

`:` `fn(`[*Float64*](#class-float64), [*Float64*](#class-float64)`):` [*Float64*](#class-float64)

Result is NaN if either is NaN.

#### `Float64.min`

`:` `fn(`[*Float64*](#class-float64), [*Float64*](#class-float64)`):` [*Float64*](#class-float64)

Result is NaN if either is NaN.

#### `Float64.near`

`:` `fn(`[*Float64*](#class-float64), [*Float64*](#class-float64), [*Float64*](#class-float64)&#63; \= \.\.\., [*Float64*](#class-float64)&#63; \= \.\.\.`):` [*Boolean*](#class-boolean)

Matches semantics of Python's *math.isclose*.

#### `Float64.round`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.sign`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.sin`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.sinh`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.sqrt`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.tan`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.tanh`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

#### `Float64.succ`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

`this + 1.0`, the successor value used by [`++` operator: increment](builtins.md#-operator-increment).

#### `Float64.pred`

`:` `fn(`[*Float64*](#class-float64)`):` [*Float64*](#class-float64)

`this - 1.0`, the predecessor value used by [`--` operator: decrement](builtins.md#---operator-decrement).

#### `Float64.eq`

`:` `fn(`[*Float64*](#class-float64), [*Float64*](#class-float64)`):` [*Boolean*](#class-boolean)

### statics

#### `Float64.e`

`static` `:` [*Float64*](#class-float64)

#### `Float64.pi`

`static` `:` [*Float64*](#class-float64)

## *class String*

A *String* is a chunk of textual content.

```ebnf
StringExpr := "\u0022" (SourceCharacter - (\\n\, \\r\, \\\, \"\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\u0022" | "`" (SourceCharacter - (\\n\, \\r\, \\\, \`\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "`" | "\" (SourceCharacter - (\\n\, \\r\, \\\, \\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* "\" | "\u0022\u0022\u0022" (Content line starting with `"`: "LineBreak" indentation ((Ignored margin character: "\u0022" | "~") (SourceCharacter - (\\\) | EscapeSequence | (UTF-16 Code Unit: "\\u" Hex Hex Hex Hex) | (Unicode Scalar Values: "\\u{" HexDigits+(",") "}") | (Interpolation: "\u0024{" Expr "}"))* | ":" StatementFragment))* "LineBreak"
```

To encourage code with repeatable behavior, strings do not have lengths; the length of a string
depends on the kind of characters, which differ across programming languages.
See the documentation for [string indices](#class-stringindex) to see how to compare
character counts and understand more about semantic consistency of string operations.

String values have a syntax similar to JavaScript, but instead of using backticks `` ` `` for
multi-line strings with interpolation, use triple-quotes: `"""` for multi-line strings.

### Multi-quoted strings

Multi-quoted strings start with 3 `"`'s.
Each content line must start with a *margin-character*,
which is not part of the content.

The allowed margin characters are:
- A double quote (`"`) which is followed by characters and/or interpolations.
  The content line has an implicit line feed (LF U+A) character at the end.
- A tilde (`~`) which is just like a double quote margin, but there is no
  implied line feed at the end.
- A colon (`:`) which allows embedding a statement fragment (see below)
  instead of literal character data.

```temper
"3 quotes" ==
  """
  "3 quotes
// ✅
```

When a non-blank line doesn't start with a margin-quote, the
multi-quoted string ends.

Quotes can be embedded inside multi-quoted strings.

```temper
(
  "Alice said\n\"Hello, World!\"\n" ==
    """
    "Alice said
    ""Hello, World!"
)
// ✅
```

Multi-quoted strings may contain interpolations.

```temper
let whom = "World";

"Hello, World!" ==
  """
  ~Hello, ${whom}!
// ✅
```

Multi-quoted string content lines may be split across multiple lines.

```temper
"""
"This is one line.
~This is a longer line that, because it starts with a tilde,
~does not have a line feed at the end, until finally we get
"to a line with a double-quote character in the margin, Phew!
;
// ✅ null
```

Statement fragment lines, which start with colon (`:`) allow
for composing complex strings by iterating and using conditionals.

These two ways of constructing content are equivalent, but the
structure of the output is easier to understand from the
multi-quoted string.

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

let b = do {
  let sb = new StringBuilder();
  sb.append("I am the Count who loves to ");
  sb.append(action);
  sb.append("!");
  for (let number of numbers) {
    sb.append(" ");
    sb.append(number);
    sb.append("! Ha HA ha.");
  }
  sb.toString()
};

a == b
// ✅
```

### Escape Sequences
Escape sequences in Temper are roughly like those in C, Java, JavaScript, JSON, etc.

```ebnf
EscapeSequence Syntax
```

The following escape sequences are recognized in strings:

| Escape                      | Meaning                                             |
| --------------------------- | --------------------------------------------------- |
| `\0`                        | Codepoint 0, NUL                                    |
| `\\`                        | A single backslash                                  |
| `\/`                        | A forward-slash                                     |
| `\"`                        | A double-quote                                      |
| `\'`                        | A single-quote                                      |
| `` \` ``                    | A back-quote                                        |
| `\{`                        | A left curly-bracket                                |
| `\}`                        | A right curly-bracket                               |
| `\$`                        | A dollar sign                                       |
| `\b`                        | A backspace                                         |
| `\t`                        | A tab                                               |
| `\n`                        | A line-feed a.k.a. new-line                         |
| `\v`                        | A vertical tab                                      |
| `\f`                        | A form-feed                                         |
| `\r`                        | A carriage-return                                   |
| `\u` *xxxx*                 | A four-digit hex escape sequence                    |
| `\u{` *xxxxx* `,` *xxx* `}` | One or more comma-separated hex code-points         |
| `\`                         | Broken escape                                       |

### Incidental spaces in multi-line strings

When a string spans multiple lines, some space is *significant*;
it contributes to the content of the resulting string value.
Spaces that do not contribute to the content are called *incidental spaces*.
Incidental spaces include:

- Those used for code indentation, and
- Those that appear at the end of a line so are invisible to readers and
  often automatically stripped by code editors, and
- Carriage returns which may be inserted or removed depending on
  whether a file is edited on Windows or UNIX.

Normalizing incidental space steps include:

1. Removing space before the margin character.
2. Removing the newline after the open quote.
3. Removing spaces and tabs at the end of each line.
4. Removing line break sequences at the end of lines with the
   tilde (`~`) margin character.
5. Normalizing line break sequences CRLF, CR, and LF to LF.

To identify incidental space, we imagine that any
interpolation `${...}`, or hole `${}` contributes
1 or more non-space, non-line-break characters.

```temper
"""
  "Line 1
  "Line 2
  ~Line 3 without LF at end
== "Line 1\nLine 2\nLine 3 without LF at end"
// ✅
```

Each content line is stripped up to and including the margin character.
It's good style to line up the margin characters, but not necessary.

```temper
"""
    " Line 1
   "  Line 2
    "   Line 3
== " Line 1\n  Line 2\n   Line 3\n"
// ✅
```

It's an error if a line is missing a margin character.
Temper's parser will treat such lines as regular code,
often leading to other syntax errors.

```temper
"""
    "Line 1
     Line 2 missing margin character
    "Line 3
// ❌ Missing close quote!, Expected a TopLevel here!
```

Spaces are removed from the end of a line, but not if there is an
interpolation or hole:

```temper
"""
    "Line 1  ${"interpolation"}
    "Line 2  ${/*hole*/}
    ~Line 3
    == "Line 1  interpolation\nLine 2  \nLine 3"
// ✅
```

For the purpose of this, space includes:

- Space character: U+20 ' '
- Tab character: U+9 '\t'

A line consists of any maximal sequence of characters other than
CR (U+D '\r') and LF (U+A '\n').

A line break is any of the following sequences:

- LF
- CR
- CR LF

Regardless of whether a source file is authored or compiled on a
Windows machine (prefers CR LF) or another machine (tend to prefer LF)
the meaning of a string is the same.  This means that all of those sequences,
where not trimmed, are simplified to LF.  Use `${}` if you really need to
embed a `\r` in a file.

See [Multi-quoted strings](#multi-quoted-strings) for more details on margin characters.

### String interpolation

Strings may contain embedded expressions.  When a
string contains a `${` followed by an expression,
followed by a `}`, the resulting string value
is the concatenation of the content before,
content from the expression, and the content after.

```temper
"foo ${"bar"} baz"
== "foo bar baz"
// ✅
```

Interpolated values that aren't *String* instances
have `.toString()` called on them automatically,
which is convenient for types that define *toString*,
such as *Int*.

```temper
let two = 2;
"one ${two} three"
== "one 2 three"
// ✅
```

An empty interpolation contributes no characters,
which means it may be used to embed meta-characters.

```temper
"$${}{}" == "\$\{\}"
// ✅
```

(This mostly comes in handy with tagged strings to
give fine-grained control over what the tag receives.)

Empty interpolations can also be used to wrap a long
string across multiple lines, but using a multi-quoted
string with the tilde (`~`) margin character can be
easier.

```temper
"A very long string ${
  // Breaking this string across multiple lines.
}that runs on and on"
== "A very long string that runs on and on"
// ✅
```

Empty interpolations also let you include spaces at
the end of a line in a multi-quoted string.

```temper
"""
"Line 1
~Line 2 ${}
== "Line 1\nLine 2 "
// ✅
```

In that multi-quoted string, the margin characters at the left (`"` and `~`)
control newlines.  Line feeds (LF U+A) are added at the end of lines that have
a double-quote character (`"`) in the margin, but not at the end of lines that
have a tilde character (`~`) in the margin.

See [Multi-quoted strings](#multi-quoted-strings) for more details on that syntax.

*String* `extends` [*AnyValue*](#interface-anyvalue), [*MapKey*](#interface-mapkey)&lt;[*String*](#class-string)&gt;

### properties

#### `String.isEmpty`

`:` [*Boolean*](#class-boolean)

True iff this string has no code-points.

#### `String.end`

`:` [*StringIndex*](#class-stringindex)

The index at the end of this string, just past the last character if any.

```temper
// An empty string's end is the same as its start.
   "".end.compareTo(String.begin) == 0 &&

// Any other string's end is after its start.
"foo".end.compareTo(String.begin) >  0
// ✅
```

### methods

#### `String.constructor`

`:` `fn(`[*String*](#class-string)`):` [*Void*](#class-void)

#### `String.get`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex)`):` [*Int32*](#class-int32)

The integer value of the codepoint at the given index or panics if there is no such value.

This may produce an unexpected result if index was derived from operations involving
a different string.  For example, within a language that uses UTF-16 surrogates to
represent supplementary code points, using a string index from another string might
point into the middle of a code-point in this string causing this method to return
the integer value of the trailing surrogate instead of the whole code point.

```temper
"foo"[String.begin] == char'f'
// ✅
```

#### `String.hasIndex`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex)`):` [*Boolean*](#class-boolean)

True when index points to a character in this string.  False if it is out of bounds.

```temper
!"".hasIndex(String.begin) && "foo".hasIndex(String.begin)
// ✅
```

#### `String.indexOf`

`:` `fn(`[*String*](#class-string), [*String*](#class-string), [*StringIndex*](#class-stringindex)&#63; \= \.\.\.`):` [*StringIndexOption*](#interface-stringindexoption)

The first index of *target* if found, starting from *start*.

#### `String.next`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex)`):` [*StringIndex*](#class-stringindex)

An index for the next code-point after the one pointed to by index in this string.
Returns *end* if there is no such code-point.

```temper
let s = "abc";
let i = s.next(String.begin);

s[i] == char'b'
// ✅
```

#### `String.prev`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex)`):` [*StringIndex*](#class-stringindex)

An index for the code-point that preceded the one pointed to by index in this string.
Returns *begin* if there is no such code-point.

```temper
let s = "abc";
let i = s.prev(s.end);

s[i] == char'c'
// ✅
```

#### `String.step`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex), [*Int32*](#class-int32)`):` [*StringIndex*](#class-stringindex)

An index for *by* steps away from the given *index*, either positive/forward or
negative/backward. If forward but there's no such code-point, returns *end*, or
else *begin* for no such code-point when stepping backward.

#### `String.hasAtLeast`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex), [*StringIndex*](#class-stringindex), [*Int32*](#class-int32)`):` [*Boolean*](#class-boolean)

True if there are at least minCount code-points between begin and end (exclusive).
Zero if begin is at or past end.

`s.hasAtLeast(begin, end, n)` is at least as efficient as
`s.countBetween(begin, end) >= n` and better in many situations.

When the native string representation is UTF-8, and the number of
octets between begin and end is minCount×4 or greater, *hasAtLeast*
requires no counting.

When the native string representation is UTF-16, and the number of
code units between begin and end is minCount×2 or greater, *hasAtLeast*
requires no counting.

At no point is it worse than O(min(n, minCount)) where n is the actual
count between begin and end.

```temper
let s = "abcdefghijklmnopqrstuvwxyz";
let i = s.next(String.begin); // Points at 'b'
let j = s.prev(s.end);        // Points at 'y'

// Between i and j in s are 26-2, 24 letters.

 s.hasAtLeast(i, j, 4)  && // Fast path for all native string encodings
 s.hasAtLeast(i, j, 24) && // Might require counting
!s.hasAtLeast(i, j, 25) && // Might require counting
!s.hasAtLeast(i, j, 100)   // Fast path too
// ✅
```

#### `String.countBetween`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex), [*StringIndex*](#class-stringindex)`):` [*Int32*](#class-int32)

The count of codepoints between *begin* and *end*.
On most backends, this is O(n) on the output size.
Use [`String.hasAtLeast`](#stringhasatleast) instead when trying to test that
there are at least a number of codepoints as it can more efficient for many
strings.

```temper
let s = "abcdefghijklmnopqrstuvwxyz";
let i = s.next(String.begin); // Points at 'b'
let j = s.prev(s.end);        // Points at 'y'

s.countBetween(i, j) == (26 - 2)
// ✅
```

#### `String.slice`

`:` `fn(`[*String*](#class-string), [*StringIndex*](#class-stringindex), [*StringIndex*](#class-stringindex)`):` [*String*](#class-string)

The string containing all and only the code points between the given
indices in this string in order.
If begin > end, returns the empty string.

```temper
let s = "tsubo";
let i = s.next(String.begin);
let j = s.prev(s.end);

"sub" == s.slice(i, j)
// ✅
```

#### `String.split`

`:` `fn(`[*String*](#class-string), [*String*](#class-string)`):` [*List*](#class-list)&lt;[*String*](#class-string)&gt;

Where splitting by "" returns each code point as a separate string.

#### `String.forEach`

`:` `fn(`[*String*](#class-string), `fn(`[*Int32*](#class-int32)`):` [*Void*](#class-void)`):` [*Void*](#class-void)

Invokes body with each code point value in order.

```temper
for (let codePoint: Int of "foo-bar") {
  console.log("U+${codePoint.toString(16)}");
}
//!outputs "U+66"
//!outputs "U+6f"
//!outputs "U+6f"
//!outputs "U+2d"
//!outputs "U+62"
//!outputs "U+61"
//!outputs "U+72"
// ✅
```

#### `String.toFloat64`

`:` `fn(`[*String*](#class-string)`):` [*Float64*](#class-float64) `throws` [*Bubble*](#class-bubble)

Supports numeric JSON format plus Infinity and NaN.

#### `String.toInt32`

`:` `fn(`[*String*](#class-string), [*Int32*](#class-int32)&#63; \= \.\.\.`):` [*Int32*](#class-int32) `throws` [*Bubble*](#class-bubble)

Supports integer JSON format, plus any radix 2 through 36.

#### `String.toInt64`

`:` `fn(`[*String*](#class-string), [*Int32*](#class-int32)&#63; \= \.\.\.`):` [*Int64*](#class-int64) `throws` [*Bubble*](#class-bubble)

Supports integer JSON format, plus any radix 2 through 36.

#### `String.toString`

`:` `fn(`[*String*](#class-string)`):` [*String*](#class-string)

#### `String.eq`

`:` `fn(`[*String*](#class-string), [*String*](#class-string)`):` [*Boolean*](#class-boolean)

### statics

#### `String.fromCodePoint`

`static` `:` `fn(`[*Int32*](#class-int32)`):` [*String*](#class-string) `throws` [*Bubble*](#class-bubble)

Convert the single code point to a string if it's valid. Specifically, it
must be a Unicode scalar value, which is between 0 and 0x10FFFF, inclusive,
except for the surrogate range 0xD800 through 0xDFFF.

#### `String.fromCodePoints`

`static` `:` `fn(`[*Listed*](#interface-listed)&lt;[*Int32*](#class-int32)&gt;`):` [*String*](#class-string) `throws` [*Bubble*](#class-bubble)

Convert the list of code points to a string if they're valid.

#### `String.begin`

`static` `:` [*StringIndex*](#class-stringindex)

A string index that works for the start of any string.

## *class StringBuilder*

A *StringBuilder* allows composing a string by appending substrings at the
end.

```temper
let stringBuilder = new StringBuilder();
stringBuilder.append("Hello");
console.log(stringBuilder.toString()); //!outputs "Hello"
stringBuilder.append(", World!");
console.log(stringBuilder.toString()); //!outputs "Hello, World!"
stringBuilder.clear();
stringBuilder.append("Goodbye.");
console.log(stringBuilder.toString()); //!outputs "Goodbye."
// ✅
```

*StringBuilder* `extends` [*AnyValue*](#interface-anyvalue)

### properties

#### `StringBuilder.end`

`:` [*StringIndex*](#class-stringindex)

The index at the end of the string built by this string
as it stands right now.  It will still be the index after
the previously appended content even if more content is appended.
The end index is invalidated by any subsequent calls to [clear].

```temper
let stringBuilder = new StringBuilder();
stringBuilder.append("Hello");
let afterHello = stringBuilder.end;
stringBuilder.append(", World!");
let str = stringBuilder.toString();
// Now I can use afterHello with str.
console.log(str.slice(String.begin, afterHello)); //!outputs "Hello"
console.log(str.slice(afterHello, str.end)); //!outputs ", World!"

// ⚠️ Do not use afterHello after calling clear.
stringBuilder.clear();
// stringBuilder.toString()[afterHello] would panic!
// ✅
```

### methods

#### `StringBuilder.constructor`

`:` `fn(`[*StringBuilder*](#class-stringbuilder)`):` [*Void*](#class-void)

#### `StringBuilder.append`

`:` `fn(`[*StringBuilder*](#class-stringbuilder), [*String*](#class-string)`):` [*Void*](#class-void)

You can append a string at the end.

#### `StringBuilder.appendCodePoint`

`:` `fn(`[*StringBuilder*](#class-stringbuilder), [*Int32*](#class-int32)`):` [*Void*](#class-void) `throws` [*Bubble*](#class-bubble)

A single code point can be appended.  If it's not a valid
[Unicode scalar value](https://www.unicode.org/glossary/#unicode_scalar_value),
the call bubbles.  This operation can be more efficient on some backends
than making a temporary string from a code point just for appending.

#### `StringBuilder.appendBetween`

`:` `fn(`[*StringBuilder*](#class-stringbuilder), [*String*](#class-string), [*StringIndex*](#class-stringindex), [*StringIndex*](#class-stringindex)`):` [*Void*](#class-void)

*appendBetween* can be connected to a function that appends a substring
without creating a new backing array for the substring.

#### `StringBuilder.clear`

`:` `fn(`[*StringBuilder*](#class-stringbuilder)`):` [*Void*](#class-void)

*clear()* removes any previously appended content, returning this
*StringBuilder* to its newly created state.

This *StringBuilder* may be reused after clearing.

#### `StringBuilder.toString`

`:` `fn(`[*StringBuilder*](#class-stringbuilder)`):` [*String*](#class-string)

## *interface StringIndexOption*

A [string index](#class-stringindex), or a similar value
that idiomatically represents [no string index](#stringindexnone).

String helpers like *indexOf* in many languages can return an integer
like value which is either a position within the string or an integer
value that represents failure to find a match like -1.

*StringIndexOption* combines those two options in a type-safe way
that typically translates to an idiomatic integer.

*StringIndexOption* `extends` [*AnyValue*](#interface-anyvalue), [*Equatable*](#interface-equatable)&lt;[*StringIndexOption*](#interface-stringindexoption)&gt;

### methods

#### `StringIndexOption.compareTo`

`:` `fn(`[*StringIndexOption*](#interface-stringindexoption), [*StringIndexOption*](#interface-stringindexoption)`):` [*Int32*](#class-int32)

Sorts [`StringIndex.none`](#stringindexnone) before every valid string index.

Comparing string indices derived from different strings produces a behaviour
that is stable within the language but which may differ across translation targets
with different internal string representations.

#### `StringIndexOption.eq`

`:` `fn(`[*StringIndexOption*](#interface-stringindexoption), [*StringIndexOption*](#interface-stringindexoption)`):` [*Boolean*](#class-boolean)

## *class StringIndex*

A string index represents a position within a string, at a character or at its end.

String indices translate to integral types (usually `int` or `size_t`) but the exact integer
value for any notional index may differ from language to language.

Strings are similar to arrays of "characters" in many languages, but the kind of character
differs from language to language.

For example a string like "κόσμε𐆊" can be represented in multiple ways.

<table border=1><tr><th>↓Encoding/Glyph→</th><th colspan="2">κ<th colspan="2">ό</th><th colspan="3">σ</th><th colspan="2">μ</th><th colspan="2">ε</th><th colspan="4">𐆊</th></tr>
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

*StringIndex* `extends` [*AnyValue*](#interface-anyvalue), [*StringIndexOption*](#interface-stringindexoption)

### statics

#### `StringIndex.none`

`static` `:` [*NoStringIndex*](#class-nostringindex)

The singleton [*NoStringIndex*](#class-nostringindex) value

## *class NoStringIndex*

The absence to find a string index.

As noted in [*StringIndexOption*](#interface-stringindexoption), this is an integer-like type
that represents the failure to find a valid string index.

*NoStringIndex* `extends` [*AnyValue*](#interface-anyvalue), [*StringIndexOption*](#interface-stringindexoption)

## *interface Console*

A *Console* provides logging and diagnostic capabilities. It might be
configured differently depending on the backend and environment. Don't rely
on it being attached to any standard stream.

Use the builtin instance `console` for default logging needs.

*Console* `extends` [*AnyValue*](#interface-anyvalue)

### methods

#### `Console.log`

`:` `fn(`[*Console*](#interface-console), [*String*](#class-string)`):` [*Void*](#class-void)

Log a message at default informational level to this console.

## *class List*

The *List* type is a random-access, immutable sequence of values. List
literals using `[...]` syntax represent immutable `List` instances. For
mutable random-access sequences, see `ListBuilder`.

See also `Listed` for recommendations on `List` vs `Listed` use.

*List* `<`[*T*](#listt)`>` `extends` [*AnyValue*](#interface-anyvalue), [*Listed*](#interface-listed)&lt;[*T*](#listt)&gt;

### typeFormals

#### `List.<T>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

### properties

#### `List.length`

`:` [*Int32*](#class-int32)

### methods

#### `List.constructor`

`:` `fn(`[*List*](#class-list)&lt;[*T*](#listt)&gt;`):` [*Void*](#class-void)

#### `List.get`

`:` `fn(`[*List*](#class-list)&lt;[*T*](#listt)&gt;, [*Int32*](#class-int32)`):` [*T*](#listt)

Panic on index out of bounds.

#### `List.toList`

`:` `fn(`[*List*](#class-list)&lt;[*T*](#listt)&gt;`):` [*List*](#class-list)&lt;[*T*](#listt)&gt;

#### `List.toListBuilder`

`:` `fn(`[*List*](#class-list)&lt;[*T*](#listt)&gt;`):` [*ListBuilder*](#class-listbuilder)&lt;[*T*](#listt)&gt;

#### `List.forEach`

`:` `fn(`[*List*](#class-list)&lt;[*T*](#listt)&gt;, `fn(`[*T*](#listt)`):` [*Void*](#class-void)`):` [*Void*](#class-void)

### statics

#### `List.of`

`static` `:` `fn<`*listT*`>(``):` [*List*](#class-list)&lt;*listT*&gt;

Same as `[...]` syntax but provides an explicit type argument.

## *class ListBuilder*

The *ListBuilder* type is a random-access, mutable, growable sequence of
values.

*ListBuilder* `<`[*T*](#listbuildert)`>` `extends` [*AnyValue*](#interface-anyvalue), [*Listed*](#interface-listed)&lt;[*T*](#listbuildert)&gt;

### typeFormals

#### `ListBuilder.<T>`

`extends` [*AnyValue*](#interface-anyvalue)

### properties

#### `ListBuilder.length`

`:` [*Int32*](#class-int32)

### methods

#### `ListBuilder.constructor`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;`):` [*Void*](#class-void)

#### `ListBuilder.add`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;, [*T*](#listbuildert), [*Int32*](#class-int32)&#63; \= \.\.\.`):` [*Void*](#class-void)

Panics for `at` less than 0 or greater than `length`.

#### `ListBuilder.addAll`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;, [*Listed*](#interface-listed)&lt;[*T*](#listbuildert)&gt;, [*Int32*](#class-int32)&#63; \= \.\.\.`):` [*Void*](#class-void)

Panics for `at` less than 0 or greater than `length`.

#### `ListBuilder.clear`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;`):` [*Void*](#class-void)

Removes all values, retaining internal capacity where possible.

#### `ListBuilder.removeLast`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;`):` [*T*](#listbuildert)

Removes the last value if any, and returns it. Expected to be constant
time. Panics if empty.

#### `ListBuilder.reverse`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;`):` [*Void*](#class-void)

Reverses this list builder in place.

#### `ListBuilder.set`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;, [*Int32*](#class-int32), [*T*](#listbuildert)`):` [*Void*](#class-void)

Panics on index out of bounds.

#### `ListBuilder.sort`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;, `fn(`[*T*](#listbuildert), [*T*](#listbuildert)`):` [*Int32*](#class-int32)`):` [*Void*](#class-void)

Sorts in place. Uses stable sorting.

#### `ListBuilder.splice`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;, [*Int32*](#class-int32)&#63; \= \.\.\., [*Int32*](#class-int32)&#63; \= \.\.\., [*Listed*](#interface-listed)&lt;[*T*](#listbuildert)&gt;&#63; \= \.\.\.`):` [*List*](#class-list)&lt;[*T*](#listbuildert)&gt;

Removes `removeCount` items starting at `index`, or until the end of the
current items. Returns the removed items, and `newValues` are put in their
place. The `index` is clamped to current bounds.

#### `ListBuilder.toList`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;`):` [*List*](#class-list)&lt;[*T*](#listbuildert)&gt;

#### `ListBuilder.toListBuilder`

`:` `fn(`[*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;`):` [*ListBuilder*](#class-listbuilder)&lt;[*T*](#listbuildert)&gt;

## *interface Listed*

The *Listed* type is an immutable view of a random-access sequence of values.
A `Listed<String>` may contain strings, a `Listed<Boolean>` may contain
booleans, etc.

The backing object might or might not be mutable. During a function call, an
argument of type `Listed` shouldn't be modified concurrently, although it
might be modified after the end of the call, so receivers *shouldn't* keep
any references to a `Listed` parameter for later use, unless accounting for
possible changes or according to specific contracts defined for particular
use cases.

If you want to keep a reference to a `Listed` argument, it's often best to
call `toList` on it and retain that value instead.

Usually, don't return type `Listed`. It's usually best to return type `List`.

*Listed* `<`[*T*](#listedt)`>` `extends` [*AnyValue*](#interface-anyvalue)

### typeFormals

#### `Listed.<T>`

`extends` [*AnyValue*](#interface-anyvalue)

### properties

#### `Listed.length`

`:` [*Int32*](#class-int32)

The *length* of a listed is the number of items it contains.

```temper
let myList: List<String> = ["foo", "bar"];
myList.length == 2
// ✅
```

#### `Listed.isEmpty`

`:` [*Boolean*](#class-boolean)

True if this contains no elements.

### methods

#### `Listed.get`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, [*Int32*](#class-int32)`):` [*T*](#listedt)

The i<sup>th</sup> element, or [*Bubble*](builtins.md#bubble-1) if *i* is outside the range \[0, [length]\).

```temper
let ls = ["a", "b", "c"]; // List of length 3

// The list is zero-indexed.
console.log(ls[0]); //!outputs "a"
console.log(ls[1]); //!outputs "b"
// ✅
```

Out of bounds results in panic. Check in advance when handling indices of
uncertain origin. Panics can't be handled by code in Temper, although some
backend languages can handle their particular implementations of panic.

```temper
// console.log(ls[-1]);
// console.log(ls[ 3]);
// ⏸️
```

#### `Listed.getOr`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, [*Int32*](#class-int32), [*T*](#listedt)`):` [*T*](#listedt)

#### `Listed.slice`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, [*Int32*](#class-int32), [*Int32*](#class-int32)`):` [*List*](#class-list)&lt;[*T*](#listedt)&gt;

A new list containing all and only elements from [begin] inclusive
to just before [end] exclusive.

#### `Listed.toList`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;`):` [*List*](#class-list)&lt;[*T*](#listedt)&gt;

Provides an immutable `List` with the values of this `Listed`. If this is
already a `List`, typically just returns `this` without any copying.

#### `Listed.toListBuilder`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;`):` [*ListBuilder*](#class-listbuilder)&lt;[*T*](#listedt)&gt;

Always creates a new object and backing buffer even if this is already a
`ListBuilder`.

#### `Listed.map`

`:` `fn<`*O*`>(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, `fn(`[*T*](#listedt)`):` *O*`):` [*List*](#class-list)&lt;*O*&gt;

Maps elements using [transform].

#### `Listed.filter`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, `fn(`[*T*](#listedt)`):` [*Boolean*](#class-boolean)`):` [*List*](#class-list)&lt;[*T*](#listedt)&gt;

#### `Listed.reduce`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, `fn(`[*T*](#listedt), [*T*](#listedt)`):` [*T*](#listedt)`):` [*T*](#listedt)

Reduce to a single value, starting from the first item in this list.
Panic if empty.

#### `Listed.reduceFrom`

`:` `fn<`*O*`>(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, *O*, `fn(`*O*, [*T*](#listedt)`):` *O*`):` *O*

Reduce to a single value, starting from the given initial value.

#### `Listed.reduceFromIndex`

`:` `fn<`*O*`>(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, *O*, [*Int32*](#class-int32), `fn(`*O*, [*T*](#listedt)`):` *O*`):` *O*

#### `Listed.join`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, [*String*](#class-string), `fn(`[*T*](#listedt)`):` [*String*](#class-string)`):` [*String*](#class-string)

#### `Listed.sorted`

`:` `fn(`[*Listed*](#interface-listed)&lt;[*T*](#listedt)&gt;, `fn(`[*T*](#listedt), [*T*](#listedt)`):` [*Int32*](#class-int32)`):` [*List*](#class-list)&lt;[*T*](#listedt)&gt;

Returns a sorted version of this listed. Uses stable sorting.

## *class Map*

A *Map* is a read-only key-value collection.

*Map* `<`[*K*](#mapk), [*V*](#mapv)`>` `extends` [*AnyValue*](#interface-anyvalue), [*Mapped*](#interface-mapped)&lt;[*K*](#mapk), [*V*](#mapv)&gt;

### typeFormals

#### `Map.<K>`

`in` `extends` [*MapKey*](#interface-mapkey)&lt;[*K*](#mapk)&gt;

#### `Map.<V>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

### methods

#### `Map.constructor`

`:` `fn(`[*Map*](#class-map)&lt;[*K*](#mapk), [*V*](#mapv)&gt;, [*List*](#class-list)&lt;[*Pair*](#class-pair)&lt;[*K*](#mapk), [*V*](#mapv)&gt;&gt;`):` [*Void*](#class-void)

Preserves entry order when accessed from Temper code.

## *class MapBuilder*

A *MapBuilder* is a read-write key-value collection for building `Map`
objects. Preserves insertion order when accessed from Temper code.

*MapBuilder* `<`[*K*](#mapbuilderk), [*V*](#mapbuilderv)`>` `extends` [*AnyValue*](#interface-anyvalue), [*Mapped*](#interface-mapped)&lt;[*K*](#mapbuilderk), [*V*](#mapbuilderv)&gt;

### typeFormals

#### `MapBuilder.<K>`

`extends` [*MapKey*](#interface-mapkey)&lt;[*K*](#mapbuilderk)&gt;

#### `MapBuilder.<V>`

`extends` [*AnyValue*](#interface-anyvalue)

### methods

#### `MapBuilder.constructor`

`:` `fn(`[*MapBuilder*](#class-mapbuilder)&lt;[*K*](#mapbuilderk), [*V*](#mapbuilderv)&gt;`):` [*Void*](#class-void)

#### `MapBuilder.clear`

`:` `fn(`[*MapBuilder*](#class-mapbuilder)&lt;[*K*](#mapbuilderk), [*V*](#mapbuilderv)&gt;`):` [*Void*](#class-void)

Removes all entries, retaining internal capacity where possible.

#### `MapBuilder.remove`

`:` `fn(`[*MapBuilder*](#class-mapbuilder)&lt;[*K*](#mapbuilderk), [*V*](#mapbuilderv)&gt;, [*K*](#mapbuilderk)`):` [*V*](#mapbuilderv) `throws` [*Bubble*](#class-bubble)

Removes the given entry if found, and returns its value.

#### `MapBuilder.set`

`:` `fn(`[*MapBuilder*](#class-mapbuilder)&lt;[*K*](#mapbuilderk), [*V*](#mapbuilderv)&gt;, [*K*](#mapbuilderk), [*V*](#mapbuilderv)`):` [*Void*](#class-void)

## *interface Mapped*

A *Mapped* is a read-only view to key-value mapped data, such as a `Map` or
`MapBuilder`.

*Mapped* `<`[*K*](#mappedk), [*V*](#mappedv)`>` `extends` [*AnyValue*](#interface-anyvalue)

### typeFormals

#### `Mapped.<K>`

`in` `extends` [*MapKey*](#interface-mapkey)&lt;[*K*](#mappedk)&gt;

#### `Mapped.<V>`

`extends` [*AnyValue*](#interface-anyvalue)

### properties

#### `Mapped.length`

`:` [*Int32*](#class-int32)

The number of key value pairs in the [*Mapped*](#interface-mapped).

### methods

#### `Mapped.get`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;, [*K*](#mappedk)`):` [*V*](#mappedv) `throws` [*Bubble*](#class-bubble)

Get the value associated with [key], or [`bubble()`](builtins.md#bubble)s if no key is found.

#### `Mapped.getOr`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;, [*K*](#mappedk), [*V*](#mappedv)`):` [*V*](#mappedv)

Get the value associated with [key], falling back to [fallback] when no such key exists.

#### `Mapped.has`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;, [*K*](#mappedk)`):` [*Boolean*](#class-boolean)

Checks whether [key] is present in the [*Mapped*](#interface-mapped).

#### `Mapped.keys`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;`):` [*Listed*](#interface-listed)&lt;[*K*](#mappedk)&gt;

Gets a [*Listed*](#interface-listed) of all of the keys.

#### `Mapped.values`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;`):` [*Listed*](#interface-listed)&lt;[*V*](#mappedv)&gt;

Gets a [*Listed*](#interface-listed) of all of the values.

#### `Mapped.toMap`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;`):` [*Map*](#class-map)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;

Converts the [*Mapped*](#interface-mapped) to a [*Map*](#class-map).

It does not need to make a copy if the [*Mapped*](#interface-mapped) is already a [*Map*](#class-map)
because neither can be mutated.

#### `Mapped.toMapBuilder`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;`):` [*MapBuilder*](#class-mapbuilder)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;

Converts the [*Mapped*](#interface-mapped) to a [*MapBuilder*](#class-mapbuilder).

It must make a copy, even when the [*Mapped*](#interface-mapped) is already a [*MapBuilder*](#class-mapbuilder)
so that both can be mutated independently.

#### `Mapped.toList`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;`):` [*List*](#class-list)&lt;[*Pair*](#class-pair)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;&gt;

Identical to [`Mapped.toListBuilder`](#mappedtolistbuilder) except for the return value being a [*List*](#class-list).

Constructs a [Pair](#class-pair)<[`Mapped.<K>`](#mappedk), [`Mapped.<V>`](#mappedv)>
for each key and value in the [*Mapped*](#interface-mapped).

#### `Mapped.toListBuilder`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;`):` [*ListBuilder*](#class-listbuilder)&lt;[*Pair*](#class-pair)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;&gt;

Constructs a [Pair](#class-pair)<[`Mapped.<K>`](#mappedk), [`Mapped.<V>`](#mappedv)>
for each key and value in the [*Mapped*](#interface-mapped).

#### `Mapped.toListWith`

`:` `fn<`*T*`>(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;, `fn(`[*K*](#mappedk), [*V*](#mappedv)`):` *T*`):` [*List*](#class-list)&lt;*T*&gt;

Similar to [`Mapped.toListBuilderWith`](#mappedtolistbuilderwith).

Calls [func] on each key and value in the [*Mapped*](#interface-mapped).

#### `Mapped.toListBuilderWith`

`:` `fn<`*T*`>(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;, `fn(`[*K*](#mappedk), [*V*](#mappedv)`):` *T*`):` [*ListBuilder*](#class-listbuilder)&lt;*T*&gt;

Returns a [*ListBuilder*](#class-listbuilder)<*T*>.

Calls [func] on each key and value in the [*Mapped*](#interface-mapped).

#### `Mapped.forEach`

`:` `fn(`[*Mapped*](#interface-mapped)&lt;[*K*](#mappedk), [*V*](#mappedv)&gt;, `fn(`[*K*](#mappedk), [*V*](#mappedv)`):` [*Void*](#class-void)`):` [*Void*](#class-void)

Calls the provided binary function func on each key and value for the side effect

This method is more likely to change or be removed before Temper 1.0 than most

## *class Pair*

*Pair* `<`[*K*](#pairk), [*V*](#pairv)`>` `extends` [*AnyValue*](#interface-anyvalue)

### typeFormals

#### `Pair.<K>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

#### `Pair.<V>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

### properties

#### `Pair.key`

`:` [*K*](#pairk)

#### `Pair.value`

`:` [*V*](#pairv)

### methods

#### `Pair.constructor`

`:` `fn(`[*Pair*](#class-pair)&lt;[*K*](#pairk), [*V*](#pairv)&gt;, [*K*](#pairk), [*V*](#pairv)`):` [*Void*](#class-void)

## *interface MapKey*

Limited at present to String and Int keys but expected to be
user-implementable in the future.

*MapKey* `<`[*EQ*](#mapkeyeq)`>` `extends` [*AnyValue*](#interface-anyvalue), [*Equatable*](#interface-equatable)&lt;[*EQ*](#mapkeyeq)&gt;

### typeFormals

#### `MapKey.<EQ>`

`extends` [*AnyValue*](#interface-anyvalue)

## *class Deque*

A *Deque* or double-ended queue allows for breadth-first processing via
efficient adds at the end and efficient removal from the front.

```temper
let deque = new Deque<String>();
deque.add("a");
deque.add("b");

console.log(deque.removeFirst()); //!outputs "a"

deque.add("c");
deque.add("d");

while (!deque.isEmpty) {
  console.log(deque.removeFirst()); //!outputs ["b", "c", "d"]
}
// ✅
```

*Deque* `<`[*T*](#dequet)`>` `extends` [*AnyValue*](#interface-anyvalue)

### typeFormals

#### `Deque.<T>`

`extends` [*AnyValue*](#interface-anyvalue)

### properties

#### `Deque.isEmpty`

`:` [*Boolean*](#class-boolean)

True if this contains no elements.

### methods

#### `Deque.constructor`

`:` `fn(`[*Deque*](#class-deque)&lt;[*T*](#dequet)&gt;`):` [*Void*](#class-void)

#### `Deque.add`

`:` `fn(`[*Deque*](#class-deque)&lt;[*T*](#dequet)&gt;, [*T*](#dequet)`):` [*Void*](#class-void)

Add an element at the end.

#### `Deque.removeFirst`

`:` `fn(`[*Deque*](#class-deque)&lt;[*T*](#dequet)&gt;`):` [*T*](#dequet)

Removes and returns the first element. Panics if empty.

## *class DenseBitVector*

A bit vector where the probability of any bit with index in \[0, capacity\]
is high.  This does not assume that the probability of a bit being set
depends on the probability of an adjacent bit being set.

*DenseBitVector* `extends` [*AnyValue*](#interface-anyvalue)

### methods

#### `DenseBitVector.constructor`

`:` `fn(`[*DenseBitVector*](#class-densebitvector), [*Int32*](#class-int32)`):` [*Void*](#class-void)

#### `DenseBitVector.get`

`:` `fn(`[*DenseBitVector*](#class-densebitvector), [*Int32*](#class-int32)`):` [*Boolean*](#class-boolean)

The bit at the given [index].  If a bit has not previously been set then
it is assumed to be [*false*](builtins.md#false).

#### `DenseBitVector.set`

`:` `fn(`[*DenseBitVector*](#class-densebitvector), [*Int32*](#class-int32), [*Boolean*](#class-boolean)`):` [*Void*](#class-void)

Sets the bit at [index] to [newValue].
After this, [get],  given [index], will return [newValue].

```temper
let bitVector = new DenseBitVector(16);
console.log(bitVector.get(3).toString()); //!outputs "false"
bitVector.set(3, true);
console.log(bitVector.get(3).toString()); //!outputs "true"
bitVector.set(3, false);
console.log(bitVector.get(3).toString()); //!outputs "false"
// ✅
```

## Function Types

The keyword `fn` is used to denote function values and types.

| Example | Means |
| ------- | ----- |
| `fn (): Void` | Type for a function that takes no arguments and returns the `void` value |
| `fn (Int): Int` | Type for a function that takes one integer and returns an integer |
| `fn<T> (T): List<T>` | Type for a generic function with a type parameter `<T>` |
| `fn (...Int): Boolean` | Type for a function that takes any number of integers |

Source: [*Types.kt*](https://github.com/temperlang/temper/blob/main/fundamentals/src/commonMain/kotlin/lang/temper/type/Types.kt), in Temper's previous compiler.

## *interface Function*

*Function* `extends` [*AnyValue*](#interface-anyvalue)

## *interface AnyValue*

The type for any successful value.

All `class` and `interface` types, including builtin types like [*Int32*](#class-int32),
are sub-types of this type.

The special type [*Bubble*](#class-bubble) is not.

*AnyValue*

## *interface Equatable*

Indicates that values can be tested for equality using `==` and `!=`.
User-defined types can't currently extend Equatable, but we expect to support
this in the future.

*Equatable* `<`[*EQ*](#equatableeq)`>` `extends` [*AnyValue*](#interface-anyvalue)

### typeFormals

#### `Equatable.<EQ>`

`extends` [*AnyValue*](#interface-anyvalue)

### methods

#### `Equatable.eq`

`:` `fn(`[*Equatable*](#interface-equatable)&lt;[*EQ*](#equatableeq)&gt;, [*EQ*](#equatableeq)`):` [*Boolean*](#class-boolean)

## *class Null*

The singleton [*null*](builtins.md#null) value represents
the absence of a value of another type.

In Temper, *null* is a valid value of any nullable type,
any regular type followed by `?`.

*String?* is a valid type for a local variable or function
input that might refer to either *null* or a string value.

*Null* `extends` [*AnyValue*](#interface-anyvalue)

## *class Empty*

*Empty* is the type for a stateless singleton value which may be used
judiciously to work around some problems in generic programming.

When not solving generic programming problems, prefer [*Void*](#class-void).
*Void* translates to the idiomatic no-usable-result concept which for many
typed functional languages is the same as *Empty* but for many dynamic
languages is the same as [*Null*](#class-null).

*Empty* differs from [*Void*](#class-void) in that it can be used as the type
of a function input and of a local variable.

Empty is like the "unit" type which is present in functional programming
languages; a function may be effectively *Void* but can instead return
*Empty* allowing its return type to bind to agree with a type parameter.

As seen below, the empty value may be retrieved by calling the `empty()`
function, and is distinct from [*Null*](#class-null).

```temper
let e: Empty? = empty();
console.log("Empty is not null: ${e != null}");
//!outputs  "Empty is not null: true"
// ✅
```

*Empty* `extends` [*AnyValue*](#interface-anyvalue), [*Equatable*](#interface-equatable)&lt;[*Empty*](#class-empty)&gt;

### methods

#### `Empty.eq`

`:` `fn(`[*Empty*](#class-empty), [*Empty*](#class-empty)`):` [*Boolean*](#class-boolean)

## *class Void*

*Void*

## Functional interface

Functional interfaces are like [SAM types](https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.8)
in Java; they let named types correspond to function types.

> [!WARNING]
> In-progress. The example below does not yet work.
> TODO: implement functional interface support and remove SAM-type guessing in be-java.
> Then remove the inert marker.

```temper
@fun interface Predicate<T>(x: T): Boolean;
// Applying a predicate to a value of its type parameter, returns a boolean.

let applyAndNegate<T>(x: T, predicate: Predicate<T>): Boolean {
  !predicate(x)    // predicate is applied as a function
}

let s = "abcd";
//!outputs: "Is 'abcd' empty? The answer is NOT: true."
console.log("Is '${s}' empty? The answer is NOT: ${
    applyAndNegate(s) { x => x.empty }
    //                ^^^^^^^^^^^^^^^^ That block is-a Predicate<String>
}.");
// ⏸️
```

The [builtin/@fun] marks the interface above as functional, and allows the
abbreviated method-like syntax.

The parts of an abbreviated functional interface declaration are shown below.

```temper
// other decorators
@fun
interface InterfaceName<
  // optional type arguments for interface
>(
  // optional arguments for public .apply method
): ReturnType;
// ⏸️
```

Functional interfaces, aka `@fun` interfaces, have the following restrictions:

- Their only allowed supertype is [type/AnyValue].
- They may declare only one method, named *apply*.
- The interface may declare type parameters but the method may not.
- They may not declare properties, getters, setters, or statics.
  This includes members implicitly added by macros like `@json`.
- A functional interface may not be used with runtime type operators:
  [builtin/is] or [builtin/as].

> [!NOTE]
> These restrictions allow translating functional interface types to
> [nominal types](https://en.wikipedia.org/wiki/Nominal_type_system)
> for target languages that only provide those.
>
> Meanwhile, the lack of a type hierarchy and the restrictions on runtime type
> operations allow translating them to
> [structural function types](https://en.wikipedia.org/wiki/Structural_type_system),
> e.g. arrow types, for target languages that benefit from those.

## Coroutine Types

### *interface GeneratorFn*

A marker interface for functions that may yield control to their caller even
if they do not lexically mention the [# `yield()` builtin](builtins.md#yield)
pseudo-control-flow instruction.

*GeneratorFn* `extends` [*AnyValue*](#interface-anyvalue), [*Function*](#interface-function)

### *interface Generator*

A multi-step computation that may be scheduled for a step by calling the
[`Generator.next`](#generatornext) method.

Block lambdas that extend [*GeneratorFn*](#interface-generatorfn) specify factories
for *Generator*s, or if they don't [*Bubble*](#class-bubble), for
[*Generator*](#interface-generator)s.

Terminology note:
Coroutines may be represented as *Generator*s where
[`Generator.next`](#generatornext)
starts or resumes computation and the coroutine pauses itself
when done with the step.
We avoid the term "coroutine" because many programmers associate it
narrowly with a kind of parallelism.

*Generator* `<`[*YIELD*](#generatoryield)`>` `extends` [*AnyValue*](#interface-anyvalue)

#### typeFormals

##### `Generator.<YIELD>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

#### properties

##### `Generator.done`

`:` [*Boolean*](#class-boolean)

True if subsequent calls to [`Generator.next`](#generatornext)
would do no work and return a [*DoneResult*](#class-doneresult).

That "if" is not an "if and only if" because in the case where a
[*GeneratorFn*](#interface-generatorfn) body yielded just before its return,
*done* may be [*false*](builtins.md#false) but a subsequent call to
*next* would return a *DoneResult* and reading *done* after that
would get [*true*](builtins.md#true).

#### methods

##### `Generator.next`

`:` `fn(`[*Generator*](#interface-generator)&lt;[*YIELD*](#generatoryield)&gt;`):` [*GeneratorResult*](#interface-generatorresult)&lt;[*YIELD*](#generatoryield)&gt; `throws` [*Bubble*](#class-bubble)

Starts/resumes the computation and runs until it pauses or completes.

If the computation was [`Generator.done`](#generatordone) prior
to the start of the call or it is done at the end, returns [*DoneResult*](#class-doneresult).
This is the case for a [*GeneratorFn*](#interface-generatorfn) that
[returns](builtins.md#return).

If the computation pauses, returns the value associated with the
decision to pause.
For a [*GeneratorFn*](#interface-generatorfn), this is the value passed to
[# `yield()` builtin](builtins.md#yield).  If a *GeneratorFn* bubbles, the call to
next propagates it.

##### `Generator.close`

`:` `fn(`[*Generator*](#interface-generator)&lt;[*YIELD*](#generatoryield)&gt;`):` [*Void*](#class-void)

Should be called once it is realized that
[`Generator.next`](#generatornext) is not going to be called
again to release resources needed by *next*.

After *close* exits, [`Generator.done`](#generatordone)
will be [*true*](builtins.md#true).

### *interface SafeGenerator*

A variant of [*Generator*](#interface-generator) that does not bubble
when computing the next result.

*SafeGenerator* `<`[*YIELD*](#safegeneratoryield)`>` `extends` [*AnyValue*](#interface-anyvalue), [*Generator*](#interface-generator)&lt;[*YIELD*](#safegeneratoryield)&gt;

#### typeFormals

##### `SafeGenerator.<YIELD>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

#### methods

##### `SafeGenerator.next`

`:` `fn(`[*SafeGenerator*](#interface-safegenerator)&lt;[*YIELD*](#safegeneratoryield)&gt;`):` [*GeneratorResult*](#interface-generatorresult)&lt;[*YIELD*](#safegeneratoryield)&gt; `throws` [*Bubble*](#class-bubble)

Starts/resumes the computation and runs until it pauses or completes.

If the computation was [`Generator.done`](#generatordone) prior
to the start of the call or it is done at the end, returns [*DoneResult*](#class-doneresult).
This is the case for a [*GeneratorFn*](#interface-generatorfn) that
[returns](builtins.md#return).

If the computation pauses, returns the value associated with the
decision to pause.
For a [*GeneratorFn*](#interface-generatorfn), this is the value passed to
[# `yield()` builtin](builtins.md#yield).

##### `SafeGenerator.nextSafe`

`:` `fn(`[*SafeGenerator*](#interface-safegenerator)&lt;[*YIELD*](#safegeneratoryield)&gt;`):` [*GeneratorResult*](#interface-generatorresult)&lt;[*YIELD*](#safegeneratoryield)&gt;

### *interface GeneratorResult*

One of a sequence of results from a [*Generator*](#interface-generator).

*GeneratorResult* `<`[*YIELD*](#generatorresultyield)`>` `extends` [*AnyValue*](#interface-anyvalue)

#### typeFormals

##### `GeneratorResult.<YIELD>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

### *class ValueResult*

A result produced from [`Generator.next`](#generatornext) when there is a value to produce and
[`Generator.done`](#generatordone) is [*false*](builtins.md#false).

*ValueResult* `<`[*YIELD*](#valueresultyield)`>` `extends` [*AnyValue*](#interface-anyvalue), [*GeneratorResult*](#interface-generatorresult)&lt;[*YIELD*](#valueresultyield)&gt;

#### typeFormals

##### `ValueResult.<YIELD>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

#### properties

##### `ValueResult.value`

`:` [*YIELD*](#valueresultyield)

#### methods

##### `ValueResult.constructor`

`:` `fn(`[*ValueResult*](#class-valueresult)&lt;[*YIELD*](#valueresultyield)&gt;, [*YIELD*](#valueresultyield)`):` [*Void*](#class-void)

### *class DoneResult*

A result produced from [`Generator.next`](#generatornext) when there is no value to produce and
[`Generator.done`](#generatordone) is [*true*](builtins.md#true).

*DoneResult* `<`[*YIELD*](#doneresultyield)`>` `extends` [*AnyValue*](#interface-anyvalue), [*GeneratorResult*](#interface-generatorresult)&lt;[*YIELD*](#doneresultyield)&gt;

#### typeFormals

##### `DoneResult.<YIELD>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

#### methods

##### `DoneResult.constructor`

`:` `fn(`[*DoneResult*](#class-doneresult)&lt;[*YIELD*](#doneresultyield)&gt;`):` [*Void*](#class-void)

## Special Types

### *interface Never*

*Never* is a type for expressions that will never complete normally.

For example, a loop that never exits, perhaps because it is supposed to process jobs
until the end of the embedding program, may use type *Never*.

*Never* should not be confused with [*Empty*](#class-empty); *Empty* admits a valid value which
has no content, but *Never* admits no value.

Unlike [⊥ types][bottom type] in some programming language, there is not a single
*Never* type in Temper.
*Never&lt;Foo&gt;* is not a sub-type of every type; it is only a sub-type of itself and its
*&lt;Foo&gt;*.

*Never&lt;String?&gt;?* is a valid type and because of the `?` nullable notation, that type
admits the special value, [*null*](builtins.md#null).

Having *Never* types in Temper allows translating control flow constructs that do not
produce "unreachable code" errors.

Temper *Never* types can translate to bottom types in languages that support that, but
*Never&lt;String&gt;*, for example, can translate to *String* in languages that do not.

[bottom type]: https://en.wikipedia.org/wiki/Bottom_type

*Never* `<`[*T*](#nevert)`>` `extends` [*AnyValue*](#interface-anyvalue)

#### typeFormals

##### `Never.<T>`

`out` `extends` [*AnyValue*](#interface-anyvalue)

### *class Bubble*

*Bubble* `extends` [*AnyValue*](#interface-anyvalue)

### *Top*
*Top* is a super-type of every type, including [*AnyValue*](#interface-anyvalue) and
[*Bubble*](#class-bubble).

### *interface Invalid*

*Invalid* is an untranslatable type that represents a failure to properly type an
expression.  Some backends, notably dynamic language backends, might be able
to produce a partially working translation and run tests even in the presence of type
errors, allowing developers to narrow down errors, or guide complex refactorings, and
the invalid type enables that.

It may be used in meta-programming code, but is not meant for general use by user code.

*Invalid*

### *class Problem*

A *Problem* is an internal abstraction that represents code that
cannot be compiled.

Representing problems in a partially-processed library allows
running some tests, giving developers more flexibility in how
they investigate what they need to do to get their code
production-ready.

*Problem* `extends` [*AnyValue*](#interface-anyvalue)

## Type Relationships

> [!NOTE]
> These type relationships are deprecated.
> Especially arbitrary type unions and intersections, and unqualified never types.

<!-- TODO: rewrite after type2 transition -->

```mermaid
graph TD
Top --> AnyValue
Top --> Void
Top --> Bubble
AnyValue --> Union["A | B"]
subgraph nominals [Nominals]
  direction LR
  A
  B
end
Union --> A
Union --> B
A --> Intersection["A & B"]
B --> Intersection
Intersection --> Never
Void --> Never
Bubble --> Never
Invalid
```

<figcaption markdown="1">

Arrows point from supertypes to subtypes.

</figcaption>

There is a *Top* type at the top which is the super-type of all types.

At the bottom is *Never* which is the bottom type, a subtype of
all types, and an appropriate type for computations that never complete like

```temper
while (true) {}
// ⏸️
```

The *Invalid* type, off to the right, is a type marker for constructs
that the compiler cannot make sense of.  It is outside the type hierarchy; not
a subtype of any other nor vice versa.

*Top* branches into *AnyValue*, *Void*, and *Bubble*.  This represents the
three ways a computation can complete, either by

- producing an actual value (it produces a subtype of *AnyValue*),
- finishing normally but without a usable value, or
- bubbling up the call stack until replaced with a value by `orelse`.

Below any value we have `A | B`, a union type.
Values of type `A | B` can be of type `A` or[^1] of type `B`.
A union type is a super-type of each of its members.

[^1]: "or" is non-exclusive.  A value could be of both types.

Below the *Nominal Type* box we have `A & B`, an
[intersection type](builtins.md#intersection-type-bound-).
An intersection is a sub-type of each of its elements.

```temper
class C extends A, B {}
// ⏸️
```

In that, `C` is a declared sub-type of both `A` and `B`, so it's a
sub-type of `A & B`.

(Union and intersection types are actually more general.
`A | Bubble` is an expressible type as is `(A?) & D`, so
this diagram does not imply that all union/intersection types fit
neatly in a region on one side of nominal types. `Void` has
constraints here, however. `Void | Bubble` makes sense, but
`A | Void` doesn't.)

In the middle are *NominalType*s.  These are types declared with a name
and parameters.  *NominalTypes* include all these:

```temper
Boolean         // A builtin type
List<T?>        // A parameterized type
C               // A nominal type, assuming a definition like `class C` is in scope.
fn (Int): Int   // A type for functions that take an Int and return an Int
// ⏸️
```

### Type Tags
Type tags are what determine whether a narrowing cast[^2] would be safe at runtime.

Static types have more nuance than type tags.

| Static Type  | Type Tag     |
| ------------ | ------------ |
| `C`          | `C`          |
| `Boolean`    | `Boolean`    |
| `List<Int>`  | `List`       |
| `fn(): Void` | `Function`   |

As you can see, all function values have the same type tag, *Function*, and a generic class's
corresponding type tag has the type parameters erased.

[^2]: A narrowing cast is from a super-type to a subtype.  For example, from [*AnyValue*](#interface-anyvalue) to `class C`.

### Type Compatibility

When calling a function method, overloaded methods are filtered based on type and argument list compatibility.

For example, [`+`](builtins.md#-2) has variants that operate on [*Int32*](#class-int32) and
[*Float64*](#class-float64).

During type checking, the Temper compiler filters out unusable variants.
So if two arguments whose static types are *Int32* are passed in, we can filter out the *Float64*
variants because we know that no *Float64* values will ever be passed in.

An input with a static type, *I* is compatible with a function argument with static type, *A*
when *A* is not nullable or *I* is nullable, and any of the following are true:

1. *I* is a functional interface type and *A* is a block lambda or is a reference to a named function
   whose signature matches *I*'s apply method signature after substituting *I*'s type parameters.
2. *I* is a type parameter and its bounds are compatible with *A*.
3. *A* is a type parameter and *I* is compatible* with *A*'s bound
4. *I* and *A* are defined types and *A*'s type shape is or inherits from *A*'s type shape.
