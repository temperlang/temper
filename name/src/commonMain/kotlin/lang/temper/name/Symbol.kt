package lang.temper.name

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured

/** A value that is significant for its textual value. */
data class Symbol(val text: String) : Structured {
    override fun toString() = "\\$text"

    override fun destructure(structureSink: StructureSink) = structureSink.value(text)

    companion object {
        /** The "name" given to a function argument that has no name. */
        val noName = Symbol("")
    }
}
