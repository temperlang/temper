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
