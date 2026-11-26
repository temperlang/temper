# BitVector Negative Index, Set False

Setting a bit at a negative index panics on every backend, even to
`false`, which past the end changes nothing, ending the run.

```text
@meta:arg expectRunFailure = true,
```

    let { BitVector } = import("std/collections");

    let v = new BitVector(2);
    v.set(5, false);
    console.log("v[5]=${v[5]}");
    console.log("before");
    v.set(-1, false);
    console.log("not reached");

```log
v[5]=false
before
```

The run ends in each backend's panic, with this message, not in a host
exception. A bit vector has no length, every index from 0 on being in
bounds, so its message gives 0, whatever it stores:

```panic
index -1 out of bounds for length 0
```
