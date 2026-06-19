package lang.temper.interp

import lang.temper.value.Value
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
 * Marker interface for types that must be deeply immutable when
 * their actual type parameters are deeply immutable.
 */
val partialImuDecorator = MetadataDecorator(
    partialImuSymbol,
    name = "@partialImu",
) {
    void
}

val vPartialImuDecorator = Value(partialImuDecorator)
