# Classes

Classes and interfaces: fields, constructors (made from a class's header,
or its own), methods, getters and setters, a setter with no getter
(assigned as a value too), static fields and functions,
private members read on another instance, a `@noProperty` input that
initializers read, though a getter and setter share its name, interfaces
with abstract and default members, `@auto("==")` and `@operator` methods
(`==` with a nullable side, in a `when`, with `this`, and on a generic
class), `toString`, `is`, `as`, and narrowing. Calls chain on objects
that methods return, which C++ must keep alive. The log is what the
interpreter gives.

    export interface Shape {
      public get area(): Int;
      public describe(): String { "shape of area ${area}" }
    }

    export class Rect(public w: Int, public h: Int) extends Shape {
      public static unit = new Rect(1, 1);
      public get area(): Int { w * h }
      public toString(): String { "Rect(${w}, ${h})" }
      public scaled(k: Int): Rect { new Rect(w * k, h * k) }
    }

    export class Square extends Shape {
      private side: Int;
      public constructor(side: Int) {
        this.side = side;
      }
      public get area(): Int { side * side }
      public describe(): String { "square of side ${side}" }
    }

    class Counter {
      public var count: Int = 0;
      private var hidden: Int = 0;
      public get doubled(): Int { count * 2 }
      public set doubled(value: Int) { count = value - count; }
      public set added(value: Int) { count = count + value; }
      public bump(): Counter {
        count = count + 1;
        hidden = hidden + 1;
        this
      }
      public same(other: Counter): Boolean { hidden == other.hidden }
      public static zero(): Counter { new Counter() }
    }

    @auto("==")
    class Point(public x: Int, public y: Int) {}

    class Loose(public n: Int) {
      @operator("==")
      public eq(other: Loose): Boolean { n == other.n || n == -other.n }
      public mirrors(other: Loose): Boolean { other == this && this == other }
    }

    let maybeLoose(n: Int): Loose? { if (n == 0) { null } else { new Loose(n) } }

    class Boxed<T>(public value: T, public tag: Int) {
      @operator("==")
      public eq(other: Boxed<T>): Boolean { tag == other.tag }
      public sameAs(other: Boxed<T>): Boolean { this == other && other == this }
    }

    @auto("==")
    class Holder(public loose: Loose, public tag: String) {}

    class Version(public major: Int, public minor: Int) {
      @operator("<=>")
      public compareTo(other: Version): Int {
        if (major != other.major) { major <=> other.major } else { minor <=> other.minor }
      }
    }

    class Stock(@noProperty items: List<Int>, public count: Int = items.length) {
      private var itemsVar: List<Int> = items;
      public get items(): List<Int> { itemsVar }
      public set items(value: List<Int>) { itemsVar = value; }
      public size: Int = items.length;
      public sizeNow(): Int { items.length }
    }

    let shape(x: Shape): Shape { x }

    let r = new Rect(2, 3);
    console.log(r.toString());
    console.log(r.area.toString());
    let s = shape(r);
    console.log(s.describe());
    let q = shape(new Square(4));
    console.log(q.describe());
    console.log(q.area.toString());
    console.log(r.scaled(2).scaled(3).area.toString());
    console.log(Rect.unit.scaled(5).toString());
    let c = Counter.zero();
    c.bump().bump();
    console.log(c.count.toString());
    c.doubled = 10;
    console.log(c.doubled.toString());
    console.log(c.same(new Counter().bump().bump()).toString());
    c.added = 5;
    let added = c.added = c.added = 2;
    console.log("${added} ${c.count}");
    console.log("${new Point(1, 2) == new Point(1, 2)} ${new Point(1, 2) != new Point(2, 1)}");
    console.log("${new Holder(new Loose(3), "a") == new Holder(new Loose(-3), "a")}");
    let loose = new Loose(2);
    console.log("${loose.mirrors(new Loose(-2))} ${maybeLoose(-2) == loose} ${loose == maybeLoose(0)} ${maybeLoose(0) != loose}");
    let matched = when (maybeLoose(-2)) {
      loose -> "loose";
      else -> "other";
    };
    console.log(matched);
    let b1 = new Boxed("x", 1);
    let b3 = new Boxed("z", 2);
    console.log("${b1.value} ${b1 == new Boxed("y", 1)} ${b1 != b3} ${b1.sameAs(new Boxed("y", 1))} ${b3.sameAs(b1)}");
    console.log("${new Version(1, 2) < new Version(1, 3)} ${new Version(2, 0) >= new Version(1, 9)}");
    console.log("${q is Square} ${s is Square}");
    let sq = q as Square;
    console.log(sq.area.toString());
    if (s is Rect) {
      console.log(s.w.toString());
    }
    let st = new Stock([4, 5]);
    st.items = [1, 2, 3];
    console.log("${st.count} ${st.size} ${st.sizeNow()} ${st.items.length}");

```log
Rect(2, 3)
6
shape of area 6
square of side 4
16
216
Rect(5, 5)
2
16
true
2 17
true true
true
true true false true
loose
x true true true false
true true
true false
16
2
2 2 3 3
```

## Equality, every way round

A table of `==` and `!=` on `Loose`, whose `@operator("==")` method
counts `n` and `-n` the same, for each pair of `null`, 1, -1, and 2,
through function literals: `T` where they're equal.

    let compared(eq: fn (Loose?, Loose?): Boolean): String {
      let ns = [0, 1, -1, 2];
      let out = new ListBuilder<String>();
      for (let a of ns) {
        for (let b of ns) {
          out.add(if (eq(maybeLoose(a), maybeLoose(b))) { "T" } else { "f" });
        }
      }
      out.join("") { (s: String): String => s }
    }
    console.log(compared { (a: Loose?, b: Loose?): Boolean => a == b });
    console.log(compared { (a: Loose?, b: Loose?): Boolean => a != b });
    console.log(compared { (a: Loose?, b: Loose?): Boolean => b == a });
    console.log(compared(fn (a: Loose?, b: Loose?): Boolean {
      if (a == null) { return false; }
      if (b == null) { return false; }
      a.mirrors(b)
    }));

```log
TffffTTffTTffffT
fTTTTffTTffTTTTf
TffffTTffTTffffT
fffffTTffTTffffT
```

## Types as values

A builtin type as a value is typed `Type`, each backend's own value for
it, as `Void.class` in Java.

    let typeNamed(n: Int): Type {
      when (n) {
        0 -> Void;
        1 -> Boolean;
        2 -> Int;
        3 -> Int64;
        4 -> Float64;
        else -> String;
      }
    }
    let same(t: Type): Type { t }
    let favorite: Type = same(Int64);
    var named = 0;
    for (var i = 0; i < 6; ++i) {
      typeNamed(i);
      named += 1;
    }
    console.log("${named} types");

```log
6 types
```

## Overloads

A method marked `@overload("append")` answers to `append` too, when the
class has no `append` of its own: of those whose parameters take the
arguments, the most specific, as `appendRect` before `appendShape` for
a `Rect`. A tagged template's holes call `append`. Each backend calls
the method by its own name, as Kotlin's do.

    class Sql {
      private parts: ListBuilder<String> = new ListBuilder<String>();
      public appendSafe(s: String): Void { parts.add(s); }
      @overload("append")
      public appendString(x: String?): Void {
        parts.add(if (x == null) { "NULL" } else { "'${x}'" });
      }
      @overload("append")
      public appendInt(x: Int): Void { parts.add(x.toString()); }
      @overload("append")
      public appendShape(x: Shape): Void { parts.add("shape ${x.area}"); }
      @overload("append")
      public appendRect(x: Rect): Void { parts.add("rect ${x.toString()}"); }
      public get accumulated(): String { parts.join("") { (p: String): String => p } }
    }

    let sql = new Sql();
    sql.append("it");
    sql.append(null);
    sql.append(3);
    sql.append(new Square(2));
    sql.append(new Rect(1, 2));
    console.log(sql.accumulated);
    let seven = 7;
    console.log(Sql"SELECT ${seven}, ${"x"} FROM ${new Rect(2, 2)}");

```log
'it'NULL3shape 4rect Rect(1, 2)
SELECT 7, 'x' FROM rect Rect(2, 2)
```
