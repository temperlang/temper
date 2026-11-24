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

Bits at the boundaries of each backend's storage units (8, 32, and 64
bits) stay apart, and a bit that grew the vector can be cleared.

    let u = new BitVector(0);
    u[8] = true;
    u[32] = true;
    u[64] = true;
    console.log("${u[7]} ${u[8]} ${u[9]} ${u[31]} ${u[32]} ${u[33]} ${u[63]} ${u[64]} ${u[65]}");
    v[1000] = false;
    console.log("v[1000]=${v[1000]} v[999]=${v[999]}");

```log
false true false false true false false true false
v[1000]=false v[999]=false
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

The run ends in each backend's panic, with this message, not in a host
exception. A bit vector has no length, every index from 0 on being in
bounds, so its message gives 0, whatever it stores:

```panic
index -1 out of bounds for length 0
```
