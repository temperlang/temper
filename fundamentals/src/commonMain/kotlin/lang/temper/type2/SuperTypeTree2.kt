package lang.temper.type2

import lang.temper.common.AtomicCounter
import lang.temper.common.OpenOrClosed
import lang.temper.common.intersect
import lang.temper.lexer.Genre
import lang.temper.log.ConfigurationKey
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext
import lang.temper.log.unknownPos
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.AndType
import lang.temper.type.BubbleType
import lang.temper.type.FunctionType
import lang.temper.type.InvalidType
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.NominalType
import lang.temper.type.OrType
import lang.temper.type.StaticType
import lang.temper.type.TopType
import lang.temper.type.TypeActual
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeParameterShape
import lang.temper.type.TypeShape
import lang.temper.type.TypeShapeImpl
import lang.temper.type.Variance
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.bubbleType2
import lang.temper.type.WellKnownTypes.bubbleTypeDefinition
import lang.temper.type.WellKnownTypes.invalidTypeDefinition
import lang.temper.type.WellKnownTypes.neverTypeDefinition
import lang.temper.type.WellKnownTypes.resultTypeDefinition
import lang.temper.type.WellKnownTypes.voidType2
import lang.temper.type.isNullType
import lang.temper.value.BaseReifiedType
import lang.temper.value.DependencyCategory
import lang.temper.value.Document
import lang.temper.value.DocumentContext
import lang.temper.value.ReifiedType
import lang.temper.value.StayLeaf
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.staySymbol
import lang.temper.value.void
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

private data class HackFnInterop(
    val formals: List<TypeFormal>,
    val nRequired: Int,
    val nOptional: Int,
    val hasRest: Boolean,
    val bubbly: Boolean,
)
private val hackFnTypeDefs = mutableMapOf<HackFnInterop, TypeShape>()
private val hackFnTypeDefsRev = mutableMapOf<TypeShape, HackFnInterop>()

internal val hackDocument = Document(object : DocumentContext {
    override val sharedLocationContext: SharedLocationContext get() = TODO()
    override val definitionMutationCounter: AtomicCounter = invalidTypeDefinition.mutationCount
    override val namingContext: NamingContext = invalidTypeDefinition.name.origin
    override val genre: Genre = Genre.Library
    override val dependencyCategory: DependencyCategory = DependencyCategory.Production
    override val configurationKey: ConfigurationKey get() = TODO()
})

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
        is FunctionType -> {
            val nOptional = t.valueFormals.count { it.isOptional }
            val nRequired = t.valueFormals.size - nOptional
            val hasRest = t.restValuesFormal != null
            val bubbly = t.returnType is OrType && t.returnType.members.any { it is BubbleType }
            val key = HackFnInterop(t.typeFormals, nRequired, nOptional, hasRest = hasRest, bubbly = bubbly)
            val defn = synchronized(hackFnTypeDefs) {
                hackFnTypeDefs.getOrPut(key) {
                    val nameMaker = ResolvedNameMaker(invalidTypeDefinition.name.origin, Genre.Library)
                    val mutationCount = invalidTypeDefinition.mutationCount
                    TypeShapeImpl(
                        invalidTypeDefinition.pos,
                        Symbol("Fn"),
                        nameMaker,
                        Abstractness.Abstract,
                        mutationCount,
                    ).also { it ->
                        hackFnTypeDefsRev[it] = key

                        fun makeTypeFormal(namePrefix: String, variance: Variance): TypeParameterShape {
                            val symbol = Symbol(namePrefix)
                            return TypeParameterShape(
                                it,
                                TypeFormal(
                                    unknownPos,
                                    nameMaker.unusedSourceName(ParsedName(namePrefix)),
                                    symbol,
                                    variance,
                                    mutationCount,
                                    listOf(WellKnownTypes.anyValueType),
                                ),
                                symbol,
                                null,
                            )
                        }
                        val requiredInputs = buildList {
                            repeat(key.nRequired) {
                                add(makeTypeFormal("I", Variance.Contravariant))
                            }
                        }
                        val optionalInputs = buildList {
                            repeat(key.nOptional) {
                                add(makeTypeFormal("I", Variance.Contravariant))
                            }
                        }
                        val restInput = if (key.hasRest) {
                            makeTypeFormal("REST", Variance.Contravariant)
                        } else {
                            null
                        }
                        val output = makeTypeFormal("O", Variance.Covariant)

                        it.superTypes.add(WellKnownTypes.functionType)
                        it.typeParameters.addAll(
                            buildList {
                                addAll(requiredInputs)
                                addAll(optionalInputs)
                                restInput?.let { add(it) }
                                add(output)
                            },
                        )

                        // Create an apply method, so it's signature can be used to inform the signature
                        // of a functional interface type.
                        val applyMethod = MethodShape(
                            enclosingType = it,
                            name = nameMaker.unusedSourceName(ParsedName("apply")),
                            symbol = applyDotName,
                            stay = null,
                            visibility = Visibility.Public,
                            methodKind = MethodKind.Normal,
                            openness = OpenOrClosed.Open,
                        )
                        it.methods.add(applyMethod)
                        applyMethod.descriptor = Signature2(
                            returnType2 = MkType2(output.definition).get().let { outType ->
                                if (bubbly) {
                                    MkType2(resultTypeDefinition)
                                        .actuals(listOf(outType, bubbleType2))
                                        .get()
                                } else {
                                    outType
                                }
                            },
                            hasThisFormal = false, // arguably this is the function, not an arg
                            requiredInputTypes = requiredInputs.map { MkType2(it.definition).get() },
                            optionalInputTypes = optionalInputs.map { MkType2(it.definition).get() },
                            restInputsType = restInput?.let { MkType2(it.definition).get() },
                            typeFormals = emptyList(), // The type formals are on the interface
                        )

                        // Make sure TypeShape metadata is set
                        val hackDecl = hackDocument.treeFarm.grow(unknownPos) {
                            Decl(it.name) {
                                V(staySymbol)
                                Stay()
                                V(functionalInterfaceSymbol)
                                V(void)
                                V(hackSynthesizedFunInterfaceSymbol)
                                V(void)
                            }
                        }
                        it.stayLeaf = hackDecl.parts!!.metadataSymbolMap.getValue(staySymbol).target as StayLeaf
                    }
                }
            }
            val actuals = buildList {
                for (v in t.valueFormals) {
                    add(v.type)
                }
                t.restValuesFormal?.let { add(hackMapOldStyleToNew(it)) }
                val returnTypeNoBubbles = if (bubbly) {
                    MkType.or(t.returnType.members.filter { it !is BubbleType })
                } else {
                    t.returnType
                }
                add(hackMapOldStyleToNewAllowNever(returnTypeNoBubbles))
            }

            MkType2(defn).actuals(actuals).position(pos).get()
        }
        InvalidType -> MkType2(invalidTypeDefinition).position(pos).get()
        TopType -> MkType2(WellKnownTypes.anyValueTypeDefinition).position(pos).canBeNull().get()
        BubbleType -> MkType2(bubbleTypeDefinition).position(pos).get()
        else -> {
            check(t is NominalType) { "$t" }
            when (val d = t.definition) {
                is TypeShape -> MkType2(d)
                    .actuals(t.bindings.map { hackMapOldStyleToNew(it as StaticType) })
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

    val fnInterop = if (definition is TypeShape) {
        synchronized(hackFnTypeDefs) {
            hackFnTypeDefsRev[definition]
        }
    } else {
        null
    }
    val t = when {
        definition == neverTypeDefinition -> OrType.emptyOrType
        definition == resultTypeDefinition -> MkType.or(hackMapNewStyleToOld(bindings[0]), BubbleType)
        definition == bubbleTypeDefinition -> BubbleType

        fnInterop != null -> {
            val returnIndex = t.bindings.lastIndex
            val restIndex = if (fnInterop.hasRest) { returnIndex - 1 } else { null }
            val lastRegularInputIndex = (restIndex ?: returnIndex) - 1
            val nRequired = lastRegularInputIndex + 1 - fnInterop.nOptional
            val valueFormals = (0..lastRegularInputIndex).map { i ->
                FunctionType.ValueFormal(null, hackMapNewStyleToOld(t.bindings[i]), isOptional = i >= nRequired)
            }
            val restValuesFormal = restIndex?.let { hackMapNewStyleToOld(t.bindings[it]) }
            var returnType = hackMapNewStyleToOld(t.bindings[returnIndex])
            if (fnInterop.bubbly) {
                returnType = MkType.or(returnType, BubbleType)
            }
            MkType.fnDetails(
                typeFormals = fnInterop.formals,
                valueFormals = valueFormals,
                restValuesFormal = restValuesFormal,
                returnType = returnType,
            )
        }

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
        restInputsType = ft.restValuesFormal?.let { hackMapOldStyleToNew(it) },
        typeFormals = ft.typeFormals,
    )
}

fun hackMapOldStyleActualsToNew(
    ob: Map<TypeFormal, TypeActual>,
): Map<TypeFormal, Type2> {
    return buildMap {
        for ((tf, v) in ob) {
            this[tf] = hackMapOldStyleToNew(v as StaticType)
        }
    }
}

val invalidSig = Signature2(
    returnType2 = WellKnownTypes.invalidType2,
    hasThisFormal = false,
    requiredInputTypes = listOf(),
    restInputsType = WellKnownTypes.invalidType2,
)
