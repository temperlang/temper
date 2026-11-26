# Test assert panics

`test.assertPanics { … }` runs its block and passes if the block panics,
as reading past a list's end does; if the block returns, the test fails,
saying "expected a panic", or the message given before the block, and
if it throws an error, which is no panic, the test fails saying so. Like a soft
`assert`, a failing `assertPanics` lets the test go on. Only a panic in
the block is caught: one after it fails its test, alone, as ever, and a
`test.fail` in the block still ends the test. What the block changed
before it panicked stays changed.

    sealed interface AtError extends Error {}
    class Unlucky() extends AtError {}

    let at(ls: List<Int>, i: Int): Int throws Unlucky {
      if (i == 99) { throw new Unlucky() }
      ls[i]
    }

    test("passes before") {
      assert(at([1, 2], 1) == 2);
    }

    test("passes when the block panics") { test =>
      test.assertPanics { at([1, 2], 5); };
      test.assertPanics { panic(); };
    }

Integer division by zero panics, as does a cast that fails, on every
backend, whether it's the host's own error (Java's
`ArithmeticException`, C#'s `DivideByZeroException`, Rust's panic) or
the runtime's:

    interface Shape {}
    class Square extends Shape {}
    class Circle extends Shape {}
    let divide(a: Int, b: Int): Int { a / b }
    let remainder(a: Int64, b: Int64): Int64 { a % b }
    let squared(s: Shape): Square { s as Square }

    test("passes when division by zero or a cast panics") { test =>
      test.assertPanics { divide(1, 0); };
      test.assertPanics { remainder(1i64, 0i64); };
      test.assertPanics { squared(new Circle()); };
      squared(new Square());
      assert(divide(-7, 2) == -3);
    }

    test("passes past an orelse") { test =>
      test.assertPanics {
        let n = at([1, 2], 5) orelse -1;
        assert(n == -1) { "caught by an orelse" };
      };
    }

    test("fails when the block returns") { test =>
      test.assertPanics { at([1, 2], 0); };
    }

    test("fails with its message") { test =>
      test.assertPanics("reading index 1 panics") { at([1, 2], 1); };
    }

    test("fails when the block throws") { test =>
      test.assertPanics { at([1, 2], 99); };
    }

    test("fails with its message when the block throws") { test =>
      test.assertPanics("reading index 99 panics") { at([1, 2], 99); };
    }

    test("goes on after failing") { test =>
      test.assertPanics { at([1, 2], 0); };
      assert(false) { "went on" };
    }

    test("nests") { test =>
      test.assertPanics {
        test.assertPanics { panic(); };
        test.assertPanics("inner returns") { at([1, 2], 0); };
        panic();
      };
    }

    test("keeps what the block changed") { test =>
      var n = 0;
      test.assertPanics { n += 1; panic(); };
      assert(n == 1) { "lost the change" };
    }

    test("fails on a panic after the block") { test =>
      test.assertPanics { panic(); };
      at([1, 2], 7);
    }

    test("ends on a fail in the block") { test =>
      test.assertPanics { test.fail("failed inside"); };
      test.fail("swallowed");
    }

A test's body may throw any error, which fails the test, saying the
error's message:

    test("fails when its body throws") {
      at([1, 2], 99);
    }

    test("passes after") {
      assert(at([1, 2], 0) == 1);
    }

Each failing test, with what its failure says:

```failures
fails when the block returns: expected a panic
fails with its message: reading index 1 panics
fails when the block throws: expected a panic, but it threw
fails with its message when the block throws: reading index 99 panics, but it threw
goes on after failing: expected a panic, went on
nests: inner returns
fails on a panic after the block: index 7 out of bounds for length 2
ends on a fail in the block: failed inside
fails when its body throws: unlucky
```

What no failure says:

```absent
fails when the block returns: threw
ends on a fail in the block: swallowed
```
