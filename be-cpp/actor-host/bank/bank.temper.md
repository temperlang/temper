# Bank

    @actor export class Account(public owner: String) {
      public var balance: Int = 0;
      private var log: ListBuilder<String> = new ListBuilder<String>();

      public get entries(): Int { log.length }

      public deposit(n: Int): Int {
        balance += n;
        log.add("d");
        balance
      }

      public withdraw(n: Int): Int throws Bubble {
        if (n > balance) { bubble() }
        balance -= n;
        log.add("w");
        balance
      }

      public transferTo(other: Account, n: Int): Void throws Bubble {
        withdraw(n);
        other.deposit(n);
      }

      // Empties `other` into this account by writing its property directly.
      public drain(other: Account): Int {
        balance += other.balance;
        other.balance = 0;
        balance
      }

      // Async steps started during a turn run as their own turns.
      public later(n: Int): Void {
        async { (): GeneratorResult<Empty> extends GeneratorFn =>
          balance += n;
        }
      }

      // A -> B -> A on one thread: `relay` holds this account while `other`
      // calls back into it, which runs inline.
      public relay(other: Account): Int { other.poke(this) }
      public poke(other: Account): Int { other.deposit(1) }
    }
