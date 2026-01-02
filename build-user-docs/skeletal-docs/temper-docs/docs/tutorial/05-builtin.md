---
title: Built-in Types Overview
temper-extract: false
---

# Built-in Types Overview

We've now built and tested libraries in Temper. We've also already seen a lot
of the built-in types and features. But to round out ourselves, let's look
more at what comes built into Temper. If you want all the details, though, the
full references on built-in [functions and constants](../reference/builtins.md)
and [also types](../reference/types.md) are elsewhere. Here, we'll just be
doing some overview.

We also diverge here from our example geometric shapes library, though it
served us well for context to get started.

For the types below, some of them can be found as class or interface definitions
in the file [temper/**/Implicits.temper] in the Temper source code. The
interpreter uses much of the code here, but classes here are also separately
redefined for each backend.


## Special types

Temper has a number of special types, some of which you'll see more than others.
See also [additional discussion and a diagram][snippet/type/relationships] of
some of these types:

- `Top` - The supertype of all other types, equivalent to `AnyValue throws Bubble`.
- `Bubble` - Unusable as an ordinary value. Handled via `orelse`. It's used
  in conjunction with other types as function return types, such as
  `Int throws Bubble`.
- `Void` - Indicates that a function return value must be ignored, allowing
  for better compatibility with type expectations of some backends.
- `AnyValue` - The supertype of all usable values. As for any type that
  isn't a sealed interface, you can't downcast from `AnyValue`. This allows
  type safety while still using appropriate backend values.
- *Null* - Supports data interop with external systems. Can be used along with
  other types, such as `String?`. There is only one *Null* value, `null`. For
  functions that might fail, usually `Bubble` is a better choice than making
  the type nullable with `?`.
  And for functions whose result should be ignored, use `Void`.
- `Never` - The subtype of all other types, representing computations that never
  complete, such as infinite loops. Computations that exit abnormally use
  `Bubble`, not `Never`.
- `Invalid` - A type for when a type can't be computed.

As suggested above, sometimes we can distinguish types at runtime, but this
often depends on the specific backend. That's why downcasting is so constrained
in Temper. But in addition to sealed interface subtypes, you can also downcast
to check against `null`:

```temper
$ let maybeMultiply(x: Float64?, y: Float64?): Float64? {
    // The ` as T` macro has return type `T throws Bubble`, so use `orelse`.
    ((x as Float64) * (y as Float64)) orelse null
  }
interactive#0: void
$ maybeMultiply(1.5, 2.0)
interactive#1: 3.0
$ maybeMultiply(null, 2.0)
interactive#2: null
$ maybeMultiply(1.5, null)
interactive#3: null
$ maybeMultiply(null, null)
interactive#4: null
```


## Function types

Function types are represented specially with the keyword `fn`. A higher order
function reference supports positional arguments only. Some examples include:

 | Example | Means |
 | ------- | ----- |
 | `fn (): Void` | Type for a function that takes no arguments and returns the `void` value |
 | `fn (Int): Int` | Type for a function that takes one integer and returns an integer |
 | `fn<T> (T): List<T>` | Type for a generic function with a type parameter `<T>` |
 | `fn (...Int): Boolean` | Type for a function that takes any number of integers |


## Numeric types

As we saw earlier, Temper has the following numeric types built in:

- `Float64` - 64-bit IEEE 754 floating point number. These are widely supported
  across modern languages and platforms. Math operations might not be guaranteed
  to provide 100% consistent results across all backends, however.
- `Int32` - Signed 32-bit integers that wrap on overflow. Alias `Int` also
  exists for this type. Where at all possible, this type maps to a signed 32-bit
  int type on each backend. And for backends without that, where possible, this
  type maps to the simplest, most idiomatic numeric representation that can
  store 32-bit ints. Note that math done *outside* of Temper might have
  different semantics than math done in code built from Temper. For example, on
  Python, the arbitrarily sized `int` type is used, and on JS, the common
  `number` type is used.
- `Int64` - Signed 64-bit integers that wrap on overflow. Where possible, this
  type maps to a signed 64-bit int type on each backend. Or if necessary, it
  maps to the most appropriate type that can store 64-bit ints. For example, on
  JS, the `bigint` type is used, because `number` can't store 64 bits of integer
  precision. On some versions of Lua, a custom type is needed when no standard
  numeric type can store 64-bit ints.

These two types don't interop directly. For example:

```temper
$ 1 + 2.5
1: 1 + 2.5
   ┗━━━━━┛
[interactive#0:1+0-7]@G: No applicable variants in (fn (Int, Int): Int & fn (Int): Int & fn (Float64, Float64): Float64 & fn (Float64): Float64) for inputs (Int, Float64)
interactive#0: fail
$ 1.toFloat64() + 2.5
interactive#1: 3.5
$ 1 + (2.5).toInt()
interactive#2: 3
$ 1 + Infinity.toInt()
interactive#3: fail
$ 1 + NaN.toInt()
interactive#4: fail
```

The methods `toFloat64` and `toInt` might fail with `Bubble` depending on the
size of the values (or NaN) and the backend's `Int` size. Variants
`toFloat64Unsafe` and `toIntUnsafe` always return a value, but they have
backend-dependent behavior outside safe values. The unsafe methods should be
used only for small values and/or with fuzz testing across backends.

We also plan to provide additional common mathematical operations, including
rounding (see [issue#27]).


## Boolean

For logical operations, Temper also has a distinct type:

- `Boolean` - Either `true` or `false`.

Common Boolean operations exist, such as `&&`, `||`, `!`, and so on. You can't
use numbers as Booleans:

```temper
$ if (1) { console.log("hi"); }
1: if (1) { console.log("hi"
       ⇧
[interactive#1:1+4-5]@G: Expected value of type Boolean not Int
1: if (1) { console.log("hi"
       ⇧
[interactive#1:1+4-5]@R: Expected value of type Boolean not Int
interactive#1: fail
$ if (true) { console.log("hi"); }
hi
interactive#2: void
```

The error repeats in the bad usage example because it happens at multiple
stages. The first report is from static type checking. When compiler errors
(including static type errors) happen in Temper, you can still carry through to
backends or the interpreter. This behavior assists testing portions of code that
aren't in error. But then the behavior is backend-dependent. In this case, the
interpreter sees the `Int` value and fails with the same error message as the
type checker.

You shouldn't publish libraries with Temper compiler errors.


## String

Temper has the following types for representing text:

- `String` - Immutable text data.
- `StringIndex` - An index into a string that allows efficient left-to-right traversal.

Here are some examples:

```temper
$ let what = "libraries";
interactive#0: void
$ let message = "I ❤ ${what}";
interactive#1: void
$ message.split(" ")
interactive#2: ["I","❤","libraries"]
$ message.isEmpty
interactive#3: false
$ message.length
1: message.length
           ┗━━━━┛
[interactive#4:1+8-14]@G: Expected function type, but got Invalid
1: message.length
   ┗━━━━━━━━━━━━┛
[interactive#4:1+0-14]@G: No member length in String__27
interactive#4: fail
$ message.countBetween(String.begin, message.end)
interactive#6: 13
$ message[message.next(message.next(String.begin))]
interactive#7: 10084
```

Different backends have different internal string representations, so simple
random access into string data can be inefficient.
`StringIndex` is an opaque type that connects to an integer type on each
backend, but the exact integer differs depending on the "native string encoding."

- In languages like Rust and C++, the integer is a byte offset.  Supplemental
  code-points use four bytes, so `myString.next(i)` might return a number
  up to 4 greater than `i`.
- In languages like C#, Java, and JavaScript, the integer is a UTF-16 code unit
  offet, and a supplemental code-point uses 2 UTF-16 surrogates, so `myString.next(i)`
  might return a number up to 2 greater than `i`.
- Python3 presents its strings as like an array of code-point, so `myString.next(i)`
  is 1 greater than `i`, unless `i` is already at the end of the string.

Temper also has raw string syntax to treat backslashes as text data via the
`raw` string tag, as well as multiline string syntax:

```
$ raw"\d+\.\d+"
interactive#0: "\\d+\\.\\d+"
```

```temper
$ ("""
    "- An outline
    // Ignored comment.
    "  - With indentation
    "- Final point
  )
interactive#1: "- An outline\n  - With indentation\n- Final point"
```

Triple-quotes like `"""` start a multiline string, then all whitespace and
comments are ignored, and every line content beginning with `"` continues the
string. If line content starts with something other than `"`, the string ends.
This syntax allows control over indentation both inside and outside the string
content.

Temper has character syntax using a string tagged with `char`.
Character values are simple integer code-point values.

```
$ char'👪'
interactive#3: 128106
```

## List types

A list is a sequential, random access, sized collection, and built-in types
include three list types:

- `Listed` - An interface for read-only access to data. Extended by both `List`
  and `ListBuilder`. In the future, third party code might also be able to
  extend it (see [issue#28]), but that doesn't work correctly today.
- `List` - A class for immutable listed data.
- `ListBuilder` - A class for mutable building of listed data.

The classes `List` and `ListBuilder`, just as for other built-in types, are
represented by core types in each backend to the extent possible, such as by
`IReadOnlyList` or `IList` in C#, `List` in Java, `Array` in JS, or `tuple` or
`list` in Python.

Here are some examples:

```temper
$ let nums = [10, 20, 30]; // inferred type: List<Int>
interactive#0: void
$ nums.length
interactive#1: 3
$ nums[1]
interactive#2: 20
$ nums.add(40);
1: nums.add(40);
        ┗━┛
[interactive#3:1+5-8]@G: Expected function type, but got Invalid
1: nums.add(40);
   ┗━━━━━━━━━━┛
[interactive#3:1+0-12]@G: No member add in List__10 | Listed__44
interactive#3: fail
$ let nums2 = do {
    let builder = new ListBuilder<Int>();
    builder.addAll(nums);
    builder.add(40);
    builder.toList()
  }
interactive#4: void
$ nums2
interactive#5: [10,20,30,40]
$ nums2.map { (n): Int => n / 10 }
interactive#6: [1,2,3,4]
```

For many common cases, functions should accept `Listed` views and return
immutable `List` data:

```temper
let calculateSomething<T>(items: Listed<T>): List<T> {
  // ... meaningful calculations here ...
}
```

As mentioned earlier, Temper allowes for mutable data but prefers immutable.


## Map types

Temper's map types are like its list types, but they associate keys more
arbitrarily with values:

- `Mapped` - An interface for read-only access to data, extended by both `Map`
  and `MapBuilder`.
- `Map` - A class for immutable listed data.
- `MapBuilder` - A class for mutable building of listed data.

As for list types, `Map` and `MapBuilder` use standard backend types where
available, such as `IReadOnlyDictionary` or `IDictionary` in C#, `Map` in Java
or JS, or `dict` in Python.

```temper
$ let messages = new Map([
    new Pair(200, "OK"),
    new Pair(404, "Not Found"),
    new Pair(500, "Internal Server Error"),
  ]);
interactive#0: void
$ messages[200]
interactive#1: "OK"
$ messages.toList().map { (entry): Int => entry.key }
interactive#2: [200,404,500]
$ let neighbors = do {
    let builder = new MapBuilder<String, List<String>>();
    builder["Honduras"] = ["El Salvador", "Guatemala", "Nicaragua"];
    builder["Nicaragua"] = ["Costa Rica", "Honduras"];
    builder.toMap()
  };
interactive#3: void
$ neighbors["Nicaragua"]
interactive#4: ["Costa Rica","Honduras"]
```

For now, you can use only `Int` or `String` keys. We hope to expand support to
user-defined key types in the future (see [issue#29]).


## Other types

Temper has additional built-in types, but above discussion highlights some of the
most important ones. Also, some types currently in [temper/**/Implicits.temper],
such as `Deque<T>` and `DenseBitVector` might move to support libraries in the
future (see [issue#30]).

There's also currently a standard library called `"std"` with modules
`"std/regex"` and `"std/testing"`. These also are likely to see some
reorgnization before 1.0 (see [issue#26]). We already saw a bit of testing
earlier. And we'll take a look at regular expressions soon, but first let's
understand how Temper handles memory management.


## Links
- **NEXT**: [Memory Management](06-memory.md)
- **PREVIOUS**: [Modules & Libraries](04-modlib.md)
- Reference: [Built-ins](../reference/builtins.md),
  [Types](../reference/types.md)
