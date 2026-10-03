# Async waiters functional test

This tests what happens to `async` blocks that await a promise when the
promise is resolved: every block that awaits it resumes, and none resumes
inside the call to `complete` or `breakPromise`.

    async { (): GeneratorResult<Empty> extends GeneratorFn =>

Two blocks await the same promise and a third completes it. Each waiter
completes a promise of its own, so the outer block can wait for both.

      do {
        let b = new PromiseBuilder<Int>();
        let firstDone = new PromiseBuilder<Empty>();
        let secondDone = new PromiseBuilder<Empty>();
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          console.log("first waiter got ${v}");
          firstDone.complete(empty());
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          console.log("second waiter got ${v}");
          secondDone.complete(empty());
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          console.log("calling complete");
          b.complete(7);
          console.log("complete returned");
        }
        await firstDone.promise;
        await secondDone.promise;
      } orelse panic();

`complete` returns before either waiter runs, and the waiters resume in the
order they started waiting.

```log
calling complete
complete returned
first waiter got 7
second waiter got 7
```

Breaking the promise wakes every waiter the same way.

      do {
        let b = new PromiseBuilder<Int>();
        let firstDone = new PromiseBuilder<Empty>();
        let secondDone = new PromiseBuilder<Empty>();
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          console.log("first waiter got ${v}");
          firstDone.complete(empty());
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          let v = await b.promise orelse -1;
          console.log("second waiter got ${v}");
          secondDone.complete(empty());
        }
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          console.log("calling breakPromise");
          b.breakPromise();
          console.log("breakPromise returned");
        }
        await firstDone.promise;
        await secondDone.promise;
      } orelse panic();

```log
calling breakPromise
breakPromise returned
first waiter got -1
second waiter got -1
```

    } // ends async {...}
