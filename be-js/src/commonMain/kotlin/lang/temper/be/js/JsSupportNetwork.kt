package lang.temper.be.js

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.GetStaticSupport
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.OtherSupportCodeRequirement
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SeparatelyCompiledSupportCode
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportCodeRequirement
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TranslationAssistant
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.aType
import lang.temper.be.tmpl.toTmpL
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.common.AtomicCounter
import lang.temper.common.OpenOrClosed
import lang.temper.common.asciiTitleCase
import lang.temper.common.asciiUnTitleCase
import lang.temper.format.CodeFormatter
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.frontend.json.jsonAdapterDotName
import lang.temper.frontend.staging.getSharedStdModules
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.log.spanningPosition
import lang.temper.log.unknownPos
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Symbol
import lang.temper.name.name
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.DefinedType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.TType
import lang.temper.value.jsonSymbol
import lang.temper.value.thisParsedName

internal object JsSupportNetwork : SupportNetwork {
    override val backendDescription = "JS backend"

    override fun adjustTypeMembers(
        typeShape: TypeShape,
        members: List<TmpL.MemberOrGarbage>,
        translationAssistant: TranslationAssistant,
    ): List<TmpL.MemberOrGarbage> {
        var adjustedMembers = members

        // If there's a zero argument jsonAdapter then we can auto-generate a toJSON method.
        val jsonAdapterMethod = members.firstOrNull {
            it is TmpL.StaticMethod && it.dotName.dotNameText == jsonAdapterDotName.text && !it.mayYield &&
                it.typeParameters.ot.typeParameters.isEmpty() && it.parameters.parameters.isEmpty() &&
                it.parameters.restParameter == null
        }
        val hasToJson = members.any {
            it is TmpL.InstanceMember &&
                it is TmpL.DotAccessible &&
                it.dotName.dotNameText == JAVASCRIPT_TOJSON_SPECIAL_NAME
        }
        if (jsonAdapterMethod is TmpL.StaticMethod && !hasToJson && jsonSymbol in typeShape.metadata.keys) {
            val p = jsonAdapterMethod.pos.rightEdge

            val nameMaker = translationAssistant.nameMaker
            val type = MkType2(typeShape).get()
            val methodName = nameMaker.unusedTemporaryName(JAVASCRIPT_TOJSON_SPECIAL_NAME)
            val thisName = nameMaker.unusedSourceName(thisParsedName)
            val visibility = Visibility.Public
            val jsonAdapterSig = Signature2( // Fn(): JsonAdapter<C>
                MkType2((marshalToJsonObjectSig.value.requiredInputTypes[0] as DefinedType).definition)
                    .actuals(listOf(type))
                    .get(),
                false,
                listOf(),
            )
            val methodShape = MethodShape(
                enclosingType = typeShape,
                name = methodName,
                symbol = Symbol(JAVASCRIPT_TOJSON_SPECIAL_NAME),
                stay = null,
                visibility = visibility,
                methodKind = MethodKind.Normal,
                openness = OpenOrClosed.Closed,
            )
            adjustedMembers = buildList {
                addAll(adjustedMembers)
                add(
                    TmpL.NormalMethod(
                        p,
                        metadata = emptyList(),
                        dotName = TmpL.DotName(p, JAVASCRIPT_TOJSON_SPECIAL_NAME),
                        name = TmpL.Id(p, methodName),
                        typeParameters = TmpL.ATypeParameters(
                            TmpL.TypeParameters(p, emptyList()),
                        ),
                        parameters = TmpL.Parameters(
                            p,
                            TmpL.Id(p, thisName),
                            listOf(
                                TmpL.Formal(
                                    p,
                                    emptyList(),
                                    TmpL.Id(p, thisName),
                                    translationAssistant.translateType(p, type).aType,
                                    type,
                                ),
                            ),
                            null,
                        ),
                        returnType = TmpL.NominalType(
                            pos = p,
                            typeName = TmpL.TemperTypeName(p, WellKnownTypes.anyValueTypeDefinition),
                            params = emptyList(),
                        ).aType,
                        body = TmpL.BlockStatement(
                            p,
                            listOf(
                                // `return marshalToJsonObject(C.jsonAdapter(), this)`
                                TmpL.ReturnStatement(
                                    p,
                                    TmpL.CallExpression(
                                        pos = p,
                                        fn = translationAssistant.supportCodeReference(
                                            p,
                                            coreMarshalToJsonObject,
                                            marshalToJsonObjectSig.value,
                                        ),
                                        parameters = listOf(
                                            TmpL.CallExpression(
                                                p,
                                                TmpL.MethodReference(
                                                    pos = p,
                                                    subject = TmpL.TemperTypeName(p, typeShape),
                                                    methodName = TmpL.DotName(p, jsonAdapterMethod.dotName.dotNameText),
                                                    type = jsonAdapterSig,
                                                    method = methodShape,
                                                ),
                                                parameters = emptyList(),
                                                type = jsonAdapterSig.returnType2,
                                            ),
                                            TmpL.This(p, TmpL.Id(p, thisName), type),
                                        ),
                                        type = marshalToJsonObjectSig.value.returnType2,
                                    ),
                                ),
                            ),
                        ),
                        visibility = TmpL.VisibilityModifier(p, visibility.toTmpL()),
                        overridden = emptyList(),
                        mayYield = false,
                        memberShape = methodShape,
                    ),
                )
            }
        }

        return adjustedMembers
    }

    override fun getSupportCode(
        pos: Position,
        builtin: NamedBuiltinFun,
        genre: Genre,
    ): SupportCode? {
        val builtinOperatorId = builtin.builtinOperatorId
        return when {
            builtin == BuiltinFuns.listifyFn && genre == Genre.Documentation ->
                docsListifyInliner
            builtin == BuiltinFuns.print && genre == Genre.Documentation ->
                docsPrintInliner
            builtin == BuiltinFuns.strCatFn && genre == Genre.Documentation ->
                catInliner
            builtin == BuiltinFuns.pureVirtualFn ->
                InlinedJs(
                    DashedIdentifier.temperCoreLibraryIdentifier,
                    JsIdentifierName("nothing"),
                    needsThisEquivalent = false,
                    builtinOperatorId = builtinOperatorId,
                ) { factoryPos, _, _, _ ->
                    Js.NullLiteral(factoryPos) // TODO: should this throw
                }
            builtin is GetStaticOp -> GetStaticSupport
            builtin == BuiltinFuns.isFn -> TODO("IS")
            builtinOperatorId == null -> null
            else -> builtinOperatorIdToSupportCode.getValue(builtinOperatorId)
        }
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = when (optionalSupportCodeKind) {
        // TODO(mikesamuel, enum-translation): define something based on
        // gist.github.com/mikesamuel/2084a7810977fa49cddf73dce2e11a4a
        OptionalSupportCodeKind.EnumTypeSupport -> null
        OptionalSupportCodeKind.InterfaceTypeSupport ->
            typeSupportCode to
                Signature2(WellKnownTypes.anyValueType2, false, listOf())
    }

    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode? {
        val factory: Inliner? = when (connectedKey) {
            "Boolean::toString" -> toStringIdiomExpander
            "Int32::toFloat64" -> identityIdiomExpander // All ints are also floats.
            "Int32::toString" -> toStringIdiomExpander
            "Int32::toInt64" -> bigintExpander
            "Int64::toString" -> toStringIdiomExpander
            "List::isEmpty" -> listIsEmptyIdiomExpander
            "List::forEach" -> listForEachIdiomExpander
            "List::toList" -> identityIdiomExpander
            "List::toListBuilder" -> listToListBuilderIdiomExpander
            "Listed::isEmpty" -> listIsEmptyIdiomExpander
            "ListBuilder::constructor" -> listBuilderConstructorIdiomExpander
            "ListBuilder::toListBuilder" -> listToListBuilderIdiomExpander
            "String::isEmpty" -> stringIsEmptyIdiomExpander
            "String::toString" -> identityIdiomExpander
            "Utf16StringSlice::length" -> lengthIdiomExpander
            "Date::constructor" -> { p, args, strict, translator ->
                newDateIdiomExpander(p, args, strict = strict, genre = genre, translator = translator)
            }
            "Date::getYear" -> propertyReadToMethodCall("getUTCFullYear")
            "Date::getMonth" -> dateGetMonthExpander // Need to add one to index
            "Date::getDay" -> propertyReadToMethodCall("getUTCDate") // getUTCDay is weekday
            "Date::getDayOfWeek" -> dateGetDayOfWeekExpander // JS has Sunday as 0, not 7
            "Date::toString" -> dateToIsoStringExpander
            "Date::fromIsoString" -> { p, args, strict, _ ->
                dateFromIsoStringExpander(p, args, strict = strict, genre = genre)
            }
            "Test::bail" -> bailExpander
            "ignore" -> ignoreIdiomExpander
            "::getConsole" -> getConsoleExpander
            // Float64 constants
            "Float64::e" -> mathProperty("E")
            "Float64::pi" -> mathProperty("PI")
            // Float64 math
            "Float64::abs" -> mathCall("abs")
            "Float64::acos" -> mathCall("acos")
            "Float64::asin" -> mathCall("asin")
            "Float64::atan" -> mathCall("atan")
            "Float64::atan2" -> mathCall("atan2", 2)
            "Float64::ceil" -> mathCall("ceil")
            "Float64::cos" -> mathCall("cos")
            "Float64::cosh" -> mathCall("cosh")
            "Float64::exp" -> mathCall("exp")
            "Float64::expm1" -> mathCall("expm1")
            "Float64::floor" -> mathCall("floor")
            "Float64::log" -> mathCall("log")
            "Float64::log10" -> mathCall("log10")
            "Float64::log1p" -> mathCall("log1p")
            "Float64::max" -> mathCall("max", 2)
            "Float64::min" -> mathCall("min", 2)
            "Float64::round" -> mathCall("round")
            "Float64::sign" -> mathCall("sign")
            "Float64::sin" -> mathCall("sin")
            "Float64::sinh" -> mathCall("sinh")
            "Float64::sqrt" -> mathCall("sqrt")
            "Float64::tan" -> mathCall("tan")
            "Float64::tanh" -> mathCall("tanh")
            "Int32::max" -> mathCall("max", 2)
            "Int32::min" -> mathCall("min", 2)
            // Mapped things
            "Mapped::length" -> mappedSizeExpander
            "Mapped::has" -> mappedHasExpander
            "Mapped::keys" -> mappedKeysExpander
            "Mapped::values" -> mappedValuesExpander
            // String and StringIndex things
            "String::begin" -> stringBeginExpander
            "String::end" -> lengthIdiomExpander
            "String::hasIndex" -> stringHasIndexExpander
            "String::slice" -> stringSliceExpander
            "StringIndexOption::compareTo" -> stringIndexOptionCompareToExpander
            "StringIndexOption::compareTo::eq" -> stringIndexOptionCompareToExpanderEq
            "StringIndexOption::compareTo::ge" -> stringIndexOptionCompareToExpanderGe
            "StringIndexOption::compareTo::gt" -> stringIndexOptionCompareToExpanderGt
            "StringIndexOption::compareTo::le" -> stringIndexOptionCompareToExpanderLe
            "StringIndexOption::compareTo::lt" -> stringIndexOptionCompareToExpanderLt
            "StringIndexOption::compareTo::ne" -> stringIndexOptionCompareToExpanderNe
            "StringIndex::none" -> stringIndexNoneExpander
            "StringBuilder::constructor" -> stringBuilderConstructorExpander
            "StringBuilder::append" -> stringBuilderAppendExpander
            "StringBuilder::appendBetween" -> stringBuilderAppendBetweenExpander
            "StringBuilder::toString" -> stringBuilderToStringExpander
            // Ignore others
            else -> null
        }
        val stableName = if (factory != null || connectedKey in supportedAutoConnecteds) {
            connectedKeyToExportedName(connectedKey)
        } else {
            supportedMappedConnecteds[connectedKey] ?: return null
        }
        return if (factory == null) {
            JsUnInlinedExternalFunctionReference(
                source = DashedIdentifier.temperCoreLibraryIdentifier,
                stableName = stableName,
            )
        } else {
            InlinedJs(
                source = DashedIdentifier.temperCoreLibraryIdentifier,
                stableName = stableName,
                needsThisEquivalent = true,
                factory = factory,
            )
        }
    }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? {
        val bindings = temperType.bindings
        return when (connectedKey) {
            // TODO: Use JS Temporal.PlainDate long term: https://tc39.es/proposal-temporal/docs/plaindate.html
            "Date" -> JsGlobalReference(ParsedName("Date")) to bindings
            "Promise" -> JsGlobalReference(ParsedName("Promise")) to bindings
            "PromiseBuilder" -> JsExternalTypeReference(
                source = DashedIdentifier.temperCoreLibraryIdentifier,
                stableName = JsIdentifierName("PromiseBuilder"),
            ) to bindings
            // It might be better if the type were `[string]`, a length:1 array.
            // For StringBuilder, we construct a [""], and then add to element 0.
            // This is the fastest per jsperf.app/join-concat/2
            "StringBuilder" -> JsGlobalReference(ParsedName("Array")) to
                listOf(WellKnownTypes.stringType2)
            "StringIndexOption", "StringIndex", "NoStringIndex",
            -> JsGlobalReference(ParsedName("number")) to bindings
            else -> null
        }
    }

    override fun translateRuntimeTypeOperation(
        pos: Position,
        rto: RuntimeTypeOperation,
        sourceType: TmpL.NominalType,
        targetType: TmpL.NominalType,
    ): SupportCode? {
        if (rto.asLike) {
            when (targetType.typeName.sourceDefinition) {
                WellKnownTypes.noStringIndexTypeDefinition -> return requireNoStringIndex
                WellKnownTypes.stringIndexTypeDefinition -> return requireStringIndex
                else -> {}
            }
        }
        return super.translateRuntimeTypeOperation(pos, rto, sourceType, targetType)
    }

    override val bubbleStrategy = BubbleBranchStrategy.CatchBubble
    override val coroutineStrategy = CoroutineStrategy.TranslateToGenerator
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionType

    override fun representationOfVoid(genre: Genre): RepresentationOfVoid =
        RepresentationOfVoid.ReifyVoid

    /** A private setter can be `set #x(newValue)` while the getter can be `get x()`. */
    override val splitComputedProperties: Boolean get() = true
}

private val supportedAutoConnecteds = setOf(
    "Float64::near",
    "Float64::toInt32",
    "Float64::toInt32Unsafe",
    "Float64::toInt64",
    "Float64::toInt64Unsafe",
    "Float64::toString",
    "Int64::max",
    "Int64::min",
    "Int64::toFloat64",
    "Int64::toFloat64Unsafe",
    "Int64::toInt32",
    "Int64::toInt32Unsafe",
    "Listed::filter",
    "Listed::get",
    "Listed::getOr",
    "Listed::join",
    "Listed::map",
    "Listed::mapDropping",
    "Listed::reduceFrom",
    "Listed::slice",
    "Listed::sorted",
    "Listed::toList",
    "ListBuilder::add",
    "ListBuilder::addAll",
    "ListBuilder::toList",
    "ListBuilder::clear",
    "ListBuilder::removeLast",
    "ListBuilder::splice",
    "ListBuilder::reverse",
    "ListBuilder::set",
    "Map::constructor",
    "MapBuilder::constructor",
    "MapBuilder::remove",
    "MapBuilder::set",
    "Pair::constructor",
    "Mapped::length",
    "Mapped::get",
    "Mapped::getOr",
    "Mapped::has",
    "Mapped::keys",
    "Mapped::values",
    "Mapped::toMap",
    "Mapped::toMapBuilder",
    "Mapped::toList",
    "Mapped::toListWith",
    "Mapped::toListBuilder",
    "Mapped::toListBuilderWith",
    "Mapped::forEach",
    "DenseBitVector::constructor",
    "DenseBitVector::get",
    "DenseBitVector::set",
    "Deque::constructor",
    "Deque::add",
    "Deque::isEmpty",
    "Deque::removeFirst",
    "PromiseBuilder",
    "Regex::compileFormatted",
    "Regex::compiledFind",
    "Regex::compiledFound",
    "Regex::compiledReplace",
    "Regex::compiledSplit",
    "RegexFormatter::adjustCodeSet",
    "RegexFormatter::pushCodeTo",
    "String::countBetween",
    "String::fromCodePoint",
    "String::fromCodePoints",
    "String::forEach",
    "String::get",
    "String::hasAtLeast",
    "String::next",
    "String::prev",
    "String::step",
    "String::split",
    "String::toFloat64",
    "String::toInt32",
    "String::toInt64",
    "StringBuilder::appendCodePoint",
    // std/net
    "stdNetSend",
    "NetResponse",
    "NetResponse::getStatus",
    "NetResponse::getContentType",
    "NetResponse::getBodyContent",
)

private val supportedMappedConnecteds = mapOf(
    "Date::today" to "dateToday",
    "Date::yearsBetween" to "dateYearsBetween",
    "List::get" to "listedGet",
    "empty" to "empty",
).mapValues { JsIdentifierName(it.value) }

/** A reference to JavaScript from a separately compiled JavaScript library. */
sealed interface JsExternalReference : SeparatelyCompiledSupportCode, NamedSupportCode {
    val stableName: JsIdentifierName
    override val stableKey: ParsedName get() = ParsedName(stableName.text)

    override val baseName: ParsedName get() = stableKey

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken(stableName.text, OutputTokenType.Word))
    }
}

/** A reference to an `export const stableName = ...` from another JS library. */
internal sealed interface JsExternalFunctionReference : JsExternalReference

internal typealias Inliner =
    (pos: Position, arguments: List<Js.Tree>, strict: Boolean, translator: JsTranslator?) -> Js.Tree

internal data class InlinedJs(
    override val source: DashedIdentifier,
    override val stableName: JsIdentifierName,
    override val needsThisEquivalent: Boolean,
    override val builtinOperatorId: BuiltinOperatorId? = null,
    override val requires: List<SupportCodeRequirement> = emptyList(),
    val factory: Inliner,
) : InlineSupportCode<Js.Tree, JsTranslator>, JsExternalFunctionReference {
    override fun renderTo(tokenSink: TokenSink) {
        val strict = false // Not giving enough children
        CodeFormatter(tokenSink).format(
            factory(unknownPos, emptyList(), strict, null),
            skipOuterCurlies = true,
        )
    }

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Js.Tree>>,
        returnType: Type2,
        translator: JsTranslator,
    ): Js.Tree {
        val strict = true // Complain if not enough children
        return factory(pos, arguments.map { it.expr }, strict, translator)
    }

    override fun equals(other: Any?): Boolean =
        // Override since we cannot compare the factory function.
        other is InlinedJs &&
            this.stableName == other.stableName &&
            this.source == other.source &&
            this.builtinOperatorId == other.builtinOperatorId

    override fun hashCode(): Int {
        var hc = stableName.hashCode()
        hc = 31 * hc + source.hashCode()
        hc = 31 * hc + (builtinOperatorId?.ordinal ?: 0)
        return hc
    }
}

/** A reference to an `export const val = ...` from another JS library. */
internal data class JsExternalValueReference(
    override val source: DashedIdentifier,
    override val stableName: JsIdentifierName,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : JsExternalReference

sealed interface JsTargetLanguageTypeName : TargetLanguageTypeName

/** A reference to an `export class TypeName = ...` from another JS library. */
internal data class JsExternalTypeReference(
    override val source: DashedIdentifier,
    override val stableName: JsIdentifierName,
) : JsExternalReference, JsTargetLanguageTypeName

/** A reference to `globalThis.`[baseName] */
data class JsGlobalReference(
    override val baseName: ParsedName,
) : NamedSupportCode, JsTargetLanguageTypeName {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word(globalThisName.text)
        tokenSink.emit(OutToks.dot)
        tokenSink.name(baseName, inOperatorPosition = false)
    }

    fun asIdentifier(pos: Position) =
        Js.Identifier(pos, JsIdentifierName(baseName.nameText), null)
}

data class JsPropertyReference(
    val obj: NamedSupportCode,
    val propertyName: JsIdentifierName,
) : NamedSupportCode {
    override fun renderTo(tokenSink: TokenSink) {
        obj.renderTo(tokenSink)
        tokenSink.emit(OutToks.dot)
        tokenSink.emit(OutputToken(propertyName.text, OutputTokenType.Name))
    }

    override val baseName: ParsedName get() = ParsedName(propertyName.text)
}

/** A reference to an `export const val = ...` from another JS library. */
internal data class JsUnInlinedExternalFunctionReference(
    override val source: DashedIdentifier,
    override val stableName: JsIdentifierName,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : JsExternalFunctionReference

private val toStringIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, _: Boolean, _ ->
        Js.CallExpression(
            pos,
            Js.MemberExpression(
                pos,
                arguments.getExprSafe(0, pos.leftEdge),
                Js.Identifier(pos, JsIdentifierName("toString"), null),
            ),
            arguments.drop(1).map { it as Js.Expression },
        )
    }

private val identityIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        if (arguments.size == 1 || (arguments.isNotEmpty() && !strict)) {
            arguments[0]
        } else {
            garbageExpression(pos, "Wrong arguments for identity idiom expander")
        }
    }

/* [x, y] -> x.has(y) */
private val mappedHasExpander = { pos: Position, arguments: List<Js.Tree>, _: Boolean, _: JsTranslator? ->
    if (arguments.size != 2) {
        garbageExpression(pos, "Wrong number of arguments to Mapped::has")
    } else {
        val obj = arguments[0] as Js.Expression
        val key = arguments[1] as Js.Expression
        Js.CallExpression(
            pos = pos,
            callee = Js.MemberExpression(
                pos = pos,
                obj = obj,
                property = Js.Identifier(pos, JsIdentifierName("has"), null),
                computed = false,
            ),
            arguments = listOf(key),
        )
    }
}

/* [x] -> Object.freeze(Array.prototype.slice.call(x.keys())) */
private val mappedKeysExpander = { pos: Position, arguments: List<Js.Tree>, _: Boolean, _: JsTranslator? ->
    if (arguments.size != 1) {
        garbageExpression(pos, "Wrong number of arguments to Mapped::keys")
    } else {
        val obj = arguments[0] as Js.Expression
        Js.CallExpression(
            pos = pos,
            callee = Js.MemberExpression(
                pos = pos,
                obj = Js.Identifier(pos, JsIdentifierName("Object"), null),
                property = Js.Identifier(pos, JsIdentifierName("freeze"), null),
            ),
            arguments = listOf(
                Js.CallExpression(
                    pos = pos,
                    callee = Js.MemberExpression(
                        pos = pos,
                        obj = Js.Identifier(pos, JsIdentifierName("Array"), null),
                        property = Js.Identifier(pos, JsIdentifierName("from"), null),
                    ),
                    arguments = listOf(
                        Js.CallExpression(
                            pos = pos,
                            callee = Js.MemberExpression(
                                pos = pos,
                                obj = obj,
                                property = Js.Identifier(pos, JsIdentifierName("keys"), null),
                            ),
                            arguments = listOf(),
                        ),
                    ),
                ),
            ),
        )
    }
}

/* [x] -> Object.freeze(Array.prototype.slice.call(x.keys())) */
private val mappedValuesExpander = { pos: Position, arguments: List<Js.Tree>, _: Boolean, _: JsTranslator? ->
    if (arguments.size != 1) {
        garbageExpression(pos, "Wrong number of arguments to Mapped::values")
    } else {
        val obj = arguments[0] as Js.Expression
        Js.CallExpression(
            pos = pos,
            callee = Js.MemberExpression(
                pos = pos,
                obj = Js.Identifier(pos, JsIdentifierName("Object"), null),
                property = Js.Identifier(pos, JsIdentifierName("freeze"), null),
            ),
            arguments = listOf(
                Js.CallExpression(
                    pos = pos,
                    callee = Js.MemberExpression(
                        pos = pos,
                        obj = Js.Identifier(pos, JsIdentifierName("Array"), null),
                        property = Js.Identifier(pos, JsIdentifierName("from"), null),
                    ),
                    arguments = listOf(
                        Js.CallExpression(
                            pos = pos,
                            callee = Js.MemberExpression(
                                pos = pos,
                                obj = obj,
                                property = Js.Identifier(pos, JsIdentifierName("values"), null),
                            ),
                            arguments = listOf(),
                        ),
                    ),
                ),
            ),
        )
    }
}

private val mappedSizeExpander = { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _: JsTranslator? ->
    val obj = arguments.getOrNull(0) as? Js.Expression
    if (strict && (arguments.size != 1 || obj == null)) {
        garbageExpression(pos, "Wrong arguments for length idiom expander")
    } else {
        Js.MemberExpression(
            pos = pos,
            obj = obj ?: Js.Identifier(pos, JsIdentifierName("x"), null),
            property = Js.Identifier(pos, JsIdentifierName("size"), null),
            computed = false,
            optional = false,
        )
    }
}

/** Given `x` constructs `x.length`. */
private val lengthIdiomExpander = { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _: JsTranslator? ->
    val obj = arguments.getOrNull(0) as? Js.Expression
    if (strict && (arguments.size != 1 || obj == null)) {
        garbageExpression(pos, "Wrong arguments for length idiom expander")
    } else {
        Js.MemberExpression(
            pos = pos,
            obj = obj ?: Js.Identifier(pos, JsIdentifierName("x"), null),
            property = Js.Identifier(pos, JsIdentifierName("length"), null),
            computed = false,
            optional = false,
        )
    }
}

private fun newDateIdiomExpander(
    pos: Position,
    arguments: List<Js.Tree>,
    strict: Boolean,
    genre: Genre,
    translator: JsTranslator?,
): Js.Expression {
    val year = arguments.getOrNull(0) as? Js.Expression
    val month = arguments.getOrNull(1) as? Js.Expression
    val day = arguments.getOrNull(2) as? Js.Expression

    return if (
        strict &&
        (arguments.size != DATE_CONSTRUCTOR_ARITY || year == null || month == null || day == null)
    ) {
        garbageExpression(pos, "new Date() requires 3 expressions")
    } else {
        val left = pos.leftEdge
        val dateConstructor = Js.Identifier(left, JsIdentifierName("Date"), null)

        // There are several forms of this expression.
        // For documentation
        //     new globalThis.Date(globalThis.Date.UTC(...))
        // For library code
        //     new            Date(Date.UTC(...))
        // unless the year might trigger JavaScript's rule where years in [0..99] have 1900 added
        //     temperCore.dateConstructor(...)
        var useNew = genre == Genre.Documentation || !strict || translator == null
        if (!useNew && year is Js.NumericLiteral) {
            val yearInt = year.value
            if (yearInt is Long || yearInt is Int) {
                useNew = yearInt.toLong() !in jsProblematicYearRange
            }
        }

        if (useNew) {
            val globalDate: Js.SimpleRef = when (genre) {
                Genre.Library -> dateConstructor.globalizeThis()
                Genre.Documentation -> dateConstructor
            }

            Js.NewExpression(
                pos = pos,
                callee = globalDate,
                arguments = listOf(
                    Js.CallExpression(
                        pos = pos,
                        callee = Js.MemberExpression(
                            pos = left,
                            obj = globalDate.deepCopy(),
                            property = Js.Identifier(left, JsIdentifierName("UTC"), null),
                        ),
                        arguments = listOf(
                            // Year
                            year ?: Js.NumericLiteral(left, DEFAULT_FULL_YEAR),

                            // Month
                            // CAVEAT:
                            // developer.mozilla.org/en-US/docs/Web/JavaScript/
                            // Reference/Global_Objects/Date/UTC#monthIndex
                            // > Integer value representing the month,
                            // > beginning with 0 for January to 11 for December
                            month?.let { monthExpr ->
                                val afterMonth = monthExpr.pos.rightEdge
                                Js.InfixExpression(
                                    monthExpr.pos,
                                    monthExpr,
                                    Js.Operator(afterMonth, "-"),
                                    // TODO: For Genre.Documentation comment why we're subtracting 1
                                    Js.NumericLiteral(afterMonth, 1),
                                )
                            } ?: Js.NumericLiteral(left, 0),

                            // Day of month
                            day ?: Js.NumericLiteral(left, 1),
                        ),
                    ),
                ),
            )
        } else {
            val callee = JsUnInlinedExternalFunctionReference(
                source = DashedIdentifier.temperCoreLibraryIdentifier,
                stableName = connectedKeyToExportedName("Date::constructor"),
            )
            val calleeName = translator!!.requireExternalReference(callee)
            Js.CallExpression(
                pos = pos,
                callee = Js.Identifier(left, calleeName, null),
                arguments = listOf(
                    year ?: Js.NumericLiteral(left, DEFAULT_FULL_YEAR),
                    month ?: Js.NumericLiteral(left, 1),
                    day ?: Js.NumericLiteral(left, 1),
                ),
            )
        }
    }
}

private fun mathAccess(
    name: String,
    arity: Int,
    build: (Position, List<Js.Expression>, Js.Identifier) -> Js.Tree,
): Inliner {
    return { pos, args, strict, t ->
        if (strict && args.size != arity) {
            garbageExpression(pos, "need $arity argument(s) for use of $name")
        } else {
            // Copies the style of mathDotTrunc.
            val idName = t?.requirePropertyReference(
                OtherSupportCodeRequirement(
                    JsPropertyReference(
                        JsGlobalReference(ParsedName("Math")),
                        JsIdentifierName(name),
                    ),
                    Signature2(
                        WellKnownTypes.intType2,
                        false,
                        listOf(WellKnownTypes.float64Type2),
                    ),
                ).required as JsPropertyReference,
            )
                ?: JsIdentifierName(name)
            @Suppress("UNCHECKED_CAST")
            build(pos, args as List<Js.Expression>, Js.Identifier(pos.rightEdge, idName, null))
        }
    }
}

/**
 * `type` function from interface.js
 * Handles interface types & multiple inheritance using multiple arguments
 */
internal val typeSupportCode = JsUnInlinedExternalFunctionReference(
    source = DashedIdentifier.temperCoreLibraryIdentifier,
    stableName = JsIdentifierName("type"),
)

/** `str.hasIndex(idx)` -> `str.length > idx` */
private val stringHasIndexExpander =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _: JsTranslator? ->
        val str = arguments.getOrNull(0) as? Js.Expression
        val idx = arguments.getOrNull(1) as? Js.Expression
        if (strict && (arguments.size != 2 || str == null || idx == null)) {
            garbageExpression(pos, "Wrong arguments for String::hasIndex")
        } else {
            // We put the length first so that we don't affect order of operations
            val strPos = str?.pos ?: pos.leftEdge
            Js.InfixExpression(
                pos,
                Js.MemberExpression(
                    strPos,
                    str ?: Js.Identifier(strPos, JsIdentifierName("str"), null),
                    Js.Identifier(strPos.rightEdge, JsIdentifierName("length"), null),
                ),
                Js.Operator(strPos.rightEdge, ">"),
                idx ?: Js.Identifier(pos.rightEdge, JsIdentifierName("idx"), null),
            )
        }
    }

/** `String.begin` -> `0` */
private val stringBeginExpander = { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _: JsTranslator? ->
    if (strict && arguments.isNotEmpty()) {
        garbageExpression(pos, "Wrong arguments for String::begin")
    } else {
        Js.NumericLiteral(pos, 0)
    }
}

private val requireStringIndex = JsUnInlinedExternalFunctionReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("requireStringIndex"),
)
private val requireNoStringIndex = JsUnInlinedExternalFunctionReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("requireNoStringIndex"),
)

private val coreMarshalToJsonObject = JsUnInlinedExternalFunctionReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("marshalToJsonObject"),
)
private data object JsBackendNamingContext : NamingContext() {
    override val loc: ModuleLocation = ModuleName(filePath("-be", "js"), 2, isPreface = false)
    val counter = AtomicCounter()
}
private val marshalToJsonObjectSig = lazy {
    val stdJson = getSharedStdModules().first { "json" in (it.loc as ModuleName).sourceFile.last().baseName }
    val jsonObjectExport = stdJson.exports!!.first { it.name.baseName.nameText == "JsonObject" }
    val jsonAdapterExport = stdJson.exports!!.first { it.name.baseName.nameText == "JsonAdapter" }
    val nameMaker = ResolvedNameMaker(JsBackendNamingContext, Genre.Library)
    val t = TypeFormal(
        Position(JsBackendNamingContext.loc, 0, 0),
        nameMaker.unusedSourceName(ParsedName("T")),
        null,
        Variance.Invariant,
        JsBackendNamingContext.counter,
    )
    val tt = MkType2(t).get()
    // <T>(JsonAdapter<T>, T) -> JsonObject
    Signature2(
        returnType2 = TType.unpack(jsonObjectExport.value!!).type2,
        hasThisFormal = false,
        requiredInputTypes = listOf(
            MkType2(
                (TType.unpack(jsonAdapterExport.value!!).type2 as DefinedNonNullType)
                    .definition,
            )
                .actuals(listOf(tt))
                .get(),
            tt,
        ),
        typeFormals = listOf(t),
    )
}

private const val SLICE_ARITY = 3 // str, begin, end

/** `str.slice(begin, end) -> `str.slice(begin, end)` */
private val stringSliceExpander =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _: JsTranslator? ->
        val str = arguments.getOrNull(0) as? Js.Expression
        val begin = arguments.getOrNull(1) as? Js.Expression
        val end = arguments.getOrNull(2) as? Js.Expression
        if (strict && (arguments.size != SLICE_ARITY || str == null || begin == null || end == null)) {
            garbageExpression(pos, "Wrong arguments for String::slice")
        } else {
            val methodPos = str?.pos ?: pos
            Js.CallExpression(
                pos,
                Js.MemberExpression(
                    pos = methodPos,
                    obj = str ?: Js.Identifier(methodPos, JsIdentifierName("str"), null),
                    property = Js.Identifier(methodPos.rightEdge, JsIdentifierName("substring"), null),
                    computed = false,
                    optional = false,
                ),
                listOf(
                    begin ?: Js.Identifier(pos.rightEdge, JsIdentifierName("begin"), null),
                    end ?: Js.Identifier(pos.rightEdge, JsIdentifierName("end"), null),
                ),
            )
        }
    }

private fun stringIndexOptionCompareToHandler(infixOperatorTokenText: String): Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _: JsTranslator? ->
        val a = arguments.getOrNull(0) as? Js.Expression
        val b = arguments.getOrNull(1) as? Js.Expression
        if (strict && (arguments.size != 2 || a == null || b == null)) {
            garbageExpression(pos, "Wrong arguments for StringIndex::compareTo")
        } else {
            val aPos = a?.pos ?: pos.leftEdge
            val bPos = b?.pos ?: pos.rightEdge
            Js.InfixExpression(
                pos,
                a ?: Js.Identifier(aPos, JsIdentifierName("a"), null),
                Js.Operator(aPos.rightEdge, infixOperatorTokenText),
                b ?: Js.Identifier(bPos, JsIdentifierName("b"), null),
            )
        }
    }

/** `stringIndex.compareTo(other) -> `a - b` */
private val stringIndexOptionCompareToExpander =
    stringIndexOptionCompareToHandler("-")
private val stringIndexOptionCompareToExpanderEq =
    stringIndexOptionCompareToHandler("===")
private val stringIndexOptionCompareToExpanderGe =
    stringIndexOptionCompareToHandler(">=")
private val stringIndexOptionCompareToExpanderGt =
    stringIndexOptionCompareToHandler(">")
private val stringIndexOptionCompareToExpanderLe =
    stringIndexOptionCompareToHandler("<=")
private val stringIndexOptionCompareToExpanderLt =
    stringIndexOptionCompareToHandler("<")
private val stringIndexOptionCompareToExpanderNe =
    stringIndexOptionCompareToHandler("!==")

/** `StringIndex.none` -> `-1` */
private val stringIndexNoneExpander = { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _: JsTranslator? ->
    if (strict && arguments.isNotEmpty()) {
        garbageExpression(pos, "Wrong arguments for StringIndex::none")
    } else {
        Js.NumericLiteral(pos, -1)
    }
}

/** new StringBuilder() -> [""] */
private val stringBuilderConstructorExpander: Inliner =
    { pos: Position, args: List<Js.Tree>, strict: Boolean, _ ->
        if (strict && args.isNotEmpty()) {
            garbageExpression(pos, "need 0 arguments for StringBuilder::constructor")
        } else {
            Js.ArrayExpression(pos, listOf(Js.StringLiteral(pos, "")))
        }
    }

/** this.append(str) -> this[0] += str */
private val stringBuilderAppendExpander: Inliner =
    { pos: Position, args: List<Js.Tree>, strict: Boolean, _ ->
        val stringBuilder = args.getOrNull(0) as? Js.Expression
        val substring = args.getOrNull(1) as? Js.Expression
        if (strict && (args.size != 2 || stringBuilder == null || substring == null)) {
            garbageExpression(pos, "need 2 arguments for StringBuilder::append")
        } else {
            val sbPos = stringBuilder?.pos ?: pos.leftEdge
            val ssPos = substring?.pos ?: pos.rightEdge
            Js.InfixExpression(
                pos,
                Js.MemberExpression(
                    sbPos,
                    stringBuilder ?: Js.Identifier(sbPos, JsIdentifierName("stringBuilder"), null),
                    Js.NumericLiteral(sbPos.rightEdge, 0),
                    computed = true,
                ),
                Js.Operator(ssPos.leftEdge, "+="),
                substring ?: Js.Identifier(ssPos, JsIdentifierName("substring"), null),
            )
        }
    }

/** this.append(str, begin, end) -> this[0] += str.slice(begin, end) */
private val stringBuilderAppendBetweenExpander: Inliner =
    { pos: Position, args: List<Js.Tree>, strict: Boolean, t ->
        val stringBuilder = args.getOrNull(0) as? Js.Expression
        val substring = args.getOrNull(1) as? Js.Expression
        val begin = args.getOrNull(2) as? Js.Expression

        @Suppress("MagicNumber") // argument number
        val end = args.getOrNull(3) as? Js.Expression
        @Suppress("MagicNumber") // arity
        if (
            strict &&
            (args.size != 4 || stringBuilder == null || substring == null || begin == null || end == null)
        ) {
            garbageExpression(pos, "wrong arguments for StringBuilder::appendBetween")
        } else {
            val substringPos = listOfNotNull(substring, begin, end)
                .spanningPosition(substring?.pos ?: pos)
            stringBuilderAppendExpander(
                pos,
                listOf(
                    stringBuilder ?: Js.Identifier(pos.leftEdge, JsIdentifierName("stringBuilder"), null),
                    stringSliceExpander(
                        substringPos,
                        listOf(
                            substring ?: Js.Identifier(pos.rightEdge, JsIdentifierName("substring"), null),
                            begin ?: Js.Identifier(pos.rightEdge, JsIdentifierName("begin"), null),
                            end ?: Js.Identifier(pos.rightEdge, JsIdentifierName("end"), null),
                        ),
                        strict,
                        t,
                    ),
                ),
                strict,
                t,
            )
        }
    }

/** this.toString() -> this[0] */
private val stringBuilderToStringExpander: Inliner =
    { pos: Position, args: List<Js.Tree>, strict: Boolean, _ ->
        if (strict && args.size != 1) {
            garbageExpression(pos, "need 1 argument for StringBuilder::toString()")
        } else {
            val stringBuilder = args.getExprSafe(0, pos.leftEdge)
            Js.MemberExpression(
                stringBuilder.pos,
                stringBuilder,
                Js.NumericLiteral(stringBuilder.pos.rightEdge, 0),
                computed = true,
            )
        }
    }

/** BigInt(n) */
private val bigintExpander: Inliner =
    { pos: Position, args: List<Js.Tree>, strict: Boolean, _ ->
        if (strict && args.size != 1) {
            garbageExpression(pos, "need 1 argument for StringBuilder::toString()")
        } else {
            Js.CallExpression(
                pos,
                JsGlobalReference(ParsedName("BigInt")).asIdentifier(pos),
                args.map { it as Js.Actual },
            )
        }
    }

private fun mathCall(name: String, arity: Int = 1) = mathAccess(name, arity) { pos, args, id ->
    Js.CallExpression(pos = pos, callee = id, arguments = args)
}

private fun mathProperty(name: String) = mathAccess(name, 0) { _, _, id -> id }

private fun propertyReadToMethodCall(methodName: String) = propertyReadToMethodCall(JsIdentifierName(methodName))

private fun propertyReadToMethodCall(
    methodName: JsIdentifierName,
): Inliner {
    return { pos, args, strict, _ ->
        val thisReference = args.getOrNull(0) as? Js.Expression
        if (strict && (args.size != 1 || thisReference == null)) {
            garbageExpression(pos, "need one `this` argument for read of .$methodName()")
        } else {
            Js.CallExpression(
                pos = pos,
                callee = Js.MemberExpression(
                    pos = pos,
                    obj = thisReference ?: Js.NullLiteral(pos),
                    property = Js.Identifier(pos.rightEdge, methodName, null),
                ),
                arguments = emptyList(),
            )
        }
    }
}

private val dateGetMonthExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        val date = arguments.getOrNull(0) as? Js.Expression

        if (strict && (arguments.size != 1 || date == null)) {
            garbageExpression(pos, "date.getMonth() requires 1 expression")
        } else {
            val right = pos.rightEdge
            Js.InfixExpression(
                pos = pos,
                left = Js.CallExpression(
                    pos = pos,
                    callee = Js.MemberExpression(
                        pos = pos,
                        obj = date ?: Js.NullLiteral(pos),
                        property = Js.Identifier(right, JsIdentifierName("getUTCMonth"), null),
                    ),
                    arguments = emptyList(),
                ),
                operator = Js.Operator(right, "+"),
                right = Js.NumericLiteral(right, 1),
            )
        }
    }

private const val ISO_WEEKDAY_NUM_SUNDAY = 7
private val dateGetDayOfWeekExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        propertyReadToMethodCall("getUTCDay")
        val date = arguments.getOrNull(0) as? Js.Expression

        if (strict && (arguments.size != 1 || date == null)) {
            garbageExpression(pos, "date.get dayOfWeek() requires 1 expression")
        } else {
            val right = pos.rightEdge
            // d.getUTCDay() || 7.
            // getUTCDay returns 0 for Sunday, which is falsey, so to turn it into an
            // ISO Weekday we just or it with 7.
            Js.LogicalExpression(
                pos = pos,
                left = Js.CallExpression(
                    pos = pos,
                    callee = Js.MemberExpression(
                        pos = pos,
                        obj = date ?: Js.NullLiteral(pos),
                        property = Js.Identifier(right, JsIdentifierName("getUTCDay"), null),
                    ),
                    arguments = emptyList(),
                ),
                operator = Js.Operator(right, "||"),
                right = Js.NumericLiteral(right, ISO_WEEKDAY_NUM_SUNDAY),
            )
        }
    }

private val dateToIsoStringExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        val date = arguments.getOrNull(0) as? Js.Expression

        if (strict && (arguments.size != 1 || date == null)) {
            garbageExpression(pos, "date.toString() requires 1 expression")
        } else {
            val right = pos.rightEdge
            // date.toISOString() is idiomatic but includes the time portion, so do
            // date.toISOString().split('T')[0]
            // TODO: maybe
            Js.MemberExpression(
                pos = pos,
                obj = Js.CallExpression( // .split
                    pos = pos,
                    Js.MemberExpression(
                        pos = pos,
                        obj = Js.CallExpression( // .toISOString()
                            pos = pos,
                            callee = Js.MemberExpression(
                                pos = pos,
                                obj = date ?: Js.NullLiteral(pos),
                                property = Js.Identifier(right, JsIdentifierName("toISOString"), null),
                            ),
                            arguments = emptyList(),
                        ),
                        property = Js.Identifier(right, JsIdentifierName("split"), null),
                    ),
                    arguments = listOf(
                        Js.StringLiteral(right, "T"),
                    ),
                ),
                property = Js.NumericLiteral(right, 0),
                computed = true,
            )
        }
    }

private fun dateFromIsoStringExpander(
    pos: Position,
    arguments: List<Js.Tree>,
    strict: Boolean,
    genre: Genre,
): Js.Expression {
    val isoStr = arguments.getOrNull(0) as? Js.Expression

    return if (strict && (arguments.size != 1 || isoStr == null)) {
        garbageExpression(pos, "Date.fromIsoString() requires 1 expression")
    } else {
        val left = pos.leftEdge
        val dateConstructor = Js.Identifier(left, JsIdentifierName("Date"), null)
        val globalDate: Js.SimpleRef = when (genre) {
            Genre.Library -> dateConstructor.globalizeThis()
            Genre.Documentation -> dateConstructor
        }

        // new Date(Date.parse(x))
        Js.NewExpression(
            pos = pos,
            globalDate.deepCopy(),
            listOf(
                Js.CallExpression(
                    pos = pos,
                    Js.MemberExpression(
                        pos = pos,
                        obj = globalDate,
                        property = Js.Identifier(left, JsIdentifierName("parse"), null),
                    ),
                    listOf(isoStr ?: Js.NullLiteral(pos)),
                ),
            ),
        )
    }
}

private val listToListBuilderIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        val obj = arguments.getOrNull(0) as? Js.Expression
        if (strict && (arguments.size != 1 || obj == null)) {
            garbageExpression(pos, "Wrong arguments for toListBuilder idiom expander")
        } else {
            Js.CallExpression(
                pos,
                Js.MemberExpression(
                    pos,
                    obj = obj ?: Js.Identifier(pos, JsIdentifierName("x"), null),
                    property = Js.Identifier(pos, JsIdentifierName("slice"), null),
                ),
                emptyList(),
            )
        }
    }

private val listBuilderConstructorIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        if (strict && arguments.isNotEmpty()) {
            garbageExpression(pos, "Wrong arguments for ListBuilder idiom expander")
        } else {
            Js.ArrayExpression(pos, emptyList())
        }
    }

/** Given `x` constructs `!x`. */
private val stringIsEmptyIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        val argument = arguments.getOrNull(0) as? Js.Expression
        if (strict && (arguments.size != 1 || argument == null)) {
            garbageExpression(pos, "String::isEmpty needs one argument")
        } else {
            Js.UnaryExpression(
                pos,
                Js.Operator(pos.leftEdge, "!"),
                argument ?: Js.Identifier(pos, JsIdentifierName("x"), null),
            )
        }
    }

/** Given `x` constructs `!x.length`. */
private val listIsEmptyIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, translator ->
        Js.UnaryExpression(
            pos,
            Js.Operator(pos.leftEdge, "!"),
            lengthIdiomExpander(pos, arguments, strict, translator),
        )
    }

/** Given `x` and `f` constructs `x.forEach((e) => f(e))`. */
private val listForEachIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        val series = arguments.getOrNull(0) as? Js.Expression
        val body = arguments.getOrNull(1) as? Js.Expression
        // Note: The function passed to forEach gets (element, index).
        // We do not mask out that extra argument, but if `f` were varargs or took extra,
        // optional arguments, we might need to.

        // TODO: Can we use type information to detect extra/optional arguments which
        // might require replacing `body` with `(x) => body(x)`?
        if (strict && (arguments.size != 2 || series == null || body == null)) {
            garbageExpression(pos, "Wrong arguments for forEach idiom expander")
        } else {
            Js.CallExpression(
                pos = pos,
                callee = Js.MemberExpression(
                    pos = pos,
                    obj = series ?: Js.Identifier(pos.leftEdge, JsIdentifierName("x"), null),
                    property = Js.Identifier(
                        series?.pos?.rightEdge ?: pos.leftEdge,
                        JsIdentifierName("forEach"),
                        null,
                    ),
                    computed = false,
                    optional = false,
                ),
                arguments = listOf(body ?: Js.Identifier(pos.rightEdge, JsIdentifierName("f"), null)),
            )
        }
    }

private val assertStrict = JsUnInlinedExternalFunctionReference(
    source = DashedIdentifier("assert"),
    // Among other things, conveniently doesn't need default import.
    // Also, this existed since before node supported `import` imports, so should be fine to use.
    // See for example: https://nodejs.org/docs/latest-v15.x/api/assert.html#assert_strict_assertion_mode
    stableName = JsIdentifierName("strict"),
)

private val bailExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, translator: JsTranslator? ->
        val test = arguments.getOrNull(0) as? Js.Expression
        @Suppress("MagicNumber") // Yeah, so we expect arity 3.
        if (translator == null) {
            garbageExpression(pos, "No translator")
        } else if (strict && (arguments.size != 1 || test == null)) {
            garbageExpression(pos, "Wrong arguments for bail")
        } else {
            Js.CallExpression(
                pos,
                callee = Js.MemberExpression(
                    pos,
                    Js.Identifier(pos, translator.requireExternalReference(assertStrict), null),
                    Js.Identifier(pos, JsIdentifierName("fail"), null),
                ),
                arguments = when (test) {
                    null -> emptyList()
                    else -> listOf(
                        Js.CallExpression(
                            pos,
                            Js.MemberExpression(
                                pos,
                                test,
                                Js.Identifier(pos, JsIdentifierName("messagesCombined"), null),
                            ),
                            emptyList(),
                        ),
                    )
                },
            )
        }
    }

private val getConsoleExpander: Inliner =
    { pos: Position, _: List<Js.Tree>, _: Boolean, translator: JsTranslator? ->
        // Call it a function because that's what we can do here.
        val consoleRef = JsUnInlinedExternalFunctionReference(
            source = DashedIdentifier.temperCoreLibraryIdentifier,
            stableName = JsIdentifierName("globalConsole"),
        )
        // Use a fallback name because we apparently need that for simple tmpl tree to string?
        val consoleName = translator?.requireExternalReference(consoleRef) ?: JsIdentifierName("console")
        // But just use it as a value. TODO Some logging framework for JS.
        Js.Identifier(pos, consoleName, null)
    }

private val ignoreIdiomExpander: Inliner =
    { pos: Position, arguments: List<Js.Tree>, strict: Boolean, _ ->
        if (strict && arguments.size != 1) {
            garbageExpression(pos, "Wrong arguments for ignore idiom expander")
        } else {
            val voidValue = Js.UnaryExpression(pos, Js.Operator(pos, "void"), Js.NumericLiteral(pos, 0))
            when (val arg = arguments.getOrNull(0)) {
                // Usually we've reduced things to just identifiers, where we can just ignore them here.
                // We could replace with nothing if we know we're unused.
                // Maybe can do that after Temper handles void more.
                // Meanwhile, minifiers I tried could discard these entirely when unused inside functions.
                // And at least some can't discard calls to empty functions, so this is still useful.
                is Js.Identifier, null -> voidValue
                // If it's more than an identifier, keep it around.
                // Minifiers I tried could discard the `void 0` if unused.
                is Js.Expression -> Js.SequenceExpression(pos, listOf(arg, voidValue))
                else -> garbageExpression(pos, "Expected one expression argument")
            }
        }
    }

/**
 * For the given connected method key, the corresponding name exported from
 * `runtime-support/index.js`.
 */
internal fun connectedKeyToExportedName(connectedKey: String) = JsIdentifierName(
    connectedKey.split("::")
        .mapIndexed { i, segment ->
            if (i == 0) {
                segment.asciiUnTitleCase()
            } else {
                segment.asciiTitleCase()
            }
        }
        .joinToString(""),
)

private fun runtimeLibraryBackedSupportCode(
    builtinOperatorId: BuiltinOperatorId,
    source: DashedIdentifier = DashedIdentifier.temperCoreLibraryIdentifier,
    needsThisEquivalent: Boolean = false,
    inline: (Position, List<Js.Tree>) -> Js.Expression?,
): Pair<BuiltinOperatorId, InlinedJs> = runtimeLibraryBackedSupportCode(
    builtinOperatorId = builtinOperatorId,
    source = source,
    requires = emptyList(),
    needsThisEquivalent = needsThisEquivalent,
    inline = { pos, argTrees, _ ->
        inline(pos, argTrees)
    },
)

private fun runtimeLibraryBackedSupportCode(
    builtinOperatorId: BuiltinOperatorId,
    source: DashedIdentifier = DashedIdentifier.temperCoreLibraryIdentifier,
    requires: List<SupportCodeRequirement>,
    needsThisEquivalent: Boolean = false,
    inline: (Position, List<Js.Tree>, JsTranslator?) -> Js.Expression?,
): Pair<BuiltinOperatorId, InlinedJs> {
    val externalName = builtinOperatorId.name.asciiUnTitleCase() // PlusIntInt -> plusIntInt
    return builtinOperatorId to InlinedJs(
        source = source,
        stableName = JsIdentifierName(externalName),
        needsThisEquivalent = needsThisEquivalent,
        builtinOperatorId = builtinOperatorId,
        requires = requires,
    ) { pos: Position, operands: List<Js.Tree>, _, t ->
        inline(pos, operands, t)
            ?: garbageExpression(pos, "Cannot inline ${builtinOperatorId.name}")
    }
}

private fun runtimeLibraryReference(id: BuiltinOperatorId, name: String? = null) =
    id to JsUnInlinedExternalFunctionReference(
        source = DashedIdentifier.temperCoreLibraryIdentifier,
        stableName = JsIdentifierName(name ?: id.name.asciiUnTitleCase()),
        builtinOperatorId = id,
    )

private fun arity1(operands: List<Js.Tree>): List<Js.Expression>? {
    if (operands.size == 1) {
        val (a) = operands
        if (a is Js.Expression) { return listOf(a) }
    }
    return null
}

private fun arity2(operands: List<Js.Tree>): List<Js.Expression>? {
    if (operands.size == 2) {
        val (a, b) = operands
        if (a is Js.Expression && b is Js.Expression) { return listOf(a, b) }
    }
    return null
}

/** `globalThis.Math.imul` */
private val mathDotIMul = OtherSupportCodeRequirement(
    JsPropertyReference(
        JsGlobalReference(ParsedName("Math")),
        JsIdentifierName("imul"),
    ),
    Signature2(
        WellKnownTypes.intType2,
        false,
        listOf(WellKnownTypes.intType2, WellKnownTypes.intType2),
    ),
)

private val coreCmpGeneric = JsUnInlinedExternalFunctionReference(
    source = DashedIdentifier.temperCoreLibraryIdentifier,
    stableName = JsIdentifierName("cmpGeneric"),
    builtinOperatorId = BuiltinOperatorId.CmpGeneric,
)

private val coreCmpFloat = JsUnInlinedExternalFunctionReference(
    source = DashedIdentifier.temperCoreLibraryIdentifier,
    stableName = JsIdentifierName("cmpFloat"),
    builtinOperatorId = BuiltinOperatorId.CmpGeneric,
)

private val coreCmpString = JsUnInlinedExternalFunctionReference(
    source = DashedIdentifier.temperCoreLibraryIdentifier,
    stableName = JsIdentifierName("cmpString"),
    builtinOperatorId = BuiltinOperatorId.CmpGeneric,
)

private fun cmpFromCoreCmp(
    supportCode: JsExternalReference,
): (Position, JsTranslator?, Js.Expression, Js.Expression) -> Js.Expression = { pos, translator, left, right ->
    Js.CallExpression(
        pos,
        Js.Identifier(
            pos,
            translator?.requireExternalReference(supportCode)
                ?: JsIdentifierName(JsIdentifierGrammar.massageJsIdentifier(supportCode.baseName.nameText)),
            null,
        ),
        listOf(left, right),
    )
}

private fun cmpToOperator(
    id: BuiltinOperatorId,
    operatorTokenText: String,
    generateCmp: (pos: Position, translator: JsTranslator?, left: Js.Expression, right: Js.Expression) -> Js.Expression,
): Pair<BuiltinOperatorId, InlinedJs> {
    return runtimeLibraryBackedSupportCode(id, requires = listOf()) { pos, args, translator ->
        Js.BinaryExpression(
            pos,
            generateCmp(pos, translator, args.getExprSafe(0, pos.leftEdge), args.getExprSafe(1, pos.rightEdge)),
            Js.Operator(pos, operatorTokenText),
            Js.NumericLiteral(pos, 0),
        )
    }
}

private val builtinOperatorIdToSupportCode = BuiltinOperatorId.entries.mapNotNull { id ->
    when (id) {
        BuiltinOperatorId.BooleanNegation -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity1(args)?.let { (a) ->
                Js.UnaryExpression(pos, Js.Operator(pos.leftEdge, "!"), a)
            }
        }

        BuiltinOperatorId.BitwiseAnd -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (a, b) ->
                Js.InfixExpression(pos, a, Js.Operator(pos.leftEdge, "&"), b)
            }
        }

        BuiltinOperatorId.BitwiseOr -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (a, b) ->
                Js.InfixExpression(pos, a, Js.Operator(pos.leftEdge, "|"), b)
            }
        }

        BuiltinOperatorId.IsNull -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity1(args)?.let { (a) ->
                Js.InfixExpression(
                    pos,
                    a,
                    Js.Operator(pos.rightEdge, "=="),
                    Js.NullLiteral(pos.rightEdge),
                )
            }
        }

        BuiltinOperatorId.NotNull -> null

        BuiltinOperatorId.DivFltFlt -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (a, b) ->
                Js.InfixExpression(
                    pos,
                    a,
                    Js.Operator(pos.leftEdge, "/"),
                    b,
                )
            }
        }

        BuiltinOperatorId.ModFltFlt -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (a, b) ->
                Js.InfixExpression(
                    pos,
                    a,
                    Js.Operator(pos.leftEdge, "%"),
                    b,
                )
            }
        }

        BuiltinOperatorId.DivIntInt -> runtimeLibraryReference(id) // Error handling
        BuiltinOperatorId.DivIntIntSafe -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (a, b) ->
                Js.InfixExpression(
                    pos,
                    a,
                    Js.Operator(b.pos.leftEdge, "/"),
                    b,
                ).toInt32()
            }
        }

        BuiltinOperatorId.ModIntInt -> runtimeLibraryReference(id) // Error handling
        BuiltinOperatorId.ModIntIntSafe ->
            // Delegate to ModIntInt.  TODO: check whether we can use `%` and `|`
            id to runtimeLibraryReference(BuiltinOperatorId.ModIntInt).second

        BuiltinOperatorId.DivIntInt64, BuiltinOperatorId.DivIntInt64Safe -> runtimeLibraryBackedSupportCode(
            id,
            requires = listOf(),
        ) { pos, args, translator ->
            arity2(args)?.let { (a, b) ->
                Js.InfixExpression(
                    pos,
                    a,
                    Js.Operator(b.pos.leftEdge, "/"),
                    b,
                ).toInt64(translator!!)
            }
        }

        BuiltinOperatorId.ModIntInt64, BuiltinOperatorId.ModIntInt64Safe -> runtimeLibraryBackedSupportCode(
            id,
            requires = listOf(),
        ) { pos, args, translator ->
            arity2(args)?.let { (a, b) ->
                Js.InfixExpression(
                    pos,
                    a,
                    Js.Operator(b.pos.leftEdge, "%"),
                    b,
                ).toInt64(translator!!)
            }
        }

        BuiltinOperatorId.MinusFlt, BuiltinOperatorId.MinusInt, BuiltinOperatorId.MinusInt64 ->
            runtimeLibraryBackedSupportCode(id, requires = listOf()) { pos, args, translator ->
                arity1(args)?.let { (e) ->
                    Js.UnaryExpression(
                        pos,
                        Js.Operator(pos.leftEdge, "-"),
                        e,
                    ).toOutType(id, translator!!)
                }
            }

        BuiltinOperatorId.MinusFltFlt, BuiltinOperatorId.MinusIntInt, BuiltinOperatorId.MinusIntInt64 ->
            runtimeLibraryBackedSupportCode(id, requires = listOf()) { pos, args, translator ->
                arity2(args)?.let { (a, b) ->
                    Js.InfixExpression(
                        pos,
                        a,
                        Js.Operator(pos.leftEdge, "-"),
                        b,
                    ).toOutType(id, translator!!)
                }
            }

        BuiltinOperatorId.PlusFltFlt, BuiltinOperatorId.PlusIntInt, BuiltinOperatorId.PlusIntInt64 ->
            runtimeLibraryBackedSupportCode(id, requires = listOf()) { pos, args, translator ->
                arity2(args)?.let { (a, b) ->
                    Js.InfixExpression(
                        pos,
                        a,
                        Js.Operator(pos.leftEdge, "+"),
                        b,
                    ).toOutType(id, translator!!)
                }
            }

        BuiltinOperatorId.PowFltFlt ->
            runtimeLibraryBackedSupportCode(id, requires = listOf()) { pos, args, _ ->
                arity2(args)?.let { (a, b) ->
                    Js.InfixExpression(
                        pos,
                        a,
                        Js.Operator(pos.leftEdge, "**"),
                        b,
                    )
                }
            }

        BuiltinOperatorId.TimesFltFlt, BuiltinOperatorId.TimesIntInt64 ->
            runtimeLibraryBackedSupportCode(id, requires = listOf()) { pos, args, translator ->
                arity2(args)?.let { (a, b) ->
                    Js.InfixExpression(
                        pos,
                        a,
                        Js.Operator(pos.leftEdge, "*"),
                        b,
                    ).toOutType(id, translator!!)
                }
            }

        BuiltinOperatorId.TimesIntInt -> runtimeLibraryBackedSupportCode(
            id,
            requires = listOf(mathDotIMul),
        ) { pos, args, t ->
            arity2(args)?.let { (a, b) ->
                val truncName = t?.requirePropertyReference(mathDotIMul.required as JsPropertyReference)
                    ?: JsIdentifierName("imul") // for doc mode?
                // `Math.imul(a, b)` but where imul is a pooled name
                Js.CallExpression(
                    pos,
                    Js.Identifier(pos.leftEdge, truncName, null),
                    listOf(a, b),
                )
            }
        }

        BuiltinOperatorId.StrCat -> runtimeLibraryBackedSupportCode(id) strCat@{ pos, args ->
            val expressions = args.map { it as Js.Expression }
            val firstExpr = expressions.firstOrNull() ?: return@strCat Js.StringLiteral(pos, "")
            val firstExprAsString = when (firstExpr) {
                is Js.StringLiteral -> firstExpr
                else -> Js.CallExpression(
                    pos,
                    Js.Identifier(pos, JsIdentifierName("String"), null),
                    listOf(firstExpr),
                )
            }
            expressions.drop(1).fold(firstExprAsString) { last, elem ->
                Js.BinaryExpression(
                    pos,
                    last,
                    Js.Operator(pos, "+"),
                    elem,
                )
            }
        }

        BuiltinOperatorId.Listify -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            Js.CallExpression(
                pos,
                Js.MemberExpression(
                    pos,
                    Js.Identifier(pos, JsIdentifierName("Object"), null),
                    Js.Identifier(pos, JsIdentifierName("freeze"), null),
                ),
                listOf(
                    Js.ArrayExpression(
                        pos,
                        args.map { it as Js.Expression },
                    ),
                ),
            )
        }

        BuiltinOperatorId.EqIntInt -> makeIntCmp(id, "===")
        BuiltinOperatorId.NeIntInt -> makeIntCmp(id, "!==")
        BuiltinOperatorId.LtIntInt -> makeIntCmp(id, "<")
        BuiltinOperatorId.GtIntInt -> makeIntCmp(id, ">")
        BuiltinOperatorId.LeIntInt -> makeIntCmp(id, "<=")
        BuiltinOperatorId.GeIntInt -> makeIntCmp(id, ">=")
        BuiltinOperatorId.CmpIntInt -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (lhs, rhs) ->
                Js.BinaryExpression(pos, lhs, Js.Operator(pos, "-"), rhs)
            }
        }

        BuiltinOperatorId.EqFltFlt -> cmpToOperator(id, "===", cmpFromCoreCmp(coreCmpFloat))
        BuiltinOperatorId.NeFltFlt -> cmpToOperator(id, "!==", cmpFromCoreCmp(coreCmpFloat))
        BuiltinOperatorId.LtFltFlt -> cmpToOperator(id, "<", cmpFromCoreCmp(coreCmpFloat))
        BuiltinOperatorId.GtFltFlt -> cmpToOperator(id, ">", cmpFromCoreCmp(coreCmpFloat))
        BuiltinOperatorId.LeFltFlt -> cmpToOperator(id, "<=", cmpFromCoreCmp(coreCmpFloat))
        BuiltinOperatorId.GeFltFlt -> cmpToOperator(id, ">=", cmpFromCoreCmp(coreCmpFloat))
        BuiltinOperatorId.CmpFltFlt -> id to coreCmpFloat

        BuiltinOperatorId.EqStrStr -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (lhs, rhs) ->
                Js.BinaryExpression(
                    pos,
                    lhs,
                    Js.Operator(pos, "==="),
                    rhs,
                )
            }
        }
        BuiltinOperatorId.NeStrStr -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (lhs, rhs) ->
                Js.BinaryExpression(
                    pos,
                    lhs,
                    Js.Operator(pos, "!=="),
                    rhs,
                )
            }
        }
        BuiltinOperatorId.LtStrStr -> cmpToOperator(id, "<", cmpFromCoreCmp(coreCmpString))
        BuiltinOperatorId.GtStrStr -> cmpToOperator(id, ">", cmpFromCoreCmp(coreCmpString))
        BuiltinOperatorId.LeStrStr -> cmpToOperator(id, "<=", cmpFromCoreCmp(coreCmpString))
        BuiltinOperatorId.GeStrStr -> cmpToOperator(id, ">=", cmpFromCoreCmp(coreCmpString))
        BuiltinOperatorId.CmpStrStr -> id to coreCmpString

        BuiltinOperatorId.EqGeneric -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (lhs, rhs) ->
                Js.CallExpression(
                    pos,
                    Js.MemberExpression(
                        pos,
                        Js.Identifier(pos, JsIdentifierName("Object"), null),
                        Js.Identifier(pos, JsIdentifierName("is"), null),
                    ),
                    listOf(lhs, rhs),
                )
            }
        }
        BuiltinOperatorId.NeGeneric -> runtimeLibraryBackedSupportCode(id) { pos, args ->
            arity2(args)?.let { (lhs, rhs) ->
                Js.UnaryExpression(
                    pos,
                    Js.Operator(pos, "!"),
                    Js.CallExpression(
                        pos,
                        Js.MemberExpression(
                            pos,
                            Js.Identifier(pos, JsIdentifierName("Object"), null),
                            Js.Identifier(pos, JsIdentifierName("is"), null),
                        ),
                        listOf(lhs, rhs),
                    ),
                )
            }
        }
        BuiltinOperatorId.LtGeneric -> cmpToOperator(id, "<", cmpFromCoreCmp(coreCmpGeneric))
        BuiltinOperatorId.GtGeneric -> cmpToOperator(id, ">", cmpFromCoreCmp(coreCmpGeneric))
        BuiltinOperatorId.LeGeneric -> cmpToOperator(id, "<=", cmpFromCoreCmp(coreCmpGeneric))
        BuiltinOperatorId.GeGeneric -> cmpToOperator(id, ">=", cmpFromCoreCmp(coreCmpGeneric))
        BuiltinOperatorId.CmpGeneric -> id to coreCmpGeneric

        BuiltinOperatorId.Bubble,
        BuiltinOperatorId.Panic,
        BuiltinOperatorId.Print,
        -> runtimeLibraryReference(id)

        BuiltinOperatorId.Async -> runtimeLibraryReference(id, "runAsync")

        // should not be used with CoroutineStrategy.TranslateToGenerator
        BuiltinOperatorId.AdaptGeneratorFn,
        BuiltinOperatorId.SafeAdaptGeneratorFn,
        -> null
    }
}.toMap()

private fun Js.Expression.toInt32() = run {
    Js.InfixExpression(
        pos,
        this,
        Js.Operator(pos, "|"),
        Js.NumericLiteral(pos, 0),
    )
}

private val clampInt64 = JsUnInlinedExternalFunctionReference(
    DashedIdentifier.temperCoreLibraryIdentifier,
    JsIdentifierName("clampInt64"),
)

private fun Js.Expression.toInt64(translator: JsTranslator) = run {
    val calleeName = translator.requireExternalReference(clampInt64)
    val callee = Js.Identifier(pos, calleeName, null)
    Js.CallExpression(pos, callee, listOf(this))
}

private fun Js.Expression.toOutType(id: BuiltinOperatorId, translator: JsTranslator) = run {
    // Some ops don't reach here, but just grab all int-producing. Should be a fast switch.
    when (id) {
        BuiltinOperatorId.DivIntInt,
        BuiltinOperatorId.DivIntIntSafe,
        BuiltinOperatorId.MinusInt,
        BuiltinOperatorId.MinusIntInt,
        BuiltinOperatorId.ModIntInt,
        BuiltinOperatorId.ModIntIntSafe,
        BuiltinOperatorId.PlusIntInt,
        BuiltinOperatorId.TimesIntInt,
        -> toInt32()
        BuiltinOperatorId.DivIntInt64,
        BuiltinOperatorId.DivIntInt64Safe,
        BuiltinOperatorId.MinusInt64,
        BuiltinOperatorId.MinusIntInt64,
        BuiltinOperatorId.ModIntInt64,
        BuiltinOperatorId.ModIntInt64Safe,
        BuiltinOperatorId.PlusIntInt64,
        BuiltinOperatorId.TimesIntInt64,
        -> toInt64(translator)
        else -> this
    }
}

private val docsListifyInliner =
    runtimeLibraryBackedSupportCode(BuiltinOperatorId.Listify) inline@{ pos, args ->
        val argExprs = mutableListOf<Js.Expression>()
        for (arg in args) {
            val argExpr = arg as? Js.Expression ?: return@inline null
            argExprs.add(argExpr)
        }
        Js.ArrayExpression(pos, argExprs.toList())
    }.second

private val docsPrintInliner =
    runtimeLibraryBackedSupportCode(BuiltinOperatorId.Print) inline@{ pos, args ->
        val argExprs = mutableListOf<Js.Expression>()
        for (arg in args) {
            val argExpr = arg as? Js.Expression ?: return@inline null
            argExprs.add(argExpr)
        }
        val calleePos = pos.leftEdge
        Js.CallExpression(
            pos,
            callee = Js.MemberExpression(
                calleePos,
                Js.MemberExpression(
                    calleePos,
                    // TODO: provide enough information on bare names to allow skipping
                    // globalThis when doing so would lead to no name collision.
                    // TODO This path is skipped on new Temper `console.log`, but for docgen, do we want globalThis?
                    Js.Identifier(calleePos, globalThisName, null),
                    Js.Identifier(calleePos, JsIdentifierName("console"), null),
                ),
                Js.Identifier(calleePos, JsIdentifierName("log"), null),
            ),
            arguments = argExprs,
        )
    }.second

// 🐈  🐈 -> 🐈🐈🐈
//   🐈
private val catInliner =
    runtimeLibraryBackedSupportCode(BuiltinOperatorId.StrCat) inline@{ pos, args ->
        val argExprs = mutableListOf<Js.Expression>()
        for (arg in args) {
            val argExpr = arg as? Js.Expression ?: return@inline null
            argExprs.add(argExpr)
        }

        val quasis = mutableListOf<Js.TemplateElement>()
        val holes = mutableListOf<Js.Expression>()

        var partialTemplateElement: Pair<Position, String>? = null
        fun addTemplateElement(pos: Position) {
            if (partialTemplateElement == null) {
                partialTemplateElement = pos to ""
            }
            val (templateElementPos, templateElementText) =
                partialTemplateElement ?: (pos to "")
            partialTemplateElement = null
            quasis.add(
                Js.TemplateElement(
                    templateElementPos,
                    JsTemplateHelpers.untaggedTemplateText(templateElementText),
                ),
            )
        }
        for (arg in argExprs) {
            if (arg is Js.StringLiteral) {
                partialTemplateElement = when (val before = partialTemplateElement) {
                    null -> arg.pos to arg.value
                    else -> {
                        val (posBefore, textBefore) = before
                        listOf(posBefore, arg.pos).spanningPosition(posBefore) to
                            "$textBefore${arg.value}"
                    }
                }
            } else {
                // Every template element, except the last (below) pairs with a hole.
                addTemplateElement(arg.pos.leftEdge)
                holes.add(arg)
            }
        }
        addTemplateElement(pos.rightEdge)
        Js.TemplateExpression(pos, quasis.toList(), holes.toList())
    }.second

/** Js.Identifier("foo").globalizeThis -> `globalThis.foo` */
internal fun Js.Identifier.globalizeThis(pos: Position = this.pos) = Js.MemberExpression(
    pos = pos,
    obj = Js.Identifier(this.pos.leftEdge, globalThisName, null),
    property = this,
    computed = false,
)

/** developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/Date#year */
@Suppress("MagicNumber") // yes, JS date constructor rules are dodgy magic
private val jsProblematicYearRange = 0L..99L
private const val DEFAULT_FULL_YEAR = 1900
private const val DATE_CONSTRUCTOR_ARITY = 3 // year, month, day

private fun makeIntCmp(
    id: BuiltinOperatorId,
    operatorTokenText: String,
) = runtimeLibraryBackedSupportCode(id) { pos, args ->
    Js.BinaryExpression(
        pos,
        args.getExprSafe(0, pos.leftEdge),
        Js.Operator(pos, operatorTokenText),
        args.getExprSafe(1, pos.rightEdge),
    )
}

/** Substitutes missing args like _0 so that support code references can render nicely inside TmpL trees */
private fun List<Js.Tree>.getExprSafe(i: Int, fallbackPos: Position): Js.Expression =
    if (i in indices) {
        this[i] as Js.Expression
    } else {
        Js.Identifier(fallbackPos, JsIdentifierName("_$i"), null)
    }

/** JSON.stringify assigns special significance to this method name */
private const val JAVASCRIPT_TOJSON_SPECIAL_NAME = "toJSON"
