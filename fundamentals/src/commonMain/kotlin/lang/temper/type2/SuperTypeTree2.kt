package lang.temper.type2

import lang.temper.common.intersect
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.type.AndType
import lang.temper.type.BubbleType
import lang.temper.type.FunctionType
import lang.temper.type.InvalidType
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.OrType
import lang.temper.type.StaticType
import lang.temper.type.TopType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.bubbleType2
import lang.temper.type.WellKnownTypes.bubbleTypeDefinition
import lang.temper.type.WellKnownTypes.invalidTypeDefinition
import lang.temper.type.WellKnownTypes.neverTypeDefinition
import lang.temper.type.WellKnownTypes.resultTypeDefinition
import lang.temper.type.WellKnownTypes.voidType2
import lang.temper.type.isNullType
import lang.temper.value.BaseReifiedType
import lang.temper.value.ReifiedType
import lang.temper.value.functionalInterfaceSymbol
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Provides a mapping from [TypeDefinition] to a list of [Type2]
 * instantiations with actual type arguments filled in as applicable. Provides
 * one entry for the current type and each super type.
 */
sealed class SuperTypeTree2<TYPE : TypeOrPartialType>(
    /** Allows looking up the super-types by their definition */
    val byDefinition: Map<TypeDefinition, List<TYPE>>,
) {
    operator fun get(defn: TypeDefinition): List<TYPE> = byDefinition[defn] ?: emptyList()

    internal val entries: Iterable<Map.Entry<TypeDefinition, List<TYPE>>>
        get() = byDefinition.entries

    companion object {
        val empty: SuperTypeTree2<Nothing> = DisconnectedSuperTypeTree2(emptyMap())

        fun of(t: Type2) = of(t, Type2::class)

        fun of(t: TypeOrPartialType) = of(t, TypeOrPartialType::class)

        fun <TYPE : TypeOrPartialType> of(t: TYPE, c: KClass<TYPE>): SuperTypeTreeForType2<TYPE> {
            val byDef = mutableMapOf<TypeDefinition, MutableSet<TYPE>>()
            val directSupers = mutableMapOf<TYPE, List<TYPE>>()
            fun buildMap(t: TYPE) {
                if (t !in directSupers) {
                    val directSupersOfT = directSuperTypesOf(t, c)
                    directSupers[t] = directSupersOfT
                    byDef.getOrPut(t.definition) { mutableSetOf() }.add(t)
                    for (st in directSupersOfT) {
                        buildMap(st)
                    }
                }
            }
            buildMap(t)
            return SuperTypeTreeForType2(
                c.cast(t),
                byDef.mapValues { it.value.toList() },
                directSupers.toMap(),
            )
        }

        operator fun invoke(superTypes: Iterable<Type2>): SuperTypeTree2<Type2> =
            DisconnectedSuperTypeTree2(superTypes.groupBy { it.definition })

        private fun <TYPE : TypeOrPartialType>directSuperTypesOf(
            t: TYPE,
            c: KClass<TYPE>,
        ): List<TYPE> {
            val defn = t.definition
            val formals = t.definition.formals
            val indices = intersect(t.bindings.indices, formals.indices)
            val formalNameToBinding: Map<TypeFormal, TypeLike> = indices.associate {
                formals[it] to t.bindings[it]
            }
            val remapped = defn.superTypes.map { st ->
                c.cast(
                    hackMapOldStyleToNew(st)
                        .mapType(emptyMap(), formalNameToBinding),
                )
            }
            return remapped
        }
    }
}

/**
 * A type tree describing the transitive super-types of a particular type.
 */
class SuperTypeTreeForType2<TYPE : TypeOrPartialType> internal constructor(
    /** The subtype whose supers are captured herein. */
    val type: TYPE,
    byDefinition: Map<TypeDefinition, List<TYPE>>,
    /**
     * Maps each super-type of [type], to its direct (non-transitive) super-types.
     * Allows navigating the types in depth order.
     */
    val typeToDirectSupers: Map<TYPE, List<TYPE>>,
) : SuperTypeTree2<TYPE>(byDefinition)

/**
 * Constructed from a list of types instead of by traversing the types under a particular type.
 */
class DisconnectedSuperTypeTree2<TYPE : TypeOrPartialType> internal constructor(
    byDefinition: Map<TypeDefinition, List<TYPE>>,
) : SuperTypeTree2<TYPE>(byDefinition)

fun <TYPE : TypeOrPartialType> SuperTypeTreeForType2<TYPE>.forEachSuperType(
    /** Returns true to continue visiting the super-types of the input */
    body: (TYPE) -> Boolean,
) {
    val superTypeDeque = ArrayDeque(listOf(this.type))
    val visited = mutableSetOf<TypeOrPartialType>()
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

fun hackMapOldStyleToNewAllowNever(t: StaticType, pos: Position? = null): Type2 =
    if (t == OrType.emptyOrType) {
        MkType2(neverTypeDefinition).position(pos).get()
    } else {
        hackMapOldStyleToNew(t, pos)
    }

fun hackMapOldStyleToNew(t: StaticType, pos: Position? = null): Type2 =
    when (t) {
        is OrType -> {
            var hasNull = false
            var hasBubble = false
            val ts = t.members.filter {
                when {
                    it.isNullType -> {
                        hasNull = true
                        false
                    }

                    it is BubbleType -> {
                        hasBubble = true
                        false
                    }

                    else -> true
                }
            }
            var t2 = when (ts.size) {
                0 -> MkType2(neverTypeDefinition).position(pos).get()
                1 -> hackMapOldStyleToNew(ts[0], pos)
                else -> error(t)
            }
            if (hasNull) {
                t2 = t2.withNullity(Nullity.OrNull)
            }
            if (hasBubble) {
                t2 = MkType2(resultTypeDefinition).actuals(listOf(t2, bubbleType2)).position(pos).get()
            }
            t2
        }
        is FunctionType -> AdHocArrowTypes.definedTypeForFunctionType(t, pos)
        InvalidType -> MkType2(invalidTypeDefinition).position(pos).get()
        TopType -> MkType2(WellKnownTypes.anyValueTypeDefinition).position(pos).canBeNull().get()
        BubbleType -> MkType2(bubbleTypeDefinition).position(pos).get()
        else -> {
            check(t is NominalType) { "$t" }
            when (val d = t.definition) {
                is TypeShape -> MkType2(d)
                    .actuals(t.bindings.map { hackMapOldStyleToNew(it) })
                    .position(pos)
                    .get()
                is TypeFormal -> MkType2(d).position(pos).get()
            }
        }
    }

/** Marker for synthesized function interface types that aid in the migration from old types to new */
val hackSynthesizedFunInterfaceSymbol = Symbol("hackSynthesizedFunInterface")

fun hackMapOldStyleToNewOrNull(t: StaticType?): Type2? = t?.let { hackMapOldStyleToNew(it) }

fun hackMapOldStyleToNew(t: BaseReifiedType): Type2 = (t as ReifiedType).type2

fun hackMapNewStyleToOld(t: Type2): StaticType {
    val definition = t.definition
    val bindings = t.bindings
    val nullity = t.nullity

    // Try converting an ad-hoc arrow type back to an old-style FunctionType
    val fnType = AdHocArrowTypes.reverseToFnType(t)

    val t = fnType ?: when (definition) {
        neverTypeDefinition -> OrType.emptyOrType
        resultTypeDefinition -> MkType.or(hackMapNewStyleToOld(bindings[0]), BubbleType)
        bubbleTypeDefinition -> BubbleType
        else -> MkType.nominal(definition, bindings.map { hackMapNewStyleToOld(it) })
    }
    return when (nullity) {
        Nullity.NonNull -> t
        Nullity.OrNull -> MkType.nullable(t)
    }
}

private fun siggyType(st: StaticType): StaticType? {
    when (st) {
        is FunctionType -> return st
        is AndType -> {
            for (member in st.members) {
                val mt = siggyType(member)
                if (mt != null) {
                    return mt
                }
            }
        }
        is NominalType -> {
            if (functionalInterfaceSymbol in st.definition.metadata) {
                return st
            }
        }
        else -> {}
    }
    return null
}

fun hackTryStaticTypeToSig(st: StaticType?): Signature2? {
    val t = st?.let { siggyType(it) }
    if (t is NominalType) {
        return withType(hackMapOldStyleToNew(t), fallback = { null }, fn = { _, s, _ -> s })
    }
    val ft = t as? FunctionType ?: return null

    val hasThisFormal = ft.valueFormals.firstOrNull()?.let {
        !it.isOptional && it.symbol?.text == "this"
    } ?: false
    val required = mutableListOf<Type2>()
    val optional = mutableListOf<Type2>()
    for (vf in ft.valueFormals) {
        val vft = vf.type
        if (vf.isOptional) {
            optional.add(vft)
        } else {
            if (optional.isNotEmpty()) { return null }
            required.add(vft)
        }
    }

    val returnType2 = when (val returnType = ft.returnType) {
        BubbleType -> MkType2(resultTypeDefinition).actuals(listOf(voidType2, bubbleType2)).get()
        OrType.emptyOrType -> MkType2(neverTypeDefinition).actuals(listOf(voidType2)).get()
        else -> hackMapOldStyleToNew(returnType)
    }

    return Signature2(
        returnType2 = returnType2,
        hasThisFormal = hasThisFormal,
        requiredInputTypes = required.toList(),
        optionalInputTypes = optional.toList(),
        typeFormals = ft.typeFormals,
    )
}

fun hackMapOldStyleActualsToNew(ob: Map<TypeFormal, StaticType>): Map<TypeFormal, Type2> {
    return buildMap {
        for ((tf, v) in ob) {
            this[tf] = hackMapOldStyleToNew(v)
        }
    }
}

val invalidSig = Signature2(
    returnType2 = WellKnownTypes.invalidType2,
    hasThisFormal = false,
    requiredInputTypes = listOf(),
)
