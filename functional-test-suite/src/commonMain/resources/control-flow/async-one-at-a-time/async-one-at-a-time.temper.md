# Async blocks run one at a time

An `async` block runs until it finishes or reaches an `await`. No other
block runs in the meantime, so blocks that share state need no locks.

    class Counter {
      public var n: Int = 0;
      public bump(tag: String, times: Int): Void {
        console.log("${tag} start");
        for (var i = 0; i < times; ++i) {
          let before = n;
          n = before + 1;
        }
        console.log("${tag} done n=${n.toString()}");
      }
    }

    let c = new Counter();

Three blocks each read and write the same field many times with no `await`
between. Run one after another, they lose no updates. Run in parallel, they
would interleave their `start` lines and end below 300000.

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      c.bump("A", 100000);
    }
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      c.bump("B", 100000);
    }
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      c.bump("C", 100000);
    }

```log
A start
A done n=100000
B start
B done n=200000
C start
C done n=300000
```
