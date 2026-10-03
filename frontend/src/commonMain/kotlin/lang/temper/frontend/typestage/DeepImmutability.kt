package lang.temper.frontend.typestage

import lang.temper.name.Symbol
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.Type2
import lang.temper.value.actorSymbol
import lang.temper.value.imuSymbol
import lang.temper.value.partialImuSymbol

/**
 * Decides which types are deeply immutable, for [ImuChecker], and which
 * may cross an actor's boundary, for [ActorChecker].
 *
 * Caches super-type trees, so one instance should serve one checker run.
 */
internal class DeepImmutability {
    private val superTypesCache = mutableMapOf<Type2, SuperTypeTree2<Type2>>()

    fun superTypeTree(nominalType: Type2): SuperTypeTree2<Type2> =
        // TODO Also cache imuSymbol and partialImuSymbol lookups?
        superTypesCache.getOrPut(nominalType) {
            SuperTypeTree2.of(nominalType)
        }

    /**
     * Null if [type] is deeply immutable, assuming that the types in
     * [presumedImu] are, otherwise the first part of [type] that is not.
     */
    fun findNonImuPart(type: Type2, presumedImu: Set<Type2>): Type2? {
        if (type in presumedImu) { return null }
        return findNonImuPart(type) { findNonImuPart(it, presumedImu) }
    }

    /**
     * Null if a value of [type] may cross an actor's boundary, otherwise
     * the first part of [type] that may not.
     *
     * A type is sendable when it is deeply immutable, an `@actor` class, or a
     * `Promise` of a sendable type, and a `@partialImu` type such as `List`
     * is sendable when its type arguments are.
     */
    fun findNonSendablePart(type: Type2): Type2? {
        if (superTypeTree(type).hasSymbol(actorSymbol)) {
            return null
        }
        if (type.definition == WellKnownTypes.promiseTypeDefinition) {
            return when (type.bindings.size) {
                1 -> findNonSendablePart(type.bindings[0])
                else -> type
            }
        }
        return findNonImuPart(type, ::findNonSendablePart)
    }

    /**
     * Null if [type] is `@imu`, or `@partialImu` with type arguments for
     * which [findProblemInActual] finds no problem.
     */
    private fun findNonImuPart(type: Type2, findProblemInActual: (Type2) -> Type2?): Type2? {
        val superTypeTree = superTypeTree(type)
        if (superTypeTree.hasSymbol(imuSymbol)) {
            return null
        }

        if (
            superTypeTree.hasSymbol(partialImuSymbol) &&
            // Type formals do not have parameters so PartialImu makes little sense there.
            type.definition is TypeShape
        ) {
            if (type.bindings.size != type.definition.formals.size) {
                return type
            }
            for (actual in type.bindings) {
                val problem = findProblemInActual(actual)
                if (problem != null) {
                    return problem
                }
            }
            return null
        }
        return type
    }
}

internal fun Collection<TypeDefinition>.hasSymbol(symbol: Symbol): Boolean = run {
    any { it.metadata.containsKey(symbol) }
}

internal fun SuperTypeTree2<Type2>.hasSymbol(symbol: Symbol): Boolean = run {
    byDefinition.keys.hasSymbol(symbol)
}
