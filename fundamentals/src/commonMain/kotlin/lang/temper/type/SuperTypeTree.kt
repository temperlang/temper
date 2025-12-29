package lang.temper.type

import lang.temper.common.intersect
import lang.temper.name.ResolvedName

/**
 * Provides a mapping from [TypeDefinition] to a list of [NominalType]
 * instantiations with actual type arguments filled in as applicable. Provides
 * one entry for the current type and each super type.
 */
sealed class SuperTypeTree(
    /** Allows looking up the super-types by their definition */
    val byDefinition: Map<TypeDefinition, List<NominalType>>,
) {

    operator fun get(defn: TypeDefinition): List<NominalType> = byDefinition[defn] ?: emptyList()

    internal val entries: Iterable<Map.Entry<TypeDefinition, List<NominalType>>>
        get() = byDefinition.entries

    companion object {
        val empty: SuperTypeTree = DisconnectedSuperTypeTree(emptyMap())

        fun of(t: NominalType): SuperTypeTreeForType {
            val byDef = mutableMapOf<TypeDefinition, MutableSet<NominalType>>()
            val directSupers = mutableMapOf<NominalType, List<NominalType>>()
            fun buildMap(t: NominalType) {
                if (t !in directSupers) {
                    val directSupersOfT = directSuperTypesOf(t)
                    directSupers[t] = directSupersOfT
                    byDef.getOrPut(t.definition) { mutableSetOf() }.add(t)
                    for (st in directSupersOfT) {
                        buildMap(st)
                    }
                }
            }
            buildMap(t)
            return SuperTypeTreeForType(
                t,
                byDef.mapValues { it.value.toList() },
                directSupers.toMap(),
            )
        }

        operator fun invoke(superTypes: Iterable<NominalType>): SuperTypeTree =
            DisconnectedSuperTypeTree(superTypes.groupBy { it.definition })

        private fun directSuperTypesOf(t: NominalType): List<NominalType> {
            val defn = t.definition
            val formals = t.definition.formals
            val indices = intersect(t.bindings.indices, formals.indices)
            val formalNameToBinding: Map<ResolvedName, TypeActual> = indices.associate {
                formals[it].name to t.bindings[it]
            }
            val mapper = TypeBindingMapper(formalNameToBinding)
            val remapped = defn.superTypes.map { st ->
                MkType.map(st, mapper) as NominalType
            }
            return remapped
        }
    }
}

/**
 * A type tree describing the transitive super-types of a particular type.
 */
class SuperTypeTreeForType internal constructor(
    /** The subtype whose supers are captured herein. */
    val type: NominalType,
    byDefinition: Map<TypeDefinition, List<NominalType>>,
    /**
     * Maps each super-type of [type], to its direct (non-transitive) super-types.
     * Allows navigating the types in depth order.
     */
    val typeToDirectSupers: Map<NominalType, List<NominalType>>,
) : SuperTypeTree(byDefinition)

/**
 * Constructed from a list of types instead of by traversing the types under a particular type.
 */
class DisconnectedSuperTypeTree internal constructor(
    byDefinition: Map<TypeDefinition, List<NominalType>>,
) : SuperTypeTree(byDefinition)

fun SuperTypeTreeForType.forEachSuperType(
    /** Returns true to continue visiting the super-types of the input */
    body: (NominalType) -> Boolean,
) {
    val superTypeDeque = ArrayDeque(listOf(this.type))
    val visited = mutableSetOf<NominalType>()
    while (superTypeDeque.isNotEmpty()) {
        val directSupers = this.typeToDirectSupers[superTypeDeque.removeFirst()]
            ?: continue
        for (superType in directSupers) {
            if (superType in visited) {
                continue
            }
            visited.add(superType)

            val shouldContinue = body(superType)
            if (shouldContinue) {
                superTypeDeque.add(superType)
            }
        }
    }
}
