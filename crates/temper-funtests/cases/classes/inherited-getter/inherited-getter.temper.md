# Object Inherited Getter Functional Test

Tests a getter that is inherited from an interface.

    interface I {
      public get place(): String { "Byron Bay" }
    }
    class C extends I {}
    let c = new C();
    console.log("Goodbye, gotta run, ${c.place}!")

Expected output:

```log
Goodbye, gotta run, Byron Bay!
```
