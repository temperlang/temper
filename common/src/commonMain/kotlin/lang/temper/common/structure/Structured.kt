package lang.temper.common.structure

/**
 * Structured can be reduced to a JSON digests.
 * JSON digests can be flexibly compared via hinting.
 */
interface Structured {
    fun destructure(structureSink: StructureSink)
}
