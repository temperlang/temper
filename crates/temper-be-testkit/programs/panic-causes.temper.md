# Panic causes

When something goes wrong, the report says what, and why where it
knows: a panic that replaces an error keeps the error as its cause,
in each language's own way where it has one (a Java or JavaScript
cause, Python's `raise … from`, a C# inner exception) and in the
message where it hasn't (C++, Lua, and Rust say `caused by: …`). An
error that nothing handles, or a panic, ends the run saying what it
was, with a stack trace where the language gives one. Each section
here is a run of its own, as each ends at its error:

    sealed interface Problem extends Error {}
    class NotPositive(public n: Int) extends Problem {}

    let positive(n: Int): Int throws NotPositive {
      if (n <= 0) { throw new NotPositive(n) }
      n
    }
    console.log("before");

## Panic in orelse

`orelse panic()` panics in place of the error, which stays its cause:

    let p = positive(-1) orelse panic();
    console.log("after ${p}");

```log
before
```

```stderr interp
Panicked: panic(), caused by NotPositive: not positive (n: -1)
```

```stderr cpp
panic
caused by: not positive (n: -1)
```

The error it replaced isn't reported as uncaught too:

```stderr-absent cpp
uncaught
```

```stderr csharp
TemperLang.Core.Panic: panic
.NotPositive: not positive (n: -1)
```

```stderr java
java.lang.AssertionError: panic
Caused by:
.NotPositive: not positive (n: -1)
```

```stderr js
Error: panic
[cause]: NotPositive
```

```stderr lua
panic
caused by: not positive (n: -1)
```

```stderr py
not positive (n: -1)
The above exception was the direct cause of the following exception:
BaseException: panic
```

```stderr rust
panic
caused by: not positive (n: -1)
```

## Panic passing orelse panic

A panic in the body passes `orelse panic()` untouched, as it passes any
`orelse`, saying what it said, not "panic":

    let read(ls: List<Int>, i: Int): Int throws NotPositive {
      positive(i + 1);
      ls[i]
    }
    let r = read([1, 2], 5) orelse panic();
    console.log("after ${r}");

```log
before
```

```stderr
index 5 out of bounds for length 2
```

It has no cause, and isn't a panic of `orelse panic()`'s:

```stderr-absent
caused by
[cause]
direct cause
```

```stderr-absent csharp
Panic: panic
NotPositive
```

```stderr-absent java
AssertionError: panic
NotPositive
```

```stderr-absent js
Error: panic
```

```stderr-absent py
BaseException: panic
```

## Panic in orelse whose body returns

A body that may `return` runs in place, where it can, and its error
stays the panic's cause there too. Its call comes first, as C++ can't
yet run a body that jumps out of itself otherwise:

    let checked(n: Int): Int {
      do {
        let p = positive(n);
        if (p == 1) { return 0 }
      } orelse panic();
      n
    }
    console.log("after ${checked(-1)}");

```log
before
```

```stderr interp
Panicked: panic(), caused by NotPositive: not positive (n: -1)
```

```stderr cpp
panic
caused by: not positive (n: -1)
```

```stderr-absent cpp
uncaught
```

```stderr csharp
TemperLang.Core.Panic: panic
.NotPositive: not positive (n: -1)
```

```stderr java
java.lang.AssertionError: panic
Caused by:
.NotPositive: not positive (n: -1)
```

```stderr js
Error: panic
[cause]: NotPositive
```

```stderr lua
panic
caused by: not positive (n: -1)
```

```stderr py
not positive (n: -1)
The above exception was the direct cause of the following exception:
BaseException: panic
```

```stderr rust
panic
caused by: not positive (n: -1)
```

## Uncaught error

An error that top-level code passes on with `try` ends the run, saying
what it was:

    try positive(-1);
    console.log("after");

```log
before
```

```stderr
not positive (n: -1)
```

```stderr interp
Failed with NotPositive
```

```stderr-absent interp
Panicked
```

```stderr cpp
uncaught failure: not positive (n: -1)
```

```stderr csharp
NotPositive
Global.Positive(Int32 n)
```

```stderr java
NotPositive
Global.positive(
```

```stderr js
NotPositive
at positive (
```

```stderr py
Traceback
in _positive
NotPositive
```

```stderr rust
uncaught failure: not positive (n: -1)
```

## Uncaught panic

    let boom(n: Int): Int {
      if (n > 0) { panic() }
      n
    }
    boom(1);
    console.log("after");

```log
before
```

```stderr interp
Panicked: panic()
```

```stderr cpp
panic
```

```stderr-absent cpp
uncaught
```

```stderr csharp
TemperLang.Core.Panic: panic
Global.Boom(Int32 n)
```

```stderr java
java.lang.AssertionError: panic
Global.boom(
```

```stderr js
Error: panic
at boom (
```

```stderr lua
panic
```

```stderr py
Traceback
BaseException: panic
```

```stderr rust
panicked at
explicit panic
```

## Panic in an async block

A panic in an `async` block ends the run, as anywhere else, after what
the block logged before it:

    let third(ls: List<Int>): Int { ls[2] }
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      console.log("in async");
      third([1, 2]);
    }

```log
before
in async
```

```stderr
index 2 out of bounds for length 2
```

## Panic on every branch

A module whose top-level code panics on every branch it can take still
builds, as in Java, whose static initializer must look as though it
could finish:

    let k = positive(1) orelse 0;
    if (k > 0) { panic() } else { positive(-1) orelse panic(); panic() }
    console.log("after");

```log
before
```

```stderr
panic
```
