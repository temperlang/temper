# Float Operations Function Test

There are enough float math operations available that we put them in a separate
test from floating point basics.

## Test ops

Just go through operations quickly.

And turns out we aren't 100% consistent in our float formatting across backends,
so check nearness rather than formatted values.

    near("half.abs()", half.abs(), half);
    near("(-half).abs()", (-half).abs(), half);

Also, use some pre-extracted `pi` or `e` constants and some direct access, since
our inlining sometimes varies on the usage, and we want to cover all cases.

    near("half.acos()", half.acos(), Float64.pi / three);
    near("half.asin()", half.asin(), pi / six);
    near("(-one).atan()", (-one).atan(), -pi / four);
    near("one.atan2(-one)", one.atan2(-one), three * pi / four);
    near("(four / three).ceil()", (four / three).ceil(), two);
    near("(-four / three).ceil()", (-four / three).ceil(), -one);
    near("(pi / three).cos()", (pi / three).cos(), half);
    near("one.cosh()", one.cosh(), (e + one / e) / two);
    near("half.exp() == e.sqrt()", half.exp(), e.sqrt());
    near("half.expm1()", half.expm1(), e.sqrt() - one);
    near("(four / three).floor()", (four / three).floor(), one);
    near("(-four / three).floor()", (-four / three).floor(), -two);
    near("e.log()", Float64.e.log(), one);
    near("ten.log10()", ten.log10(), one);
    near("(e - one).log1p()", (e - one).log1p(), one);

Take a brief intermission for `max` and `min`, because we want to test `NaN` in
both positions. Some backends might give `NaN` result by default.

    let maxMinCombos = [NaN, one].join(", ") { x =>
      [NaN, two].join(", ") { y =>
        let pair = [x.max(y), x.min(y)].join(", ") { z => z.toString() };
        "(${pair})"
      }
    };
    console.log("maxMinCombos: ${maxMinCombos}");

Back to your regularly scheduled, tersely listed comparisons, including float
modulus ops.

    near("2.25 % 2.0", (two + quarter) % two, 0.25);
    near("2.25 % -2.0", (two + quarter) % -two, 0.25);
    near("-2.25 % 2.0", -(two + quarter) % two, -0.25);
    near("-2.25 % -2.0", -(two + quarter) % -two, -0.25);
    near("pi.round()", pi.round(), three);
    near("(-pi).round()", (-pi).round(), -three);
    near("pi.sign()", pi.sign(), one);
    near("(-pi).sign()", (-pi).sign(), -one);

And just kidding, because we have some string checking to do again, expecting
signed zero, which we expect to format consistently but whose value is harder
to compare by nearness.

    console.log("zero.sign(): ${zero.sign()}");
    console.log("(-zero).sign(): ${(-zero).sign()}");

And again back to simple cases.

    near("(pi / six).sin()", (pi / six).sin(), half);
    near("one.sinh()", one.sinh(), (e - one / e) / two);
    near("(-pi / four).tan()", (-pi / four).tan(), -one);
    near("(three * pi / four).tan()", (three * pi / four).tan(), -one);
    near("one.tanh()", one.tanh(), (e ** 2.0 - 1.0) / (e ** 2.0 + 1.0));

But also a bit of testing of `near` params.

    console.log("one.near(one + 0.1): ${one.near(one + 0.1)}");

We have both `relTol` and `absTol` tolerance options, in that order. Either or
both can be specified, although we try only one or the other here.

    console.log("one.near(one + 0.1, absTol = 0.11): ${
      one.near(one + 0.1, null, 0.11).toString()
    }");
    console.log("one.near(one - 0.1, absTol = 0.11): ${
      one.near(one - 0.1, null, 0.11).toString()
    }");
    console.log("ten.near(ten + 0.1, absTol = 0.011): ${
      ten.near(ten + 0.1, null, 0.011).toString()
    }");
    console.log("ten.near(ten + 0.1, relTol = 0.011): ${
      ten.near(ten + 0.1, 0.011).toString()
    }");

Now all the expected output.

```log
half.abs(): ✅
(-half).abs(): ✅
half.acos(): ✅
half.asin(): ✅
(-one).atan(): ✅
one.atan2(-one): ✅
(four / three).ceil(): ✅
(-four / three).ceil(): ✅
(pi / three).cos(): ✅
one.cosh(): ✅
half.exp() == e.sqrt(): ✅
half.expm1(): ✅
(four / three).floor(): ✅
(-four / three).floor(): ✅
e.log(): ✅
ten.log10(): ✅
(e - one).log1p(): ✅
maxMinCombos: (NaN, NaN), (NaN, NaN), (NaN, NaN), (2.0, 1.0)
2.25 % 2.0: ✅
2.25 % -2.0: ✅
-2.25 % 2.0: ✅
-2.25 % -2.0: ✅
pi.round(): ✅
(-pi).round(): ✅
pi.sign(): ✅
(-pi).sign(): ✅
zero.sign(): 0.0
(-zero).sign(): -0.0
(pi / six).sin(): ✅
one.sinh(): ✅
(-pi / four).tan(): ✅
(three * pi / four).tan(): ✅
one.tanh(): ✅
one.near(one + 0.1): false
one.near(one + 0.1, absTol = 0.11): true
one.near(one - 0.1, absTol = 0.11): true
ten.near(ten + 0.1, absTol = 0.011): false
ten.near(ten + 0.1, relTol = 0.011): true
```

## Support

But as for the basic float test, let's get a base value and hack var assignment
to avoid inlining.

    var one = 1.0;
    one = one;

If we don't inline the above value, we presumably don't inline dependent values.
And since top-level is order-independent, we can assign half before two.

    let zero = one - one;
    let half = one / two;
    let quarter = half / two;
    let two = one + one;
    let three = one + two;
    let four = two * two;
    let five = one + four;
    let six = two * three;
    let ten = two * five;
    let { e, pi } = Float64;

And get ready for convenient approximate testing.

    let near(label: String, actual: Float64, expected: Float64): Void {
      let result = when (actual.near(expected)) {
        true -> "✅";
        else -> "❌ wanted ${expected}, was ${actual}";
      }
      console.log("${label}: ${result}");
    }
