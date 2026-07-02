# Userspace connected methods

Interfaces support calling backend code from Temper, buy requires a separate
client project to provide that. It's much more flexible if a Temper library can
just include and use backend code as needed.

## Top-level connected-only function

This function has no Temper implementation, so it needs connected on all
backends. It's also top-level, which is the simplest form.

Also, being exported for a top-level connected relies on the backend code to do
the exporting.

    @connected
    export let inc(n: Int): Int;

A simple test will do. And this can't be inlined by Temper, since Temper has no
visibility to it.

    console.log("connected inc(2): ${inc(2)}")

```log
connected inc(2): 3
```
