# List Reduce When Empty

Reducing an empty list without a first value panics on every backend,
as reading its first value does.

```text
@meta:arg expectRunFailure = true,
```

    let sum(values: Listed<Int>): Int {
      values.reduce { a: Int, b: Int => a + b }
    }

    console.log(sum([1, 2, 3]).toString());
    console.log("before");
    console.log(sum([]).toString());
    console.log("not reached");

```log
6
before
```

The run ends in each backend's panic, with this message:

```panic
index 0 out of bounds for length 0
```
