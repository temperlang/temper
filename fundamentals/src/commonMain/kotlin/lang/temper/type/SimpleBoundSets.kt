package lang.temper.type

import lang.temper.common.mapReduceNotEmpty

class SimpleBoundSets private constructor(
    val low: Set<SimpleType>,
    val high: Set<SimpleType>,
) {
    companion object {
        operator fun invoke(t: StaticType): SimpleBoundSets? = when (t) {
            InvalidType -> null
            is SimpleType -> {
                val set = setOf(t)
                SimpleBoundSets(low = set, high = set)
            }
            is AndType -> {
                val memberBoundSets = t.members.map { invoke(it) }
                val nonNullBoundSets = memberBoundSets.filterNotNull()
                if (nonNullBoundSets.isEmpty()) {
                    null
                } else {
                    val low: Set<SimpleType> = if (memberBoundSets.size == nonNullBoundSets.size) {
                        nonNullBoundSets.mapReduceNotEmpty({ it.low }) { a, b ->
                            a intersect b
                        }
                    } else {
                        emptySet()
                    }
                    val high: Set<SimpleType> =
                        nonNullBoundSets.mapReduceNotEmpty({ it.high }) { a, b ->
                            a intersect b
                        }
                    SimpleBoundSets(low = low, high = high)
                }
            }
            is OrType -> if (t.members.isEmpty()) {
                null
            } else {
                val memberBoundSets = t.members.mapNotNull { invoke(it) }
                if (memberBoundSets.size == t.members.size) {
                    val low = memberBoundSets.mapReduceNotEmpty({ it.low }) { a, b -> a union b }
                    val high = memberBoundSets.mapReduceNotEmpty({ it.high }) { a, b -> a union b }
                    SimpleBoundSets(low = low, high = high)
                } else {
                    null
                }
            }
        }
    }
}
