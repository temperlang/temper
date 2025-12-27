# List Reduce (aka Fold) Test

This is separated from other operations because arbitrary multi-parameter
callbacks with Temper Core were being difficult in Java. But being separate
might be fine here to add more combos in the future or maybe test `all`/`any`
methods once we have those.

Reducing a list to a single value is a common need. But use a list that doesn't
start with 0 or 1, because that can hide bugs for sum or product.

    let vals = [2, 3, 4];
    let sum = vals.reduce { sum, n => sum + n };

And note that reduce without a starting point panics on empty list, so be sure
to start with `reduceFrom` if you can't ensure that you have items.

    // let notSum = vals.slice(0, 0).reduce { sum, n => sum + n };
    let poorReverseJoin = vals.reduceFrom("") { (text: String, n): String =>
      "${n}${text}"
    };
    console.log(
      "Reductions: ${sum} ${poorReverseJoin}"
    );

```log
Reductions: 9 432
```

## All by reduce

This exercises a case where we failed to adapt function types properly in Java.
We might get a built-in `all` method later, but this can still work as a test.

    let all<T>(items: Listed<T>, check: fn (T): Boolean): Boolean {

One key difference from above is that this is a local closure. Beyond that, it
also (1) calls a captured function which (2) references type parameter `T`, both
of which need special treatment in Rust.

      items.reduceFrom(true) { (result: Boolean, it): Boolean =>
        result && check(it)
      }
    }
    console.log("All even: ${all(vals) { n: Int => n % 2 == 0 }}");
    console.log("All positive: ${all(vals) { n: Int => n > 0 }}");

```log
All even: false
All positive: true
```
