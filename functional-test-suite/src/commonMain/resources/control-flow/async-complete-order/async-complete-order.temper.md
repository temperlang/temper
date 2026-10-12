# Completing a promise does not run its waiter

`complete` settles the promise and returns. The block that awaits the promise
resumes later, after the completing block yields or ends, so code after
`complete` runs before the waiter acts.

    let pb = new PromiseBuilder<Int>();
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      console.log("waiter: awaiting");
      let v = await pb.promise orelse -1;
      console.log("waiter: resumed with ${v.toString()}");
    }
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      console.log("completer: calling complete");
      pb.complete(1);
      console.log("completer: complete returned");
    }

```log
waiter: awaiting
completer: calling complete
completer: complete returned
waiter: resumed with 1
```
