# Panics

`orelse` handles a failure, wherever it comes from, and never a panic.
Failures here come from `bubble()`, from builtins (a failed number
parse throws a `ParseError`, and a code point out of range an
`InvalidCodePoint`), from std (a regex that finds nothing), and
from inside a function value an `orelse` calls. Each `orelse` gives its
fallback:

    let { Regex } = import("std/regex");
    let { Date } = import("std/temporal");
    let { JsonError, JsonTextProducer, parseJson } = import("std/json");

    interface Shape {}
    class Square(public side: Int) extends Shape {}
    class Circle extends Shape {}

    let failing(n: Int): Int throws Bubble {
      if (n > 0) { bubble() }
      n
    }
    let downcast(s: Shape): Square throws Bubble {
      if (s is Square) { s } else { bubble() }
    }
    let calling(f: fn (): Int throws Bubble): Int { f() orelse -9 }

    let zero = failing(0) orelse -1;
    let a = failing(1) orelse -1;
    let b = downcast(new Circle()).side orelse -2;
    let e = "nope".toInt32() orelse -5;
    let f = "x".toFloat64() orelse -6.0;
    let g = String.fromCodePoint(0x110000) orelse "?";
    let digits: Regex = /[0-9]+/;
    let h = digits.find("none").full.value orelse "no digits";
    let i = calling(fn (): Int throws Bubble { failing(2) });
    console.log("${a} ${b} ${e} ${(f == -6.0).toString()} ${g} ${h} ${i}");

The other builtins and std functions that fail, each where a host
library fails for it on some backend: other number parses and
conversions, code points out of range, a missing key, an invalid date,
and malformed or partial JSON throw declared failures, which `orelse`
handles as it does a bubble, and `catch` binds:

    let appending(codePoint: Int): String throws InvalidCodePoint {
      let sb = new StringBuilder();
      sb.appendCodePoint(codePoint);
      sb.toString()
    }
    let parsing(text: String): String throws JsonError {
      parseJson(text);
      "parsed"
    }
    let k = "zz".toInt64() orelse -7i64;
    let l = "99999999999".toInt32() orelse -8;
    let m = 1.0e20.toInt32() orelse -9;
    let n = 5000000000i64.toInt32() orelse -10;
    let points = [0x41, 0x110000];
    let o = String.fromCodePoints(points) catch (e) { e.codePoint.toString(16) };
    let q = appending(-1) catch (e) { e.codePoint.toString() };
    let builder = new MapBuilder<String, Int>();
    let r = builder["x"] orelse -11;
    let r2 = builder.remove("y").toString() catch (e) { e.message };
    let s = new Date(2023, 2, 30).toString() orelse "no date";
    let t = Date.fromIsoString("2023-13-01").toString() orelse "bad date";
    let u = parsing("[1,") orelse "bad json";
    let partial = new JsonTextProducer();
    partial.startObject();
    let v = partial.toJsonString() catch (e) { e.problem };
    console.log("${k.toString()} ${l} ${m} ${n} ${o} ${q} ${r} ${r2} ${s} ${t} ${u}");
    console.log(v);

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
-1 -2 -5 true ? no digits -9
-7 -8 -9 -10 110000 -1 -11 missing key (key: y) no date bad date bad json
expected one JSON value
panicking
```
