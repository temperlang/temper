package lang.temper.frontend.json

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.common.asciiTitleCase
import lang.temper.common.buildMapMultimap
import lang.temper.common.dtree.DecisionTree
import lang.temper.common.dtree.buildDecisionTree
import lang.temper.common.putMultiList
import lang.temper.common.putMultiMap
import lang.temper.interp.New
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.Position
import lang.temper.name.ModularName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.ResolvedParsedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.Temporary
import lang.temper.name.identifiers.IdentStyle
import lang.temper.type.Abstractness
import lang.temper.type.DotHelper
import lang.temper.type.ExternalBind
import lang.temper.type.ExternalGet
import lang.temper.type.MkType
import lang.temper.type.MutableTypeFormal
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.TypeActual
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypePartMapper
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type.canOnlyBeNull
import lang.temper.type.isNeverType
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.value.BubbleFn
import lang.temper.value.Document
import lang.temper.value.DocumentContext
import lang.temper.value.ErrorFn
import lang.temper.value.FunTree
import lang.temper.value.IsNullFn
import lang.temper.value.PanicFn
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TString
import lang.temper.value.TType
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.caseIsSymbol
import lang.temper.value.caseSymbol
import lang.temper.value.defaultSymbol
import lang.temper.value.dotBuiltinName
import lang.temper.value.ifBuiltinName
import lang.temper.value.initSymbol
import lang.temper.value.outTypeSymbol
import lang.temper.value.returnBuiltinName
import lang.temper.value.returnedFromSymbol
import lang.temper.value.typeFormalSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.void

private const val DECODE_METHOD_NAME = "decodeFromJson"
private const val ENCODE_METHOD_NAME = "encodeToJson"

/**
 * Error messages related to generating adapter code for Json-compatible types.
 */
enum class JsonInteropMessage(
    override val formatString: String,
    override val suggestedLevel: Log.Level = Log.Error,
) : LeveledMessageTemplate {
    MissingToJsonMethod(
        "%s declared $DECODE_METHOD_NAME but has no corresponding $ENCODE_METHOD_NAME",
    ),
    MissingFromJsonMethod(
        "%s declared $ENCODE_METHOD_NAME but has no corresponding $DECODE_METHOD_NAME",
    ),
    NoJsonStrategyForAbstractType("Cannot auto-derive JSON adapter for un-sealed interface %s"),
    MissingTypeInfoForEncodedProperty("Missing type info for JSON adapter for property %s.%s"),
    UnencodableValue("Cannot JSON encode fixed value %s for property %s.%s"),
    PropertyNotUniquelyIdentified("Cannot JSON encode ambiguous property %s.%s"),
    NoAdapterForPropertyWithType("No JsonAdapter for property %s with type %s"),
    NoJsonOnSubTypeOfJsonSealedType(
        "No @json decoration on %s which is a sub-type of @json type %s",
    ),
    AmbiguousDecodingForSealedType(
        "Ambiguous @json: decoder for sealed type %s cannot distinguish %s",
    ),
}

/**
 * Relates type formals on a `@json` type definitions to type formals in generated code.
 *
 * For example, to generate a static method that decodes a `Class<T>`, the static method
 * needs its own `<T>`.
 *
 *     @json class C<T> {
 *       ...
 *       // generated code
 *       // local ━━━━━━━━━━┓  uses━━━━━━━━━━━━━━━━━━┳━━━━━━━━━━━━━━━━━━┳━━━━━━━━┓
 *       public jsonAdapter<T>(tAdapter: JsonAdapter<T>): JsonAdapter<C<T>> { // ┃
 *         //                      ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 *         return new CJsonAdapter<T>(tAdapter);
 *       }
 *     }
 */
private class AdaptFormals(
    pos: Position,
    val docContext: DocumentContext,
    nameMaker: ResolvedNameMaker,
    typeFormals: Iterable<TypeFormal>,
) {
    private val nameMap: Map<TypeFormal, ResolvedName> = buildMap {
        typeFormals.forEach {
            this[it] = nameMaker.unusedModularName(it.name)
        }
    }
    val allFormals: List<TypeFormal> = typeFormals.map { tf ->
        val newName = nameMap.getValue(tf)
        TypeFormal(
            pos,
            newName,
            tf.word,
            tf.variance,
            docContext.definitionMutationCounter,
            upperBounds = emptyList(), // Filled later
        )
    }
    private val formalByName = allFormals.associateBy { it.name }
    val typeMapper: TypePartMapper = object : TypePartMapper {
        override fun mapType(t: StaticType): StaticType {
            if (t is NominalType && t.bindings.isEmpty()) {
                val targetName = nameMap[t.definition]
                if (targetName != null) {
                    // The use of getValue here assumes that type formal's upper bounds
                    // only refer to previously declared type formals.
                    return MkType.nominal(formalByName.getValue(targetName))
                }
            }
            return t
        }

        override fun mapBinding(b: TypeActual): TypeActual {
            if (b is StaticType) { return mapType(b) }
            return b
        }

        override fun mapDefinition(d: TypeDefinition): TypeDefinition = d
    }

    fun remapNominal(t: NominalType): NominalType = remap(t as StaticType) as NominalType

    fun remap(t: StaticType): StaticType = MkType.map(t, typeMapper)

    init {
        for ((oldTf, newTf) in typeFormals zip allFormals) {
            check(newTf is MutableTypeFormal)
            check(newTf.upperBounds.isEmpty())
            newTf.upperBounds.addAll(oldTf.upperBounds.map { remapNominal(it) })
        }
    }
}

/**
 * Subsidiary adapters map type formals to adapters.
 *
 * Sometimes we need expressions for adapters for type variables.
 *
 *     @json class Pair<A, B>(
 *       public let a: A,
 *       public let b: B,
 *     ) {}
 *
 * A type adapter for the *Pair* class needs subsidiary adapters for *A* and *B*.
 *
 * In the context,
 *
 * This also stores some extra state about the context.
 * For a recursive type, we might already know the type adapter.
 *
 *     @json class ConsList<T>(
 *       public let head: T,
 *       public let tail: ConsList<T>?;
 *     )
 *
 * From within a *ConsListAdapter<T>* we need an adapter for the tail,
 * but the expression *this* already refers to an adapter for *ConsList<T>*.
 * The overrides field us to attach extra info.
 */
private class Subsidiaries(
    val p: JsonInteropPass,
    typeFormals: Iterable<TypeFormal>,
    extras: List<Subsidiary> = emptyList(),
) {
    val adaptedTypeToLocalName: Map<Type2, Subsidiary> = buildMap {
        extras.forEach {
            this[it.adaptedType] = it
        }

        for (typeFormal in typeFormals) {
            if (typeFormal.needsAdapting) {
                val newName = p.nameMaker.unusedSourceNameWithPrefix(prefix = "adapterFor", typeFormal.name)
                val nt = MkType2(typeFormal).get()
                if (nt !in this) {
                    this[nt] = Subsidiary(nt, newName, typeFormal, wasAllocated = true)
                }
            }
        }
    }

    fun plantAdapterParameters(planting: Planting) {
        for (sub in adaptedTypeToLocalName.values) {
            if (sub.wasAllocated) {
                planting.Decl(sub.name) {
                    V(typeSymbol)
                    V(
                        Value(
                            ReifiedType(
                                MkType2(p.details.stdJson.typeJsonAdapter)
                                    .actuals(listOf(sub.adaptedType))
                                    .get(),
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun plantAdapterFor(planting: Planting, t: Type2?, pos: Position, errContext: Any) {
        val knownAdapter = adaptedTypeToLocalName[t]
        if (knownAdapter != null) {
            planting.Rn(knownAdapter.name)
            return
        }
        if (t?.isNeverType == true) {
            // never type -> bubble()
            planting.Call(BubbleFn) {}
            return
        }
        if (t is DefinedNonNullType) {
            val shape = t.definition
            // TypeName.jsonAdapter(...)
            planting.Call {
                Call {
                    Rn(dotBuiltinName)
                    V(Value(ReifiedType(MkType2(shape).get(), hasExplicitActuals = false)))
                    V(jsonAdapterDotName)
                }
                t.bindings.forEach {
                    // TODO: should we at least detect recursive types
                    plantAdapterFor(this, it, pos, errContext)
                }
            }
            return
        }
        if (t?.nullity == Nullity.OrNull) {
            // split null adapter out to OrNullAdapter construction
            val tMinusNull = t.withNullity(Nullity.NonNull)
            // new OrNullJsonAdapter<T_MINUS_NULL>(adapterForTMinusNull)
            planting.Call(New) {
                V(
                    Value(
                        ReifiedType(
                            MkType2(p.details.stdJson.typeOrNullJsonAdapter)
                                .actuals(listOf(tMinusNull))
                                .get(),
                        ),
                    ),
                )
                plantAdapterFor(this, tMinusNull, pos, errContext)
            }
            return
        }
        val err = if (t == null) {
            LogEntry(
                JsonInteropMessage.MissingTypeInfoForEncodedProperty,
                pos,
                listOf(errContext),
            )
        } else {
            LogEntry(
                JsonInteropMessage.NoAdapterForPropertyWithType,
                pos,
                listOf(errContext, t),
            )
        }
        planting.Call(ErrorFn) {
            V(Value(err, TProblem))
        }
        err.logTo(p.logSink)
    }

    data class Subsidiary(
        val adaptedType: Type2,
        val name: ModularName,
        val correspondingTypeFormal: TypeFormal?,
        /** Was this allocated?  True if the caller is responsible for declaring the name. */
        val wasAllocated: Boolean,
    )
}

/**
 * Generates methods and classes to augment existing classes that opt into JSON interop.
 */
internal class JsonInteropPass(
    document: Document,
    val logSink: LogSink,
    val details: JsonInteropDetails,
) {
    val docContext = document.context
    val nameMaker = ResolvedNameMaker(docContext.namingContext, docContext.genre)
    private val addedMethods =
        mutableMapOf<ResolvedName, MutableList<JsonInteropChanges.AddedMethod>>()
    private val adapterClasses = mutableListOf<JsonInteropChanges.AddedType>()

    fun computeChanges(): JsonInteropChanges {
        for (typeDecl in details.localTypes.values) {
            if (typeDecl.hasJsonDecoration) {
                computeChangesForType(typeDecl)
            }
        }

        return JsonInteropChanges(
            addedMethods.mapValues { it.value.toList() },
            adapterClasses.toList(),
        )
    }

    private fun computeChangesForType(typeDecl: JsonInteropDetails.TypeDecl) {
        if (typeDecl.fromJsonMethod.available || typeDecl.toJsonMethod.available) {
            computeChangesUsingCustomStrategy(typeDecl)
        } else if (typeDecl.abstractness == Abstractness.Concrete) {
            computeChangesUsingConcreteClassStrategy(typeDecl)
        } else if (typeDecl.isSealed) {
            computeChangesUsingSealedInterfaceStrategy(typeDecl)
        } else {
            logSink.log(
                JsonInteropMessage.NoJsonStrategyForAbstractType,
                typeDecl.pos, listOf(typeDecl.name),
            )
        }
    }

    private fun computeChangesUsingCustomStrategy(typeDecl: JsonInteropDetails.TypeDecl) {
        if (!typeDecl.fromJsonMethod.available) {
            logSink.log(JsonInteropMessage.MissingFromJsonMethod, typeDecl.pos, listOf(typeDecl.name))
        } else if (!typeDecl.toJsonMethod.available) {
            logSink.log(JsonInteropMessage.MissingToJsonMethod, typeDecl.pos, listOf(typeDecl.name))
        }
        adapterClass(
            typeDecl,
            enc = { pos, adaptFormals, subs ->
                // Argument to encode
                val x = nameMaker.unusedSourceName(ParsedName("x"))
                val xType = MkType2(typeDecl.definition)
                    .actuals(adaptFormals.allFormals.map { MkType2(it).get() })
                    .get()
                val p = nameMaker.unusedSourceName(ParsedName("p"))
                Fn(pos) {
                    Decl(x) {
                        V(typeSymbol)
                        V(Value(ReifiedType(xType)))
                    }
                    Decl(p) {
                        V(typeSymbol)
                        V(Value(ReifiedType(MkType2(details.stdJson.typeJsonProducer).get())))
                    }
                    V(outTypeSymbol)
                    V(Types.vVoid)
                    V(returnedFromSymbol)
                    V(TBoolean.valueTrue)
                    Block {
                        Call {
                            Call {
                                Rn(dotBuiltinName)
                                Rn(x)
                                V(encodeToJsonDotName)
                            }
                            // TODO: pass type parameters
                            Rn(p)
                            subs.adaptedTypeToLocalName.forEach {
                                Rn(it.value.name)
                            }
                        }
                    }
                }
            },
            dec = { pos, adaptFormals, subs ->
                val t = nameMaker.unusedSourceName(ParsedName("t"))
                val ic = nameMaker.unusedSourceName(ParsedName("ic"))
                val decodedType = MkType2(typeDecl.definition)
                    .actuals(adaptFormals.allFormals.map { MkType2(it).get() })
                    .get()
                Fn(pos) {
                    Decl(t) {
                        V(typeSymbol)
                        V(Value(ReifiedType(MkType2(details.stdJson.typeJsonSyntaxTree).get())))
                    }
                    Decl(ic) {
                        V(typeSymbol)
                        V(Value(ReifiedType(MkType2(details.stdJson.typeInterchangeContext).get())))
                    }
                    V(outTypeSymbol)
                    V(Value(ReifiedType(MkType2.result(decodedType, WellKnownTypes.bubbleType2).get())))
                    V(returnedFromSymbol)
                    V(TBoolean.valueTrue)
                    Block {
                        Call {
                            Call {
                                Rn(dotBuiltinName)
                                V(Value(ReifiedType(MkType2(typeDecl.definition).get())))
                                V(decodeFromJsonDotName)
                            }
                            Rn(t)
                            Rn(ic)
                            subs.adaptedTypeToLocalName.forEach { (_, sub) ->
                                if (sub.wasAllocated) {
                                    Rn(sub.name)
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    private fun computeChangesUsingConcreteClassStrategy(typeDecl: JsonInteropDetails.TypeDecl) {
        val properties = typeDecl.properties
        val extraMethodPos = typeDecl.pos.rightEdge
        val thisType = MkType2(typeDecl.definition)
            .actuals(typeDecl.typeFormals.map { MkType2(it).get() })
            .get()
        val countOfSymbols = buildMap {
            properties.forEach {
                this[it.symbol] = (this[it.symbol] ?: 0) + 1
            }
        }

        // We add encodeToJson and decodeFromJsonProperties and then delegate to the
        // custom strategy to define the rest.

        // Define an encodeToJson method that has private access to the backed properties.
        // For `class C { private x: Int }` we get the below:
        //
        //    public encodeToJson(/*this: C, */p: JsonTextProducer, ...): Void {
        //      p.startObject();
        //      p.objectKey("x");
        //      Int.jsonAdapter.encodeToJson(x, p);
        //      p.endObject();
        //    }
        addedMethods.putMultiList(
            typeDecl.name,
            JsonInteropChanges.AddedMethod(
                isStatic = false,
                visibility = Visibility.Public,
                name = encodeToJsonDotName,
                body = {
                    val p = nameMaker.unusedSourceName(ParsedName("p"))
                    val subs = Subsidiaries(this@JsonInteropPass, typeDecl.typeFormals)
                    Fn(extraMethodPos) {
                        Decl(p) {
                            V(typeSymbol)
                            V(Value(ReifiedType(MkType2(details.stdJson.typeJsonProducer).get())))
                        }
                        subs.plantAdapterParameters(this)
                        V(outTypeSymbol)
                        V(Types.vVoid)
                        V(returnedFromSymbol)
                        V(TBoolean.valueTrue)
                        Block {
                            Call {
                                Call(DotHelper(ExternalBind, startObjectDotName)) {
                                    Rn(p)
                                }
                            }
                            properties.forEach { prop ->
                                if (!prop.shouldEncode) {
                                    return@forEach
                                }
                                val propertySymbol = prop.symbol
                                val propertyName = prop.name
                                if (countOfSymbols[propertySymbol] != 1) {
                                    // We use the property symbol to generate a `this.propName`
                                    // expression below.  It'd be nice if we had a way to refer
                                    // by the PropertyShape name, but for now, fail fast on
                                    // ambiguity.
                                    logSink.log(
                                        JsonInteropMessage.PropertyNotUniquelyIdentified,
                                        prop.pos,
                                        listOf(typeDecl.name, propertyName),
                                    )
                                    return@forEach
                                }

                                fun Planting.plantPropertyKey() {
                                    Call {
                                        Call(DotHelper(ExternalBind, objectKeyDotName)) {
                                            Rn(p)
                                        }
                                        V(Value(prop.jsonPropertyKey, TString))
                                    }
                                }

                                val knownValue = prop.knownValue
                                val type = prop.type?.let { hackMapOldStyleToNew(it) }
                                if (knownValue != null) {
                                    val encodeMethodName = when (knownValue.typeTag) {
                                        TString -> stringValueDotName
                                        TInt -> int32ValueDotName
                                        TBoolean -> booleanValueDotName
                                        TNull -> nullValueDotName
                                        TFloat64 -> float64ValueDotName
                                        else -> {
                                            logSink.log(
                                                JsonInteropMessage.UnencodableValue,
                                                prop.pos,
                                                listOf(knownValue, typeDecl.name, propertyName),
                                            )
                                            null
                                        }
                                    }
                                    if (encodeMethodName != null) {
                                        plantPropertyKey()
                                        Call {
                                            Call(DotHelper(ExternalBind, encodeMethodName)) {
                                                Rn(p)
                                            }
                                            V(knownValue)
                                        }
                                    }
                                } else if (type != null) {
                                    plantPropertyKey()
                                    fun Planting.propertyValueExpr() {
                                        Call(BuiltinFuns.getpFn) {
                                            Rn(propertyName)
                                            Call(BuiltinFuns.thisPlaceholder) {
                                                V(Value(ReifiedType(thisType)))
                                            }
                                        }
                                    }
                                    val producerMethodName = when (type) {
                                        WellKnownTypes.booleanType2 -> booleanValueDotName
                                        WellKnownTypes.float64Type2 -> float64ValueDotName
                                        WellKnownTypes.intType2 -> int32ValueDotName
                                        WellKnownTypes.int64Type2 -> int64ValueDotName
                                        WellKnownTypes.stringType2 -> stringValueDotName
                                        else -> if (type.canOnlyBeNull) {
                                            nullValueDotName
                                        } else {
                                            null
                                        }
                                    }
                                    if (producerMethodName != null) {
                                        // p.???Value(propertyName)
                                        Call {
                                            Call(DotHelper(ExternalBind, producerMethodName)) {
                                                Rn(p)
                                            }
                                            propertyValueExpr()
                                        }
                                    } else {
                                        Call {
                                            Call(DotHelper(ExternalBind, encodeToJsonDotName)) {
                                                subs.plantAdapterFor(this, type, prop.pos, propertyName)
                                            }
                                            propertyValueExpr()
                                            Rn(p)
                                        }
                                    }
                                } else {
                                    // TODO: Maybe we could solve this in some cases
                                    // by generating a `typeof(propertyName).jsonAdapter()`
                                    // but we still wouldn't know how many subsidiaries it needs.
                                    logSink.log(
                                        JsonInteropMessage.MissingTypeInfoForEncodedProperty,
                                        prop.pos,
                                        listOf(typeDecl.name, propertyName),
                                    )
                                }
                            }
                            Call {
                                Call(DotHelper(ExternalBind, endObjectDotName)) {
                                    Rn(p)
                                }
                            }
                            V(void)
                        }
                    }
                },
            ),
        )

        defineDecodeFromJsonFromJsonObject(typeDecl) { ic, adapted, subs, decodedType, objLocal ->
            val namePropertyPairs = properties.mapNotNull {
                if (it.shouldEncode && it.type != null && it.abstractness == Abstractness.Concrete) {
                    nameMaker.unusedModularName(it.name) to it
                } else {
                    null
                }
            }
            // TODO: check known value
            // Declare locals for each constructor argument
            namePropertyPairs.forEach { (name, prop) ->
                Decl(name) {
                    V(typeSymbol)
                    V(Value(ReifiedType(hackMapOldStyleToNew(prop.type!!))))
                }
            }
            // assign locals
            namePropertyPairs.forEach { (name, prop) ->
                // Read the JSON property for the named constructor input.
                // obj.propertyValueOrBubble("prop")
                fun Planting.propertyTreeExpr() {
                    Call {
                        Call(DotHelper(ExternalBind, propertyValueOrBubbleDotName)) {
                            Rn(objLocal)
                        }
                        V(Value(prop.jsonPropertyKey, TString))
                    }
                }

                val propType = prop.type?.let { hackMapOldStyleToNew(adapted.remap(it)) }
                val jsonSyntaxTreeVariant = when (propType) {
                    WellKnownTypes.booleanType2 -> details.stdJson.typeJsonBoolean to null
                    WellKnownTypes.float64Type2 -> details.stdJson.typeJsonNumeric to asFloat64DotName
                    WellKnownTypes.intType2 -> details.stdJson.typeJsonNumeric to asInt32DotName
                    WellKnownTypes.int64Type2 -> details.stdJson.typeJsonNumeric to asInt64DotName
                    WellKnownTypes.stringType2 -> details.stdJson.typeJsonString to null
                    else -> null
                }

                Call(BuiltinFuns.setLocalFn) { // local = DECODED_VALUE_EXPR
                    Ln(name)
                    if (jsonSyntaxTreeVariant != null) {
                        val (variantType, methodName) = jsonSyntaxTreeVariant
                        val reifiedVariantType = ReifiedType(MkType2(variantType).get())
                        if (methodName == null) {
                            // (PROPERTY_TREE as ContentType).content
                            Call(DotHelper(ExternalGet, contentDotName)) {
                                Call(BuiltinFuns.asFn) {
                                    propertyTreeExpr()
                                    V(Value(reifiedVariantType))
                                }
                            }
                        } else {
                            // (PROPERTY_TREE as ContentType).methodName()
                            Call {
                                Call(DotHelper(ExternalBind, methodName)) {
                                    Call(BuiltinFuns.asFn) {
                                        propertyTreeExpr()
                                        V(Value(reifiedVariantType))
                                    }
                                }
                            }
                        }
                    } else {
                        // JSON_ADAPTER_EXPR.decodeFromJson(PROPERTY_TREE_EXPR, ic)
                        Call {
                            Call(DotHelper(ExternalBind, decodeFromJsonDotName)) {
                                subs.plantAdapterFor(this, propType, prop.pos, prop.name)
                            }
                            propertyTreeExpr()
                            Rn(ic)
                        }
                    }
                }
            }
            // return a value
            Call(New) {
                V(Value(ReifiedType(decodedType)))
                namePropertyPairs.forEach { (name, prop) ->
                    V(prop.symbol)
                    Rn(name)
                }
            }
        }

        computeChangesUsingCustomStrategy(
            typeDecl.copy(
                toJsonMethod = JsonInteropDetails.MethodPresence.Present,
                fromJsonMethod = JsonInteropDetails.MethodPresence.Present,
            ),
        )
    }

    private fun computeChangesUsingSealedInterfaceStrategy(typeDecl: JsonInteropDetails.TypeDecl) {
        val localTypes = details.localTypes
        // Enumerate concrete subtypes, looking through sealed interfaces
        val unsealedSubTypes = mutableListOf<JsonInteropDetails.SealedSubType>()
        val q = ArrayDeque(
            listOf(
                // Treat the typeDecl as a sealed sub-type of itself so that we can propagate type
                // parameter info through.
                JsonInteropDetails.SealedSubType(
                    typeDecl.name, typeDecl.typeFormals.map { MkType.nominal(it) },
                ),
            ),
        )
        val visited = mutableSetOf<ResolvedName>()

        while (q.isNotEmpty()) {
            val sealedSubType = q.removeFirst()
            val sealedSubTypeName = sealedSubType.subTypeName
            // A sealed interface with multiple sealed sub-interfaces may have a sub-type that inherits from
            // multiple of those, so keep track of what we've seen.  For example:
            //    sealed interface SI {}
            //    sealed interface A extends SI
            //    sealed interface B extends SI
            //    class C extends A, B
            if (sealedSubTypeName in visited) {
                continue
            }
            visited.add(sealedSubTypeName)
            val subTypeDecl = localTypes[sealedSubTypeName] ?: continue
            if (subTypeDecl.isSealed) {
                q.addAll(subTypeDecl.sealedSubTypes!!)
            } else {
                unsealedSubTypes.add(sealedSubType)
            }
        }

        val (jsonSubTypes, noJsonSubTypes) = unsealedSubTypes.partition {
            localTypes.getValue(it.subTypeName).hasJsonDecoration
        }
        if (noJsonSubTypes.isNotEmpty()) {
            // We can't encode all variants because they haven't opted into `@json`
            // This means that non-bubbling paths in encodeToJson would fail.
            logSink.log(
                JsonInteropMessage.NoJsonOnSubTypeOfJsonSealedType,
                typeDecl.pos,
                listOf(
                    noJsonSubTypes.map { it.subTypeName },
                    typeDecl.name,
                ),
            )
        }

        val extraMethodPos = typeDecl.pos.rightEdge

        // We add encodeToJson and decodeFromJsonProperties and then delegate to the
        // custom strategy to define the rest.

        // Define an encodeToJson method that type-switches on the input.
        // For `sealed interface S` with subtypes `Sub1` and `Sub2` we get the below:
        //
        //    public encodeToJson(/*this: S, */p: JsonTextProducer, ...): Void {
        //      when (this) {
        //        is Sub1 -> Sub1.jsonAdapter().encodeToJson(this, p);
        //        is Sub2 -> Sub2.jsonAdapter().encodeToJson(this, p);
        //        else -> panic();
        //      }
        //    }
        addedMethods.putMultiList(
            typeDecl.name,
            JsonInteropChanges.AddedMethod(
                isStatic = false,
                visibility = Visibility.Public,
                name = encodeToJsonDotName,
                body = {
                    val p = nameMaker.unusedSourceName(ParsedName("p"))
                    val x = ParsedName("x")
                    val subs = Subsidiaries(this@JsonInteropPass, typeDecl.typeFormals)
                    Fn(extraMethodPos) {
                        Decl(p) {
                            V(typeSymbol)
                            V(Value(ReifiedType(MkType2(details.stdJson.typeJsonProducer).get())))
                        }
                        subs.plantAdapterParameters(this)
                        V(outTypeSymbol)
                        V(Types.vVoid)
                        V(returnedFromSymbol)
                        V(TBoolean.valueTrue)
                        Block {
                            Decl(x) {
                                V(initSymbol)
                                Call(BuiltinFuns.thisPlaceholder) {}
                            }
                            Call(BuiltinFuns.whenMacro) {
                                Rn(x)
                                Fn {
                                    Block {
                                        for (t in jsonSubTypes) {
                                            V(caseIsSymbol)
                                            Rn(t.subTypeName)
                                            Call {
                                                Call(DotHelper(ExternalBind, encodeToJsonDotName)) {
                                                    Call {
                                                        Call {
                                                            Rn(dotBuiltinName)
                                                            Rn(t.subTypeName)
                                                            V(jsonAdapterDotName)
                                                        }
                                                    }
                                                }
                                                Rn(x)
                                                Rn(p)
                                            }
                                        }
                                        V(defaultSymbol)
                                        Call(PanicFn) {}
                                    }
                                }
                            }
                        }
                    }
                },
            ),
        )

        // Build a decision tree like the below based on structural tests.
        //     class Sub1 extends SealedSuper { x: Int }
        //     class Sub2 extends SealedSuper { y: Int }
        // Given those two subtypes, we can distinguish between them with a
        // single structural test.
        //     if (properties.has("x")) {
        //       Sub1.jsonAdapter().decodeFromJson(t, ic)
        //     } else {
        //       Sub2.jsonAdapter().decodeFromJson(t, ic)
        //     }
        // First, we produce a feature set.
        //     has "x"
        //     has "y"
        // Then we build a decision tree using those features.

        // Maps JSON property keys to maps from subtype names to classifications.
        val propertyClassifications: Map<String, Map<ResolvedName, PropertyClassification>> = buildMapMultimap {
            for (jsonSubType in jsonSubTypes) {
                val subTypeName = jsonSubType.subTypeName
                val decl = localTypes.getValue(subTypeName)
                for (propertyDecl in decl.properties) {
                    if (!propertyDecl.shouldEncode) { continue }
                    val jsonPropertyKey = propertyDecl.jsonPropertyKey
                    val classification = when {
                        propertyDecl.knownValue != null -> PropertyExactValue(propertyDecl.knownValue)
                        else -> PropertyPresent
                    }
                    val old =
                        putMultiMap(jsonPropertyKey, subTypeName, classification)
                    if (old != null) {
                        putMultiMap(jsonPropertyKey, subTypeName, old.merge(classification))
                    }
                }
            }
        }

        val onlyKnownValues = mutableSetOf<String>()
        val someKnownValues = mutableSetOf<String>()
        val present = mutableSetOf<String>()
        for ((jsonPropertyKey, m) in propertyClassifications) {
            var hasPresent = false
            var hasKnownValue = false
            for (v in m.values) {
                if (v is PropertyExactValue) {
                    hasKnownValue = true
                    if (hasPresent) { break }
                } else {
                    hasPresent = true
                    if (hasKnownValue) { break }
                }
            }
            val s = when {
                hasKnownValue -> if (hasPresent) someKnownValues else onlyKnownValues
                else -> present
            }
            s.add(jsonPropertyKey)
        }

        val features = buildList {
            // Order known-value properties earlier.
            // When a developer specifies a known value property, it's likely
            // intentionally to disambiguate, and the tree builder gives a
            // slight preference to earlier properties, so sort those early.

            addAll(onlyKnownValues)
            addAll(someKnownValues)
            addAll(present)
        }
        val decisionTree = buildDecisionTree(
            jsonSubTypes, features,
        ) { jsonSubType, jsonPropertyKey ->
            when (val c = propertyClassifications[jsonPropertyKey]?.get(jsonSubType.subTypeName)) {
                is PropertyExactValue -> if (jsonPropertyKey in onlyKnownValues) {
                    listOf(c)
                } else {
                    listOf(c, PropertyPresent)
                }
                PropertyPresent -> listOf(PropertyPresent)
                null -> listOf(PropertyAbsent)
            }
        }

        defineDecodeFromJsonFromJsonObject(typeDecl) { ic, _, _, _, objLocal ->
            fun Planting.plantDecisionTree(d: SealedSubDecisionTree) {
                when (d) {
                    is DecisionTree.Leaf ->
                        if (d.cases.size == 1) {
                            val case = d.cases[0]
                            // return ChosenType.jsonAdapter().decodeFromJson(obj, ic)
                            Call {
                                Rn(returnBuiltinName)
                                Call {
                                    Call(DotHelper(ExternalBind, decodeFromJsonDotName)) {
                                        Call {
                                            Call {
                                                Rn(dotBuiltinName)
                                                Rn(case.subTypeName)
                                                V(jsonAdapterDotName)
                                            }
                                        }
                                    }
                                    Rn(objLocal)
                                    Rn(ic)
                                }
                            }
                        } else {
                            val problem = LogEntry(
                                JsonInteropMessage.AmbiguousDecodingForSealedType, typeDecl.pos,
                                listOf(typeDecl.definition, d.cases.map { it.subTypeName }),
                            )
                            problem.logTo(logSink)
                            Call(ErrorFn) {
                                V(Value(problem, TProblem))
                            }
                        }

                    is DecisionTree.Inner -> {
                        val propertyKey = d.discriminant
                        // let valueFor$KEY = obj.propertyValueOrNull("$KEY")
                        val propertyValueName = nameMaker.unusedSourceName(
                            ParsedName(
                                "valueFor${
                                    jsonPropertyKeyToCamel(propertyKey).asciiTitleCase()
                                }",
                            ),
                        )
                        Decl(propertyValueName) {
                            V(initSymbol)
                            Call {
                                Call(DotHelper(ExternalBind, propertyValueOrNullDotName)) {
                                    Rn(objLocal)
                                }
                                V(Value(propertyKey, TString))
                            }
                        }
                        val exactValuesGrouped =
                            mutableMapOf<TypeShape, MutableList<Pair<Value<*>, SealedSubDecisionTree>>>()
                        val presentOrAbsent = mutableListOf<Pair<Boolean, SealedSubDecisionTree>>()
                        for (choice in d.choices) {
                            val testResult = choice.key as PropertyTestResult
                            val subChoice = choice.value
                            when (testResult) {
                                PropertyAbsent -> presentOrAbsent.add(false to subChoice)
                                PropertyPresent -> presentOrAbsent.add(true to subChoice)
                                is PropertyExactValue -> {
                                    val typeShape = when (val typeTag = testResult.value.typeTag) {
                                        TBoolean -> details.stdJson.typeJsonBoolean
                                        TInt -> details.stdJson.typeJsonInt
                                        TFloat64 -> details.stdJson.typeJsonFloat64
                                        TString -> details.stdJson.typeJsonString
                                        TNull -> details.stdJson.typeJsonNull
                                        else -> TODO("$typeTag")
                                    }
                                    exactValuesGrouped.putMultiList(typeShape, testResult.value to subChoice)
                                }
                            }
                        }

                        // if (! isNull(valueFor$KEY)) {
                        //   ...
                        // }
                        fun Planting.plantPresentOrAbsentChecks() {
                            Block {
                                presentOrAbsent.forEach { (present, c) ->
                                    Call {
                                        Rn(ifBuiltinName)
                                        if (present) {
                                            Call(BuiltinFuns.notFn) {
                                                Call(IsNullFn) {
                                                    Rn(propertyValueName)
                                                }
                                            }
                                        } else {
                                            Call(IsNullFn) {
                                                Rn(propertyValueName)
                                            }
                                        }
                                        Fn {
                                            Block {
                                                plantDecisionTree(c)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // If we have known values, `when` on the type.
                        //     when (valueFor$KEY) {
                        //       is JsonString -> when (valueFor$Key.content) {
                        //         ...
                        //       }
                        //       else -> // PRESENT OR ABSENT
                        //     }
                        if (exactValuesGrouped.isEmpty()) {
                            plantPresentOrAbsentChecks()
                        } else {
                            Call(BuiltinFuns.whenMacro) {
                                Rn(propertyValueName)
                                Fn {
                                    Block {
                                        exactValuesGrouped.forEach { (t, choices) ->
                                            val typeValue = Value(ReifiedType(MkType2(t).get()))
                                            V(caseIsSymbol)
                                            V(typeValue)
                                            Call(BuiltinFuns.whenMacro) {
                                                Call(DotHelper(ExternalGet, contentDotName)) {
                                                    Rn(propertyValueName)
                                                }
                                                Fn {
                                                    Block {
                                                        choices.forEach { (v, c) ->
                                                            V(caseSymbol)
                                                            V(v)
                                                            plantDecisionTree(c)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        V(defaultSymbol)
                                        plantPresentOrAbsentChecks()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            plantDecisionTree(decisionTree)
            Call(BubbleFn) {}
        }

        computeChangesUsingCustomStrategy(
            typeDecl.copy(
                toJsonMethod = JsonInteropDetails.MethodPresence.Present,
                fromJsonMethod = JsonInteropDetails.MethodPresence.Present,
            ),
        )
    }

    private fun defineDecodeFromJsonFromJsonObject(
        typeDecl: JsonInteropDetails.TypeDecl,
        plantDecodeBody: Planting.(ic: SourceName, AdaptFormals, Subsidiaries, Type2, obj: SourceName) -> Unit,
    ) {
        val extraMethodPos = typeDecl.pos.rightEdge
        val t = nameMaker.unusedSourceName(ParsedName("t"))
        val ic = nameMaker.unusedSourceName(ParsedName("ic"))
        val adapted = AdaptFormals(typeDecl.pos, docContext, nameMaker, typeDecl.typeFormals)
        val subs = Subsidiaries(this@JsonInteropPass, adapted.allFormals)
        val decodedType = MkType2(typeDecl.definition)
            .actuals(adapted.allFormals.map { MkType2(it).get() })
            .get()
        val returnType = MkType2.result(decodedType, WellKnownTypes.bubbleType2).get()
        val objLocal = nameMaker.unusedSourceName(ParsedName("obj"))
        addedMethods.putMultiList(
            typeDecl.name,
            JsonInteropChanges.AddedMethod(
                isStatic = true,
                visibility = Visibility.Public,
                name = decodeFromJsonDotName,
                body = {
                    // Map type parameters on the class to inputs for that type's adapters
                    Fn(extraMethodPos) {
                        Decl(t) {
                            V(typeSymbol)
                            V(Value(ReifiedType(MkType2(details.stdJson.typeJsonSyntaxTree).get())))
                        }
                        Decl(ic) {
                            V(typeSymbol)
                            V(Value(ReifiedType(MkType2(details.stdJson.typeInterchangeContext).get())))
                        }
                        subs.plantAdapterParameters(this)
                        adapted.allFormals.forEach {
                            V(typeFormalSymbol)
                            V(Value(ReifiedType(MkType2(it).get())))
                        }
                        V(outTypeSymbol)
                        V(Value(ReifiedType(returnType)))
                        V(returnedFromSymbol)
                        V(TBoolean.valueTrue)
                        Block {
                            Decl(objLocal) {
                                V(initSymbol)
                                Call(BuiltinFuns.asFn) {
                                    Rn(t)
                                    V(Value(ReifiedType(MkType2(details.stdJson.typeJsonObject).get())))
                                }
                            }
                            plantDecodeBody(ic, adapted, subs, decodedType, objLocal)
                        }
                    }
                },
            ),
        )
    }

    private fun adapterClass(
        typeDecl: JsonInteropDetails.TypeDecl,
        enc: Planting.(Position, AdaptFormals, Subsidiaries) -> UnpositionedTreeTemplate<FunTree>,
        dec: Planting.(Position, AdaptFormals, Subsidiaries) -> UnpositionedTreeTemplate<FunTree>,
    ) {
        val baseName = when (val name = typeDecl.name) {
            is ResolvedParsedName -> ParsedName("${name.baseName.nameText}JsonAdapter")
            else -> ParsedName("JsonAdapterImpl")
        }
        val adapterName = nameMaker.unusedSourceName(baseName)
        val extraMethodPos = typeDecl.pos.rightEdge
        val classAdaptedFormals = AdaptFormals(extraMethodPos, docContext, nameMaker, typeDecl.typeFormals)
        val adapterFormalToAdapterPropertyName = buildMap {
            classAdaptedFormals.allFormals.forEach { typeFormal ->
                val baseNameSuffix = when (val formalName = typeFormal.name) {
                    is ResolvedParsedName -> formalName.baseName.nameText
                    is Temporary -> formalName.nameHint.asciiTitleCase()
                }
                val baseNameText = "adapterFor$baseNameSuffix"
                var candidate = Symbol(baseNameText)
                var counter = 0
                while (this.containsValue(candidate)) {
                    candidate = Symbol("$baseNameText${++counter}")
                }
                this[typeFormal] = candidate
            }
        }
        adapterClasses.add(
            JsonInteropChanges.AddedType(
                name = adapterName,
                abstractness = Abstractness.Concrete,
                typeFormals = classAdaptedFormals.allFormals,
                superTypes = listOf(
                    MkType.nominal(
                        details.stdJson.typeJsonAdapter,
                        listOf(
                            MkType.nominal(
                                typeDecl.definition,
                                typeDecl.typeFormals.map {
                                    MkType.nominal(it)
                                },
                            ),
                        ),
                    ),
                ),
                properties = adapterFormalToAdapterPropertyName.map { (tf, dotName) ->
                    JsonInteropChanges.AddedProperty(
                        isStatic = false,
                        visibility = Visibility.Private,
                        name = dotName,
                        MkType.nominal(
                            details.stdJson.typeJsonAdapter,
                            listOf(MkType.nominal(tf)),
                        ),
                    )
                },
                methods = listOf(
                    JsonInteropChanges.AddedMethod(
                        isStatic = false,
                        visibility = Visibility.Public,
                        name = encodeToJsonDotName,
                        body = {
                            val subsidiaries = Subsidiaries(this@JsonInteropPass, typeDecl.typeFormals)
                            enc(extraMethodPos, classAdaptedFormals, subsidiaries)
                        },
                    ),
                    JsonInteropChanges.AddedMethod(
                        isStatic = false,
                        visibility = Visibility.Public,
                        name = decodeFromJsonDotName,
                        body = {
                            val subsidiaries = Subsidiaries(this@JsonInteropPass, typeDecl.typeFormals)
                            dec(extraMethodPos, classAdaptedFormals, subsidiaries)
                        },
                    ),
                ),
            ),
        )

        val jsonAdapterMethod = JsonInteropChanges.AddedMethod(
            isStatic = true,
            visibility = Visibility.Public,
            name = jsonAdapterDotName,
            body = {
                // We need a type formal for this static method.
                // We need an argument for each subsidiary type adapter.
                // If typeDecl has a type formal, <T>, we might need a JsonAdapter<T> so that
                // we can adapt property values whose type uses <T> as in
                //
                //    class C<T> { public p: T; public q: List<T>; }
                //
                // In that case, our adapter needs a field like
                //    class CJsonAdapter<T> extends JsonAdapter<C<T>> {
                //      public tAdapter: JsonAdapter<T>;
                //    }
                val adaptedFormals = AdaptFormals(extraMethodPos, docContext, nameMaker, typeDecl.typeFormals)
                val adaptedType = MkType2(typeDecl.definition)
                    .actuals(adaptedFormals.allFormals.map { MkType2(it).get() })
                    .get()
                val subsidiaries = Subsidiaries(this@JsonInteropPass, adaptedFormals.allFormals)

                Fn(extraMethodPos) {
                    subsidiaries.plantAdapterParameters(this)
                    for (typeFormal in adaptedFormals.allFormals) {
                        V(typeFormalSymbol)
                        V(Value(ReifiedType(MkType2(typeFormal).get()), TType))
                    }
                    V(outTypeSymbol)
                    V( // JsonAdapter<CurrentType<...CurrentMethodTypeFormals>>
                        Value(
                            ReifiedType(
                                MkType2(details.stdJson.typeJsonAdapter)
                                    .actuals(listOf(adaptedType))
                                    .get(),
                            ),
                        ),
                    )
                    V(returnedFromSymbol)
                    V(TBoolean.valueTrue)
                    Block {
                        Call(New) {
                            val adapterTypeArgs = adaptedFormals.allFormals.filter { it.needsAdapting }
                            if (adapterTypeArgs.isEmpty()) {
                                Rn(adapterName)
                            } else {
                                Call(BuiltinFuns.angleFn) {
                                    Rn(adapterName)
                                    adapterTypeArgs.forEach {
                                        if (it.needsAdapting) {
                                            V(Value(ReifiedType(MkType2(it).get()), TType))
                                        }
                                    }
                                }
                            }
                            subsidiaries.adaptedTypeToLocalName.values.forEach {
                                if (it.wasAllocated) {
                                    Rn(it.name)
                                }
                            }
                        }
                    }
                }
            },
        )

        addedMethods.putMultiList(typeDecl.name, jsonAdapterMethod)
    }
}

val jsonAdapterDotName = Symbol("jsonAdapter")
val encodeToJsonDotName = Symbol(ENCODE_METHOD_NAME)
val decodeFromJsonDotName = Symbol(DECODE_METHOD_NAME)
val startObjectDotName = Symbol("startObject")
val objectKeyDotName = Symbol("objectKey")
val endObjectDotName = Symbol("endObject")
val booleanValueDotName = Symbol("booleanValue")
val int32ValueDotName = Symbol("int32Value")
val int64ValueDotName = Symbol("int64Value")
val float64ValueDotName = Symbol("float64Value")
val nullValueDotName = Symbol("nullValue")
val stringValueDotName = Symbol("stringValue")
val contentDotName = Symbol("content")
val propertyValueOrBubbleDotName = Symbol("propertyValueOrBubble")
val propertyValueOrNullDotName = Symbol("propertyValueOrNull")
val asFloat64DotName = Symbol("asFloat64")
val asInt32DotName = Symbol("asInt32")
val asInt64DotName = Symbol("asInt64")

// TODO: Allow a decorator to opt a formal out from adapting
@Suppress("UnusedReceiverParameter") // We will base this on metadata
val TypeFormal.needsAdapting get() = true

private fun ResolvedNameMaker.unusedModularName(name: ResolvedName): ModularName = when (name) {
    is ResolvedParsedName -> unusedSourceName(name.baseName)
    is Temporary -> unusedTemporaryName(name.nameHint)
}

private fun ResolvedNameMaker.unusedSourceNameWithPrefix(
    prefix: String,
    name: ResolvedName,
): ModularName = unusedSourceName(
    ParsedName(
        buildString {
            append(prefix)
            when (name) {
                is ResolvedParsedName -> append(name.baseName.nameText)
                is Temporary -> append(name.nameHint)
            }
        },
    ),
)

private typealias SealedSubDecisionTree = DecisionTree<JsonInteropDetails.SealedSubType, String>

/** A kind of result from checking a property in a JSON object */
private sealed interface PropertyTestResult
private data object PropertyAbsent : PropertyTestResult

/**
 * For a given (class, json property) pair, should it be present in a
 * JSON object encoded from an instance of that class or should it take
 * a specific value.
 */
private sealed interface PropertyClassification : PropertyTestResult {
    /** Greater implies broader. To merge different items to avoid ambiguity, prefer the greater. */
    fun merge(other: PropertyClassification): PropertyClassification {
        if (this == other) { return this }
        // If either is present, or we have different exact values, merge to present.
        return PropertyPresent
    }
}
private data object PropertyPresent : PropertyClassification
private data class PropertyExactValue(
    val value: Value<*>,
) : PropertyClassification

private fun jsonPropertyKeyToCamel(key: String): String =
    IdentStyle.Pascal.convertTo(
        IdentStyle.Camel,
        // Dash case shows up in JSON keys, but not consistently
        key.replace('-', '_'),
    )
