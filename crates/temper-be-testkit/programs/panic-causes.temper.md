# Panic causes

When something goes wrong, the report says what, and why where it
knows: a panic that replaces a bubble keeps the bubble as its cause,
in each language's own way where it has one (a Java or JavaScript
cause, Python's `raise … from`, a C# inner exception) and in the
message where it hasn't (C++, Lua, and Rust say `caused by: …`). A
failure that nothing handles, a bubble or a panic, ends the run saying
what it was, with a stack trace where the language gives one. Each
section here is a run of its own, as each ends at its failure:

    let positive(n: Int): Int throws Bubble {
      if (n <= 0) { bubble() }
      n
    }
    console.log("before");

## Panic in orelse

`orelse panic()` panics in place of the bubble, which stays its cause:

    let p = positive(-1) orelse panic();
    console.log("after ${p}");

```log
before
```

```stderr interp
Panicked: panic(), caused by a bubble
```

```stderr cpp
panic
caused by: bubble
```

```stderr csharp
TemperLang.Core.Panic: panic
---> TemperLang.Core.Bubble
```

```stderr java
java.lang.AssertionError: panic
Caused by: temper.core.Bubble
```

```stderr js
Error: panic
[cause]: Bubble
```

```stderr lua
panic
caused by: Bubble
```

```stderr py
The above exception was the direct cause of the following exception:
BaseException: panic
```

```stderr rust
panic
caused by: bubble
```

## Panic passing orelse panic

A panic in the body passes `orelse panic()` untouched, as it passes any
`orelse`, saying what it said, not "panic":

    let read(ls: List<Int>, i: Int): Int throws Bubble {
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

## Panic in orelse whose body returns

A body that may `return` runs in place, where it can, and its bubble
stays the panic's cause there too:

    let checked(n: Int): Int {
      do {
        if (n == 0) { return 0 }
        positive(n);
      } orelse panic();
      n
    }
    console.log("after ${checked(-1)}");

```log
before
```

```stderr interp
Panicked: panic(), caused by a bubble
```

```stderr cpp
panic
caused by: bubble
```

```stderr csharp
TemperLang.Core.Panic: panic
---> TemperLang.Core.Bubble
```

```stderr java
java.lang.AssertionError: panic
Caused by: temper.core.Bubble
```

```stderr js
Error: panic
[cause]: Bubble
```

```stderr lua
panic
caused by: Bubble
```

```stderr py
The above exception was the direct cause of the following exception:
BaseException: panic
```

```stderr rust
panic
caused by: bubble
```

## Uncaught bubble

A bubble nothing handles ends the run, saying it bubbled:

    positive(-1);
    console.log("after");

```log
before
```

```stderr interp
Failed
```

```stderr cpp
uncaught bubble
```

```stderr csharp
TemperLang.Core.Bubble
Global.Positive(Int32 n)
```

```stderr java
temper.core.Bubble
Global.positive(
```

```stderr js
Bubble
at positive (
```

```stderr lua
Bubble
```

```stderr py
Traceback
temper_core.Bubble
```

```stderr rust
uncaught bubble
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
panicked
```
