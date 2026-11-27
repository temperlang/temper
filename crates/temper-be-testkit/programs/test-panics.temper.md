# Test panics

A test that panics fails alone, saying why, and the tests after it
still run and report, on every backend: only the test runner catches a
panic, test by test, never an `orelse`, as the one here around a read
past a list's end. A failing assertion fails its own test, as ever, and
the others pass:

    sealed interface Problem extends Error {}
    class Oops extends Problem {}

    let at(ls: List<Int>, i: Int): Int throws Oops {
      if (i == 99) { throw new Oops() }
      ls[i]
    }

    test("passes before") {
      assert(1 + 1 == 2);
    }

    test("panics reading past the end") {
      let n = at([1, 2], 5) orelse -1;
      assert(n == -1);
    }

    test("dies outright") {
      panic();
    }

    test("fails an assertion") {
      assert(1 + 1 == 3) { "wrong on purpose" };
    }

    test("passes after") {
      assert(at([1, 2], 1) == 2);
    }

A test that panics after a failed `assert` fails saying it panicked,
its own failure, which wins over the assertion's, said after it where
the runner shows more:

    test("dies after failing") {
      assert(1 + 1 == 3) { "failed before" };
      panic();
    }

Each failing test, with what its failure says:

```failures
panics reading past the end: index 5 out of bounds for length 2
fails an assertion: wrong on purpose
dies after failing: panic
```

What `panic()` says, as each backend's runner reports it:

```failures interp
dies outright: Panicked: panic()
```

```failures cpp
dies outright: Panicked: panic
```

```failures rust
dies outright: explicit panic
```

```failures csharp java js lua py
dies outright: panic
```
