# Import Values Functional Test

Import from a directory module.

    let { magicNumber } = import("./exporter");

Make a local function to use the imported function. Even if we inline values,
we should still generate this exported function anyway.

    export let timesMagic(n: Int): Int {
      n * magicNumber
    }

But still jump through hoops to avoid inlining using techniques that work for now.

    var a = 1;
    a = a;
    let c = timesMagic(a);
    console.log("number = ${c}");

Expected output:

```log
number = 42
```
