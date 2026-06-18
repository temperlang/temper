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
        BuiltinOperatorId.BitwiseAnd32, BuiltinOperatorId.BitwiseAnd64 -> Like.binary("&")
        BuiltinOperatorId.BitwiseOr32, BuiltinOperatorId.BitwiseOr64 -> Like.binary("|")
        BuiltinOperatorId.BitwiseXor32, BuiltinOperatorId.BitwiseXor64 -> Like.binary("^")
        BuiltinOperatorId.BitwiseNegation32, BuiltinOperatorId.BitwiseNegation64 -> Like.unary("~")
        BuiltinOperatorId.BitwiseShl32, BuiltinOperatorId.BitwiseShl64 -> Like.binary("<<")
        BuiltinOperatorId.BitwiseShr32, BuiltinOperatorId.BitwiseShr64 -> Like.binary(">>")
        BuiltinOperatorId.BitwiseShrUnsigned32 -> Like.core("ushr32")
        BuiltinOperatorId.BitwiseShrUnsigned64 -> Like.core("ushr64")
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
        "core.getConsole()" -> Like.core("get_console")
        "core.ignore()" -> Like.ignoring(theLastArg)
        "core.type Boolean.toString()" -> Like.core("toString")
        "std/temporal.type Date.constructor()" -> Like.core("Date::make")
        "std/temporal.type Date.fromIsoString()" -> Like.core("Date::make")
        "std/temporal.type Date.day" -> Like.core("get_day")
        "std/temporal.type Date.month" -> Like.core("get_month")
        "std/temporal.type Date.year" -> Like.core("get_year")
        "std/temporal.type Date.get dayOfWeek()" -> Like.core("get_day_of_week")
        "std/temporal.type Date.toString()" -> Like.core("toString")
        "std/temporal.type Date.today()" -> Like.core("to_day")
        "std/temporal.type Date.yearsBetween()" -> Like.core("years_between")
        "core.type Float64.e" -> Like.core("Float64::e")
        "core.type Float64.pi" -> Like.core("Float64::pi")
        "core.type Float64.abs()" -> Like.core("abs")
        "core.type Float64.acos()" -> Like.core("acos")
        "core.type Float64.asin()" -> Like.core("asin")
        "core.type Float64.atan()" -> Like.core("atan")
        "core.type Float64.atan2()" -> Like.core("atan2")
        "core.type Float64.ceil()" -> Like.core("ceil")
        "core.type Float64.cos()" -> Like.core("cos")
        "core.type Float64.cosh()" -> Like.core("cosh")
        "core.type Float64.exp()" -> Like.core("exp")
        "core.type Float64.expm1()" -> Like.core("expm1")
        "core.type Float64.floor()" -> Like.core("floor")
        "core.type Float64.log()" -> Like.core("log")
        "core.type Float64.log10()" -> Like.core("log10")
        "core.type Float64.log1p()" -> Like.core("log1p")
        "core.type Float64.max()" -> Like.core("max")
        "core.type Float64.min()" -> Like.core("min")
        "core.type Float64.near()" -> Like.core("near")
        "core.type Float64.round()" -> Like.core("round")
        "core.type Float64.sign()" -> Like.core("sign")
        "core.type Float64.sin()" -> Like.core("sin")
        "core.type Float64.sinh()" -> Like.core("sinh")
        "core.type Float64.sqrt()" -> Like.core("sqrt")
        "core.type Float64.tan()" -> Like.core("tan")
        "core.type Float64.tanh()" -> Like.core("tanh")
        "core.type Float64.toInt32()" -> Like.core("toInt")
        "core.type Float64.toInt32Unsafe()" -> Like.core("toIntUnsafe")
        "core.type Float64.toString()" -> Like.core("toString")
        "core.type Int32.max()" -> Like.core("max")
        "core.type Int32.min()" -> Like.core("min")
        "core.type Int32.toFloat64()" -> Like.core("toFloat64")
        "core.type Int32.toFloat64Unsafe()" -> Like.core("toFloat64Unsafe")
        "core.type Int32.toString()" -> Like.core("toString")
        "core.type PromiseBuilder.breakPromise()" -> Like.core("breakpromise")
        "core.type PromiseBuilder.complete()" -> Like.core("complete")
        "core.type PromiseBuilder.constructor()" -> Like.core("PromiseBuilder::make")
        "core.type PromiseBuilder.get promise()" -> Like.core("getpromise")
        "core.type String.fromCodePoint()" -> Like.core("make")
        "core.type String.fromCodePoints()" -> Like.core("make")
        "core.type String.get isEmpty()" -> Like.core("isempty")
        "core.type String.begin" -> Like.core("begin")
        "core.type String.get end()" -> Like.core("end")
        "core.type String.get()" -> Like.core("get")
        "core.type String.countBetween()" -> Like.core("countbetween")
        "core.type String.forEach()" -> Like.core("foreach")
        "core.type String.hasAtLeast()" -> Like.core("hasAtLeast")
        "core.type String.hasIndex()" -> Like.core("hasIndex")
        "core.type String.next()" -> Like.core("next")
        "core.type String.prev()" -> Like.core("prev")
        "core.type String.slice()" -> Like.core("slice")
        "core.type String.split()" -> Like.core("split")
        "core.type String.toFloat64()" -> Like.core("toFloat64")
        "core.type String.toInt32()" -> Like.core("toInt")
        "core.type String.toString()" -> Like.core("toString")
        "core.type StringBuilder.constructor()" -> Like.core("StringBuilder::make")
        "core.type StringBuilder.append()" -> Like.core("append")
        "core.type StringBuilder.appendBetween()" -> Like.core("appendBetween")
        "core.type StringBuilder.appendCodePoint()" -> Like.core("appendCodepoint")
        "core.type StringBuilder.toString()" -> Like.core("toString")
        "core.type StringIndex.none" -> Like.core("none")
        "core.type StringIndexOption.compareTo()" -> Like.core("cmp")
        "core.type Console.log()" -> Like.core("log")
        "core.type List.isEmpty()" -> Like.core("isempty")
        "core.type List.forEach()" -> Like.core("foreach")
        "core.type List.get()" -> Like.core("get")
        "core.type List.get length()" -> Like.core("length")
        "core.type List.toList()" -> Like.core("toList")
        "core.type List.toListBuilder()" -> Like.core("toListBuilder")
        "core.type Listed.filter()" -> Like.core("filter")
        "core.type Listed.isEmpty()" -> Like.core("is_empty")
        "core.type Listed.join()" -> Like.core("join")
        "core.type Listed.map()" -> Like.core("map")
        "core.type Listed.slice()" -> Like.core("slice")
        "core.type Listed.get()" -> Like.core("get")
        "core.type Listed.getOr()" -> Like.core("getor")
        "core.type Listed.length()" -> Like.core("length")
        "core.type Listed.reduce()" -> Like.core("reduce")
        "core.type Listed.reduceFrom()" -> Like.core("reduce_from")
        "core.type Listed.sorted()" -> Like.core("sorted")
        "core.type Listed.toList()" -> Like.core("toList")
        "core.type Listed.toListBuilder()" -> Like.core("toListBuilder")
        "core.type ListBuilder.constructor()" -> Like.core("ListBuilder::make")
        "core.type ListBuilder.add()" -> Like.core("add")
        "core.type ListBuilder.addAll()" -> Like.core("addall")
        "core.type ListBuilder.removeLast()" -> Like.core("removeLast")
        "core.type ListBuilder.reverse()" -> Like.core("reverse")
        "core.type ListBuilder.splice()" -> Like.core("splice")
        "core.type ListBuilder.toList()" -> Like.core("toList")
        "core.type ListBuilder.toListBuilder()" -> Like.core("toListBuilder")
        "core.type ListBuilder.set()" -> Like.core("set")
        "core.type ListBuilder.sort()" -> Like.core("sort")
        "core.type ListBuilder.length()" -> Like.core("length")
        "core.type Map.constructor()" -> Like.core("Map::make")
        "core.type MapBuilder.constructor()" -> Like.core("MapBuilder::make")
        "core.type MapBuilder.remove()" -> Like.core("remove")
        "core.type MapBuilder.set()" -> Like.core("set")
        "core.type Pair.constructor()" -> Like.core("Pair::make")
        "core.type Mapped.length()" -> Like.core("length")
        "core.type Mapped.get()" -> Like.core("get")
        "core.type Mapped.getOr()" -> Like.core("getor")
        "core.type Mapped.has()" -> Like.core("has")
        "core.type Mapped.keys()" -> Like.core("keys")
        "core.type Mapped.values()" -> Like.core("values")
        "core.type Mapped.toMap()" -> Like.core("toMap")
        "core.type Mapped.toMapBuilder()" -> Like.core("toMapBuilder")
        "core.type Mapped.toList()" -> Like.core("toList")
        "core.type Mapped.toListBuilder()" -> Like.core("toListBuilder")
        "core.type Mapped.toListWith()" -> Like.core("toListWith")
        "core.type Mapped.toListBuilderWith()" -> Like.core("toListBuilderWith")
        "core.type Mapped.forEach()" -> Like.core("forEach")
        "core.type DenseBitVector.constructor()" -> Like.core("DenseBitVector::make")
        "core.type DenseBitVector.get()" -> Like.core("get")
        "core.type DenseBitVector.set()" -> Like.core("set")
        "core.type Deque.constructor()" -> Like.core("Deque::make")
        "core.type Deque.add()" -> Like.core("add")
        "core.type Deque.get isEmpty()" -> Like.core("isEmpty")
        "core.type Deque.removeFirst()" -> Like.core("removeFirst")
        "std/regex.type Regex.compiledFind()" -> Like.core("compiledFind")
        "std/regex.type Regex.compiledFound()" -> Like.core("compiledFound")
        "std/regex.type Regex.compiledReplace()" -> Like.core("compiledReplace")
        "std/regex.type Regex.compiledSplit()" -> Like.core("compiledSplit")
        "std/regex.type Regex.compileFormatted()" -> Like.core("compileFormatted")
        "std/regex.type RegexFormatter.pushCaptureName()" -> Like.core("pushCaptureName")
        "std/regex.type RegexFormatter.pushCodeTo()" -> Like.core("pushCodeTo")
        "std/testing.type Test.bail()" -> Like.core("testBail")
        "core.type Generator.next()", "core.type SafeGenerator.next()" -> Like.core("next")

        "std/regex.type Regex.format()" -> Like.core("format")
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
