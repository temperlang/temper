package lang.temper.log

import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureHint
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder

/**
 * A range of adjacent character positions in a source file.
 */
data class Position(
    val loc: CodeLocation,
    val left: Int,
    val right: Int,
) : Structured, Positioned {
    val leftEdge get() = if (left != right) this.copy(right = left) else this
    val rightEdge get() = if (left != right) this.copy(left = right) else this
    val size get() = right - left

    override val pos: Position get() = this

    operator fun contains(pos: Position): Boolean =
        pos.loc == loc && left <= pos.left && right >= pos.right

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        positionPropertiesTo(this, emptySet())
    }

    fun positionPropertiesTo(propertySink: PropertySink, hints: Set<StructureHint>) {
        propertySink.key("loc", hints) { value(loc) }
        propertySink.key("left", hints) { value(left) }
        propertySink.key("right", hints) { value(right) }
    }

    override fun toString(): String =
        if (left == right) {
            "${loc.diagnostic}+$left"
        } else {
            "${loc.diagnostic}+$left-$right"
        }

    fun toString(positions: Map<FilePath, FilePositions>): String = toString(
        (this.loc as? FileRelatedCodeLocation)?.let {
            positions[it.sourceFile]
        },
    )

    fun toString(positions: FilePositions?) =
        if (positions == null || positions.codeLocation !== loc) {
            toString()
        } else {
            toStringViaBuilder { sb ->
                sb.append(loc)
                sb.append(':')
                val lp = positions.filePositionAtOffset(left)
                sb.append(lp.line)
                sb.append('+')
                sb.append(lp.charInLine)
                if (left != right) {
                    val rp = positions.filePositionAtOffset(right)
                    if (rp.line != lp.line) {
                        sb.append(" - ")
                        sb.append(rp.line)
                        sb.append('+')
                    } else {
                        sb.append('-')
                    }
                    sb.append(rp.charInLine)
                }
            }
        }
}
