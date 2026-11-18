# Loop Reenterable Functional Test

Tests that a loop's lexical scope can be reentered.

    var i = 0;
    while (i < 2) {
      let x = i;
      console.log("${x}");
      i += 1
    }

Expected output:

```log
0
1
```
