package lang.temper.common.structure

/**
 * [Structured] values' properties can be marked with hints:
 * - An object, as a whole may be considered equivalent to any value of one of its [Sufficient]
 *   properties.
 * - If an object has one or more [NaturallyOrdered] then the value can be considered equivalent
 *   to an array with those properties' values in the same order they appear in the object.
 * - [Unnecessary] properties can be ignored if the other side of the comparison does not have them.
 */
enum class StructureHint {
    NaturallyOrdered,
    Sufficient,
    Unnecessary,
}

/** Abbreviations for hint sets. */
object Hints {
    val n = setOf(StructureHint.NaturallyOrdered)
    val ns = setOf(StructureHint.NaturallyOrdered, StructureHint.Sufficient)
    val nsu = setOf(
        StructureHint.NaturallyOrdered,
        StructureHint.Sufficient,
        StructureHint.Unnecessary,
    )
    val nu = setOf(StructureHint.NaturallyOrdered, StructureHint.Unnecessary)
    val s = setOf(StructureHint.Sufficient)
    val su = setOf(StructureHint.Sufficient, StructureHint.Unnecessary)
    val u = setOf(StructureHint.Unnecessary)
    val empty = emptySet<StructureHint>()
}
