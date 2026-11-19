# Test assert panics

`test.assertPanics { … }` runs its block and passes if the block panics,
as reading past a list's end does; if the block returns, the test fails,
saying "expected a panic", or the message given before the block, and
if it bubbles, which is no panic, the test fails saying so. Like a soft
`assert`, a failing `assertPanics` lets the test go on. Only a panic in
the block is caught: one after it fails its test, alone, as ever, and a
`test.fail` in the block still ends the test. What the block changed
before it panicked stays changed.

    let at(ls: List<Int>, i: Int): Int throws Bubble {
      if (i == 99) { bubble() }
      ls[i]
    }

    test("passes before") {
      assert(at([1, 2], 1) == 2);
    }

    test("passes when the block panics") { test =>
      test.assertPanics { at([1, 2], 5); };
      test.assertPanics { panic(); };
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

    test("fails when the block bubbles") { test =>
      test.assertPanics { at([1, 2], 99); };
    }

    test("fails with its message when the block bubbles") { test =>
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

    test("passes after") {
      assert(at([1, 2], 0) == 1);
    }

Each failing test, with what its failure says:

```failures
fails when the block returns: expected a panic
fails with its message: reading index 1 panics
fails when the block bubbles: expected a panic, but it bubbled
fails with its message when the block bubbles: reading index 99 panics, but it bubbled
goes on after failing: expected a panic, went on
nests: inner returns
fails on a panic after the block: index 7 out of bounds for length 2
ends on a fail in the block: failed inside
```

What no failure says:

```absent
fails when the block returns: bubbled
ends on a fail in the block: swallowed
```
