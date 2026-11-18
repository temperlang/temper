# Nullables

Nullable types, null checks that narrow, `??`, `==` with a side that may
be null, and `orelse` with `bubble()` and `panic()`: nullable
parameters, results, locals, and fields, a nullable class type, and a
failure caught where it's a value and where it stands alone, and a
fallback with `??` as a function's last value. The log is what
the interpreter gives.

    export class Node(public value: Int, public next: Node?) {
      public last(): Int {
        let n = next;
        if (n == null) { value } else { n.last() }
      }
    }

    export let describe(x: Int?): String {
      if (x == null) { return "none"; }
      "some ${x + 1}"
    }

    export let firstWord(s: String?): String? {
      if (s != null && s != "") { s } else { null }
    }

    let same(a: String?, b: String?): Boolean { a == b }
    let sameInt(a: Int?, b: Int?): Boolean { a == b }

    let parse(s: String): Int { s.toInt32() orelse -1 }

    // The division may bubble, and so may the call in it, which the
    // `orelse` catches too.
    let halved(s: String, d: Int): Int { s.toInt32() / d orelse -1 }

    let positive(n: Int): Int throws Bubble {
      if (n < 0) { bubble() }
      n
    }

    let checked(n: Int): Int { positive(n) orelse panic() }

    // Exported, as nothing calls it: Kotlin's build fails on it ("does
    // not export symbol return__3").
    export let stops(): Int { (do { panic(); 1 }) orelse 2 }

    let orZero(x: Int?): Int { x ?? 0 }

    let reason(n: Int, why: String?): String {
      do { positive(n); "ok" } orelse (why ?? "fails")
    }

    let varying(): Void {
      var maybe: Int? = null;
      console.log((maybe == null).toString());
      maybe = 3;
      console.log((maybe != null).toString());
    }

    console.log(describe(null));
    console.log(describe(41));
    console.log(firstWord("hi") ?? "nothing");
    console.log(firstWord("") ?? "nothing");
    console.log("${same(null, null)} ${same("a", null)} ${same(null, "a")} ${same("a", "a")}");
    console.log("${sameInt(1, 2)} ${sameInt(3, 3)} ${sameInt(null, 3)}");
    console.log("${parse("12")} ${parse("x")}");
    console.log("${halved("12", 2)} ${halved("x", 2)} ${halved("12", 0)}");
    console.log(checked(7).toString());
    console.log("${orZero(null)} ${orZero(5)}");
    varying();
    let list = new Node(1, new Node(2, new Node(3, null)));
    console.log(list.last().toString());
    let tail = list.next;
    if (tail != null) {
      console.log(tail.value.toString());
    }
    positive(-1) orelse console.log("negative");
    let fallback = positive(-2) orelse 0;
    console.log(fallback.toString());
    console.log("${reason(1, null)} ${reason(-1, null)} ${reason(-1, "bad")}");

```log
none
some 42
hi
nothing
true false false true
false true false
12 -1
6 -1 -1
7
0 5
true
true
3
2
negative
0
ok fails bad
```
