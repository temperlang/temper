# Panics

`orelse` handles a bubble, wherever it comes from, and never a panic.
Bubbles here come from `bubble()`, from builtins (a failed downcast,
`Int` and `Int64` division by zero, a failed number parse, and a code
point out of range), from std (a regex that finds nothing), and from
inside a function value an `orelse` calls. Each `orelse` gives its
fallback:

    let { Regex } = import("std/regex");

    interface Shape {}
    class Square(public side: Int) extends Shape {}
    class Circle extends Shape {}

    let failing(n: Int): Int throws Bubble {
      if (n > 0) { bubble() }
      n
    }
    let downcast(s: Shape): Square throws Bubble { s as Square }
    let calling(f: fn (): Int throws Bubble): Int { f() orelse -9 }

    let zero = failing(0) orelse -1;
    let a = failing(1) orelse -1;
    let b = downcast(new Circle()).side orelse -2;
    let c = 1 / zero orelse -3;
    let d = (1i64 / zero.toInt64()).toInt32() orelse -4;
    let e = "nope".toInt32() orelse -5;
    let f = "x".toFloat64() orelse -6.0;
    let g = String.fromCodePoint(0x110000) orelse "?";
    let digits: Regex = /[0-9]+/;
    let h = digits.find("none").full.value orelse "no digits";
    let i = calling(fn (): Int throws Bubble { failing(2) });
    console.log("${a} ${b} ${c} ${d} ${e} ${(f == -6.0).toString()} ${g} ${h} ${i}");

A panic passes every `orelse` untouched: the one in `inner` around the
call that panics, and the one around `inner()`. So the run fails right
after `panicking`, and neither the fallback nor the line after logs:

    let explode(n: Int): Int throws Bubble {
      if (n > 0) { panic() }
      bubble()
    }
    let noted(name: String): Int {
      console.log("${name} fallback");
      -2
    }
    let inner(): Int throws Bubble { explode(1) orelse noted("inner") }
    console.log("panicking");
    let p = inner() orelse -1;
    console.log("after the panic ${p}");

```log
-1 -2 -3 -4 -5 true ? no digits -9
panicking
```
