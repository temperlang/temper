# Libraries

What real libraries (Chromarite, Ordable, Hrefine, and Kelpto) use that
the other programs don't, each as small as it can be: a division that
may fail inside an `orelse` in a function that can't bubble, a float
division of constants, which folds, `m[i, j]` by a class's `get`,
nullable values in templates, an `is` check of a call's nullable
result, a private constructor that a static field calls, a function
literal whose call's receiver needs statements first, `<=>` on strings
that are calls' results, `orelse null` as a static method's value, and
`==` by a class's method where a side may be null, and a local set by
an `orelse` that a function literal captures, an `orelse`'s value
whose body has a function literal, which returns only from itself, a string with a
character that changes text's direction, and strings compared with
templates, and a function literal whose body is one `orelse`; list
reads, which C++ needs keep in no order, and calls whose order it keeps
in temporaries, in an `if`'s value, on the right of `&&`, and in a
loop's condition; and an exported generic function that reads a
module's private `let`, which C++ defines in the header; and what
Kelpto needs: a `when` whose `is` checks cover a sealed interface,
without `else`, two classes joined to the interface they share, a `[]`
branch typed by the other, `?.` on `this`'s getter by its bare name, a
private static function that a static field's value calls, a private
static field a getter reads, a field set
in a constructor and again in a branch, a parameter's null default kept
in a field, and an `if`'s number value whose method Rust calls; and
what a library's contextual autoescaping uses:
`@overload`s that take a class, lists, and lists of nullables, a type
parameter's value in a template, an `is` check of a generic class,
`==` by identity on nullable generic values, `[]` and a list literal
passed to a generic function, `[]` to a generic class's constructor, a
`let` typed by a bare `List`, a generic class made through an alias, a
generic interface's default method that sets `this`'s property, which a
class extending it and the interface it extends inherits, an override
of a method that takes a type parameter's value, a trait method's
`String?` given an `if`'s value, `when`s of only `null` and `panic()`,
`orelse continue`, and `--i` in a loop's condition. The log is what
the interpreter gives.

    export let parseGrid(s: String): Grid throws Bubble {
      if (s == "") { bubble() }
      new Grid(1, [s.toFloat64()])
    }

    export class Grid(public ncols: Int, public values: List<Float64>) {
      public get(i: Int, j: Int): Float64 throws Bubble {
        values[i * ncols + j]
      }

      public static parse(s: String): Grid? { parseGrid(s) orelse null }
    }

    export class Casing {
      public name: String;

      @operator("==")
      public eq(other: Casing): Boolean { name == other.name }

      private constructor(name: String) {
        this.name = name;
      }

      public toString(): String { name }

      public static first = new Casing("first");

      private static named(name: String): Casing { new Casing(name) }

      public static last = Casing.named("last");

      private static mark = "#";

      public get marked(): String { "${Casing.mark}${name}" }
    }

    export sealed interface Shape { public get size(): Int; }
    export class Dot(public size: Int) extends Shape {
      public get bigger(): Dot? { if (size < 3) { new Dot(size + 1) } else { null } }
      public get grown(): Int? { bigger?.scaled(10) }
      public scaled(k: Int): Int { size * k }
    }
    export class Line(public size: Int) extends Shape {}

    export class Carry {
      public carrying: Boolean;
      public state: Int;
      public constructor(start: Int) {
        this.carrying = false;
        this.state = 0;
        if (start != 0) {
          this.carrying = true;
          this.state = start;
        }
      }
    }

    export class Tagged(public label: String?) {}

    let tagged(label: String? = null): Tagged { new Tagged(label) }

    let weight(c: Int): Int {
      let base = if (c == 42) { 0 } else { 6 };
      (if (c > 50) { 1 } else { 2 }) + base + c
    }

    let shapeName(s: Shape): String {
      let name = when (s) {
        is Dot -> "dot";
        is Line -> "line";
      };
      name
    }

    let shapeOf(n: Int): Shape {
      let shape = if (n > 1) { new Line(n) } else { new Dot(n) };
      shape
    }

    let sizes(s: Shape): List<Int> {
      let found = when (s) {
        is Dot -> [s.size, s.size];
        else -> [];
      };
      found
    }

    let rows(values: List<Float64>, ncols: Int): Int {
      (values.length / ncols) orelse panic()
    }

    let cubes(grid: Grid): List<Float64> {
      do {
        let more = scaled(grid).values.map { (x): Float64 => x * x * x };
        more
      } orelse panic()
    }

    let each(values: List<Float64>, f: fn (Float64): Void): Void {
      for (var i = 0; i < values.length; i += 1) { f(values[i]); }
    }

    let total(values: List<Float64>): Float64 {
      var sum = 0.0;
      each(values) { x =>
        do {
          let part = x / 2.0;
          sum += part;
        } orelse panic();
      }
      sum
    }

    let half(x: Float64): Float64 {
      do { x / 2.0 } orelse panic()
    }

    let cube(x: Float64): Float64 { x * (1.0 / 4.0) }

    let corner(grid: Grid): Float64 { grid[1, 1] orelse -1.0 }

    let maybe(n: Int): Grid? { if (n > 0) { new Grid(n, [1.0]) } else { null } }

    let scaled(grid: Grid): Grid throws Bubble {
      if (grid.ncols == 0) { bubble() }
      grid
    }

    let mapped(grid: Grid): List<Float64> {
      (scaled(grid) orelse panic()).values.map { x =>
        if (x > 1.5) { x * 2.0 } else { x }
      }
    }

    let byteAt(digits: List<Int>, short: Boolean, c: Int): Int {
      let byte = if (short) {
        digits[c] * 17
      } else {
        digits[2 * c] * 16 + digits[2 * c + 1]
      };
      byte
    }

    let ascending(digits: List<Int>): Boolean {
      digits.length > 1 && digits[0] + 1 == digits[1]
    }

    let firstDrop(digits: List<Int>): Int {
      var i = 1;
      while (i < digits.length && digits[i - 1] <= digits[i]) { i += 1; }
      i
    }

    var ticks = 0;
    let tick(): Int { ticks += 1; ticks }

    let tickIf(short: Boolean): Int {
      let n = if (short) { tick() } else { tick() * 10 + tick() };
      n
    }

    let ticked(): Boolean { ticks > 0 && tick() * 10 + tick() > 0 }

    let tickWhile(): Int {
      var i = 0;
      while (i < 3 && tick() < tick()) { i += 1; }
      i
    }

    let extras = [1];

    export let sizeWith<T>(items: List<T>): Int { items.length + extras.length }

    let grid = new Grid(2, [1.0, 2.0, 3.0, 4.0]);
    console.log("${rows(grid.values, 2)} ${half(3.0)} ${cube(8.0)}");
    console.log(corner(grid).toString());
    console.log(total(grid.values).toString());
    let text(n: Int): String? { if (n > 0) { "x" } else { null } }
    let count(n: Int): Int? { if (n > 0) { n } else { null } }
    let none = text(0);
    let some = count(3);
    console.log("${none} ${some} ${maybe(0)?.ncols}");
    console.log("${maybe(1) is Grid} ${maybe(0) is Grid} ${Grid.parse("") == null}");
    console.log(Casing.first.toString());
    console.log(mapped(grid).join(",") { x => x.toString() });
    console.log(cubes(grid).join(",") { x => x.toString() });
    let casing(n: Int): Casing? { if (n > 0) { Casing.first } else { null } }
    console.log("${casing(1) == Casing.first} ${casing(0) == Casing.first} ${casing(0) != casing(0)}");
    let captured(s: String): String {
      let found = parseGrid(s) orelse null;
      [1, 2].map { (x): Boolean => found == null }.join(",") { (b): String => b.toString() }
    }
    console.log("${captured("")} ${captured("2.5")}");
    let bidi(): String { "look\u{202e}out" }
    console.log((bidi() != "lookout").toString());
    let word(n: Int): String { if (n > 1) { "b" } else { "a" } }
    console.log("${word(1) <=> word(2)} ${word(2) <=> word(1)} ${word(2) <=> word(3)}");
    let named = word(2);
    console.log((named == "${word(3)}" && "${named}!" != named).toString());
    let digits = [1, 2, 4, 3];
    console.log("${byteAt(digits, true, 1)} ${byteAt(digits, false, 0)} ${ascending(digits)} ${firstDrop(digits)} ${sizeWith(digits)}");
    console.log("${tickIf(true)} ${tickIf(false)} ${ticked()} ${tickWhile()}");
    console.log("${shapeName(shapeOf(1))} ${shapeName(shapeOf(2))} ${sizes(shapeOf(1)).length} ${sizes(shapeOf(2)).length} ${new Dot(1).grown} ${new Dot(3).grown} ${Casing.last.toString()} ${Casing.first.marked}");
    let carry = new Carry(3);
    console.log("${carry.carrying} ${carry.state} ${new Carry(0).carrying} ${tagged().label} ${tagged("x").label} ${weight(42)} ${weight(60)}");

    export class Word(public text: String) {}

    export class Joiner {
      public var out: String = "";
      @overload("add")
      public addWord(w: Word): Void { out = "${out}w${w.text}"; }
      @overload("add")
      public addFlags(x: List<Boolean>): Void { out = "${out}f${x.length}"; }
      @overload("add")
      public addTexts(x: List<String>): Void { out = "${out}t${x.length}"; }
      @overload("add")
      public addMaybes(x: List<String?>): Void { out = "${out}m${x.length}"; }
    }

    export interface Shown {
      public toString(): String;
    }

    export class Tag(public name: String) extends Shown {
      public toString(): String { "<${name}>" }
    }

    export class Held<T extends Shown>(public item: T, public next: Held<T>?) {
      public toString(): String { "Held(${item})" }
    }

    let TagHeld = Held<Tag>;

    export interface Effect<T> {}
    export class Plain<T>(public text: String) extends Effect<T> {}
    export class Happened<T>(public event: T) extends Effect<T> {}
    export class Bundle<T>(public effects: List<Effect<T>>, public first: T) {}

    let joinEffects<T>(into: ListBuilder<Effect<T>>, more: List<Effect<T>>): Int {
      for (let e of more) { into.add(e); }
      into.length
    }

    export let effectsOf<T>(text: String, event: T, other: T): String {
      let into = new ListBuilder<Effect<T>>();
      joinEffects(into, []);
      joinEffects(into, [new Plain<T>(text)]);
      joinEffects(into, [new Happened<T>(event), new Plain<T>("!"), new Happened<T>(other)]);
      var shown = "";
      for (let e of into.toList()) {
        if (e is Plain<T>) { shown = "${shown}${e.text}"; } else { shown = "${shown}*"; }
      }
      shown
    }

    export let sameHeld<T extends Shown>(a: Held<T>?, b: Held<T>?): Boolean { a == b }

    export interface Stepper<T> {
      step(): List<T>;
    }

    export interface Counting<T> extends Stepper<T> {
      public get count(): Int;
      public set count(c: Int): Void;
      public made(): T;
      step(): List<T> {
        this.count = count + 1;
        [made()]
      }
    }

    export class Ticks extends Counting<String> {
      private var n: Int = 0;
      public get count(): Int { n }
      public set count(c: Int): Void { n = c; }
      public made(): String { "tick${n}" }
    }

    export interface Sink<T> {
      take(e: T): Int;
    }

    export class Names extends Sink<Tag> {
      public take(e: Tag): Int { if (e.name == "abc") { 3 } else { 0 } }
    }

    export interface Escapes {
      escape(s: String?): String;
      escapeFlag(b: Boolean): String { escape(if (b) { "T" } else { "F" }) }
    }

    export class Quoting extends Escapes {
      public escape(s: String?): String { "'${s ?? "null"}'" }
    }

    let flags: List = do {
      let b = new ListBuilder<Boolean>();
      b.add(true);
      b.add(false);
      b.toList()
    };

    let problem(n: Int): String? {
      let p: String? = when (n) {
        0 -> null;
        else -> panic();
      };
      p
    }

    let message(n: Int): String? {
      let p = when (n) {
        0 -> null;
        1 -> "one";
        2 -> null;
        else -> panic();
      };
      p
    }

    let sumParsed(xs: List<String>): Int {
      var total = 0;
      for (var i = 0; i < xs.length; ++i) {
        let x = xs[i];
        let n = x.toInt32() orelse continue;
        total += n;
      }
      for (let x of xs) {
        let m = x.toInt32() orelse do { continue };
        total += m * 10;
      }
      total
    }

    let backwards(xs: List<Int>): Int {
      var t = 0;
      for (var i = xs.length; --i >= 0;) {
        t = t * 10 + xs[i];
      }
      t
    }

    let joiner = new Joiner();
    joiner.add(new Word("a"));
    joiner.add([true]);
    joiner.add(["x", "y"]);
    let maybes: List<String?> = [null, "z"];
    joiner.add(maybes);
    console.log(joiner.out);
    let held = new TagHeld(new Tag("a"), null);
    let other = new Held<Tag>(new Tag("b"), held);
    let nobody: Held<Tag>? = null;
    console.log("${held} ${other.next?.item ?? new Tag("none")} ${sameHeld(held, held)} ${sameHeld(held, other)} ${sameHeld(nobody, nobody)} ${sameHeld(nobody, held)}");
    console.log("${effectsOf("ab", 1, 2)} ${new Bundle<String>([], "2").first} ${flags.length}");
    let counter = new Ticks();
    let stepper: Counting<String> = counter;
    stepper.step();
    console.log("${stepper.step()[0]} ${counter.count} ${new Names().take(new Tag("abc"))} ${new Quoting().escapeFlag(true)}");
    console.log("${problem(0) ?? "none"} ${message(1) ?? "none"} ${message(2) ?? "none"} ${sumParsed(["1", "x", "2"])} ${backwards([1, 2, 3])}");

```log
2 1.5 2.0
4.0
5.0
null 3 null
true false true
first
1.0,4.0,6.0,8.0
1.0,8.0,27.0,64.0
true false false
true,true false,false
true
-1 1 0
true
34 18 true 3 5
1 23 true 3
dot line 2 0 20 null last #first
true 3 false null x 44 67
waf1t2m2
Held(<a>) <a> true false true false
ab*!* 2 2
tick2 2 3 'T'
none one none 33 321
```
