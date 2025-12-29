package lang.temper.type

/**
 * Like [AbstractOrTypeSimplifier] but for intersection types.
 */
internal abstract class AbstractAndTypeSimplifier<TYPE : Any>(
    val neverType: TYPE,
    val topType: TYPE,
    val bubbleType: TYPE,
) {
    /** The parts of a union if [t] is a union, else `null` */
    abstract fun alternativesOf(t: TYPE): Set<TYPE>?

    /** The parts of an intersection if [t] is an intersection, else `null` */
    abstract fun requirementsOf(t: TYPE): Set<TYPE>?

    abstract fun isAnyValueType(t: TYPE): Boolean

    abstract fun isNominalType(t: TYPE): Boolean

    abstract fun isFunctionType(t: TYPE): Boolean

    abstract fun makeOr(alternatives: Iterable<TYPE>): TYPE

    fun simplify(requirements: Iterable<TYPE>): MutableSet<TYPE> {
        // Group together proper types
        val membersFlat = mutableSetOf<TYPE>()
        requirements.forEach { requirement ->
            val nestedRequirements = requirementsOf(requirement)
            if (nestedRequirements != null) {
                membersFlat.addAll(nestedRequirements)
            } else {
                membersFlat.add(requirement)
            }
        }
        membersFlat.remove(topType) // Simplify out top type
        if (neverType in membersFlat) {
            // Bottom cancels everything else out
            membersFlat.clear()
            membersFlat.add(neverType)
        }

        val anyValueType = membersFlat.firstOrNull { isAnyValueType(it) }
        if (anyValueType != null) {
            // Simplify ((A | Bubble) & AnyValue) -> A
            var subtractedFromAll = true
            var subtractedFromAny = false
            for (flatMember in membersFlat) {
                // AnyType does not affect subtractedFrom{All,Any}
                if (flatMember == anyValueType) { continue }

                val flatMemberAlternatives = alternativesOf(flatMember)
                if (flatMemberAlternatives != null && bubbleType in flatMemberAlternatives) {
                    subtractedFromAny = true
                } else {
                    subtractedFromAll = false
                }
            }
            for (flatMember in membersFlat) {
                val flatMemberAlternatives = alternativesOf(flatMember)
                if (flatMemberAlternatives != null && bubbleType in flatMemberAlternatives) {
                    subtractedFromAny = true
                } else {
                    subtractedFromAll = false
                }
            }
            if (subtractedFromAny) {
                if (subtractedFromAll) {
                    membersFlat.remove(anyValueType)
                }
                val membersFlatBefore = membersFlat.toList()
                membersFlat.clear()
                membersFlatBefore.mapTo(membersFlat) { flatMember ->
                    val flatMemberAlternatives = alternativesOf(flatMember)
                    if (flatMemberAlternatives != null && bubbleType in flatMemberAlternatives) {
                        makeOr(
                            buildSet {
                                flatMemberAlternatives.filterTo(this) { it != bubbleType }
                            },
                        )
                    } else {
                        flatMember
                    }
                }
            }

            // Simplify (NominalType & AnyValueType) -> NominalType
            // since every nominal type is a sub-type of AnyValueType
            if (!(subtractedFromAny && subtractedFromAll)) {
                if (membersFlat.any { isAnyValueStrictSubType(it, anyValueType) }) {
                    membersFlat.remove(anyValueType)
                }
            }
        }

        if (bubbleType in membersFlat && membersFlat.any { isNominalType(it) }) {
            // Bubble is disjoint from any nominal type
            membersFlat.clear()
            membersFlat.add(neverType)
        }

        return membersFlat
    }

    private fun isAnyValueStrictSubType(t: TYPE, anyValueType: TYPE): Boolean {
        if (isNominalType(t)) {
            return t != anyValueType
        }
        if (isFunctionType(t)) {
            return true
        }
        val alternatives = alternativesOf(t)
        return alternatives != null &&
            alternatives.all { isAnyValueStrictSubType(it, anyValueType) }
    }
}
