# Float Basics Functional Test

Define a few common floats. Reassign them to keep all the math from getting inlined

See the [temper docs for more requirements](https://temperlang.dev/preview/oli5Pv3JnJy1FsrdscQtHAMAcgMj2i89xkFMfL5wAIyO/temper-language/builtins/#general-comparison-caveats)

    let negativeZero = -0.0;
    var zero = 0.0;
    zero = zero;
    var one = 1.0;
    one = one;
    var two = 2.0;
    two = two;
    var three = 3.0;
    three = three;

    let gt(a: Float64, b: Float64): Boolean {
      return a > b;
    }
    let lt(a: Float64, b: Float64): Boolean {
      return a < b;
    }
    let eq(a: Float64, b: Float64): Boolean {
      return a == b;
    }
    let neq(a: Float64, b: Float64): Boolean {
      return a != b;
    }
    let add(a: Float64, b: Float64): Float64 {
      return a + b;
    }

Basic equality and comparisons

    if (gt(one, zero)) {
      console.log("greater than");
    }
    if (lt(zero, one)) {
      console.log("less than");
    }
    if (eq(zero, one)) {
      console.log("equality is wrong")
    }
    if (neq(zero, one)) {
      console.log("inequality")
    }
    console.log("${zero}");

```log
greater than
less than
inequality
0.0
```

Negative zero is a tricky case.
`zero` and `negativeZero` should be equal per IEE754 but temper says not equal.
Adding zero and negative zero is always zero, and subtraction gets conceptually rewritten to adding the opposite.

    if (neq(negativeZero, zero)) {
      console.log("zeroes not equal");
    }
    if (lt(negativeZero, zero)) {
    console.log("negZero < zero")
    }
    if (zero - zero == zero) {
      console.log("zero subtraction");
    }
    if (zero + zero == zero) {
      console.log("zero addition");
    }
    if (negativeZero - zero == negativeZero) {
      console.log("negative zero minus zero is negative");
    }
    if (negativeZero + zero == zero) {
      console.log("negative zero plus zero is positive");
    }
    if (zero - negativeZero == zero) {
      console.log("zero minus negative zero is zero");
    }
    if (zero + negativeZero == zero) {
      console.log("zero plus negative zero is zero");
    }
    if (negativeZero - negativeZero == zero) {
      console.log("negative zero minus negative zero is zero");
    }
    if (negativeZero + negativeZero == negativeZero) {
      console.log("negative zero plus negative zero is negative zero");
    }
    console.log("${negativeZero}");

```log
zeroes not equal
negZero < zero
zero subtraction
zero addition
negative zero minus zero is negative
negative zero plus zero is positive
zero minus negative zero is zero
zero plus negative zero is zero
negative zero minus negative zero is zero
negative zero plus negative zero is negative zero
-0.0
```

Fractions

    var third = one / three;
    third = third;
    var half = one / two;
    half = half;
    var twoThirds = two / three;
    twoThirds = twoThirds;

    if (gt(third, half)) {
      console.log("oops third > half");
    }
    if (gt(half, twoThirds)) {
      console.log("oops half > twoThirds");
    }
    if (neq(third * two, twoThirds)) {
      console.log("misaligned multiplication");
    }
    if (neq(add(third, twoThirds), one)) {
      console.log("doesn't add back up");
    }

String outputs

    console.log("${half}");
    console.log("${4.0 ** -half}");
    console.log("${third}");
    console.log("${twoThirds}");
    console.log("${one}")
    let almostBig = 1e25 * one;
    console.log("${almostBig}")
    var big = 99999999999999.0 * 999999999999.0;
    big = big;
    console.log("${big}");
    var bigger = big * big * big * big * big * big * big * big;
    bigger = bigger;
    console.log("${bigger}");

```log
0.5
0.5
0.3333333333333333
0.6666666666666666
1.0
1.0e+25
9.9999999999899e+25
9.999999999919205e+207
```

# Diverging cases

## Overflow/Infinity

Once you exceed the largest Float64 then you get an Infinity and are a significant edge case

    var biggest = bigger * bigger * bigger * bigger * bigger * bigger;
    biggest = biggest;
    console.log("biggest ${biggest}");
    var tiniest = one / biggest;
    tiniest = tiniest;
    console.log("tiniest ${tiniest}");
    var negativeInfinity = biggest * -1.0;
    negativeInfinity = negativeInfinity;
    console.log("negative infinity ${negativeInfinity}");

Correct output TBD see issues 1163 and 1166

```log
biggest Infinity
tiniest 0.0
negative infinity -Infinity
```

## Infinity constant

    var infinity = 1E999;
    infinity = infinity;
    console.log("${infinity}");
    console.log("processed an infinity");

```log
Infinity
processed an infinity
```

## NaN

    var nan = NaN;
    nan = nan;
    console.log("${nan}");
    if (nan > infinity) {
      console.log("NaN is biggest");
    } else {
      console.log("Infinity is biggest");
    }

```log
NaN
NaN is biggest
```

    console.log("NaN == NaN? ${nan == NaN}");

```log
NaN == NaN? true
```

## Int32 conversion

Converting from `Int32` to `Float64` is always safe, but converting from
`Float64` to `Int32` is potentially lossy.

    console.log("Int to float: ${two.toInt32Unsafe().toFloat64()}");
    console.log("Positive to int: ${(4.9 * one).toInt32Unsafe()}");
    console.log("Negative to int: ${(-4.9 * one).toInt32Unsafe()}");
    console.log(
      "Infinity to int: ${infinity.toInt32().toString() orelse "no"}",
    );
    console.log("NaN to int: ${NaN.toInt32().toString() orelse "no"}");

```log
Int to float: 2.0
Positive to int: 4
Negative to int: -4
Infinity to int: no
NaN to int: no
```

## Int64 conversion

Converting between `Int64` and `Float64` is potentially lossy, either direction.

    let oneInt64 = one.toInt64Unsafe();
    let notSoBig = 0x7fff_ffffi64 * oneInt64;
    let notSoSmall = -0x8000_0000i64 * oneInt64;
    let slightlyBigger = (notSoBig.toFloat64Unsafe() + 1.5).toInt64Unsafe();
    let slightlySmaller = (notSoSmall.toFloat64Unsafe() - 1.5).toInt64Unsafe();
    let tooBigForFloat64 = 0x20_0000_0000_0000i64 * one.toInt64();
    let okToConvert64 = (tooBigForFloat64 - 1i64).toFloat64Unsafe();
    let sneakyBig = okToConvert64 + 1.0;

    console.log("Bigger to Int64: ${slightlyBigger}");
    console.log("Smaller to Int64: ${slightlySmaller}");
    console.log("Barely ok to Int64: ${okToConvert64.toInt64Unsafe()}");
    console.log("Sneaky big to Int64: ${
      sneakyBig.toInt64().toString() orelse "no"
    }");
    console.log("Too big Int64 to Float64: ${
      tooBigForFloat64.toFloat64().toString() orelse "no"
    }");
    console.log(
      "Infinity to Int64: ${infinity.toInt64().toString() orelse "no"}",
    );
    console.log("NaN to Int64: ${NaN.toInt64().toString() orelse "no"}");

```log
Bigger to Int64: 2147483648
Smaller to Int64: -2147483649
Barely ok to Int64: 9007199254740991
Sneaky big to Int64: no
Too big Int64 to Float64: no
Infinity to Int64: no
NaN to Int64: no
```

## Parsing

    let checkParse(string: String): Void {
      let converted = string.toFloat64().toString() orelse "failed";
      console.log("Parse ${string} -> ${converted}");
    }
    checkParse("-1.5");
    checkParse("-0");
    checkParse("2.0");
    checkParse("2");
    checkParse(" 2 ");
    checkParse("5e-1");
    checkParse("NaN");
    checkParse("-Infinity");

Check against illegal Temper things, including some things that sometimes work
in some backends by default. This makes sure we've done things carefully. Also,
disallow forms not supported by JSON.

    checkParse("2.");
    checkParse(".2");
    checkParse("2.0.0");
    checkParse("-inf");
    checkParse("totally");

```log
Parse -1.5 -> -1.5
Parse -0 -> -0.0
Parse 2.0 -> 2.0
Parse 2 -> 2.0
Parse  2  -> 2.0
Parse 5e-1 -> 0.5
Parse NaN -> NaN
Parse -Infinity -> -Infinity
Parse 2. -> failed
Parse .2 -> failed
Parse 2.0.0 -> failed
Parse -inf -> failed
Parse totally -> failed
```
