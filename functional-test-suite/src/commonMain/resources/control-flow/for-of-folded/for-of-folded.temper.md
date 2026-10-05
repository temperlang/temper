# Loops in functions called with constant arguments

A call with constant arguments to a function the compiler can evaluate
is folded while compiling. These functions loop with `for ... of`,
`forEach` and `filter`, whose bodies are block lambdas that assign a
local of the enclosing function, so folding the call creates closures
that did not exist before it ran.

    let total(xs: List<Int>): Int {
      var t = 0;
      for (let x of xs) { t += x; }
      t
    }

    let totalForEach(xs: List<Int>): Int {
      var t = 0;
      xs.forEach { (x): Void => t += x; };
      t
    }

    let totalFiltered(xs: List<Int>): Int {
      var t = 0;
      xs.filter { (x): Boolean => t += x; true };
      t
    }

    let totalNested(xss: List<List<Int>>): Int {
      var t = 0;
      for (let xs of xss) { for (let x of xs) { t += x; } }
      t
    }

    let firstOver(xs: List<Int>, n: Int): Int {
      for (let x of xs) {
        if (x > n) { return x; }
      }
      -1
    }

    console.log("total ${total([1, 2, 3])}");
    console.log("forEach ${totalForEach([1, 2, 3])}");
    console.log("filter ${totalFiltered([1, 2, 3])}");
    console.log("nested ${totalNested([[1, 2], [3], []])}");
    console.log("firstOver ${firstOver([1, 2, 3], 1)} ${firstOver([1, 2, 3], 5)}");

```log
total 6
forEach 6
filter 6
nested 6
firstOver 2 -1
```
