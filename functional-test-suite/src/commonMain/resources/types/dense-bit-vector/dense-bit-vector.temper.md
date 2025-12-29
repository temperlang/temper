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
