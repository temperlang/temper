package lang.temper.interp

import lang.temper.value.Value
import lang.temper.value.imuSymbol
import lang.temper.value.partialImuSymbol
import lang.temper.value.void

val imuDecorator = MetadataDecorator(
    imuSymbol,
    name = "@imu",
) {
    void
}

val vImuDecorator = Value(imuDecorator)

val partialImuDecorator = MetadataDecorator(
    partialImuSymbol,
    name = "@partialImu",
) {
    void
}

val vPartialImuDecorator = Value(partialImuDecorator)
