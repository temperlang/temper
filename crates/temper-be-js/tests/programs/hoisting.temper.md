# Hoisting

What JS writes as statements before the expression they're in: `orelse`
in arguments, in `&&` and `||` (which run it only when they get there),
in `if` values' branches, and in a loop's condition, which runs it each
time round; earlier arguments still run first. Also `==` on `Float64?`.

    var calls = 0;
    let noted(n: Int): Int {
      calls += 1;
      console.log("noted ${n}");
      n
    }
    sealed interface Problem extends Error {}
    class TooBig extends Problem {}
    let failing(n: Int): Int throws TooBig {
      if (n > 2) { throw new TooBig() }
      n
    }
    let pair(a: Int, b: Int): String { "${a},${b}" }
    let sameFloat(a: Float64?, b: Float64?): Boolean { a == b }

    console.log(pair(noted(1), failing(3) orelse 9));
    let t = true;
    let f = false;
    console.log((f && (failing(5) orelse 1) == 1).toString());
    console.log((t && (failing(1) orelse 2) == 1).toString());
    console.log((t || (noted(7) orelse 0) == 0).toString());
    console.log((if (t) { failing(4) orelse 4 } else { 0 }).toString());
    var i = 0;
    while ((failing(i) orelse 100) < 3) {
      i += 1;
    }
    console.log(i.toString());
    console.log("${sameFloat(null, null)} ${sameFloat(0.5, null)} ${sameFloat(0.5, 0.5)}");
    console.log(calls.toString());

```log
noted 1
1,9
false
true
true
4
3
true false true
1
```
