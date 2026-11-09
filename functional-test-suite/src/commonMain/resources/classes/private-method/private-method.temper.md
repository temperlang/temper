# Private Method Functional Test

Tests invoking a private method from within a public method.

    class C {
        private helper(x: Int): Int { x + 1 }
        public method1(x: Int): Int { helper(x * 2) }
        public method2(x: Int): Int { helper(x * 3) }
    }

    let c = new C();

    console.log("method1(10)=${ c.method1(10) }");
    console.log("method2(10)=${ c.method2(10) }");

Expected output:

```log
method1(10)=21
method2(10)=31
```

## Other instances

Code inside a class can also use the private members of other instances
of that class, not just `this`, as in Java, C#, and TypeScript. That's
what makes methods like `plus` and `==` easy to write without exposing
their parts.

    class Money(
      private cents: Int,
    ) {
      private var spent: Int = 0;
      private whole(): Int { cents / 100 }
      private get rounded(): Int { (cents + 50) / 100 }

      public plus(other: Money): Money { new Money(cents + other.cents) }
      public sameWhole(other: Money): Boolean { whole() == other.whole() }
      public sameRounded(other: Money): Boolean { rounded == other.rounded }
      public spendFrom(other: Money): Void {
        other.spent = other.spent + 1;
      }
      public get timesSpent(): Int { spent }
      public toString(): String { "${cents} cents" }
    }

    let a = new Money(150);
    let b = new Money(175);
    console.log("plus: ${a.plus(b).toString()}");
    console.log("same whole: ${a.sameWhole(b).toString()}");
    console.log("same rounded: ${a.sameRounded(b).toString()}");
    a.spendFrom(b);
    a.spendFrom(b);
    console.log("b spent: ${b.timesSpent}");

```log
plus: 325 cents
same whole: true
same rounded: true
b spent: 2
```

## Members named alike in other types

Inside a class, a call on a value of another type finds that type's
method, even when the class has a property of the same name.

    interface Sink { end(): Int; }
    class TenSink extends Sink { public end(): Int { 10 } }
    class Note(public body: String) {
      public text(more: String): Note { new Note("${body}${more}") }
    }

    class Label {
      private end: Int;
      public constructor(end: Int) { this.end = end; }
      public get text(): String { "label" }
      public close(sink: Sink): Int { sink.end() + end }
      public noted(more: String): String { new Note(text).text(more).body }
    }

    let label = new Label(1);
    console.log("close: ${label.close(new TenSink())}");
    console.log("noted: ${label.noted("!")}");

```log
close: 11
noted: label!
```

## Other instances in constructors

A constructor can read the fields of other instances too, even before
it has set its own: `parent.depth` and `children[i].size` below aren't
the new object's `depth` and `size`.

    class Tally {
      public depth: Int;
      private size: Int;
      public constructor(size: Int, parent: Tally?, children: List<Tally>) {
        this.depth = if (parent == null) { 0 } else { parent.depth + 1 };
        var sum = size;
        for (var i = 0; i < children.length; ++i) { sum += children[i].size; }
        this.size = sum + this.depth;
      }
      public get total(): Int { size }
    }

    let leaf = new Tally(2, null, []);
    let mid = new Tally(3, leaf, [leaf, leaf]);
    let top = new Tally(1, mid, [mid, leaf]);
    console.log("depths: ${leaf.depth} ${mid.depth} ${top.depth}");
    console.log("totals: ${leaf.total} ${mid.total} ${top.total}");

```log
depths: 0 1 2
totals: 2 8 13
```
