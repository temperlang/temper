# Generics

Generic functions, a generic class with a bounded type parameter and a
generic method, a generic interface, and core's generic types: list
literals, `Listed`'s and `List`'s members, `ListBuilder`, `Pair`, `Map`,
and `MapBuilder`, with nullables mixed in; and generic functions and a
method with a type parameter that no parameter's type mentions, which
the frontend binds to its bound unless the call says, and which Rust,
C#, and C++ then spell out; and an exported generic function that calls
a private one. The log is what the interpreter gives.

    export interface Holder<T> {
      public get held(): T;
    }

    export class Box<T>(public value: T) extends Holder<T> {
      public get held(): T { value }
      public pairWith(other: Int): Pair<T, Int> { new Pair(value, other) }
    }

    export class Named(public name: String) {}

    export class Counter(public var n: Int) {}

    export class Shelf<T extends Named>(public items: List<T>) {
      public firstName(): String? {
        if (items.isEmpty) { null } else { items[0].name }
      }
      public tally<TName extends Named>(extra: Int): Int { items.length + extra }
    }

    export interface Scene {}
    export class Stage extends Scene {}

    // `S` appears nowhere, and `T` only in the body.
    let tagged<S extends Scene, T>(x: T): T { x }
    let countOf<T>(n: Int): Int {
      let b = new ListBuilder<T>();
      b.length + n
    }
    let relayed<S extends Scene, T>(x: T): T { tagged<S, T>(x) }

    // A private function that an exported generic one calls, which C++
    // declares in the header that defines the generic one.
    let noneIn(length: Int): Boolean { length == 0 }

    export let first<T>(items: Listed<T>, fallback: T): T {
      if (noneIn(items.length)) { fallback } else { items[0] }
    }

    let held<T>(h: Holder<T>): T { h.held }

    let total(ns: List<Int>): Int {
      var sum = 0;
      for (var i = 0; i < ns.length; i += 1) {
        sum += ns[i];
      }
      sum
    }

    let ns: List<Int> = [1, 2, 3];
    let none: List<Int> = [];
    console.log("${total(ns)} ${first(ns, 0)} ${first(none, -1)}");
    console.log(first(["a", "b"], "z"));
    let box = new Box<String>("boxed");
    console.log(held(box));
    let pair = box.pairWith(7);
    console.log("${pair.key} ${pair.value}");
    let shelf = new Shelf([new Named("ann"), new Named("bo")]);
    console.log(shelf.firstName() ?? "none");
    let noNames: List<Named> = [];
    let empty = new Shelf(noNames);
    console.log(empty.firstName() ?? "none");
    let n = shelf.tally(1) + shelf.tally<Named>(2);
    console.log("${tagged(4)} ${tagged("t")} ${countOf(3)} ${relayed<Stage, Int>(5)} ${n}");
    let builder = new ListBuilder<String>();
    builder.add("x");
    builder.add("y");
    builder.add("w", 0);
    console.log("${builder.length} ${builder[0]} ${builder.removeLast()}");
    let built = builder.toList();
    console.log("${built.length} ${built.getOr(5, "?")} ${built.slice(1, 2)[0]}");
    let m = new Map([new Pair("a", 1), new Pair("b", 2)]);
    console.log("${m["b"] orelse -1} ${m["c"] orelse -1} ${m.has("a")} ${m.length}");
    let mb = new MapBuilder<String, Int?>();
    mb.set("q", 5);
    mb.set("n", null);
    console.log("${mb.getOr("q", 0) ?? -2} ${mb.getOr("n", 0) ?? -2} ${mb.has("n")}");
    console.log((mb.remove("q") orelse 0)?.toString() ?? "null");
    console.log(mb.keys().length.toString());
    // Compound assignment and increments on indexed targets read the
    // builder and the index once. In a function, as top-level statements
    // run in dependency order, and `at++` assigns `at`.
    let indexed(): Void {
      let counts = new ListBuilder<Int>();
      counts.add(1);
      counts.add(10);
      var at = 0;
      counts[at] += 4;
      counts[1] *= 3;
      let was = counts[at++]++;
      let pre = ++counts[at];
      let post = counts[0]--;
      let down = --counts[0];
      console.log("${was} ${pre} ${post} ${down} ${at}");
    }
    indexed();
    // Two increments in one expression each keep their own temporary
    // and run in order, on locals, fields, and indexes.
    let both(): Void {
      var x = 1;
      var y = 10;
      let locals = x++ + y++;
      let p = new Counter(100);
      let q = new Counter(1000);
      let fields = p.n++ + q.n++;
      let same = p.n++ + p.n++;
      let a = new ListBuilder<Int>();
      a.add(3);
      let b = new ListBuilder<Int>();
      b.add(30);
      var i = 0;
      let items = a[i]++ + b[i]++;
      console.log("${locals} ${x} ${y} ${fields} ${same} ${p.n} ${q.n} ${items} ${a[0]} ${b[0]}");
    }
    both();
    let tally = new MapBuilder<String, Int>();
    tally.set("k", 2);
    let bump(key: String): Int throws Bubble {
      tally[key] -= 1;
      let before = tally[key]++;
      let after = ++tally[key];
      before + after
    }
    console.log("${bump("k") orelse -1} ${bump("z") orelse -1} ${tally["k"] orelse -1}");
    // Java's boxes compare by value (Kotlin's compare references, so
    // two 1000s differ).
    let firstSame(a: List<Int>, b: List<Int>): Boolean { a[0] == b[0] }
    console.log("${firstSame([1000], [1000])} ${firstSame([1000], [7])}");

```log
6 1 -1
a
boxed
boxed 7
ann
none
4 t 3 5 7
3 w y
2 ? x
2 -1 true 2
5 -2 true
5
1
5 31 6 4 1
11 2 11 1100 203 103 1001 33 4 31
4 -1 3
true false
```
