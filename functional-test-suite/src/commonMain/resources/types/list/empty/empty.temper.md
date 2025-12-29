# List Empty Functional Test

Checks the `isEmpty` method.

    let f(b: List<Int>): Void {
      if (b.isEmpty) {
        console.log("empty")
      } else {
        console.log("not empty")
      }
    };

    f([2, 3]);

Expected output:

```log
not empty
```
