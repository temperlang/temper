# Actor turns

Each call into an `@actor` instance is a turn, and turns on one instance do
not overlap. These are the cases that every backend can show with one
thread: calls that start inside a turn run inline, a bubble leaves the turn
and the actor keeps working, and an `async` block started in a turn runs
later, as turns of its own.

    @actor class Account {
      public var balance: Int = 0;

      public deposit(n: Int): Int {
        balance += n;
        balance
      }

A call from the actor to itself runs inline, inside the current turn.

      public depositTwice(n: Int): Int {
        deposit(n);
        deposit(n)
      }

So does a callback the actor invoked that calls back into it.

      private each(xs: List<Int>, f: fn (Int): Void): Void {
        for (let x of xs) { f(x); }
      }

      public depositAll(xs: List<Int>): Int {
        each(xs) { (x: Int): Void => deposit(x); };
        balance
      }

      public withdraw(n: Int): Int throws Bubble {
        if (n > balance) { bubble() }
        balance -= n;
        balance
      }

The block starts during this turn but runs after it ends. It waits for
`gate` before touching any state, and each step of it is a turn of this
actor.

      public depositLater(n: Int, gate: Promise<Empty>): Promise<Int> {
        let done = new PromiseBuilder<Int>();
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          do {
            await gate;
            let seen = balance;
            deposit(n);
            done.complete(seen);
          } orelse done.breakPromise();
        }
        done.promise
      }
    }

    let account = new Account();
    console.log("self call: ${account.depositTwice(5).toString()}");
    console.log("callback: ${account.depositAll([1, 2, 3]).toString()}");

```log
self call: 10
callback: 16
```

A bubble reaches the caller and releases the actor.

    let tooMuch = do { account.withdraw(100).toString() } orelse "bubbled";
    console.log("withdraw 100: ${tooMuch}");
    console.log("withdraw 6: ${account.withdraw(6).toString()}");

```log
withdraw 100: bubbled
withdraw 6: 10
```

The block sees the deposit made after it was started, because it ran later.

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      do {
        let gate = new PromiseBuilder<Empty>();
        let later = account.depositLater(100, gate.promise);
        account.deposit(1000);
        gate.complete(empty());
        let seen = await later;
        console.log("block saw: ${seen.toString()}");
        console.log("balance: ${account.balance.toString()}");
      } orelse panic();
    }

```log
block saw: 1010
balance: 1110
```
