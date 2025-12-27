# Object Backed Vs Computed Property Order Functional Test

Tests that properties are computed in the order expected.

        class C(
          public backed: Int = (do {
            console.log("initializing backed");
            1
          }),
        ) {
          public get computed(): Int {
            console.log("computing computed");
            1
          }
        }
        console.log("about to create instance");
        let c = new C();
        console.log("about to get backed");
        console.log("c.backed=${c.backed}");
        console.log("about to get computed");
        console.log("c.computed=${c.computed}");
        console.log("done");

Expected output:

```log
about to create instance
initializing backed
about to get backed
c.backed=1
about to get computed
computing computed
c.computed=1
done
```
