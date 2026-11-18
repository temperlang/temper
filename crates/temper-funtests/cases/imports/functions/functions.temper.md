# Import Functions Functional Test

Imports from a source file in a directory module.

    let { normSquared } = import("./exporter/");

Make a local function to use the imported function. Even if we inline values,
we should still generate this exported function anyway.

    export let norm(x: Float64, y: Float64): Float64 {
      normSquared(x, y).sqrt()
    }

But still jump through hoops to avoid inlining using techniques that work for now.

    var a = 3.0;
    a = a;
    var b = a + 1.0;
    let values = [a, b, norm(a, b)].join(", ") { it => "${it}" };
    console.log("${values} triangle");

Expected output:

```log
3.0, 4.0, 5.0 triangle
```

## Imported function as value

We also should be able to use the imported function as a reference, and
`normSquared` is imported.

    export let something(vals: Listed<Float64>): Float64 {
      // Not sure there's anything meaningful to this, but meh.
      vals.reduceFrom(0.0, normSquared)
    }

    console.log("${something([a, b])}")

Expected output from `(0.0 + 3.0 ** 2.0) ** 2.0 + 4.0 ** 2.0`:

```log
97.0
```
