# Test async

A test ends only once the async work it started has run as far as it
can, so what fails there fails that test: a soft `assert` in an `async`
block, one in a later turn, after an `await`, a `test.fail`, and a
panic. Each counts for the test that started the block, not the next
one, and a test whose block passes passes.

    test("fails in an async block") {
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        assert(1 + 1 == 3) { "failed in async" };
      }
    }

    test("passes in an async block") {
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        assert(1 + 1 == 2) { "not shown" };
      }
    }

The first block waits for a promise that the second, which starts after
it, completes, so its `assert` runs in a later turn:

    test("fails in a later turn") {
      let pb = new PromiseBuilder<Int>();
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        let n = await pb.promise orelse -1;
        assert(n == 2) { "later turn got ${n.toString()}" };
      }
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        pb.complete(1);
      }
    }

    test("passes after a later turn") {
      assert(1 + 1 == 2);
    }

    test("fails hard in an async block") { test =>
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        test.fail("failed hard in async");
      }
    }

    test("dies in an async block") {
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        panic();
      }
    }

    test("passes after") {
      assert(1 + 1 == 2);
    }

A block that waits for a promise nothing completes stays waiting, and
its test ends once nothing else can run, passing, as nothing failed:

    test("waits on nothing") {
      let never = new PromiseBuilder<Int>();
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        let n = await never.promise orelse -1;
        assert(false) { "ran ${n}" };
      }
    }

A test's own failure wins over what fails after it, as its async work
and its soft failures, which still end it, so it fails saying why it
did, here by reading past a list's end:

    test("own failure wins") { test =>
      assert(false) { "soft failure" };
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        test.fail("async failure");
      }
      let ls = [1];
      assert(ls[5] == 1);
    }

Each failing test, with what its failure says:

```failures
fails in an async block: failed in async
fails in a later turn: later turn got 1
fails hard in an async block: failed hard in async
```

```failures ~cpp ~lua
own failure wins: index 5 out of bounds for length 1
```

C++ and Lua run an `async` block's first step at once, in the body, so
there its `test.fail` ends the test first:

```failures cpp lua
own failure wins: async failure
```

What `panic()` says, as each backend's runner reports it:

```failures interp
dies in an async block: Panicked: panic()
```

```failures cpp
dies in an async block: Panicked: panic
```

```failures rust
dies in an async block: explicit panic
```

```failures csharp java js lua py
dies in an async block: panic
```

What no failure says:

```absent
waits on nothing: ran
```
