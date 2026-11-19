# Test fails

`test.fail(message)` records why a test fails and ends it there, as a
failure that no `orelse` catches, not even one in the code under test:
the test reports the message as its own failure, after any soft
`assert`s' before it, and the tests after it still run. Since `fail`
never returns, a null check that leaves through it narrows what
follows. A hard assertion, `assertHard`, and `softFailToHard` end a
test the same way, while soft `assert`s go on, collecting each message.

The code under test here swallows any failure of what it runs, so a
test that went on past a `fail` would fail with "swallowed" too:

    let tries(f: fn (): Void throws Bubble): Boolean {
      var finished = true;
      f() orelse do { finished = false };
      finished
    }

    let find(key: String): Int? {
      if (key == "one") { 1 } else { null }
    }

    test("passes before") {
      assert(tries { assert(find("one") == 1); });
    }

    test("fails past an orelse") { test =>
      tries { test.fail("failed on purpose") };
      test.fail("swallowed");
    }

    test("narrows after fail") { test =>
      let n = find("one");
      if (n == null) { test.fail("one not found") }
      assert(n + 1 == 2);
    }

    test("fails before narrowing") { test =>
      let n = find("two");
      if (n == null) { test.fail("two not found") }
      assert(n + 1 == 3) { "went on" };
    }

    test("collects soft failures") {
      assert(1 + 1 == 3) { "first" };
      assert(true) { "not shown" };
      assert(2 + 2 == 5) { "second" };
    }

    test("fails hard after soft failures") { test =>
      assert(false) { "soft before" };
      test.fail("then hard");
    }

    test("fails a hard assertion past an orelse") { test =>
      tries { test.assertHard(1 + 1 == 3) { "hard assertion" } };
      test.fail("swallowed");
    }

    test("hardens soft failures past an orelse") { test =>
      assert(false) { "soft only" };
      tries { test.softFailToHard() };
      test.fail("swallowed");
    }

    test("passes after") {
      assert(find("one") == 1);
    }

Each failing test, with what its failure says:

```failures
fails past an orelse: failed on purpose
fails before narrowing: two not found
collects soft failures: first, second
fails hard after soft failures: soft before, then hard
fails a hard assertion past an orelse: hard assertion
hardens soft failures past an orelse: soft only
```

What no failure says, as it would if a test went on:

```absent
fails past an orelse: swallowed
fails before narrowing: went on
collects soft failures: not shown
fails a hard assertion past an orelse: swallowed
hardens soft failures past an orelse: swallowed
```
