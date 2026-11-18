# Test panics

A test that panics fails alone, saying why, and the tests after it
still run and report, on every backend: only the test runner catches a
panic, test by test, never an `orelse`, as the one here around a read
past a list's end. A failing assertion fails its own test, as ever, and
the others pass:

    let at(ls: List<Int>, i: Int): Int throws Bubble {
      if (i == 99) { bubble() }
      ls[i]
    }

    test("passes before") {
      assert(1 + 1 == 2);
    }

    test("panics reading past the end") {
      let n = at([1, 2], 5) orelse -1;
      assert(n == -1);
    }

    test("panics outright") {
      panic();
    }

    test("fails an assertion") {
      assert(1 + 1 == 3) { "wrong on purpose" };
    }

    test("passes after") {
      assert(at([1, 2], 1) == 2);
    }

Each failing test, with what its failure says:

```failures
panics reading past the end: index 5 out of bounds for length 2
panics outright: panic
fails an assertion: wrong on purpose
```
