# BitVector Operations Test

*BitVector* is in std.

```text
@meta:arg expectRunFailure = true,
```

    let { BitVector } = import("std/collections");

    let v = new BitVector(16);

Each bit should be initially `false`, but sets should affect subsequent gets.

    console.log("v[2]=${v.get(2)}");
    console.log("v[3]=${v.get(3)}");
    console.log("v[3] <- true");
    v.set(3, true);
    console.log("v[2]=${v.get(2)}");
    console.log("v[3]=${v.get(3)}");
    console.log("v[3] <- false");
    v.set(3, false);
    console.log("v[2]=${v.get(2)}");
    console.log("v[3]=${v.get(3)}");

```log
v[2]=false
v[3]=false
v[3] <- true
v[2]=false
v[3]=true
v[3] <- false
v[2]=false
v[3]=false
```

It grows when a bit past its capacity is set, and a bit past the end, or
at a negative index, is `false`. Setting `false` past the end changes
nothing.

    v[100] = true;
    v.set(200, false);
    console.log("v[99]=${v[99]} v[100]=${v[100]} v[101]=${v[101]} v[200]=${v[200]} v[-1]=${v[-1]}");
    v[1000] = true;
    console.log("v[999]=${v[999]} v[1000]=${v[1000]} v[100]=${v[100]}");

```log
v[99]=false v[100]=true v[101]=false v[200]=false v[-1]=false
v[999]=false v[1000]=true v[100]=true
```

A negative capacity is none.

    let w = new BitVector(-9);
    w.set(1, true);
    console.log("w[0]=${w[0]} w[1]=${w[1]}");

```log
w[0]=false w[1]=true
```

Setting a bit at a negative index panics on every backend, ending the
run, so it goes last.

    console.log("before");
    v.set(-1, true);
    console.log("not reached");

```log
before
```
