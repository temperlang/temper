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
 * Marker for classes whose instances are each a concurrent actor, on
 * backends that have them: on the BEAM, each instance is its own process,
 * its methods run there, and any process may hold it. Other backends ignore
 * it, so the class behaves exactly as an undecorated one.
 */
val actorDecorator = MetadataDecorator(
    actorSymbol,
    name = "@actor",
) {
    void
}

val vActorDecorator = Value(actorDecorator)
