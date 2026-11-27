# Java nullables

Nullable values where Java's boxes could go wrong, and `orelse` inside
expressions, where Java needs statements before the expression: two
narrowed `Integer`s compared with `==` would compare references, so
`1000 == 1000` would be false, and a conditional expression mixing a
null `Integer` with an `int` would unbox it and throw.

    let same(a: Int?, b: Int?): Boolean {
      if (a != null && b != null) { a == b } else { false }
    }

    let sameFlag(a: Boolean?, b: Boolean?): Boolean {
      if (a != null && b != null) { a == b } else { false }
    }

    let sameLong(a: Int64?, b: Int64?): Boolean { a == b }

    var calls = 0;
    let noted(n: Int): Int {
      calls += 1;
      console.log("noted ${n} at ${calls}");
      n
    }

    sealed interface Problem extends Error {}
    class Odd extends Problem {}

    let half(n: Int): Int throws Odd {
      if (n % 2 != 0) { throw new Odd() }
      n / 2
    }

    let pick(c: Boolean, x: Int?): Int? {
      if (c) { x } else { 7 }
    }

    let below(i: Int): Int? { if (i < 3) { i } else { null } }

    let count(limit: Int): Int {
      var i = 0;
      while ((below(i) ?? limit) < limit) {
        i += 1;
      }
      i
    }

    console.log("${same(1000, 1000)} ${same(1000, 1001)} ${same(null, 1)}");
    console.log("${sameFlag(true, true)} ${sameLong(5000000000i64, 5000000000i64)}");
    console.log("${noted(1) + (half(noted(3)) orelse noted(4))}");
    console.log("${noted(2) > 0 && (half(5) orelse 0) == 0}");
    console.log("${if (calls > 3) { half(8) orelse 1 } else { 2 }}");
    console.log("${pick(false, null) ?? -1} ${pick(true, null) ?? -1}");
    console.log(count(9).toString());

```log
true false false
true true
noted 1 at 1
noted 3 at 2
noted 4 at 3
5
noted 2 at 4
true
4
7 -1
3
```
