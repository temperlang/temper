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

    test("panics in an async block") {
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        panic();
      }
    }

    test("passes after") {
      assert(1 + 1 == 2);
    }

Each failing test, with what its failure says:

```failures
fails in an async block: failed in async
fails in a later turn: later turn got 1
fails hard in an async block: failed hard in async
panics in an async block: panic
```
