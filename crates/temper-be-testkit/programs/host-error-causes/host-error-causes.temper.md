# Host error causes

A host's own error, as host code throws, isn't a failure, so it passes
every `orelse`, `orelse panic()` too, and ends the run saying what it
said, not "panic". Here a connected function's host code throws one;
the interpreter, which has no host, runs its Temper body, which panics,
as passing every `orelse` is what a panic does too. The function may
throw `Oops`, so an `orelse` around it handles that alone:

    export sealed interface OopsError extends Error {}
    export class Oops extends OopsError {}

    @connected
    export let hostFails(n: Int): Int throws Oops {
      if (n > 0) { panic() }
      throw new Oops()
    }

    console.log("before");
    let h = hostFails(1) orelse panic();
    console.log("after ${h}");

```log
before
```

```stderr interp
Panicked: panic()
```

```stderr ~interp
host bug 1
```

It has no cause, and isn't a panic of `orelse panic()`'s:

```stderr-absent ~interp
caused by
[cause]
direct cause
```

```stderr-absent csharp
Panic: panic
```

```stderr-absent java
AssertionError: panic
```

```stderr-absent js
Error: panic
```

```stderr-absent py
BaseException: panic
```
