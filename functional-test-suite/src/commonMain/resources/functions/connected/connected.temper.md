# Userspace connected methods

Interfaces support calling backend code from Temper, buy requires a separate
client project to provide that. It's much more flexible if a Temper library can
just include and use backend code as needed.

## Top-level connected-only functions

This function has no Temper implementation, so it needs connected on all
backends. It's also top-level, which is the simplest form. But Temper backends
might also still need to generate a wrapper function in the standard location
for calling the user-provided functiom. Specifics vary by backend.

    @connected
    export let sum(i: Int, j: Int): Int { panic() }

Try both exported above and unexported below to make sure both work. Again,
managing this varies by backend. This one also uses an unexported type that
connected code needs to have access to.

    @connected
    /* unexported */ let prod(hidden: Hidden, j: Int): Int { panic() }
    /* unexported */ class Hidden(public i: Int) {}

A simple test will do. And this can't be inlined by Temper, since Temper the
Temper implementation can only panic.

    console.log("sum(1, 2): ${sum(1, 2)}")
    console.log("prod(1, 2): ${prod(new Hidden(1), 2)}")

```log
sum(1, 2): 3
prod(new Hidden(1), 2): 2
```

## Instance methods

We could also presumably automate support for instance methods, but it might be
clearer if we just require manual effort for now. For example, here's a demo of
instance methods passing private data to a connected function.

Again, demo calling both exported and unexported functions.

    export class Hider(
      @noProperty i: Int,
    ) {
      private hidden: Hidden = new Hidden(i);
      public plus(j: Int): Int { sum(hidden.i, j) }
      public times(j: Int): Int { prod(hidden, j) }
    }

And reuse the same hider instance just for fun.

    let hider = new Hider(3);
    console.log("new Hider(3).plus(4): ${hider.plus(4)}")
    console.log("new Hider(3).times(4): ${hider.times(4)}")

```log
new Hider(3).plus(4): 7
new Hider(3).times(4): 12
```
