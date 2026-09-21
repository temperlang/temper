package lang.temper.type2

import lang.temper.common.AtomicCounter
import lang.temper.common.OpenOrClosed
import lang.temper.lexer.Genre
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.ConfigurationKey
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.BubbleType
import lang.temper.type.FunctionType
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.MkType
import lang.temper.type.OrType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeParameterShape
import lang.temper.type.TypeShape
import lang.temper.type.TypeShapeImpl
import lang.temper.type.Variance
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type.WellKnownTypes.bubbleType2
import lang.temper.type.WellKnownTypes.invalidTypeDefinition
import lang.temper.type.WellKnownTypes.resultTypeDefinition
import lang.temper.value.DependencyCategory
import lang.temper.value.Document
import lang.temper.value.DocumentContext
import lang.temper.value.StayLeaf
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.staySymbol
import lang.temper.value.void

/**
 * Temper defines functional interfaces, and it is a long-term goal
 * to only use those when typing references to functions.
 *
 * The frontend, though, assumes that expressions have types, not
 * [Descriptor]s and fixing that will take some time.
 *
 * This object maps between [Signature2]s and [Type2]s in a
 * consistent way so that the frontend can focus first on
 * having new-style descriptors everywhere. Later it can start
 * warning on using ad-hoc arrow types on function inputs and
 * declarations. Finally, we can work on eliminating ad-hoc
 * types on temporaries, and in type inferences.
 */
object AdHocArrowTypes {
    fun isAdhocArrowTypeDefinition(defn: TypeDefinition) =
        defn is TypeShape && synchronized(hackFnTypeDefs) {
            defn in hackFnTypeDefsRev
        }

    fun definedTypeForFunctionType(
        t: FunctionType,
        pos: Position? = null,
    ): DefinedNonNullType {
        val nOptional = t.valueFormals.count { it.isOptional }
        val nRequired = t.valueFormals.size - nOptional
        val hasRest = t.restValuesFormal != null
        val bubbly = t.returnType is OrType && t.returnType.members.any { it is BubbleType }
        val key = HackFnInterop(t.typeFormals, nRequired, nOptional, hasRest = hasRest, bubbly = bubbly)
        val defn = adHocFnTypeDefnFor(key)
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

        return MkType2(defn).actuals(actuals).position(pos).get()
            as DefinedNonNullType
    }

    fun definedTypeForSig(
        sig: Signature2,
        pos: Position? = null,
    ): DefinedNonNullType {
        val nOptional = sig.optionalInputTypes.size
        val nRequired = sig.requiredInputTypes.size
        val hasRest = sig.restValuesFormal != null
        val bubbly = sig.returnType2.definition == resultTypeDefinition
        val key = HackFnInterop(sig.typeFormals, nRequired, nOptional, hasRest = hasRest, bubbly = bubbly)
        val defn = adHocFnTypeDefnFor(key)
        val actuals = buildList {
            addAll(sig.requiredInputTypes)
            addAll(sig.optionalInputTypes)
            sig.restInputsType?.let { add(it) }
            val returnTypeNoBubbles = withType(
                sig.returnType2,
                result = { passType, _, _ -> passType },
                fallback = { it },
            )
            add(returnTypeNoBubbles)
        }

        return MkType2(defn).actuals(actuals).position(pos).get()
            as DefinedNonNullType
    }

    fun toType2(d: Descriptor) = when (d) {
        is Signature2 -> definedTypeForSig(d)
        is Type2 -> d
    }

    fun reverseToFnType(t: Type2): FunctionType? {
        val definition = t.definition as? TypeShape ?: return null
        val fnInterop = synchronized(hackFnTypeDefs) {
            hackFnTypeDefsRev[definition]
        } ?: return null

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
        return MkType.fnDetails(
            typeFormals = fnInterop.formals,
            valueFormals = valueFormals,
            restValuesFormal = restValuesFormal,
            returnType = returnType,
        )
    }

    private fun adHocFnTypeDefnFor(key: HackFnInterop): TypeShape = synchronized(hackFnTypeDefs) {
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
                            adHocPos,
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
                        if (key.bubbly) {
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
                val hackDecl = adHocument.treeFarm.grow(adHocPos) {
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
}

private val adHocument = Document(object : DocumentContext {
    override val sharedLocationContext: SharedLocationContext = object : SharedLocationContext {
        override fun <T : Any> get(loc: CodeLocation, v: CodeLocationKey<T>): T? = null
    }
    override val definitionMutationCounter: AtomicCounter = invalidTypeDefinition.mutationCount
    override val namingContext: NamingContext = invalidTypeDefinition.name.origin
    override val genre: Genre = Genre.Library
    override val dependencyCategory: DependencyCategory = DependencyCategory.Production
    override val configurationKey: ConfigurationKey = object : ConfigurationKey {}
})

private val adHocPos = Position(adHocument.context.namingContext.loc, 0, 0)

private data class HackFnInterop(
    val formals: List<TypeFormal>,
    val nRequired: Int,
    val nOptional: Int,
    val hasRest: Boolean,
    val bubbly: Boolean,
)
private val hackFnTypeDefs = mutableMapOf<HackFnInterop, TypeShape>()
private val hackFnTypeDefsRev = mutableMapOf<TypeShape, HackFnInterop>()
