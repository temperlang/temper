# Host failures

What hosts see of declared failures, as RFC 164 proposes them, written
ahead as a specification (`SPECS` in the testkit), now built on every
backend. A connected function's host code throws the library's own
failure type, which Temper code catches like any other, and its own
host error, which passes every handler, as a panic does. A failure
nothing handles ends the run, saying its message and its cause's. Each
section is a run of its own, as each ends at its failure; the
interpreter, which has no host, runs the Temper body, which panics
where host code has its own error. It assumes what `failures` does,
and, of what's still open:

- Each failure type is a host type under its root's, which host code
  makes and throws as its language does (on Rust and C++ by returning
  it); host code's names and paths here are guesses that each backend
  settles when it runs this.
- A failure's message, unless its type gives its own with a `message`
  getter, is its type's name in lowercase words, then its fields:
  `not found (key: y)`.
- A `throw` in a handler takes the failure being handled as its cause,
  and a report shows the cause's message too.
- A failure that top-level code passes on with `try` ends the run.

The library's root, the function, and a line logged before each
section's:

    export sealed interface HostFailuresError extends Error {}
    export class NotFound(public key: String) extends HostFailuresError {}

    @connected
    export let hostLookup(key: String): Int throws NotFound {
      if (key == "bug") { panic() }
      if (key == "a") { return 1; }
      throw new NotFound(key);
    }

    console.log("before");

## Host failure

Host code's failure is caught as any other is; one nothing handles
ends the run, saying its message:

    let n = hostLookup("z") catch (e) { console.log("caught ${e.key}"); -1 };
    console.log("got ${n} ${hostLookup("a") orelse 0}");
    try hostLookup("y");
    console.log("after");

```log
before
caught z
got -1 1
```

```stderr
not found (key: y)
```

## Host error passes catch

Host code's own error isn't a failure, so no handler catches it, and it
ends the run saying what it said:

    let h = hostLookup("bug") catch { -1 };
    console.log("after ${h}");

```log
before
```

```stderr interp
Panicked: panic()
```

```stderr ~interp
host bug
```

## Cause

A failure thrown while handling another keeps that one as its cause,
and the report says both:

    class LookupFailed(public name: String) extends HostFailuresError {}

    let setting(name: String): Int throws LookupFailed {
      hostLookup(name) catch { throw new LookupFailed(name); }
    }

    try setting("port");
    console.log("after");

```log
before
```

```stderr
lookup failed (name: port)
not found (key: port)
```

## Own message

A failure type's `message` getter gives its message instead:

    class Expired(public days: Int) extends HostFailuresError {
      public get message(): String { "expired ${days} days ago" }
    }

    let check(days: Int): Void throws Expired {
      if (days > 0) { throw new Expired(days); }
    }

    try check(0);
    try check(3);
    console.log("after");

```log
before
```

```stderr
expired 3 days ago
```

```stderr-absent
expired (days: 3)
```

## Panic passes catch

A panic isn't a failure either, so it passes every handler:

    let risky(n: Int): Int throws NotFound {
      if (n > 1) { panic() }
      if (n > 0) { throw new NotFound("n"); }
      n
    }

    let r = risky(2) catch { -1 };
    console.log("after ${r}");

```log
before
```

```stderr interp
Panicked: panic()
```

```stderr ~interp
panic
```

```stderr-absent
not found
```

## Several libraries

One handler catches failures of two libraries, each under a root of its
own, `count-failures`' beside this one's: what it catches is core's
`Error`, whose message it reads, and which `when` tells apart. A
`throw` there takes it as its cause, and an `orelse` catches a bubble
beside both. Another library's failure that nothing handles ends the
run too:

    let { parseCount } = import("count-failures");

    let total(key: String, count: String): Int {
      (hostLookup(key) + parseCount(count)) catch (e) {
        console.log("caught ${e.message}");
        when (e) { is NotFound -> -1; else -> -2 }
      }
    }
    console.log("${total("a", "two")} ${total("z", "two")} ${total("a", "x")}");

    class Unreadable(public key: String) extends HostFailuresError {}

    let reading(key: String, count: String): Int throws Unreadable {
      (hostLookup(key) + parseCount(count)) catch { throw new Unreadable(key); }
    }

    let r = reading("a", "x") catch (e) {
      console.log("${e.message}, caused by ${e.cause?.message ?? "nothing"}");
      0
    };

    let nonzero(n: Int): Int throws Bubble { if (n == 0) { bubble() } else { n } }
    let ratio(key: String, count: String): Int {
      (hostLookup(key) / nonzero(parseCount(count))) orelse -9
    }
    console.log(
      "${r} ${ratio("a", "two")} ${ratio("a", "zero")} ${ratio("a", "x")} ${ratio("z", "two")}"
    );
    try parseCount("x");
    console.log("after");

```log
before
caught not found (key: z)
caught bad count (text: x)
3 -1 -2
unreadable (key: a), caused by bad count (text: x)
0 0 -9 -9 -9
```

```stderr
bad count (text: x)
```

## Host error passes several libraries' handler

Host code's own error passes a handler of two libraries' failures too:

    let { parseCount } = import("count-failures");

    let h = (parseCount("two") + hostLookup("bug")) catch (e) {
      console.log("caught ${e.message}");
      -1
    };
    console.log("after ${h}");

```log
before
```

```stderr interp
Panicked: panic()
```

```stderr ~interp
host bug
```

## Panic passes several libraries' handler

So does a panic, where the handler catches a bubble too:

    let { parseCount } = import("count-failures");

    let p = do {
      let n = try parseCount("two");
      let m = try hostLookup("a");
      if (n + m > 2) { panic() }
      10 / (n - m)
    } orelse -1;
    console.log("after ${p}");

```log
before
```

```stderr interp
Panicked: panic()
```

```stderr ~interp
panic
```
