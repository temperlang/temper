# Jump values

`return`, `break`, and `continue` as values, of type `Never`, so they
fit anywhere and need no parentheses as an operator's operand: after
`??`, `orelse`, and `||`, as an `if`'s branch, in arguments, list
elements, and string holes, and as a whole `let`'s value, after which
nothing runs. What runs before the jump runs first, in order. The log
is what the interpreter gives.

    var steps = 0;
    let step(n: Int): Int {
      steps += 1;
      console.log("step ${n}");
      n
    }
    let pair(a: Int, b: Int): Int { a * 10 + b }
    let parse(s: String): Int throws ParseError { s.toInt32() }

    let next(y: Int?): Int? {
      let x = y ?? return null;
      x + 1
    }

    let sumPresent(ys: List<Int?>): Int {
      var sum = 0;
      for (var i = 0; i < ys.length; ++i) {
        sum += ys[i] ?? continue;
      }
      sum
    }

    let sumUntilBad(words: List<String>): Int {
      var sum = 0;
      var i = 0;
      while (i < words.length) {
        sum += parse(words[i]) orelse break;
        i += 1;
      }
      sum
    }

    let parsedOr(s: String): Int { parse(s) orelse return -1 }

    // A `continue` in the body runs the step first, a copy of it, with
    // its own locals (the `??`'s).
    let after(i: Int): Int? { if (i < 3) { i + 1 } else { null } }
    let stepped(): String {
      var seen = "";
      for (var i = 0; i < 6; i = after(i) ?? 9) {
        if (i == 1) { continue; }
        seen = "${seen}${i}";
      }
      seen
    }

    let paired(y: Int?): Int { pair(step(1), y ?? return -2) }

    let listed(y: Int?, z: Int?): List<Int> { [1, (y ?? return []), 3, z ?? return [9]] }

    let shown(y: Int?): String { "y=${y ?? return "none"}" }

    let both(t: Boolean, y: Int?): String {
      let ok = t && y != null || return "no";
      "ok ${ok}"
    }

    let sign(n: Int): String {
      pair(0, if (n > 0) { 1 } else if (n < 0) { -1 } else { return "zero" }).toString()
    }

    let firstWord(text: String?): Void {
      let t = text ?? return;
      console.log("first ${t.split(" ")[0]}");
    }

    let early(): Int {
      let z: String = return 7;
      console.log("never ${z}");
      8
    }

    let partly(): Int { pair(step(2), return step(3)) }

    // Jumps from nested blocks in a `do` block's value: `continue` and
    // `break` in a `for` loop with a step, `return`, and the block's own
    // value where it doesn't jump.
    let doJumps(ys: List<Int?>): String {
      var seen = "";
      for (var i = 0; i < ys.length; ++i) {
        let v = ys[i] ?? do {
          seen = "${seen}a";
          if (i == 1) { continue; }
          seen = "${seen}b";
          break;
        };
        seen = "${seen}${v}";
      }
      seen
    }

    let doReturn(y: Int?, z: Int): String {
      let v = y ?? do {
        if (z > 0) {
          if (z > 5) { return "big"; }
          return "pos";
        }
        0
      };
      "v${v}"
    }

    // Arguments before one that may jump run first; those after it only
    // when it doesn't.
    let trio(a: Int, b: Int, c: Int): Int { a + b + c }
    let ordered(ys: List<Int?>): Int {
      var sum = 0;
      for (var i = 0; i < ys.length; ++i) {
        sum += trio(step(10 + i), ys[i] ?? do { step(20 + i); continue }, step(30 + i));
      }
      sum
    }

    // A `do` inside a `do`, breaking the labeled loop.
    let nested(xs: List<Int?>, ys: List<Int?>): String {
      var seen = "";
      outer: for (var i = 0; i < xs.length; ++i) {
        let v = xs[i] ?? do { ys[i] ?? do { break outer; } };
        seen = "${seen}${v}";
      }
      seen
    }

    // Jumps from an inner loop out of the labeled outer loop: `?? break
    // outer`, and `continue outer` in a `do` block, which runs the outer
    // loop's step first; and an inner loop in a `do` block's value.
    let gridded(rows: List<List<Int?>>): String {
      var seen = "";
      outer: for (var i = 0; i < rows.length; ++i) {
        let row = rows[i];
        for (var j = 0; j < row.length; ++j) {
          let v = row[j] ?? break outer;
          let w = (if (v < 0) { null } else { v }) ?? do { seen = "${seen}-"; continue outer };
          seen = "${seen}${w}";
        }
        seen = "${seen}|";
      }
      seen
    }

    let firstBig(rows: List<List<Int?>>, keys: List<Int?>): String {
      var seen = "";
      outer: for (var i = 0; i < rows.length; ++i) {
        let k = keys[i] ?? do {
          let row = rows[i];
          var first = -1;
          for (var j = 0; j < row.length; ++j) {
            let v = row[j] ?? continue outer;
            if (v > 5) { break outer; }
            if (v > 2) { first = v; break; }
          }
          first
        };
        seen = "${seen}${k},";
      }
      seen
    }

    // In an `orelse`'s fallback and an `if`'s branch.
    let viaOrelse(words: List<String>): String {
      var seen = "";
      for (var i = 0; i < words.length; ++i) {
        let n = parse(words[i]) orelse do {
          seen = "${seen}!";
          if (i > 2) { break; }
          continue;
        };
        seen = "${seen}${n}";
      }
      seen
    }

    let viaIf(n: Int): String {
      let s = if (n > 0) { "pos" } else {
        if (n < -5) { return "low"; }
        "neg"
      };
      "s=${s}"
    }

    console.log("${next(1)} ${next(null)}");
    console.log("${sumPresent([1, null, 3])} ${sumUntilBad(["1", "2", "x", "4"])}");
    console.log("${parsedOr("12")} ${parsedOr("x")} ${stepped()}");
    console.log("${paired(3)} ${paired(null)}");
    console.log("${listed(2, 4).length} ${listed(null, 4).length} ${listed(2, null)[0]}");
    console.log("${shown(5)} ${shown(null)}");
    console.log("${both(true, 1)} ${both(true, null)} ${both(false, 1)}");
    console.log("${sign(4)} ${sign(-4)} ${sign(0)}");
    firstWord("hello there");
    firstWord(null);
    console.log("${doJumps([1, null, 2, null, 3])} ${doReturn(1, 0)} ${doReturn(null, 9)} ${doReturn(null, 1)} ${doReturn(null, 0)}");
    console.log(ordered([1, null]).toString());
    console.log("${nested([1, null, null, 4], [0, 7, null, 0])} ${viaOrelse(["1", "x", "2", "y", "z", "3"])}");
    console.log("${gridded([[1, 2], [3, -1, 4], [5, null, 6], [7]])} ${firstBig([[1, 3], [null, 1], [4], [9], [1]], [7, null, null, null, null])}");
    console.log("${viaIf(1)} ${viaIf(-1)} ${viaIf(-9)}");
    console.log("${early()} ${partly()} ${steps}");

```log
2 null
4 3
12 -1 023
step 1
step 1
13 -2
4 0 9
y=5 none
ok true no no
1 -1 zero
first hello
1a2ab v1 big pos v0
step 10
step 30
step 11
step 21
41
17 1!2!
12|3-5 7,4,
s=pos s=neg low
step 2
step 3
7 3 8
```
