# Functions

Function values: a closure counter that writes a captured `var`, a
`@fun` interface passed and returned, a static method's reference,
core's list members that take functions (`map`, `filter`, `join`, and
`sorted` with a comparator), a closure stored in a class's field, and
closures made in a loop, each capturing that time round's `let`, in a
function and in a generator, which is stepped once past its end; and
loops: `continue` in a `for … of`, and a `for` whose second
declaration reads the first; a local function that calls itself, in a
block that's a `let`'s value and in one that's a function's; and a
nullable `@fun` value held in a local and called after a null check;
and instance extensions, functions marked `@extension("name")` that
`x.name(…)` calls with `x` first, of a builtin type, a generic one, a
nullable one, and a class, whose own method of that name wins where its
parameters fit. The log is what the interpreter gives.

    @fun interface Counter(): Int;
    @fun interface IntOp(x: Int): Int;

    let makeCounter(start: Int): Counter {
      var count = start;
      fn (): Int {
        count += 1;
        count
      }
    }

    let twice(f: IntOp): IntOp {
      fn (x: Int): Int { f(f(x)) }
    }

    let applyTo(f: IntOp, x: Int): Int { f(x) }

    let addOne(x: Int): Int { x + 1 }

    class Scaler {
      public static triple(x: Int): Int { x * 3 }
    }

    class Button(public label: String, public onPress: Counter) {
      public press(): String { "${label} ${onPress()}" }
    }

    let counter = makeCounter(10);
    counter();
    console.log("count ${counter()}");
    let other = makeCounter(0);
    console.log("other ${other()} count ${counter()}");
    let addTwo = twice(addOne);
    console.log("twice ${addTwo(5)} ${applyTo(addOne, 1)}");
    console.log("triple ${applyTo(Scaler.triple, 4)} ${twice(Scaler.triple)(2)}");
    let ns: List<Int> = [5, 2, 8, 1, 4];
    let doubled = ns.map { (n: Int): Int => n * 2 };
    let big = doubled.filter { (n: Int): Boolean => n > 4 };
    console.log(big.join(", ") { (n: Int): String => n.toString() });
    let order = ns.sorted { (a: Int, b: Int): Int => a - b };
    console.log(order.join(" ") { (n: Int): String => "${n}" });
    var total = 0;
    ns.forEach { (n: Int): Void => total += n; };
    console.log("total ${total}");
    let button = new Button("hits", makeCounter(0));
    button.press();
    console.log(button.press());
    let makers = new ListBuilder<Counter>();
    for (var i = 0; i < 3; i += 1) {
      let k = i * 10;
      makers.add(fn (): Int { k });
    }
    console.log(makers.join(" ") { (c: Counter): String => "${c()}" });
    let drive(steps: Int, body: fn (): SafeGenerator<Empty>): Void {
      let g = body();
      for (var s = 0; s < steps; s += 1) { g.nextSafe(); }
    }
    drive(5) { (): GeneratorResult<Empty> extends GeneratorFn =>
      let fs = new ListBuilder<Counter>();
      for (var i = 0; i < 3; i += 1) {
        let j = i * 10;
        fs.add(fn (): Int { j });
        yield();
        fs.add(fn (): Int { j + 1 });
      }
      console.log(fs.join(" ") { (c: Counter): String => "${c()}" });
      yield();
    }
    let sumFrom(xs: List<Int>, least: Int): Int {
      var sum = 0;
      for (let x of xs) {
        if (x < least) { continue; }
        sum += x;
      }
      sum
    }
    let doubles(s: String): Int {
      var n = 0;
      let end = s.end;
      for (var i = String.begin, prev = i; i < end; i = s.next(i)) {
        if (prev < i && s[prev] == s[i]) { n += 1; }
        prev = i;
      }
      n
    }
    console.log("sum ${sumFrom(ns, 4)} doubles ${doubles("bookkeeper")}");
    let climb(x: Int): Int {
      var d = when (x) {
        0 -> do {
          let down(n: Int): Int { if (n <= x) { 0 } else { down(n - 1) + 1 } }
          down(3)
        }
        else -> return -1;
      };
      d += 1;
      when (d) {
        4 -> do {
          let top = d + 1;
          let up(n: Int): Int { if (n >= top) { n } else { up(n + 1) } }
          up(d)
        }
        else -> d
      }
    }
    console.log("climb ${climb(0)} ${climb(1)}");
    class Gate() {
      public var check: IntOp? = null;
      public allows(n: Int): Boolean {
        let c = check;
        c == null || c(n) > 0
      }
      public passed(n: Int): Int {
        let c = check;
        if (c != null) { c(n) } else { n }
      }
    }
    let gate = new Gate();
    console.log("gate ${gate.allows(0)} ${gate.passed(4)}");
    gate.check = fn (x: Int): Int { x - 1 };
    console.log("gate ${gate.allows(1)} ${gate.allows(2)} ${gate.passed(4)}");

    @extension("isPalindrome")
    let stringIsPalindrome(s: String): Boolean {
      var i = String.begin;
      var j = s.end;
      while (i < j) {
        j = s.prev(j);
        if (s[i] != s[j]) { return false }
        i = s.next(i);
      }
      return true
    }
    @extension("orZero") let intOrZero(n: Int?): Int { n ?? 0 }
    @extension("firstOr")
    let listFirstOr<T>(ls: List<T>, d: T): T {
      if (ls.isEmpty) { d } else { ls[0] }
    }
    @extension("padded")
    let stringPadded(s: String, n: Int, fill: String = "."): String {
      var out = s;
      for (var i = 0; i < n; ++i) { out = "${out}${fill}" }
      out
    }
    @extension("passed")
    let gatePassed(g: Gate, s: String): String { "${s} ${g.allows(1)}" }
    let none: Int? = if (gate.allows(0)) { 3 } else { null };
    console.log("${"step on no pets".isPalindrome()} ${"steps".isPalindrome()}");
    console.log("${none.orZero()} ${[4, 5].firstOr(0)} ${["x"].firstOr("-")}");
    console.log("${"ab".padded(2)} ${"ab".padded(1, "*")}");
    console.log("${gate.passed(4)} ${gate.passed("gate")}");

```log
count 12
other 1 count 13
twice 7 2
triple 12 18
10, 16, 8
1 2 4 5 8
total 20
hits 2
0 10 20
0 1 10 11 20 21
sum 17 doubles 3
climb 5 -1
gate true 4
gate false true 3
true false
0 4 x
ab.. ab*
3 gate false
```
