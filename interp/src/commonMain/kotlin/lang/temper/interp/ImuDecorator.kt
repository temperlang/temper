package lang.temper.interp

import lang.temper.value.Value
import lang.temper.value.actorSymbol
import lang.temper.value.imuSymbol
import lang.temper.value.partialImuSymbol
import lang.temper.value.void

/**
 * <!-- snippet: builtin/@imu -->
 * # `@imu` decorator
 * Marker for types that must be deeply immutable.
 */
val imuDecorator = MetadataDecorator(
    imuSymbol,
    name = "@imu",
) {
    void
}

val vImuDecorator = Value(imuDecorator)

/**
 * <!-- snippet: builtin/@partialImu -->
 * # `@partialImu` decorator
 * Marker for types that must be deeply immutable when their actual type
 * parameters are deeply immutable.
 */
val partialImuDecorator = MetadataDecorator(
    partialImuSymbol,
    name = "@partialImu",
) {
    void
}

val vPartialImuDecorator = Value(partialImuDecorator)

/**
 * <!-- snippet: builtin/@actor -->
 * # `@actor` decorator
 * Marks a class whose instances are each an actor: calls into one instance
 * take turns, so its state is only ever touched by one turn at a time.
 *
 * ```temper 15
 * @actor class Account(private var balance: Int) {
 *   public deposit(amount: Int): Int {
 *     balance += amount;
 *     balance
 *   }
 * }
 * new Account(10).deposit(5)
 * ```
 *
 * The rules are the same on every backend:
 *
 * - One turn runs at a time per instance.
 * - A turn is one call into the instance: a method, a getter, a setter, or
 *   the constructor.
 * - An `async` block started during a turn belongs to the actor.  Each step
 *   of it, from its start to its first `await` and from one `await` to the
 *   next, is its own turn.  An `await` is a turn boundary: other calls may
 *   run on the actor while the block waits, so state read before an `await`
 *   may have changed after it.
 * - A call from inside the actor's current chain of calls runs inline, for
 *   example `this.method()`, or a callback the actor invoked that calls back
 *   into it.
 * - A call that would wait on an actor that is itself waiting, directly or
 *   through other actors, on the caller panics with "actor call cycle"
 *   instead of deadlocking.
 * - A bubble or panic leaves the turn, releases the actor, and reaches the
 *   caller.  The actor stays usable.
 * - Only sendable values cross the boundary.  The compiler checks every
 *   public method, getter, setter and constructor parameter and result, and
 *   every public property, of an `@actor` class.  A type is sendable when it
 *   is deeply immutable (see [snippet/builtin/@imu]: `String`, numbers,
 *   `Boolean`, `List` and `Map` of sendable types, and `@imu` classes), an
 *   `@actor` class, a `Promise` of a sendable type, or a nullable sendable
 *   type.  Functions, builders, and ordinary classes are not sendable.
 *   Private members are not checked.
 *
 * `@actor` applies only to classes.  It may not be combined with `@imu` or
 * `@partialImu`, and may not be applied to an interface.
 *
 * ??? warning
 *
 *     The compiler checks which values cross the boundary and where
 *     `@actor` may appear.  The other rules are for backends to implement,
 *     in separate changes.  The Python backends (`py` and `mypyc`) keep
 *     them, including for host code that calls in from several threads.
 *     On the other backends an `@actor` class still runs exactly like an
 *     undecorated one.
 */
val actorDecorator = MetadataDecorator(
    actorSymbol,
    name = "@actor",
) {
    void
}

val vActorDecorator = Value(actorDecorator)
