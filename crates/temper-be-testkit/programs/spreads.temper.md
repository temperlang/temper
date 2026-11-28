# Spreads

`{ ...old, a: e }` copies a `@data` object with some properties changed,
in every place a value goes: those where backends hoist statements, and
those where Rust and C++ copy natively, which other modules and
libraries don't (`modules` and `imports-library` spread across them).
The log is what the interpreter gives.

A class with an interface, a generic one, and a `let`, an expression, a
`return`, `&&`'s right side, a nested spread, and a spread among a
call's other arguments, which run in order:

    interface Sized { public area(): Int; }
    @imu @data
    class Box(w: Int, h: Int, label: String? = null) extends Sized {
      public area(): Int { w * h }
      public toString(): String { "${label ?? "box"} ${w}x${h}" }
    }
    @data
    class Two<T>(first: T, second: T) {}
    let grown(b: Box): Box { { ...b, w: b.w + 1 } }
    let areaOf(s: Sized): Int { s.area() }
    let a: Box = { w: 2, h: 3 };
    let b = grown(a);
    console.log("${a} ${b} ${areaOf(b)}");
    console.log({ ...b, label: "named" }.toString());
    let wide = a.w > 1 && { ...a, w: 10 }.area() > 20;
    let p: Two<Int> = { first: 1, second: 2 };
    let q = { ...p, second: 5 };
    console.log("${wide.toString()} ${q.first} ${q.second}");
    let nested = { ...{ ...a, w: 7 }, h: 8 };
    let said(s: String, n: Int): Int { console.log(s); n }
    let both(x: Int, b: Box): String { "${x} ${b}" }
    console.log(both(said("one", 1), { ...a, h: said("two", 2) }));
    console.log("${nested} ${(a.w > 5 || { ...a, w: said("three", 3) }.w == 3).toString()}");

```log
box 2x3 box 3x3 9
named 3x3
true 1 5
one
two
1 box 2x2
three
box 7x8 true
```

Loops: an assignment whose given values read the variable assigned,
which a copy into it would change first; a loop's condition; a spread
whose given values run out of order, through temporaries; and a `let`
that hides the parameter it spreads:

    let widths(b: Box, n: Int): Int {
      var t = b;
      for (var i = 0; i < n; ++i) { t = { ...t, w: t.h, h: t.w + 1 }; }
      while ({ ...t, h: 1 }.area() < 9) { t = { ...t, w: t.w + 1 }; }
      for (var i = 0; i < 2; ++i) {
        t = { ...t, h: said("h", t.w), w: said("w", t.h) };
      }
      if (t.w > 0) {
        let b = { ...b, label: "inner" };
        console.log(b.toString());
      }
      t.w
    }
    console.log(widths(a, 3).toString());

```log
h
w
h
w
inner 2x3
9
```

`this` in a method; a generic class with nothing given, with a given
value, and in a generic function; a sealed interface's subtypes, smart
cast by `when`; an `if`'s value; and a function literal's body:

    @data class Step(n: Int, tag: String) {
      public next(): Step { { ...this, n: n + 1 } }
    }
    let swapped<T>(t: Two<T>): Two<T> { { ...t, first: t.second, second: t.first } }
    let s = new Step(1, "s").next().next();
    let same = { ...q };
    let words: Two<String> = { first: "a", second: "b" };
    let flipped = swapped(words);
    console.log("${s.n} ${s.tag} ${same.first}${same.second} ${flipped.first}${flipped.second}");
    sealed interface Shape {}
    @data class Circle(r: Int) extends Shape {}
    @data class Square(side: Int) extends Shape {}
    let doubled(x: Shape): Shape {
      let made = when (x) {
        is Circle -> { ...x, r: x.r * 2 };
        is Square -> { ...x, side: x.side * 2 };
      };
      made
    }
    let size(x: Shape): Int {
      let n = when (x) {
        is Circle -> x.r;
        is Square -> x.side;
      };
      n
    }
    console.log("${size(doubled(new Circle(3)))} ${size(doubled(new Square(5)))}");
    let picked(c: Boolean): Box { if (c) { { ...a, w: 9 } } else { a } }
    let applied(f: fn (Int): Box): Box { f(4) }
    let made = applied { (n: Int): Box => ({ ...a, h: n }) };
    console.log("${picked(true)} ${picked(false)} ${made}");

```log
3 s 15 ba
6 10
box 9x3 box 2x3 box 2x4
```

Defaults that aren't null, on properties that can't hold null, so the
object still round-trips: a `var` that isn't nullable, and a nullable
property that isn't a `var`, with a default that can't give null. A
spread copies each as it is, not made again, natively where Rust and
C++ can, for a class without a `var`:

    @data class Dial(var level: Int = 5, note: String? = "n", twice: Int = level * 2) {}
    @data class Mark(note: String? = "n", size: Int = 2) {}
    let dial = new Dial(1, null);
    dial.level = 4;
    let turned = { ...dial, note: "m" };
    let mark = new Mark(null);
    let bigger = { ...mark, size: 3 };
    console.log("${turned.level} ${turned.note ?? "-"} ${turned.twice} ${bigger.note ?? "-"} ${bigger.size}");

```log
4 m 2 n 3
```

Given values that may fail: caught by `orelse`, bare or in parentheses,
and bubbling out of a function that `throws`, which leaves no object:

    let positive(n: Int): Int throws Bubble {
      if (n < 0) { bubble() }
      n
    }
    let sized(b: Box, n: Int): Box throws Bubble { { ...b, w: positive(n) } }
    let caught = { ...a, w: positive(-1) orelse 0, h: (positive(5) orelse -1) };
    console.log("${caught} ${sized(a, 4).toString() orelse "-"}");
    console.log(do { sized(a, -1); "sized" } orelse "bubbled");

```log
box 0x5 box 4x3
bubbled
```

In an `async` block, a spread's given value awaits, so `old`, held first,
is kept across the `await`:

    let gate = new PromiseBuilder<Int>();
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      let later = { ...a, label: "later", h: await gate.promise orelse -1 };
      console.log(later.toString());
    }
    gate.complete(6);

```log
later 2x6
```
