# Host error causes

A host's own error, as host code throws, isn't a bubble, so it passes
every `orelse`, `orelse panic()` too, and ends the run saying what it
said, not "panic". Here a connected function's host code throws one;
the interpreter, which has no host, runs its Temper body, which panics,
as passing every `orelse` is what a panic does too:

    @connected
    export let hostFails(n: Int): Int throws Bubble {
      if (n > 0) { panic() }
      bubble()
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
