# Userspace connected methods

Interfaces support calling backend code from Temper, buy requires a separate
client project to provide that. It's much more flexible if a Temper library can
just include and use backend code as needed.

## Kept definitions

TODO Use this for backends as we provide stable naming for unexported things.

Some connected code might want to use a unexported helper written in Temper in
the same module, so a `@keep` decorator prevents such from being pruned.

    @keep
    let sumOf3(a: Int, b: Int, c: Int): Int {
      a + b + c
    }

There's also a `@keepTest` decorator to retain code for connected code for tests
only, but we don't test testing here.

And we define this early, so top-level code below can access it through
connected code calls, including in dynamic backends. Since it's only called from
connected code, that means our module sorting needs to be stable. Otherwise, we
can't depend on this being kept in place.

## Top-level connected-only functions

This function has no Temper implementation, so it needs connected on all
backends. It's also top-level, which is the simplest form. But Temper backends
might also still need to generate a wrapper function in the standard location
for calling the user-provided functiom. Specifics vary by backend.

Also include an optional arg to ensure backends cope, although it's the
responsibility of the connected code itself to provide the correct default. But,
for example, overloads sending in `null` for optionals should be automated by
be-java.

    @connected
    export let sum(i: Int, j: Int, bonus: Int = 0): Int;

Try both exported above and unexported below to make sure both work. Again,
managing this varies by backend. This one also uses an unexported type that
connected code needs to have access to.

    @connected
    /* unexported */ let prod(hidden: Hidden, j: Int): Int;

    /* unexported */ class Hidden(public i: Int) {}

Also try a function with a nullable default parameter that defaults to null. Our
implementations of `length` for this test are sloppy, so only pass in ASCII.

    @connected
    export let length(string: String? = null): Int;

A simple test will do. And this can't be inlined by Temper, since the Temper
implementation can only panic.

TODO How to test interpreter fallback? Different `log` blocks for interp?

    console.log("sum(1, 2): ${sum(1, 2)}");
    console.log("prod(new Hidden(1), 2): ${prod(new Hidden(1), 2)}");
    console.log("""
      ~length "" vs null vs ():
      ~ ${length("")} vs ${length(null)} vs ${length()}
    );

```log
sum(1, 2): 3
prod(new Hidden(1), 2): 2
length "" vs null vs (): 0 vs -1 vs -1
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
