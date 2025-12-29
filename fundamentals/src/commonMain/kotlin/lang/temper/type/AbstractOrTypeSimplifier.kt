package lang.temper.type

/**
 * Machinery to simplify union types.
 *
 * This is generic so that we can do the same simplification steps for [StaticType]s and
 * for the partial types used by the type inference machinery.
 *
 * Simplification is important because it allows us to know, by construction, that there are few
 * types that are structurally distinct but semantically the same.
 * For example:
 * - There is no gap between `AnyValue throws Bubble` and `Top`.
 * - There is no gap between `AnyValue & (C throws Bubble)` and `C`
 */
internal abstract class AbstractOrTypeSimplifier<TYPE : Any>(
    val neverType: TYPE,
    val topType: TYPE,
    val bubbleType: TYPE,
) {
    /** The parts of a union if [t] is a union, else `null` */
    abstract fun alternativesOf(t: TYPE): Set<TYPE>?

    abstract fun isAnyValueType(t: TYPE): Boolean

    fun simplify(alternatives: Iterable<TYPE>): MutableSet<TYPE> {
        // Group together proper types
        val membersFlat = mutableSetOf<TYPE>()
        alternatives.forEach { alternative ->
            val nestedAlternatives = alternativesOf(alternative)
            if (nestedAlternatives != null) {
                nestedAlternatives.forEach {
                    membersFlat.add(it)
                }
            } else {
                membersFlat.add(alternative)
            }
        }
        membersFlat.remove(neverType) // Simplify out bottom type
        if (topType in membersFlat) {
            // Top subsumes everything else out
            membersFlat.clear()
            membersFlat.add(topType)
        }
        if (bubbleType in membersFlat && membersFlat.any { isAnyValueType(it) }) {
            // Bubble | AnyValue == Top
            membersFlat.clear()
            membersFlat.add(topType)
        }

        return membersFlat
    }
}
