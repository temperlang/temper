# Async waiters functional test

This tests that every `async` block awaiting a promise resumes when the
promise is resolved, not only one of them. It does not depend on the order
the waiters run in, or on whether they run before or after the call that
resolves the promise returns: each waiter passes what it got to a promise of
its own, and only the outer block prints.

    async { (): GeneratorResult<Empty> extends GeneratorFn =>

Two blocks await the same promise and a third completes it.

      do {
        let b = new PromiseBuilder<Int>();
        let firstDone = new PromiseBuilder<Int>();
        let secondDone = new PromiseBuilder<Int>();
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          firstDone.complete(v);
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          secondDone.complete(v);
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          b.complete(7);
        }
        let first = await firstDone.promise;
        let second = await secondDone.promise;
        console.log("after complete: first got ${first}, second got ${second}");
      } orelse panic();

```log
after complete: first got 7, second got 7
```

Breaking the promise wakes every waiter too.

      do {
        let b = new PromiseBuilder<Int>();
        let firstDone = new PromiseBuilder<Int>();
        let secondDone = new PromiseBuilder<Int>();
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          firstDone.complete(v);
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          secondDone.complete(v);
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          b.breakPromise();
        }
        let first = await firstDone.promise;
        let second = await secondDone.promise;
        console.log("after breakPromise: first got ${first}, second got ${second}");
      } orelse panic();

```log
after breakPromise: first got -1, second got -1
```

    } // ends async {...}
