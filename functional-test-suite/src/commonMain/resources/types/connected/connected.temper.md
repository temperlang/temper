# Connected types

A whole class can be connected. Each backend's connected code provides a class
with the names and members Temper would have generated, and Temper uses it in
place of generating one. No package dependencies here, so this runs everywhere.

Connected functions have their own test, `functions/connected`.

A connected class can also have a complete Temper implementation, which the
interpreter uses, as do backends under `--no-connected`. With no backend list,
as here, every backend has connected code. A fallback must be complete: either
every method has a Temper body, or none does.

Here, `total()` deliberately answers 1000 more in Temper than in the connected
code, so each run's output shows which code ran. Expected output blocks name the
runs they apply to: `interp` for the interpreter, `no-connected` for runs under
`--no-connected`, and backend IDs, with `~` to exclude one.

    @connected
    export class Counter {
      private var count: Int = 0;
      public add(i: Int): Void { count += i; }
      public total(): Int { count + 1000 }
    }

    let counter = new Counter();
    counter.add(2);
    counter.add(3);
    console.log("counter.total(): ${counter.total()}");

```log ~interp ~no-connected
counter.total(): 5
```

```log interp no-connected
counter.total(): 1005
```

A backend list names the backends with connected code, and others use the
Temper implementation. This one lists every backend except Lua, so Lua uses the
Temper implementation even without `--no-connected`.

    @connected(["cpp", "csharp", "java", "js", "py", "rust"])
    export class Doubler {
      public twice(i: Int): Int { i * 2 + 1000 }
    }

    console.log("new Doubler().twice(5): ${new Doubler().twice(5)}");

```log ~interp ~no-connected ~lua
new Doubler().twice(5): 10
```

```log interp no-connected lua
new Doubler().twice(5): 1010
```
