# Functions as values

Normally a named function is just called directly.

    let hello(how: String): Void { console.log("Called ${how}!") }

    hello("directly");

```log
Called directly!
```

Named functions can be passed to other functions, but the type of the
argument should be a functional interface type.

    @fun interface StringSink(s: String): Void;

    let callIt(f: StringSink): Void {
      f("by a function");
    }

    callIt(hello);

```log
Called by a function!
```

Named functions can be stored in properties with a functional interface type.

    class DelayedFunction(private f: StringSink) {
      public callIt(): Void { f("from a method") }
    }

    new DelayedFunction(hello).callIt()

```log
Called from a method!
```

## Mutual recursion detour

Not sure where else we already test recursion, but here's some for now. And
specifically, we want to ensure mutual recursion works.

    console.log("Is 5 even? ${isEven(5)}");
    console.log("Is 6 even? ${isEven(6)}");

These only work for non-negative integers, but that's ok here.

    let isEven(i: Int): Boolean {
      if (i == 0) {
        true
      } else {
        isOdd(i - 1)
      }
    }

    let isOdd(i: Int): Boolean {
      if (i == 0) {
        false
      } else {
        isEven(i - 1)
      }
    }

```log
Is 5 even? false
Is 6 even? true
```
