### `@actor` classes

`@actor` marks a class whose instances are each an actor: calls into one
instance take turns.  The compiler now checks what crosses an actor's
boundary.  Every public method, getter, setter and constructor parameter and
result, and every public property, of an `@actor` class must be sendable:
deeply immutable, another `@actor` class, or a `Promise` of a sendable type.

```temper
class Box { public var v: Int = 0; }

@actor class Account(public owner: String) {
  private var balance: Int = 0;
  public deposit(n: Int): Int { balance += n; balance }
  // Compiler error: Box is mutable, so the caller and the actor would share it.
  public stash(b: Box): Int { b.v }
}
```

`@actor` on an interface, on anything that is not a class, or together with
`@imu` or `@partialImu` is also a compiler error.

The Python backends (`py` and `mypyc`) take turns: host code may call into an
`@actor` instance from several threads, calls run one at a time, a call that
would close a cycle of actors waiting on each other across threads panics
with "actor call cycle" instead of deadlocking, and each step of an `async`
block started during a turn is a turn of that actor.  On the other backends
an `@actor` class still runs like an undecorated class.
