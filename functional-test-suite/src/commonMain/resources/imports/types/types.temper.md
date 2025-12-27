# Import Types Functional Test

Import from a directory module.

    let { ExportedClass } = import("./exporter");

We'll simply test that we can construct and use an instance.

    let x = new ExportedClass(42);
    console.log("foo = ${x.foo}");

Expected output:

```log
foo = 42
```
