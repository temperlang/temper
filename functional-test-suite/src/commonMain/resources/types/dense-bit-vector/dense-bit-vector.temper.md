# DenseBitVector Operations Test

    let v = new DenseBitVector(16);

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

Setting a bit far past the capacity grows the vector enough to hold it.

    v.set(1000, true);
    console.log("v[1000]=${v.get(1000)}");
    console.log("v[999]=${v.get(999)}");

```log
v[1000]=true
v[999]=false
```
