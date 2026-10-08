# Top-level order functional test

Top-level declarations are order-independent, but a reassignment of a
`var` that is already initialized keeps its place relative to the
statements that read it.

    var w = 0;
    console.log("before ${w}");
    w += 1;
    console.log("mid ${w}");
    w = 99;
    console.log("after ${w}");

An assignment after a loop does not move above the loop.

    var i = 0;
    var n = 0;
    while (i < 3) {
      n += 1;
      i += 1;
    }
    i = 99;
    console.log("loop ran ${n} times");

A declaration can still use one written below it.

    console.log("half = ${half}");
    let half = one / two;
    let two = one + one;
    let one = 1.0;

A `var` declared without an initializer is initialized by its first
assignment. Later assignments are reassignments and stay in order.

    var late: Int;
    late = 7;
    console.log("late = ${late}");
    late = 8;
    console.log("late = ${late}");

A read written above a `var`'s declaration is a forward reference. It sees
the `var` after all top-level assignments to it, as the float tests rely on.

    console.log("early = ${early}");
    var early = 1;
    early = early + 1;

A function body reads a `var` when it is called, not where it is defined.

    var v = 1;
    let readV(): Int { v }
    console.log("readV() = ${readV()}");
    v = 2;
    console.log("readV() = ${readV()}");

Calling a function reads what its body reads. `k` is used above `u = 1`,
and its initializer calls a function that reads `u`, so like a direct
read of `u` it stays below `u = 1`.

    var u = 0;
    console.log("k = ${k}");
    u = 1;
    let k = readU();
    let readU(): Int { u }

Expected output:

```log
before 0
mid 1
after 99
loop ran 3 times
half = 0.5
late = 7
late = 8
early = 2
readV() = 1
readV() = 2
k = 1
```
