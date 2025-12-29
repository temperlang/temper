package lang.temper.log

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured

/** Represents a location from which source code is received. */
interface CodeLocation : Structured {
    /**
     * A human-readable description of the code location.  It may be a file path or URL, but
     * may be abbreviated or adjusted and not be relied upon to fetch source.
     */
    val diagnostic: String

    override fun destructure(structureSink: StructureSink) = structureSink.value(diagnostic)
}

object UnknownCodeLocation : CodeLocation {
    override val diagnostic = "<unknown>"
}
