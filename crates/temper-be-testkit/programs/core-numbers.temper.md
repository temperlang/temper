# Core numbers

Core's number members where backends' hosts differ: `Int32`'s and
`Int64`'s `succ` and `pred`, which tmpl writes as `+ 1` and `- 1`, so
they wrap; `Int32.signum`, at the extremes too, where `-this` wraps;
and `Float64.near` on NaN, infinities, and signed zeros, which is true
whenever `==` is, so NaN is near NaN, and else never for an infinity or
within a NaN margin. And `reduceFromIndex`, which core writes in
Temper, from an index inside, at, and past the end. The log is what the
interpreter gives.

    let one = 1;
    let big = 0x7fff_ffff * one;
    let small = -0x8000_0000 * one;
    console.log("${big.succ()} ${small.pred()} ${one.succ()} ${one.pred()} ${small.succ()} ${big.pred()}");
    let one64 = one.toInt64();
    let big64 = 9223372036854775807i64 * one64;
    let small64 = -big64 - one64;
    console.log("${big64.succ().toString()} ${small64.pred().toString()} ${one64.pred().toString()}");
    let zero = one - one;
    console.log("${big.signum} ${small.signum} ${zero.signum} ${one.signum} ${(zero - one).signum} ${small.succ().signum}");

    let digits = [1, 2, 3];
    let from(index: Int): Int {
      digits.reduceFromIndex(100, index) { (s: Int, n: Int): Int => s * 10 + n }
    }
    console.log("${from(0)} ${from(1)} ${from(3)} ${from(7)}");

    let inf = 1.0e308 * 10.0;
    let nan = inf - inf;
    let ninf = 0.0 - inf;
    console.log("${nan.near(nan)} ${inf.near(inf)} ${ninf.near(ninf)} ${nan.near(1.0)} ${1.0.near(nan)}");
    console.log("${inf.near(1.0)} ${inf.near(ninf)} ${inf.near(1.0, 0.0, 0.0)} ${inf.near(inf, 0.0, 0.0)} ${inf.near(1.0, 1.0, inf)} ${1.0.near(ninf, 1.0, inf)}");
    console.log("${1.0.near(1.5, nan, 1.0)} ${1.0.near(1.5, 1.0, nan)} ${1.0.near(1.0, nan, nan)} ${(-0.0).near(0.0, 0.0, 0.0)}");
    console.log("${1.0.near(1.0 + 1e-10)} ${1.0.near(1.1)} ${1.0.near(1.1, 0.2)} ${0.0.near(1e-300, 1e-9, 1e-12)}");

```log
-2147483648 2147483647 2 0 -2147483647 2147483646
-9223372036854775808 9223372036854775807 0
1 -1 0 1 -1 -1
100123 10023 100 100
true true true false false
false false false true false false
false false true true
true false true true
```
