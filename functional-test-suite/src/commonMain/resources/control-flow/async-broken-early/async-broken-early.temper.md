# Async broken early

A promise can be broken before the async block that awaits it has
started. The `orelse` on that `await` still handles it.

    let b = new PromiseBuilder<Int>();
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      let v = await b.promise orelse -1;
      console.log("got ${v}");
    }

The block has not reached its `await` yet when top-level code breaks the
promise.

    b.breakPromise();

```log
got -1
```
