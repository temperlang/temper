# Miscellaneous

    let ls: List<Int> = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

    console.log("All  ${ (ls.join(", ") { x => x.toString() }) }");

    console.log("Even ${
      (ls.filter { x => (x & 1) == 0 }
        .join(", ") { x => x.toString() })
    }");

    console.log("Odd  ${
      (ls.filter { x => (x & 1) != 0 }
        .join(", ") { x => x.toString() })
    }");

    console.log("Neg  ${
      (ls.map {(x): Int => -x }
        .join(", ") { x => x.toString() })
    }");

    let strs = "1 ; 2 ; 3".split(" ; ");
    console.log("Splt ${
      (strs.join(", ") { x => x })
    }");

```log
All  0, 1, 2, 3, 4, 5, 6, 7, 8, 9
Even 0, 2, 4, 6, 8
Odd  1, 3, 5, 7, 9
Neg  0, -1, -2, -3, -4, -5, -6, -7, -8, -9
Splt 1, 2, 3
```
