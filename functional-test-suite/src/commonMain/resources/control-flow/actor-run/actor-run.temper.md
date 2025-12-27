# Actor Run

## Test

Tests a yielding function.

    let f(body: fn (): SafeGenerator<Empty>): Void {
      let generator = body();
      console.log("f0");
      generator.next();
      console.log("f1");
      generator.next();
      console.log("f2");
      generator.next();
      console.log("f3");
    };

    f { (): GeneratorResult<Empty> extends GeneratorFn =>
      var i = 0;

      while (true) {
        console.log("generated ${i}");
        i += 1;
        yield();
      }
    }

The lambda passed to `f` is initialized and yields to the caller at `yield`.

```log
f0
generated 0
f1
generated 1
f2
generated 2
f3
```
