# List Builder Remove Last When Empty

Removing the last value of an empty list builder panics on every
backend, as reading a list out of bounds does.

```text
@meta:arg expectRunFailure = true,
```

    let builder = new ListBuilder<String>();
    builder.add("a");
    console.log(builder.removeLast());
    console.log("before");
    console.log(builder.removeLast());
    console.log("not reached");

```log
a
before
```

The run ends in each backend's panic, with this message:

```panic
index -1 out of bounds for length 0
```
