# Async turns

An `async` block runs until it finishes or reaches an `await`, and no
other block runs meanwhile, so blocks that share state need no locks
(after Temper PR #532's `async-one-at-a-time` and `async-complete-order`
tests). Completing a promise only queues its waiter, so code after
`complete` runs before the waiter resumes.

Host code uses the library the standard way for each language (each
backend's `host_interop` test): it awaits what `later` returns as its own
language's promise, and loops over what `ticks` returns with its own loop.
`ticks` yields `Empty`, reporting each step through `each`. If `each`
fails, the generator is done.

    export let later(n: Int): Promise<Int> {
      let pb = new PromiseBuilder<Int>();
      async { (): GeneratorResult<Empty> extends GeneratorFn =>
        pb.complete(n * 2);
      }
      pb.promise
    }

    @fun export interface Tick(i: Int): Void;

    let generated<T>(make: fn (): SafeGenerator<T>): SafeGenerator<T> { make() }

    export let ticks(n: Int, each: Tick): SafeGenerator<Empty> {
      generated { (): GeneratorResult<Empty> extends GeneratorFn =>
        for (var i = 0; i < n; i += 1) {
          each(i);
          yield();
        }
      }
    }

    let t = ticks(2) { (i: Int): Void => console.log("tick ${i.toString()}"); };
    t.nextSafe();
    t.nextSafe();
    t.nextSafe();

```log
tick 0
tick 1
```

Generators also yield values, lazily: `fibonacci` never ends, so its
callers take what they need, and `collatz` gets its start as its
literal's parameter and yields from a loop that it leaves early. Host
code loops over both with its own language's loop.

    let startedAt<T>(start: Int, make: fn (Int): SafeGenerator<T>): SafeGenerator<T> {
      make(start)
    }

    export let fibonacci(): SafeGenerator<Int> {
      generated { (): GeneratorResult<Int> extends GeneratorFn =>
        var a = 0;
        var b = 1;
        while (true) {
          yield(a);
          let next = a + b;
          a = b;
          b = next;
        }
      }
    }

    export let collatz(start: Int): SafeGenerator<Int> {
      startedAt(start) { (first: Int): GeneratorResult<Int> extends GeneratorFn =>
        var n = first;
        while (true) {
          yield(n);
          if (n == 1) {
            break;
          }
          if (n % 2 == 0) {
            n = n / 2;
          } else {
            n = 3 * n + 1;
          }
        }
      }
    }

`take` steps a generator by `nextSafe`, whose result is a `ValueResult`
until the generator is done. Taking part of `fibonacci` leaves the rest
for later.

    let take(g: SafeGenerator<Int>, n: Int): List<Int> {
      let taken = new ListBuilder<Int>();
      while (taken.length < n) {
        let r = g.nextSafe();
        if (r is ValueResult<Int>) {
          taken.add(r.value);
        } else {
          break;
        }
      }
      taken.toList()
    }

    let shown(ints: List<Int>): String {
      ints.join(" ") { (i: Int): String => i.toString() }
    }

    console.log("fibonacci ${shown(take(fibonacci(), 10))}");
    console.log("collatz ${shown(take(collatz(6), 100))}");
    let fib = fibonacci();
    take(fib, 5);
    console.log("then ${shown(take(fib, 3))}");

```log
fibonacci 0 1 1 2 3 5 8 13 21 34
collatz 6 3 10 5 16 8 4 2 1
then 5 8 13
```

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
    let gateA = new PromiseBuilder<Int>();
    let gateB = new PromiseBuilder<Int>();

Each of two blocks reads and writes the shared counter many times with no
`await` between, awaits its own gate, and does it again. A third block
opens the gates. Run in parallel, the blocks would interleave their
`start` lines and end below 40000.

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      c.bump("A", 10000);
      let k = await gateA.promise orelse -1;
      c.bump("A${k.toString()}", 10000);
    }
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      c.bump("B", 10000);
      let k = await gateB.promise orelse -1;
      c.bump("B${k.toString()}", 10000);
    }
    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      console.log("opener: calling complete");
      gateA.complete(1);
      gateB.complete(2);
      console.log("opener: complete returned");
    }

```log
A start
A done n=10000
B start
B done n=20000
opener: calling complete
opener: complete returned
A1 start
A1 done n=30000
B2 start
B2 done n=40000
```

`halve` throws `Odd` for an odd number, which host code catches by its
own type: an exception class in Python, Java, C#, and JavaScript, an
`Expected`'s error in C++, a table `error` raises in Lua, and an `Err`
in Rust.

    export sealed interface HalveError extends Error {}
    export class Odd(public n: Int) extends HalveError {}

    export let halve(n: Int): Int throws Odd {
      if (n % 2 != 0) { throw new Odd(n) }
      n / 2
    }
