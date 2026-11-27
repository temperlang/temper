# Errors

Code with errors still translates and runs: each error panics where it
runs (tmpl's `Error`), so nothing handles it, and everything else runs
as written. Code that's only ill-typed runs on as written on the
interpreter and dynamically typed backends, as in Kotlin's
interpreter, and panics there on statically typed ones, whose compilers
would reject it. Each section here is a run of its own, as each ends
at its error's panic:

    export let good(x: Int): Int { x + 1 }

A value that doesn't fit its place (a type-only error), an undeclared
name, a misspelled member and method, and the wrong number of
arguments:

    let mismatched(x: Int): Int { let s: String = x; 1 }
    let undeclared(): Int { missing(1) }
    let misspelled(s: String): Int { s.lenght }
    let misspelledMethod(s: String): Int { s.lengthy() }
    let wrongArity(): Int { good(1, 2) }

Each panics as the others do, so the runs below call only some.

An undeclared type leaves the declaration usable, its type unknown:

    let unknownType(x: Nope): Int { 1 }

A missing bracket stays local, so what follows still parses:

    let unclosed(x: Int: Int { x * 2 }
    let afterUnclosed(x: Int): Int { x * 3 }

A missing operator is one error, and `part` is still declared, with
`cents`'s type, so only its `let` panics, where it runs:

    let dropped(cents: Int): String {
      let part = cents  100;
      "${part.toString()}"
    }

More type-only errors: a value stored, returned, or passed where
another type is wanted:

    let stored(x: Int): String { let s: String = x; "${s}!" }
    let returned(): Int { "text" }
    let passedOn(x: Int): Int { x }

A method with an error panics, but not its class's others:

    class Box(public value: Int) {
      public get(): Int { value }
      public bad(): String { value }
    }

    let box = new Box(3);
    console.log("${good(1)} ${unknownType(1)} ${afterUnclosed(2)} ${box.get()}");

## Type-only errors

The type-only errors run on where ill-typed code runs, and panic
elsewhere; a misspelled member panics everywhere:

    console.log("${mismatched(1)} ${stored(1)} ${returned()} ${passedOn("in")} ${box.bad()}");
    misspelled("a");
    console.log("after");

```log interp js lua py
2 1 6 3
1 1! text in 3
```

```log cpp csharp java rust
2 1 6 3
```

```stderr cpp csharp java js lua py rust
panic
```

## Undeclared name

    undeclared();
    console.log("after");

```log
2 1 6 3
```

```stderr cpp csharp java js lua py rust
panic
```

## Missing operator

    dropped(1);
    console.log("after");

```log
2 1 6 3
```

```stderr cpp csharp java js lua py rust
panic
```
