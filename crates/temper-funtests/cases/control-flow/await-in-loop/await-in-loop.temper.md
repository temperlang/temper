# Await in loop

An `await` inside a loop body assigns to a variable declared in that
body. The promise is already complete, so every `await` succeeds.

    let once = new PromiseBuilder<String>();
    once.complete("same");

    let later = new PromiseBuilder<String>();

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      for (var i = 0; i < 2; i += 1) {
        let s = await once.promise orelse "?";
        console.log("${s} ${i}");
      }
      var j = 0;
      while (j < 2) {
        let s = await later.promise orelse "?";
        console.log("${s} ${j}");
        j += 1;
      }
      let t = await once.promise orelse "?";
      console.log("${t} after the loops");
    }

The second loop awaits a promise that is completed only after the block
has started and is waiting on it.

    later.complete("later");

```log
same 0
same 1
later 0
later 1
same after the loops
```
