# Operators

Subset A's operators and control flow, beyond what the functional tests
that translate so far use: `Int32` wrapping, shifts, bitwise and
three-way comparisons, functions that bubble, string comparisons,
`Boolean`s' equality and order (which Kotlin's Lua, JS, and C++ don't
translate), and `do … while`. The log is what the interpreter gives.

    let one = 1;
    let big = 0x7fff_ffff * one;
    let small = -0x8000_0000 * one;
    console.log("${big + 1} ${small - 1} ${big * 4} ${-small}");
    console.log("${(-7 * one) >> 1} ${(-7 * one) >>> 28} ${one << 33} ${~one} ${5 & 3} ${5 | 3} ${5 ^ 3}");
    console.log("${(3 <=> 5).toString()} ${("b" <=> "a").toString()}");
    let same(a: Boolean, b: Boolean): String { "${a == b} ${a != b} ${(a <=> b).toString()}" }
    console.log("${same(true, true)} ${same(true, false)} ${same(false, true)}");

    let quot(a: Int, b: Int): Int throws Bubble { a / b }
    let rem(a: Int, b: Int): Int throws Bubble { a % b }
    console.log("${quot(-7, 2)} ${rem(-7, 2)} ${quot(small, -1)}");

    let words(a: String, b: String): String {
      if (a < b) {
        return "${a} < ${b}";
      }
      if (a <= b) {
        "${a} == ${b}"
      } else {
        "${a} > ${b}"
      }
    }
    console.log(words("apple", "banana"));
    console.log(words("pear", "pear"));
    console.log(words("zoo", "yak"));
    console.log((words("a", "b") == "a < b").toString());

    var n = 0;
    var odd = 0;
    do {
      n += 1;
      if (n % 2 != 0) { odd += n; }
    } while (n < 9);
    console.log("odd ${odd}");

    var found = -1;
    var i = 0;
    while (i < 5 && found < 0) {
      var j = 0;
      while (j < 5) {
        if (i * j == 6) {
          found = i * 10 + j;
          break;
        }
        j += 1;
      }
      i += 1;
    }
    console.log("found ${found}");

    let empty = "";
    let x = "x";
    console.log((empty.isEmpty && !x.isEmpty).toString());

```log
-2147483648 2147483647 -4 -2147483648
-4 15 2 -2 1 7 6
-1 1
true false 0 false true 1 false true -1
-3 -1 -2147483648
apple < banana
pear == pear
zoo > yak
true
odd 25
found 23
true
```
