package lang.temper.frontend

import lang.temper.common.SnapshotKey
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.Position
import lang.temper.value.ControlFlow

/**
 * Collects information about how [ControlFlow] parts were captured while weaving.
 *
 * This helps TyperTest relate lexical blocks to types of names.
 */
internal data class CaptureDigest(val positionToCaptureResult: Map<Position, CaptureResult>)

/**
 * Information about how results from statement like structures were captured.
 */
internal data class CaptureInfo(val digests: List<CaptureDigest>) : Structured {
    operator fun plus(other: CaptureInfo) = CaptureInfo(this.digests + other.digests)

    override fun destructure(structureSink: StructureSink) {
        structureSink.arr {
            for (d in digests) {
                structureSink.obj {
                    for ((p, r) in d.positionToCaptureResult) {
                        key("$p") {
                            when (r) {
                                is KnownValueCaptureResult -> value(r.value)
                                is NameCaptureResult -> value(r.capturedIn.displayName)
                            }
                        }
                    }
                }
            }
        }
    }

    object Key : SnapshotKey<CaptureInfo> {
        override val databaseKeyText: String = "captureInfo"
    }

    companion object {
        val empty = CaptureInfo(listOf())
    }
}
