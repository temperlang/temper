# List Index Past the End

Reading a list past its end panics on every backend, ending the run,
rather than bubbling or raising a host exception that `orelse` might
catch.

```text
@meta:arg expectRunFailure = true,
```

    let at(listed: Listed<String>, i: Int): String { listed[i] }

    let letters = ["a", "b", "c"];
    console.log(at(letters, 2));
    console.log("before");
    console.log(at(letters, 3));
    console.log("not reached");

```log
c
before
```

The run ends in each backend's panic, with this message:

```panic
index 3 out of bounds for length 3
```
