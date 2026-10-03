# Async block resumed by top-level code

An async block awaits a promise that only top-level code completes. The
block has to resume after the top-level code finishes, even though no
other async block is running at that point.

    let ready = new PromiseBuilder<String>();

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      console.log(await ready.promise orelse "broken");
    }

The completion happens at top level, after the block has been launched.

    ready.complete("resumed");

The top-level code prints nothing, so the expected output does not depend
on whether a backend starts the block before or after the code that
launched it returns.

```log
resumed
```
