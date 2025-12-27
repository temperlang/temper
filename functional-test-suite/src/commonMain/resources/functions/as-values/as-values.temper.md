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
