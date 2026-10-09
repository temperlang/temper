# Actor

`@actor` marks a class whose instances take calls one turn at a time. On
the BEAM each instance is a process; elsewhere the class is an ordinary
class, and every rule below already holds on one thread. The output is the
same either way.

A method may call the actor's own methods, with or without `this`. Those
run inline, in the same turn.

    @actor class Account {
      private var balance: Int = 0;
      public deposit(n: Int): Int { balance += n; balance }
      public twice(n: Int): Int { deposit(n); this.deposit(n) }
      public withdraw(n: Int): Int throws Bubble {
        if (n > balance) { bubble() }
        balance -= n;
        balance
      }
    }

    let account = new Account();
    console.log("twice: ${account.twice(5)}");

```log
twice: 10
```

A bubble leaves the turn and reaches the caller, and the actor carries on.

    console.log("withdraw 100: ${account.withdraw(100) orelse -1}");
    console.log("withdraw 3: ${account.withdraw(3) orelse -1}");

```log
withdraw 100: -1
withdraw 3: 7
```

A call back into an actor from inside its own call chain runs inline. Here
`Ping` waits on `Pong`, and `Pong` calls back into `Ping`.

    @actor class Ping {
      private var n: Int = 0;
      public ping(q: Pong): Int { q.pong(this); n }
      public add(k: Int): Void { n += k; }
    }
    @actor class Pong {
      public pong(p: Ping): Void { p.add(1); }
    }
    console.log("ping: ${new Ping().ping(new Pong())}");

```log
ping: 1
```

An `await` is a turn boundary. An async block started by a method belongs
to the actor, and while it is suspended other calls run, so a field read
before the `await` may have changed after it. A promise of a sendable
type may cross the boundary, here as the result of `begin` and of
`blocked`, which settles once the block has reached its `await`.

    @actor class Cell {
      private var v: Int = 0;
      private var gate: PromiseBuilder<Empty> = new PromiseBuilder<Empty>();
      private var waiting: PromiseBuilder<Empty> = new PromiseBuilder<Empty>();
      private var done: PromiseBuilder<Int> = new PromiseBuilder<Int>();
      public begin(): Promise<Int> {
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          waiting.complete(empty());
          let before = v;
          await gate.promise orelse panic();
          console.log("block read ${before} before its await and ${v} after");
          done.complete(v);
        }
        done.promise
      }
      public get blocked(): Promise<Empty> { waiting.promise }
      public set(n: Int): Void { v = n; }
      public release(): Void { gate.complete(empty()); }
    }

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      let cell = new Cell();
      let finished = cell.begin();
      await cell.blocked orelse panic();
      cell.set(5);
      cell.release();
      console.log("block finished with ${await finished orelse -1}");
    }

```log
block read 0 before its await and 5 after
block finished with 5
```
