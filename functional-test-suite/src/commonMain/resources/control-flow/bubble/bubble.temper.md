# Bubble control flow and typing

Verify we can handle bubble calls as return types. Try out immediate bubble
return. Also use `Int` here for possibly value types in some backends, in case
that makes things interesting.

    let more(x: Int?): Int throws Bubble { bubble() }

And also conditional cases that are likely to be translated to assignments.

    let something(x: String?): String throws Bubble {

Code here is a bit silly, because `x as String` achieves the same effect, but
this form exercises more constructs, unless we optimize it out at some point.

      when (x) {
        is String -> x;
        else -> bubble();
      }
    }

    let report(x: String?): Void {
      let message = something(x) orelse "null";
      let suffix = more(do { x as String; 1 } orelse 0).toString() orelse "";
      console.log("${message}${suffix}");
    }

Try it out.

    report("hi");
    report(null);

```log
hi
null
```

## Bubble juggling

We had some problems with Lua dropping code in this function. Also, we include
logging to avoid inlining:

    let calcRows(cols: Int, total: Int): Int throws Bubble {
      console.log("calcRows(${cols}, ${total})");
      if (total % cols != 0) { bubble() }
      total / cols
    }

    console.log(calcRows(3, 6).toString());

```log
calcRows(3, 6)
2
```

## A loop whose condition bubbles

The frontend evaluates a call with constant arguments while compiling. When a
loop's condition bubbled during that evaluation, the interpreter unwound to
the `orelse` and then popped the loop a second time, from an empty stack, so
the build crashed with an IndexOutOfBoundsException instead of using the
`orelse`.

    let countBelow(s: String): Int throws Bubble {
      var i = 0;
      while (i < s.toInt32()) { i += 1; }
      i
    }
    console.log((countBelow("abc") orelse -1).toString());

```log
-1
```

## Folding through `orelse`

Folding a call with constant arguments ran the callee in the interpreter, and
an `orelse` there took its fallback for things that aren't bubbles: a `break`,
`continue`, or `return` in a loop, before nested blocks are flattened, and an
`==` not yet expanded. So the wrong value was baked into the output.

    let firstOver(n: Int): Int throws Bubble {
      var i = 0;
      while (i < 9) {
        i += 1;
        if (i > n) { break; }
      }
      i
    }
    let sumSkipping(n: Int): Int throws Bubble {
      var sum = 0;
      for (var i = 0; i < 5; ++i) {
        if (i == n) { continue; }
        sum += i;
      }
      sum
    }
    let tenTimes(n: Int): Int throws Bubble {
      for (var i = 0; i < 9; ++i) {
        if (i == n) { return i * 10; }
      }
      bubble()
    }
    let isNone(r: Int?): Boolean { r == null }

    console.log("${firstOver(1) orelse -1} ${isNone(firstOver(1) orelse null)}");
    let viaOrElse(n: Int): String {
      "${firstOver(n) orelse -1} ${sumSkipping(n) orelse -1} ${tenTimes(n) orelse -1}"
    }
    console.log(viaOrElse(2));
    console.log(viaOrElse(12));

```log
2 false
3 8 20
9 10 -1
```

## Match miscellany

This code doesn't have anything to do with bubbling, but it failed in Lua, and
some code above uses `match`, so I'll just sneak this in here for now. Anyway,
Lua was dropping match branches after the 2nd and before the `else` at the end.

    let branch(a: Int, b: Int): Void {
      let c = when (a) {
        0 -> when (b) {
          0 -> "00";
          1 -> "01";
          2 -> "02";
          else -> "0x";
        }
        1 -> "1x";
        2 -> "2x";
        3 -> "3x";
        else -> "xx";
      };
      console.log("branch(${a}, ${b}) -> ${c}");
    }

    branch(0, 2);
    branch(2, 0);

```log
branch(0, 2) -> 02
branch(2, 0) -> 2x
```
