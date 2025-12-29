# Async functional test

This tests asynchronous operations.

This test happens in the context of a synchronous operation:

    async { (): GeneratorResult<Empty> extends GeneratorFn =>

We can create a promise, resolve it, and get the result.

      do {
        let b = new PromiseBuilder<String>();
        b.complete("Completed synchronously");
        console.log(await b.promise);
      } orelse panic();

```log
Completed synchronously
```

We can do the same, but with a broken promise :(

      do {
        let b = new PromiseBuilder<String>();
        b.breakPromise();
        console.log(await b.promise orelse "Broken synchronously");
      }

```log
Broken synchronously
```

And we can complete a promise asynchronously.

      do {
        let b = new PromiseBuilder<String>();
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          b.complete("Completed asynchronously");
        }
        console.log(await b.promise);
      } orelse panic();

```log
Completed asynchronously
```

And a promise can be observed multiple times.

      do {
        let b = new PromiseBuilder<String>();
        b.complete("Worth doing twice");
        console.log(await b.promise);
        console.log(await b.promise);
      } orelse panic();

```log
Worth doing twice
Worth doing twice
```

We don't do things after an `await` until its completed.

      do {
        let a = new PromiseBuilder<Empty>();
        let b = new PromiseBuilder<Empty>();

        // Launch two blocks.  The first doesn't print until
        // the second lets it.
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          await b.promise orelse panic();  // Blocks until unblocked
          console.log("After");
          a.complete(empty());
        };

        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          console.log("Before");
          b.complete(empty()); // Unblocks
        };

        // let those settle before continuing to the test code below
        await a.promise;
      } orelse panic();

```log
Before
After
```

The API doesn't prevent multiple resolution, but the first one wins.

      do {
        console.log("What is your favourite colour?");

        let b = new PromiseBuilder<String>();
        b.complete("Blue");
        b.complete("No, yell...");

        console.log(await b.promise);
      } orelse void;

```log
What is your favourite colour?
Blue
```

    } // ends async {...}
