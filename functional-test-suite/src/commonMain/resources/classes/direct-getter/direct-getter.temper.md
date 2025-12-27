# Object Direct Getter Functional Test

Tests invoking a simple property getter.

    class C {
      public get place(): String { "Hilo, HI" }
    }
    let c = new C();
    console.log("Hello, ${c.place}!")

Expected output:

```log
Hello, Hilo, HI!
```
