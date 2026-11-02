# Connected types

A whole class can be connected. Each backend's connected code provides a class
with the names and members Temper would have generated, and Temper uses it in
place of generating one. No package dependencies here, so this runs everywhere.

Connected functions have their own test, `functions/connected`.

A connected class can also have a complete Temper implementation, which the
interpreter uses, as do backends under `--no-connected`. With no backend list,
as here, every backend has connected code. A list, as in
`@connected(["cpp", "rust"])`, would name the backends that do, and others would
use the Temper implementation. A fallback must be complete: either every method
has a Temper body, or none does.

    @connected
    export class Counter {
      private var count: Int = 0;
      public add(i: Int): Void { count += i; }
      public total(): Int { count }
    }

    let counter = new Counter();
    counter.add(2);
    counter.add(3);
    console.log("counter.total(): ${counter.total()}");

```log
counter.total(): 5
```
