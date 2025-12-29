package lang.temper.be.cpp

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.format.OutputToken
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.pureVirtualBuiltinName

internal const val TEMPER_CORE_NAMESPACE = "temper::core"

internal object CppSupportNetwork : SupportNetwork {
    override val backendDescription: String
        get() = "Cpp Backend"
    override val bubbleStrategy: BubbleBranchStrategy = BubbleBranchStrategy.CatchBubble
    override val coroutineStrategy: CoroutineStrategy = CoroutineStrategy.TranslateToGenerator
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionType

    override fun representationOfVoid(
        genre: Genre,
    ): RepresentationOfVoid = RepresentationOfVoid.ReifyVoid

    override fun getSupportCode(
        pos: Position,
        builtin: NamedBuiltinFun,
        genre: Genre,
    ): SupportCode? = when (builtin.builtinOperatorId) {
        BuiltinOperatorId.BooleanNegation -> Like.unary("!")
        BuiltinOperatorId.BitwiseAnd -> Like.binary("&")
        BuiltinOperatorId.BitwiseOr -> Like.binary("|")
        BuiltinOperatorId.IsNull -> Like.core("is_null")
        BuiltinOperatorId.NotNull -> Like.core("not_null")
        BuiltinOperatorId.DivFltFlt -> Like.binary("/")
        BuiltinOperatorId.DivIntInt, BuiltinOperatorId.DivIntInt64 -> Like.binary("/")
        BuiltinOperatorId.DivIntIntSafe, BuiltinOperatorId.DivIntInt64Safe -> Like.core("div_safe")
        BuiltinOperatorId.ModFltFlt -> Like.binary("%")
        BuiltinOperatorId.ModIntInt, BuiltinOperatorId.ModIntInt64 -> Like.binary("%")
        BuiltinOperatorId.ModIntIntSafe, BuiltinOperatorId.ModIntInt64Safe -> Like.core("mod_safe")
        BuiltinOperatorId.MinusFlt -> Like.unary("-")
        BuiltinOperatorId.MinusFltFlt -> Like.binary("-")
        BuiltinOperatorId.MinusInt, BuiltinOperatorId.MinusInt64 -> Like.unary("-")
        BuiltinOperatorId.MinusIntInt, BuiltinOperatorId.MinusIntInt64 -> Like.binary("-")
        BuiltinOperatorId.PlusFltFlt -> Like.binary("+")
        BuiltinOperatorId.PlusIntInt, BuiltinOperatorId.PlusIntInt64 -> Like.binary("+")
        BuiltinOperatorId.TimesIntInt, BuiltinOperatorId.TimesIntInt64 -> Like.binary("*")
        BuiltinOperatorId.TimesFltFlt -> Like.binary("*")
        BuiltinOperatorId.PowFltFlt -> Like.core("pow")
        BuiltinOperatorId.LtFltFlt -> Like.core("lt")
        BuiltinOperatorId.LtIntInt -> Like.binary("<")
        BuiltinOperatorId.LtStrStr -> Like.core("lt")
        BuiltinOperatorId.LtGeneric -> TODO()
        BuiltinOperatorId.LeFltFlt -> Like.core("le")
        BuiltinOperatorId.LeIntInt -> Like.binary("<=")
        BuiltinOperatorId.LeStrStr -> Like.core("le")
        BuiltinOperatorId.LeGeneric -> TODO()
        BuiltinOperatorId.GtFltFlt -> Like.core("gt")
        BuiltinOperatorId.GtIntInt -> Like.binary(">")
        BuiltinOperatorId.GtStrStr -> Like.core("gt")
        BuiltinOperatorId.GtGeneric -> TODO()
        BuiltinOperatorId.GeFltFlt -> Like.core("ge")
        BuiltinOperatorId.GeIntInt -> Like.binary(">=")
        BuiltinOperatorId.GeStrStr -> Like.core("ge")
        BuiltinOperatorId.GeGeneric -> TODO()
        BuiltinOperatorId.EqFltFlt -> Like.core("eq")
        BuiltinOperatorId.EqIntInt -> Like.binary("==")
        BuiltinOperatorId.EqStrStr -> Like.core("eq")
        BuiltinOperatorId.EqGeneric -> TODO()
        BuiltinOperatorId.NeFltFlt -> Like.core("ne")
        BuiltinOperatorId.NeIntInt -> Like.binary("!=")
        BuiltinOperatorId.NeStrStr -> Like.core("ne")
        BuiltinOperatorId.NeGeneric -> TODO()
        BuiltinOperatorId.CmpFltFlt -> Like.core("cmp")
        BuiltinOperatorId.CmpIntInt -> Like.core("cop")
        BuiltinOperatorId.CmpStrStr -> Like.core("cmp")
        BuiltinOperatorId.CmpGeneric -> TODO()
        BuiltinOperatorId.Bubble -> Like.core("bubble")
        BuiltinOperatorId.Print -> Like.core("print")
        BuiltinOperatorId.StrCat -> Like.core("cat")
        BuiltinOperatorId.Listify -> Like.core("List::make")
        BuiltinOperatorId.AdaptGeneratorFn -> TODO()
        BuiltinOperatorId.SafeAdaptGeneratorFn -> TODO()
        BuiltinOperatorId.Async -> TODO()
        null -> when (builtin.name) {
            pureVirtualBuiltinName.builtinKey -> Like.core("pure_virtual")
            else -> TODO("builtin: ${builtin.javaClass} ${builtin.name}")
        }

        BuiltinOperatorId.Panic -> TODO()
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode? = when (connectedKey) {
        "::getConsole" -> Like.core("get_console")
        "ignore" -> Like.ignoring(theLastArg)
        "Boolean::toString" -> Like.core("toString")
        "Date::constructor" -> Like.core("Date::make")
        "Date::fromIsoString" -> Like.core("Date::make")
        "Date::getDay" -> Like.core("get_day")
        "Date::getMonth" -> Like.core("get_month")
        "Date::getYear" -> Like.core("get_year")
        "Date::getDayOfWeek" -> Like.core("get_day_of_week")
        "Date::toString" -> Like.core("toString")
        "Date::today" -> Like.core("to_day")
        "Date::yearsBetween" -> Like.core("years_between")
        "Float64::e" -> Like.core("Float64::e")
        "Float64::pi" -> Like.core("Float64::pi")
        "Float64::abs" -> Like.core("abs")
        "Float64::acos" -> Like.core("acos")
        "Float64::asin" -> Like.core("asin")
        "Float64::atan" -> Like.core("atan")
        "Float64::atan2" -> Like.core("atan2")
        "Float64::ceil" -> Like.core("ceil")
        "Float64::cos" -> Like.core("cos")
        "Float64::cosh" -> Like.core("cosh")
        "Float64::exp" -> Like.core("exp")
        "Float64::expm1" -> Like.core("expm1")
        "Float64::floor" -> Like.core("floor")
        "Float64::log" -> Like.core("log")
        "Float64::log10" -> Like.core("log10")
        "Float64::log1p" -> Like.core("log1p")
        "Float64::max" -> Like.core("max")
        "Float64::min" -> Like.core("min")
        "Float64::near" -> Like.core("near")
        "Float64::round" -> Like.core("round")
        "Float64::sign" -> Like.core("sign")
        "Float64::sin" -> Like.core("sin")
        "Float64::sinh" -> Like.core("sinh")
        "Float64::sqrt" -> Like.core("sqrt")
        "Float64::tan" -> Like.core("tan")
        "Float64::tanh" -> Like.core("tanh")
        "Float64::toInt32" -> Like.core("toInt")
        "Float64::toInt32Unsafe" -> Like.core("toIntUnsafe")
        "Float64::toString" -> Like.core("toString")
        "Int32::max" -> Like.core("max")
        "Int32::min" -> Like.core("min")
        "Int32::toFloat64" -> Like.core("toFloat64")
        "Int32::toFloat64Unsafe" -> Like.core("toFloat64Unsafe")
        "Int32::toString" -> Like.core("toString")
        "PromiseBuilder::breakPromise" -> Like.core("breakpromise")
        "PromiseBuilder::complete" -> Like.core("complete")
        "PromiseBuilder::constructor" -> Like.core("PromiseBuilder::make")
        "PromiseBuilder::getPromise" -> Like.core("getpromise")
        "String::fromCodePoint" -> Like.core("make")
        "String::fromCodePoints" -> Like.core("make")
        "String::isEmpty" -> Like.core("isempty")
        "String::begin" -> Like.core("begin")
        "String::end" -> Like.core("end")
        "String::get" -> Like.core("get")
        "String::countBetween" -> Like.core("countbetween")
        "String::forEach" -> Like.core("foreach")
        "String::hasAtLeast" -> Like.core("hasAtLeast")
        "String::hasIndex" -> Like.core("hasIndex")
        "String::next" -> Like.core("next")
        "String::prev" -> Like.core("prev")
        "String::slice" -> Like.core("slice")
        "String::split" -> Like.core("split")
        "String::toFloat64" -> Like.core("toFloat64")
        "String::toInt32" -> Like.core("toInt")
        "String::toString" -> Like.core("toString")
        "StringBuilder::constructor" -> Like.core("StringBuilder::make")
        "StringBuilder::append" -> Like.core("append")
        "StringBuilder::appendBetween" -> Like.core("appendBetween")
        "StringBuilder::appendCodePoint" -> Like.core("appendCodepoint")
        "StringBuilder::toString" -> Like.core("toString")
        "StringIndex::none" -> Like.core("none")
        "StringIndexOption::compareTo" -> Like.core("cmp")
        "Console::log" -> Like.core("log")
        "List::isEmpty" -> Like.core("isempty")
        "List::forEach" -> Like.core("foreach")
        "List::get" -> Like.core("get")
        "List::length" -> Like.core("length")
        "List::toList" -> Like.core("toList")
        "List::toListBuilder" -> Like.core("toListBuilder")
        "Listed::filter" -> Like.core("filter")
        "Listed::isEmpty" -> Like.core("is_empty")
        "Listed::join" -> Like.core("join")
        "Listed::map" -> Like.core("map")
        "Listed::mapDropping" -> Like.core("mapDropping")
        "Listed::slice" -> Like.core("slice")
        "Listed::get" -> Like.core("get")
        "Listed::getOr" -> Like.core("getor")
        "Listed::length" -> Like.core("length")
        "Listed::reduce" -> Like.core("reduce")
        "Listed::reduceFrom" -> Like.core("reduce_from")
        "Listed::sorted" -> Like.core("sorted")
        "Listed::toList" -> Like.core("toList")
        "Listed::toListBuilder" -> Like.core("toListBuilder")
        "ListBuilder::constructor" -> Like.core("ListBuilder::make")
        "ListBuilder::add" -> Like.core("add")
        "ListBuilder::addAll" -> Like.core("addall")
        "ListBuilder::removeLast" -> Like.core("removeLast")
        "ListBuilder::reverse" -> Like.core("reverse")
        "ListBuilder::splice" -> Like.core("splice")
        "ListBuilder::toList" -> Like.core("toList")
        "ListBuilder::toListBuilder" -> Like.core("toListBuilder")
        "ListBuilder::set" -> Like.core("set")
        "ListBuilder::sort" -> Like.core("sort")
        "ListBuilder::length" -> Like.core("length")
        "Map::constructor" -> Like.core("Map::make")
        "MapBuilder::constructor" -> Like.core("MapBuilder::make")
        "MapBuilder::remove" -> Like.core("remove")
        "MapBuilder::set" -> Like.core("set")
        "Pair::constructor" -> Like.core("Pair::make")
        "Mapped::length" -> Like.core("length")
        "Mapped::get" -> Like.core("get")
        "Mapped::getOr" -> Like.core("getor")
        "Mapped::has" -> Like.core("has")
        "Mapped::keys" -> Like.core("keys")
        "Mapped::values" -> Like.core("values")
        "Mapped::toMap" -> Like.core("toMap")
        "Mapped::toMapBuilder" -> Like.core("toMapBuilder")
        "Mapped::toList" -> Like.core("toList")
        "Mapped::toListBuilder" -> Like.core("toListBuilder")
        "Mapped::toListWith" -> Like.core("toListWith")
        "Mapped::toListBuilderWith" -> Like.core("toListBuilderWith")
        "Mapped::forEach" -> Like.core("forEach")
        "DenseBitVector::constructor" -> Like.core("DenseBitVector::make")
        "DenseBitVector::get" -> Like.core("get")
        "DenseBitVector::set" -> Like.core("set")
        "Deque::constructor" -> Like.core("Deque::make")
        "Deque::add" -> Like.core("add")
        "Deque::isEmpty" -> Like.core("isEmpty")
        "Deque::removeFirst" -> Like.core("removeFirst")
        "Regex::compiledFind" -> Like.core("compiledFind")
        "Regex::compiledFound" -> Like.core("compiledFound")
        "Regex::compiledReplace" -> Like.core("compiledReplace")
        "Regex::compiledSplit" -> Like.core("compiledSplit")
        "Regex::compileFormatted" -> Like.core("compileFormatted")
        "RegexFormatter::pushCaptureName" -> Like.core("pushCaptureName")
        "RegexFormatter::pushCodeTo" -> Like.core("pushCodeTo")
        "Test::bail" -> Like.core("testBail")
        "Generator::next", "SafeGenerator::next" -> Like.core("next")

        "Regex::format" -> Like.core("format")
        else -> TODO("connected: $connectedKey")
    }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? {
        return null
    }

    override fun translateRuntimeTypeOperation(
        pos: Position,
        rto: RuntimeTypeOperation,
        sourceType: TmpL.NominalType,
        targetType: TmpL.NominalType,
    ): SupportCode = handle {
        val src = cpp.name(sourceType.typeName.sourceDefinition.name)
        val dest = cpp.name(targetType.typeName.sourceDefinition.name)
        val func = when (rto) {
            RuntimeTypeOperation.As -> "${src}::as_${dest}"
            RuntimeTypeOperation.AssertAs -> "${src}::assert_as_${dest}"
            RuntimeTypeOperation.Is -> "${src}::is_${dest}"
        }
        cpp.callExpr(
            cpp.name(func),
            listOf(values[0]),
        )
    }
}

internal data class InlineContext(
    val translator: CppTranslator,
    val cpp: CppBuilder,
    val values: List<Cpp.Expr>,
    val types: List<Type2>,
    val retType: Type2,
)

internal object Like {
    private fun fromParts(parts: Iterable<String>) = handle {
        cpp.callExpr(
            cpp.name(
                parts.flatMap {
                    it.split("::")
                },
            ),
            values,
        )
    }

    fun property(name: String): CppInlineSupportCode = handle {
        cpp.op(".", values[0], cpp.name(name))
    }

    fun new(vararg parts: String): CppInlineSupportCode = handle {
        cpp.callExpr(
            fromParts(parts.toList()).generate(this),
            values,
        )
    }

    fun core(vararg parts: String): CppInlineSupportCode = handle {
        fromParts(listOf(TEMPER_CORE_NAMESPACE) + parts.toList()).generate(this)
    }

    fun name(vararg parts: String): CppInlineSupportCode = handle {
        fromParts(parts.toList()).generate(this)
    }

    fun unary(name: String): CppInlineSupportCode = handle {
        require(values.size == 1)
        cpp.op(name, values)
    }

    fun binary(name: String): CppInlineSupportCode = handle {
        require(values.size == 2)
        cpp.op(name, values)
    }

    fun ignoring(other: CppInlineSupportCode): CppInlineSupportCode {
        return handle {
            cpp.cast(
                cpp.type("void"),
                other.generate(this),
            )
        }
    }
}

internal val theLastArg = handle {
    val ignore = if (values.size == 1) {
        values[0]
    } else {
        cpp.op(",", values)
    }
    cpp.cast(cpp.type("void"), ignore)
}

internal fun handle(generate: InlineContext.() -> Cpp.Expr) = CppInlineSupportCode(generate)

internal class CppInlineSupportCode(
    val generate: InlineContext.() -> Cpp.Expr,
) : InlineSupportCode<Cpp.Tree, CppTranslator> {
    override val needsThisEquivalent: Boolean = false

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken.makeSlashStarComment("/* $generate */"))
    }

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
    ): Cpp.Tree = translator.cpp.pos(pos) {
        InlineContext(
            translator,
            translator.cpp,
            arguments.map { it.expr as Cpp.Expr },
            arguments.map { it.type },
            returnType,
        ).generate()
    }
}
