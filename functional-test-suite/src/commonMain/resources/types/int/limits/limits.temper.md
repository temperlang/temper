# Int Limits

Temper `Int` is a signed, 32-bit, two's complement integer type with wrapping
semantics at the limits. `Int64` is the same but 64-bit.

NOTE: Tests here can possibly move to the
[int/basics test](../basics/basics.md) once all current backends are passing.
But keeping it separate helps at least for now with only some backends passing.

## `Int32`, aka `Int`

Let's start within the limits.

    console.log("Int32")
    let big = 0x7fff_ffff * one;
    let small = -0x8000_0000 * one;

Then walk past. The originals should be signed as given, but the overflows
should wrap.

    console.log("${big} + 1 == ${big + 1}");
    console.log("${small} - 1 == ${small - 1}");

```log
Int32
2147483647 + 1 == -2147483648
-2147483648 - 1 == 2147483647
```

Multiply also needs to wrap. Wrap around more than once for fun.

    console.log("${big} * 4 == ${big * 4}");
    console.log("${small} * 4 == ${small * 4}");
    console.log("${small + 1} * 4 == ${(small + 1) * 4}");
    console.log("${small + 1} * ${big} == ${(small + 1) * big}");

```log
2147483647 * 4 == -4
-2147483648 * 4 == 0
-2147483647 * 4 == 4
-2147483647 * 2147483647 == -1
```

Also check some `-1` edge cases. Operator `%` is interesting at least to test,
in case some backends involve division in that.

    console.log("${small} / -1 == ${small / -1}")
    console.log("${small} % -1 == ${small % -1}")
    console.log("-(${small}) == ${-small}")

```log
-2147483648 / -1 == -2147483648
-2147483648 % -1 == 0
-(-2147483648) == -2147483648
```

Test more negative div and mod while at it, though not about limits.

    let three = 3 * one;
    let five = 5 * one;
    console.log("${five} / ${three} == ${five / three}")
    console.log("${five} / -${three} == ${five / -three}")
    console.log("-${five} / ${three} == ${-five / three}")
    console.log("-${five} / -${three} == ${-five / -three}")
    console.log("${five} % ${three} == ${five % three}")
    console.log("${five} % -${three} == ${five % -three}")
    console.log("-${five} % ${three} == ${-five % three}")
    console.log("-${five} % -${three} == ${-five % -three}")

```log
5 / 3 == 1
5 / -3 == -1
-5 / 3 == -1
-5 / -3 == 1
5 % 3 == 2
5 % -3 == 2
-5 % 3 == -2
-5 % -3 == -2
```

## `Int64`

Sometimes we need larger limits, so we allow 64-bit ints.

    console.log("Int64")
    let notSoBig = big.toInt64();
    let notSoSmall = small.toInt64();

We can walk past those in 64 bits.

    console.log("${notSoBig} + 1 == ${notSoBig + 1i64}");
    console.log("${notSoSmall} - 1 == ${notSoSmall - 1i64}");

```log
Int64
2147483647 + 1 == 2147483648
-2147483648 - 1 == -2147483649
```

We need bigger values to reach limits in 64 bits.

    let big64 = 0x7fff_ffff_ffff_ffff_i64 * one.toInt64();
    let small64 = -0x8000_0000_0000_0000_i64 * one.toInt64();

    console.log("${big64} + 1 == ${big64 + 1i64}");
    console.log("${small64} - 1 == ${small64 - 1i64}");

```log
9223372036854775807 + 1 == -9223372036854775808
-9223372036854775808 - 1 == 9223372036854775807
```

And check `-1` again for `Int64`.

    console.log("${small64} / -1 == ${small64 / -1i64}")
    console.log("${small64} % -1 == ${small64 % -1i64}")
    console.log("-(${small64}) == ${-small64}")

```log
-9223372036854775808 / -1 == -9223372036854775808
-9223372036854775808 % -1 == 0
-(-9223372036854775808) == -9223372036854775808
```

Also, non-limit div and mod for i64.

    let three64 = three.toInt64();
    let five64 = five.toInt64();
    console.log("${five64} / ${three64} == ${five64 / three64}")
    console.log("${five64} / -${three64} == ${five64 / -three64}")
    console.log("-${five64} / ${three64} == ${-five64 / three64}")
    console.log("-${five64} / -${three64} == ${-five64 / -three64}")
    console.log("${five64} % ${three64} == ${five64 % three64}")
    console.log("${five64} % -${three64} == ${five64 % -three64}")
    console.log("-${five64} % ${three64} == ${-five64 % three64}")
    console.log("-${five64} % -${three64} == ${-five64 % -three64}")

```log
5 / 3 == 1
5 / -3 == -1
-5 / 3 == -1
-5 / -3 == 1
5 % 3 == 2
5 % -3 == 2
-5 % 3 == -2
-5 % -3 == -2
```

Also check parsing limits.

    let checkParse32(string: String): Void {
      let converted = string.toInt32(16).toString() orelse "failed";
      console.log("Parse ${string} -> ${converted}");
    }

    checkParse32("7FFF${}FFFF");
    checkParse32("-8000${}0000");
    checkParse32("8000${}0000");
    checkParse32("-8000${}0001");

```log
Parse 7FFFFFFF -> 2147483647
Parse -80000000 -> -2147483648
Parse 80000000 -> failed
Parse -80000001 -> failed
```

## `Int64` conversions

Converting from `Int32` to `Int64` is always safe, but converting from `Int64`
to `Int32` is potentially lossy.

    console.log("Not so big: ${notSoBig.toInt32Unsafe()}")
    console.log("Actually big: ${big64.toInt32().toString() orelse "no"}")

Also check `String` conversions.

    let checkParse64(string: String): Void {
      let converted = string.toInt64(16).toString() orelse "failed";
      console.log("Parse ${string} -> ${converted}");
    }

    checkParse64("7FFF${}FFFF${}FFFF${}FFFF");
    checkParse64("-8000${}0000${}0000${}0000");
    checkParse64("8000${}0000${}0000${}0000");
    checkParse64("-8000${}0000${}0000${}0001");

```log
Not so big: 2147483647
Actually big: no
Parse 7FFFFFFFFFFFFFFF -> 9223372036854775807
Parse -8000000000000000 -> -9223372036854775808
Parse 8000000000000000 -> failed
Parse -8000000000000001 -> failed
```

Finally check some calculation methods.

    console.log("${small64.min(big64)} < ${small64.max(big64)}");

```log
-9223372036854775808 < 9223372036854775807
```

## Support

Do the usual juggling against inlining until we have better support for that.

    var one = 1;
    one = one;
