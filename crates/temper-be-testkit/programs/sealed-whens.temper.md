# Sealed whens

A `when` whose `is` checks cover every subtype of a sealed interface
needs no `else`, wherever it's a value: a function's result, nested in
another's branch, an `if`'s branch, a method's result, returned, and an
argument, as well as a `let`'s value. Its missing `else` is never
reached. The log is what the interpreter gives.

    sealed interface Shape { public get size(): Int; }
    class Dot(public size: Int) extends Shape {}
    class Line(public size: Int) extends Shape {}

    let shapeOf(n: Int): Shape { if (n > 1) { new Line(n) } else { new Dot(n) } }

    let named(s: Shape): String {
      let name = when (s) { is Dot -> "dot"; is Line -> "line"; };
      name
    }

    let pair(s: Shape, t: Shape): String {
      when (s) {
        is Dot -> when (t) { is Dot -> "dd"; is Line -> "dl"; };
        is Line -> "l";
      }
    }

    let doubled(n: Int): Int { n * 2 }

    let code(s: Shape, early: Boolean): Int {
      if (early) { return when (s) { is Dot -> 1; is Line -> 2; }; }
      if (s.size > 1) {
        when (s) { is Dot -> 10; is Line -> 20; }
      } else {
        doubled(when (s) { is Dot -> 3; is Line -> 4; })
      }
    }

    class Namer {
      public name(s: Shape): String { when (s) { is Dot -> "a dot"; is Line -> "a line"; } }
    }

    let dot = shapeOf(1);
    let line = shapeOf(2);
    console.log("${named(dot)} ${named(line)} ${pair(dot, line)} ${pair(line, dot)}");
    console.log("${code(line, true)} ${code(dot, false)} ${code(line, false)} ${new Namer().name(line)}");

```log
dot line dl l
2 6 20 a line
```
