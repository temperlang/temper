@file:Suppress("SpellCheckingInspection", "MagicNumber")

package lang.temper.be.py

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.GetStaticSupport
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.InlineTmpLSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SeparatelyCompiledSupportCode
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.TypedArg
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.common.subListToEnd
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.OutName
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.consoleBuiltinName
import lang.temper.value.getStaticBuiltinName
import lang.temper.value.internalGetStaticBuiltinName
import lang.temper.value.listBuiltinName
import lang.temper.value.pureVirtualBuiltinName

/** Fulfills requests for support codes made by TmpL. */
internal object PySupportNetwork : SupportNetwork {
    override val backendDescription = "Python backend"
    override val coroutineStrategy = CoroutineStrategy.TranslateToGenerator
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionType

    override fun getSupportCode(
        pos: Position,
        builtin: NamedBuiltinFun,
        genre: Genre,
    ): SupportCode {
        if (genre == Genre.Documentation) {
            if (builtin.builtinOperatorId == BuiltinOperatorId.Print) {
                return docPrintInliner
            }
        }
        return byOpId(builtin.builtinOperatorId)
            ?: builtinNames[builtin.name]
            ?: notSupported(builtin.name, builtin)
    }

    private fun byOpId(opId: BuiltinOperatorId?): PySupportCode? =
        when (opId) {
            BuiltinOperatorId.Print -> PrintFunction
            BuiltinOperatorId.IsNull -> IsNull
            BuiltinOperatorId.NotNull -> null
            BuiltinOperatorId.CmpIntInt -> IntCmp
            BuiltinOperatorId.CmpFltFlt -> DubCmp
            BuiltinOperatorId.CmpStrStr -> StrCmp
            BuiltinOperatorId.CmpGeneric -> GenericCmp
            BuiltinOperatorId.GtIntInt -> IntGt
            BuiltinOperatorId.GtFltFlt -> DubGt
            BuiltinOperatorId.GtStrStr -> StrGt
            BuiltinOperatorId.GtGeneric -> GenericGt
            BuiltinOperatorId.LtIntInt -> IntLt
            BuiltinOperatorId.LtFltFlt -> DubLt
            BuiltinOperatorId.LtStrStr -> StrLt
            BuiltinOperatorId.LtGeneric -> GenericLt
            BuiltinOperatorId.GeIntInt -> IntGtEq
            BuiltinOperatorId.GeFltFlt -> DubGtEq
            BuiltinOperatorId.GeStrStr -> StrGtEq
            BuiltinOperatorId.GeGeneric -> GenericGtEq
            BuiltinOperatorId.LeIntInt -> IntLtEq
            BuiltinOperatorId.LeFltFlt -> DubLtEq
            BuiltinOperatorId.LeStrStr -> StrLtEq
            BuiltinOperatorId.LeGeneric -> GenericLtEq
            BuiltinOperatorId.EqIntInt -> IntEq
            BuiltinOperatorId.EqFltFlt -> DubEq
            BuiltinOperatorId.EqStrStr -> StrEq
            BuiltinOperatorId.EqGeneric -> GenericEq
            BuiltinOperatorId.NeIntInt -> IntNotEq
            BuiltinOperatorId.NeFltFlt -> DubNotEq
            BuiltinOperatorId.NeStrStr -> StrNotEq
            BuiltinOperatorId.NeGeneric -> GenericNotEq
            BuiltinOperatorId.DivFltFlt -> ArithDubDiv
            BuiltinOperatorId.DivIntInt -> ArithIntDiv
            BuiltinOperatorId.DivIntInt64 -> ArithInt64Div
            BuiltinOperatorId.DivIntIntSafe -> ArithIntDiv
            BuiltinOperatorId.DivIntInt64Safe -> ArithInt64Div
            BuiltinOperatorId.ModFltFlt -> ArithDubMod
            BuiltinOperatorId.ModIntInt, BuiltinOperatorId.ModIntInt64 -> ArithIntMod
            BuiltinOperatorId.ModIntIntSafe, BuiltinOperatorId.ModIntInt64Safe -> ArithIntMod
            BuiltinOperatorId.MinusFlt -> ArithDubNegate
            BuiltinOperatorId.MinusFltFlt -> ArithDubSub
            BuiltinOperatorId.MinusInt -> ArithIntNegate
            BuiltinOperatorId.MinusInt64 -> ArithInt64Negate
            BuiltinOperatorId.MinusIntInt -> ArithIntSub
            BuiltinOperatorId.MinusIntInt64 -> ArithInt64Sub
            BuiltinOperatorId.PlusFltFlt -> ArithDubAdd
            BuiltinOperatorId.PlusIntInt -> ArithIntAdd
            BuiltinOperatorId.PlusIntInt64 -> ArithInt64Add
            BuiltinOperatorId.PowFltFlt -> ArithDubPow
            BuiltinOperatorId.TimesFltFlt -> ArithDubTimes
            BuiltinOperatorId.TimesIntInt -> ArithIntTimes
            BuiltinOperatorId.TimesIntInt64 -> ArithInt64Times
            BuiltinOperatorId.StrCat -> StrCatFn
            BuiltinOperatorId.BooleanNegation -> BoolNot
            BuiltinOperatorId.Bubble, BuiltinOperatorId.Panic -> BubbleFunc
            BuiltinOperatorId.Listify -> Listify
            BuiltinOperatorId.BitwiseAnd -> ArithBitAnd
            BuiltinOperatorId.BitwiseOr -> ArithBitOr
            // Handled specially because it translates to two statements
            // and needs to be translated in the context of any containing
            // assignment.
            BuiltinOperatorId.Async -> Async
            // should not be used with CoroutineStrategy.TranslateToGenerator
            BuiltinOperatorId.AdaptGeneratorFn,
            BuiltinOperatorId.SafeAdaptGeneratorFn,
            -> null
            null -> null
        }

    private val builtinNames = listOf(
        pureVirtualBuiltinName to PureVirtualBody,
        getStaticBuiltinName to GetStaticSupport,
        internalGetStaticBuiltinName to GetStaticSupport,
        listBuiltinName to Listify,
    ).associate { (k, v) -> k.builtinKey to v }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode? = pyConnections[connectedKey]
        ?: when (connectedKey) {
            "Console::log" -> when (genre) {
                Genre.Documentation -> DocConsoleLogInliner
                else -> null
            }
            else -> null
        }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? = when (connectedKey) {
        "Date" -> DateType to null
        "Promise", "PromiseBuilder" -> ConcurrentFuturesFuture to null
        // Method 4 of https://waymoot.org/home/python_string/ is the most
        // performant that is generally useful, so we connect StringBuilder to list[str].
        "StringBuilder" -> ListType to listOf<Type2>(WellKnownTypes.stringType2)
        else -> null
    }?.let { (name: PyConnectedType, typeArgs: List<Type2>?) ->
        name to (typeArgs ?: (temperType as? DefinedNonNullType)?.bindings ?: emptyList())
    }

    override fun translateRuntimeTypeOperation(
        pos: Position,
        rto: RuntimeTypeOperation,
        sourceType: TmpL.NominalType,
        targetType: TmpL.NominalType,
    ): SupportCode? {
        if (rto.asLike) {
            when (targetType.typeName.sourceDefinition) {
                WellKnownTypes.noStringIndexTypeDefinition -> return RequireNoStringIndex
                WellKnownTypes.stringIndexTypeDefinition -> return RequireStringIndex
                else -> {}
            }
        }
        return super.translateRuntimeTypeOperation(pos, rto, sourceType, targetType)
    }

    override val bubbleStrategy = BubbleBranchStrategy.CatchBubble

    // Technically, None is void in Python, but too many things are statements only, such as assert.
    override fun representationOfVoid(genre: Genre): RepresentationOfVoid =
        RepresentationOfVoid.DoNotReifyVoid
}

sealed class PySupportCode(
    final override val baseName: ParsedName,
    override val builtinOperatorId: BuiltinOperatorId?,
) : NamedSupportCode {
    final override fun equals(other: Any?): Boolean =
        this === other || (other is PySupportCode && baseName == other.baseName)
    final override fun hashCode(): Int = baseName.hashCode()
    final override fun toString(): String = "PySupportCode($baseName)"

    override fun renderTo(tokenSink: TokenSink) =
        tokenSink.name(baseName, inOperatorPosition = false)
}

typealias ExprConsumingTreeFactory = (pos: Position, args: List<Py.Expr>) -> Py.Tree
typealias ExprConsumingTranslatorTreeFactory = (pos: Position, args: List<Py.Expr>, translator: PyTranslator) -> Py.Tree

class PyInlineSupportCode(
    baseName: String,
    private val arity: IntRange?,
    builtinOperatorId: BuiltinOperatorId? = null,
    needsSelf: Boolean = false,
    private val translatorFactory: ExprConsumingTranslatorTreeFactory? = null,
    private val factory: ExprConsumingTreeFactory? = null,
) : PySupportCode(
    ParsedName(baseName),
    builtinOperatorId = builtinOperatorId,
),
    InlineSupportCode<Py.Tree, PyTranslator> {

    constructor(
        baseName: String,
        arity: Int,
        builtinOperatorId: BuiltinOperatorId? = null,
        needsSelf: Boolean = false,
        factory: ExprConsumingTreeFactory,
    ) : this(
        baseName = baseName,
        arity = if (arity < 0) { null } else { arity..arity },
        builtinOperatorId = builtinOperatorId,
        needsSelf = needsSelf,
        factory = factory,
    )

    constructor(
        baseName: String,
        arity: IntRange?,
        builtinOperatorId: BuiltinOperatorId? = null,
        needsSelf: Boolean = false,
        translatorFactory: ExprConsumingTranslatorTreeFactory,
    ) : this(
        baseName = baseName,
        arity = arity,
        builtinOperatorId = builtinOperatorId,
        needsSelf = needsSelf,
        translatorFactory = translatorFactory,
        factory = null,
    )

    override val needsThisEquivalent: Boolean = needsSelf

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Py.Tree>>,
        returnType: Type2,
        translator: PyTranslator,
    ): Py.Tree = inlineExprsToTree(
        pos,
        arguments.map { it.expr as Py.Expr },
        translator,
    )

    fun inlineExprsToTree(
        pos: Position,
        arguments: List<Py.Expr>,
        translator: PyTranslator,
    ): Py.Tree =
        if (arity != null && arguments.size !in arity) {
            garbageExpr(
                pos,
                "inlineToTree",
                "$baseName expects $arity argument(s) but got $arguments",
            )
        } else {
            factory?.let { it(pos, arguments) } ?: translatorFactory!!(pos, arguments, translator)
        }
}
private fun inlineAttribute(
    connectedKey: String,
    attributeName: PyIdentifierName,
) = PyInlineSupportCode(connectedKey, arity = 1, needsSelf = true) { pos, args ->
    Py.Attribute(pos, args[0], Py.Identifier(pos.rightEdge, attributeName))
}

open class PySeparateCode internal constructor(
    baseName: String,
    val module: PyDottedIdentifier,
    builtinOperatorId: BuiltinOperatorId? = null,
) : PySupportCode(ParsedName(baseName), builtinOperatorId = builtinOperatorId),
    SeparatelyCompiledSupportCode {
    override val source: DashedIdentifier get() = DashedIdentifier.temperCoreLibraryIdentifier
    override val stableKey: ParsedName = this.baseName
}

class PyConnectedType(
    baseName: String,
    module: PyDottedIdentifier,
) : PySeparateCode(baseName, module), TargetLanguageTypeName {
    override fun renderTo(tokenSink: TokenSink) {
        module.renderTo(tokenSink)
        tokenSink.emit(OutToks.dot)
        baseName.renderTo(tokenSink)
    }
}

val RUNTIME = PyDottedIdentifier("temper_core")
val RUNTIME_REGEX = RUNTIME.dot("regex")
val SYS_ABC = PyDottedIdentifier("abc")
val SYS_TYPES = PyDottedIdentifier("types")
val SYS_BUILTINS = PyDottedIdentifier("builtins")
val SYS_COLLECTIONS = PyDottedIdentifier("collections")
val TYPING = PyDottedIdentifier("typing")
val TYPING_EXTENSIONS = PyDottedIdentifier("typing_extensions")
val MYPY_EXTENSIONS = PyDottedIdentifier("mypy_extensions")
val CONCURRENT_FUTURES = PyDottedIdentifier.dotted(
    OutName("concurrent", null),
    OutName("futures", null),
)
val DATE_TIME = PyDottedIdentifier("datetime")
val MATH = PyDottedIdentifier("math")
val UNITTEST = PyDottedIdentifier("unittest")
val URLLIB_RESPONSE = PyDottedIdentifier("urllib.response")

val AbstractBaseClassMeta = PySeparateCode("ABCMeta", SYS_ABC)
val AbstractMethod = PySeparateCode("abstractmethod", SYS_ABC)
val Override = PySeparateCode("override", TYPING_EXTENSIONS)

val Identity = PyInlineSupportCode("identity", 1) { _, args -> args[0] }
val PureVirtualBody = PyInlineSupportCode(
    "pure_virtual",
    -1,
    needsSelf = false,
) { pos, _ ->
    PyConstant.Ellipsis.at(pos)
}
val EmptyInliner = PyInlineSupportCode("empty", 0) { pos, _ ->
    Py.Tuple(pos, emptyList())
}
val Ignore = PyInlineSupportCode("ignore", 1) { pos, args ->
    when (val arg = args[0]) {
        // If we could be sure we aren't used as an expression, we could say `pass` instead.
        is Py.Name -> PyConstant.None.at(pos)
        // We always expect names today, but support others just in case.
        // If we could be sure we aren't used as an expression, we could say `arg` all by itself.
        else -> pyCommaOp(listOf(arg, PyConstant.None.at(pos)), pos)
    }
}
val Exception = PySeparateCode("Exception", SYS_BUILTINS)
val AttributeError = PySeparateCode("AttributeError", SYS_BUILTINS)
val ImportError = PySeparateCode("ImportError", SYS_BUILTINS)
val NotImplementedError = PySeparateCode("NotImplementedError", SYS_BUILTINS)
val IsInstance = PySeparateCode("isinstance", SYS_BUILTINS)
val IntType = PySeparateCode("int", SYS_BUILTINS)
val BoolType = PySeparateCode("bool", SYS_BUILTINS)
val StrType = PySeparateCode("str", SYS_BUILTINS)
val FloatType = PySeparateCode("float", SYS_BUILTINS)
val ListType = PyConnectedType("list", SYS_BUILTINS)
val TupleType = PySeparateCode("tuple", SYS_BUILTINS)
val TypeType = PySeparateCode("type", SYS_BUILTINS)
val DictType = PySeparateCode("dict", SYS_BUILTINS)
val MappingProxyType = PySeparateCode("MappingProxyType", SYS_TYPES)
val DateConstructor = PySeparateCode("date", DATE_TIME)
val DateType = PyConnectedType("date", DATE_TIME)
val DateGetDay = inlineAttribute("Date::getDay", PyIdentifierName("day"))
val DateGetMonth = inlineAttribute("Date::getMonth", PyIdentifierName("month"))
val DateGetYear = inlineAttribute("Date::getYear", PyIdentifierName("year"))
val DateGetDayOfWeek = PyInlineSupportCode(
    "Date::getDayOfWeek",
    arity = 1,
    needsSelf = true,
) { pos, arg ->
    arg[0].method("isoweekday", pos = pos)
}
val IsInstanceInt = PySeparateCode("isinstance_int", RUNTIME)
val CallableTest = PySeparateCode("callable", SYS_BUILTINS)
val CastByType = PySeparateCode("cast_by_type", RUNTIME)
val CastByTest = PySeparateCode("cast_by_test", RUNTIME)
val CastNone = PySeparateCode("cast_none", RUNTIME)
val LoggingConsoleType = PySeparateCode("LoggingConsole", RUNTIME)

val IntToFloat64 = PyInlineSupportCode("int_to_float64", -1) { pos, args ->
    Py.Name(pos.leftEdge, PyIdentifierName("float")).call(args)
}
val IntToString = PySeparateCode("int_to_string", RUNTIME)
val Int64ToFloat64 = PySeparateCode("int64_to_float64", RUNTIME)
val Int64ToInt32 = PySeparateCode("int64_to_int32", RUNTIME)
val Int64ToInt32Unsafe = PySeparateCode("int64_to_int32_unsafe", RUNTIME)
val BooleanToString = PySeparateCode("boolean_to_string", RUNTIME)
val DateFromIsoString = PySeparateCode("date_from_iso_string", RUNTIME)
val DateToString = PySeparateCode("date_to_string", RUNTIME)
val DateToday = PySeparateCode("date_today", RUNTIME)
val DateYearsBetween = PySeparateCode("years_between", RUNTIME)
val Float64Abs = PySeparateCode("abs", SYS_BUILTINS)
val Float64Acos = PySeparateCode("acos", MATH)
val Float64Asin = PySeparateCode("asin", MATH)
val Float64Atan = PySeparateCode("atan", MATH)
val Float64Atan2 = PySeparateCode("atan2", MATH)
val Float64Ceil = PySeparateCode("ceil", MATH)
val Float64Cos = PySeparateCode("cos", MATH)
val Float64Cosh = PySeparateCode("cosh", MATH)
val Float64Exp = PySeparateCode("exp", MATH)
val Float64Expm1 = PySeparateCode("expm1", MATH)
val Float64Floor = PySeparateCode("floor", MATH)
val Float64Log = PySeparateCode("log", MATH)
val Float64Log10 = PySeparateCode("log10", MATH)
val Float64Log1p = PySeparateCode("log1p", MATH)
val Float64Max = PySeparateCode("float64_max", RUNTIME)
val Float64Min = PySeparateCode("float64_min", RUNTIME)
val Float64Near = PySeparateCode("float64_near", RUNTIME)
val Float64Round = PySeparateCode("round", SYS_BUILTINS)
val Float64Sign = PySeparateCode("float64_sign", RUNTIME)
val Float64Sin = PySeparateCode("sin", MATH)
val Float64Sinh = PySeparateCode("sinh", MATH)
val Float64Sqrt = PySeparateCode("sqrt", MATH)
val Float64Tan = PySeparateCode("tan", MATH)
val Float64Tanh = PySeparateCode("tanh", MATH)
val Float64E = PySeparateCode("e", MATH)
val Float64Pi = PySeparateCode("pi", MATH)
val Float64ToInt = PySeparateCode("float64_to_int", RUNTIME)
val Float64ToIntUnsafe = PySeparateCode("float64_to_int_unsafe", RUNTIME)
val Float64ToInt64 = PySeparateCode("float64_to_int64", RUNTIME)
val Float64ToInt64Unsafe = PySeparateCode("float64_to_int64_unsafe", RUNTIME)
val Float64ToString = PySeparateCode("float64_to_string", RUNTIME)
val IntMax = PySeparateCode("max", SYS_BUILTINS)
val IntMin = PySeparateCode("min", SYS_BUILTINS)
val SymbolType = PySeparateCode("Symbol", RUNTIME)
val StringBuilderConstructor = PyInlineSupportCode(
    "StringBuilder::constructor",
    arity = 0,
    needsSelf = false,
) { pos, _ ->
    // new StringBuilder() -> [""]
    Py.ListExpr(pos, listOf(Py.Str(pos, "")))
}
val StringBuilderAppend = PyInlineSupportCode(
    "StringBuilder::append",
    arity = 2,
    needsSelf = true,
) { pos, args ->
    args[0].method("append", args[1], pos = pos)
}
val StringBuilderAppendBetween = PyInlineSupportCode(
    "StringBuilder::appendBetween",
    arity = 4..4,
    needsSelf = true,
    translatorFactory = { pos, args, t ->
        val substringArgs = args.subListToEnd(1)
        StringBuilderAppend.inlineExprsToTree(
            pos,
            listOf(
                args[0],
                StringSlice.inlineExprsToTree(
                    substringArgs.spanningPosition(substringArgs.first().pos),
                    substringArgs,
                    t,
                ) as Py.Expr,
            ),
            t,
        )
    },
)

val StringBuilderAppendCodePoint = PyInlineSupportCode(
    "StringBuilder::appendCodePoint",
    arity = 2..2,
    needsSelf = true,
    translatorFactory = { pos, args, t ->
        val (str, cp) = args
        StringBuilderAppend.inlineExprsToTree(
            pos,
            listOf(
                str,
                Py.Call( // temper_core.string_from_code_point(cp)
                    cp.pos,
                    Py.Name(cp.pos.leftEdge, PyIdentifierName(t.request(StringFromCodePoint).outputNameText)),
                    listOf(Py.CallArg(cp)),
                ),
            ),
            t,
        )
    },
)
val StringBuilderToString = PyInlineSupportCode(
    "StringBuilder::toString",
    arity = 1,
    needsSelf = true,
) { pos, args ->
    // this.toString() -> "".join(this)
    Py.Str(pos.leftEdge, "").method("join", args[0])
}
val StringIndexNone = PyInlineSupportCode(
    "StringIndex::none",
    arity = 0,
) { pos, _ ->
    Py.Num(pos, -1)
}
private fun stringIndexOptionComparer(
    baseName: String,
    op: BinaryOpEnum,
): PyInlineSupportCode =
    PyInlineSupportCode(
        baseName = baseName,
        arity = 2,
        needsSelf = true,
    ) { pos: Position, arguments: List<Py.Expr> ->
        val (a, b) = arguments
        val op = Py.BinaryOp(a.pos.rightEdge, op, PyOperatorDefinition.Sub)
        Py.BinExpr(pos, a, op, b)
    }

val StringIndexOptionCompareTo = stringIndexOptionComparer("StringIndexOption::compareTo", BinaryOpEnum.Sub)
val StringIndexOptionCompareToEq = stringIndexOptionComparer("StringIndexOption::compareTo::eq", BinaryOpEnum.Eq)
val StringIndexOptionCompareToGe = stringIndexOptionComparer("StringIndexOption::compareTo::ge", BinaryOpEnum.GtEq)
val StringIndexOptionCompareToGt = stringIndexOptionComparer("StringIndexOption::compareTo::gt", BinaryOpEnum.Gt)
val StringIndexOptionCompareToLe = stringIndexOptionComparer("StringIndexOption::compareTo::le", BinaryOpEnum.LtEq)
val StringIndexOptionCompareToLt = stringIndexOptionComparer("StringIndexOption::compareTo::lt", BinaryOpEnum.Lt)
val StringIndexOptionCompareToNe = stringIndexOptionComparer("StringIndexOption::compareTo::ne", BinaryOpEnum.NotEq)
val RequireStringIndex = PySeparateCode("require_string_index", RUNTIME)
val RequireNoStringIndex = PySeparateCode("require_no_string_index", RUNTIME)

val BubbleException = PySeparateCode("RuntimeError", SYS_BUILTINS)
val LabelContextManager = PySeparateCode("Label", RUNTIME)
val LabelPairContextManager = PySeparateCode("LabelPair", RUNTIME)
val GenericIsEmpty = PyInlineSupportCode(
    "generic_is_empty",
    arity = 1,
    needsSelf = true,
) { pos, arg ->
    UnaryOpEnum.BoolNot.invoke(arg[0], pos = pos)
}

val LoggingConsole = PySeparateCode("LoggingConsole", RUNTIME)
val DenseBitVector = PySeparateCode("DenseBitVector", RUNTIME)
val Deque = PySeparateCode("deque", SYS_COLLECTIONS)

val PrintFunction = PySeparateCode("temper_print", RUNTIME, BuiltinOperatorId.Print)
val BubbleFunc = PySeparateCode("bubble", RUNTIME, BuiltinOperatorId.Bubble)
val StrCatFn = PySeparateCode("str_cat", RUNTIME, BuiltinOperatorId.StrCat)
val BoolNot = PyInlineSupportCode("bool_not", 1, BuiltinOperatorId.BooleanNegation) { pos, arg ->
    UnaryOpEnum.BoolNot.invoke(arg[0], pos = pos)
}
val ArithIntMod = PySeparateCode("arith_int_mod", RUNTIME, BuiltinOperatorId.ModIntInt)
val ArithDubMod = PySeparateCode("fmod", MATH, BuiltinOperatorId.ModFltFlt)
val ArithIntDiv = PySeparateCode("int_div", RUNTIME, BuiltinOperatorId.DivIntInt)
val ArithInt64Div = PySeparateCode("int64_div", RUNTIME, BuiltinOperatorId.DivIntInt64)
val ArithDubDiv = PyInlineSupportCode("arith_dub_div", 2, BuiltinOperatorId.DivIntInt) { pos, arg ->
    BinaryOpEnum.Div(arg[0], arg[1], pos = pos)
}
val ArithIntAdd = PySeparateCode("int_add", RUNTIME, BuiltinOperatorId.PlusIntInt)
val ArithIntSub = PySeparateCode("int_sub", RUNTIME, BuiltinOperatorId.MinusIntInt)
val ArithIntNegate = PySeparateCode("int_negate", RUNTIME, BuiltinOperatorId.MinusInt)
val ArithInt64Add = PySeparateCode("int64_add", RUNTIME, BuiltinOperatorId.PlusIntInt64)
val ArithInt64Sub = PySeparateCode("int64_sub", RUNTIME, BuiltinOperatorId.MinusIntInt64)
val ArithInt64Negate = PySeparateCode("int64_negate", RUNTIME, BuiltinOperatorId.MinusInt64)
val ArithDubAdd = PyInlineSupportCode("arith_dub_add", 2, BuiltinOperatorId.PlusFltFlt) { pos, arg ->
    BinaryOpEnum.Add(arg[0], arg[1], pos = pos)
}
val ArithDubSub = PyInlineSupportCode("arith_dub_sub", 2, BuiltinOperatorId.MinusFltFlt) { pos, arg ->
    BinaryOpEnum.Sub(arg[0], arg[1], pos = pos)
}
val ArithDubNegate = PyInlineSupportCode("arith_dub_negate", 1, BuiltinOperatorId.MinusFlt) { pos, arg ->
    UnaryOpEnum.UnarySub(arg[0], pos = pos)
}
val ArithIntTimes = PySeparateCode("int_mul", RUNTIME, BuiltinOperatorId.TimesIntInt)
val ArithInt64Times = PySeparateCode("int64_mul", RUNTIME, BuiltinOperatorId.TimesIntInt)
val ArithDubTimes = PyInlineSupportCode("arith_dub_times", 2, BuiltinOperatorId.TimesFltFlt) { pos, arg ->
    BinaryOpEnum.Mult(arg[0], arg[1], pos = pos)
}
val ArithDubPow = PyInlineSupportCode("arith_dub_pow", 2, BuiltinOperatorId.PowFltFlt) { pos, arg ->
    BinaryOpEnum.Pow(arg[0], arg[1], pos = pos)
}

val ArithBitAnd = PyInlineSupportCode("arith_bit_and", 2, BuiltinOperatorId.BitwiseAnd) { pos, arg ->
    BinaryOpEnum.BitwiseAnd(arg[0], arg[1], pos = pos)
}
val ArithBitOr = PyInlineSupportCode("arith_bit_or", 2, BuiltinOperatorId.BitwiseOr) { pos, arg ->
    BinaryOpEnum.BitwiseOr(arg[0], arg[1], pos = pos)
}
val Listify = PyInlineSupportCode("listify", -1, BuiltinOperatorId.Listify) { pos, args ->
    Py.Tuple(pos, elts = args)
}

val IsNull = PyInlineSupportCode("is_none", 1, BuiltinOperatorId.IsNull) { pos, args ->
    BinaryOpEnum.Is(args[0], Py.Name(pos, PyIdentifierName("None"), null), pos = pos)
}

val GenericCmp = PySeparateCode("generic_cmp", RUNTIME, BuiltinOperatorId.CmpGeneric)
val GenericEq = PySeparateCode("generic_eq", RUNTIME, BuiltinOperatorId.EqGeneric)
val GenericNotEq = PySeparateCode("generic_not_eq", RUNTIME, BuiltinOperatorId.NeGeneric)
val GenericLtEq = PySeparateCode("generic_lt_eq", RUNTIME, BuiltinOperatorId.LeGeneric)
val GenericLt = PySeparateCode("generic_lt", RUNTIME, BuiltinOperatorId.LtGeneric)
val GenericGtEq = PySeparateCode("generic_gt_eq", RUNTIME, BuiltinOperatorId.GeGeneric)
val GenericGt = PySeparateCode("generic_gt", RUNTIME, BuiltinOperatorId.GtGeneric)

val IntCmp = PyInlineSupportCode("int_cmp", -1, BuiltinOperatorId.EqIntInt) { pos, args ->
    BinaryOpEnum.Sub(args[0], args[1], pos = pos)
}
val IntEq = PyInlineSupportCode("int_lt", -1, BuiltinOperatorId.EqIntInt) { pos, args ->
    BinaryOpEnum.Eq(args[0], args[1], pos = pos)
}
val IntNotEq = PyInlineSupportCode("int_not_eq", -1, BuiltinOperatorId.EqIntInt) { pos, args ->
    BinaryOpEnum.NotEq(args[0], args[1], pos = pos)
}
val IntLt = PyInlineSupportCode("int_lt", -1, BuiltinOperatorId.LtIntInt) { pos, args ->
    BinaryOpEnum.Lt(args[0], args[1], pos = pos)
}
val IntLtEq = PyInlineSupportCode("int_le", -1, BuiltinOperatorId.LeIntInt) { pos, args ->
    BinaryOpEnum.LtEq(args[0], args[1], pos = pos)
}
val IntGt = PyInlineSupportCode("int_gt", -1, BuiltinOperatorId.GtIntInt) { pos, args ->
    BinaryOpEnum.Gt(args[0], args[1], pos = pos)
}
val IntGtEq = PyInlineSupportCode("int_ge", -1, BuiltinOperatorId.GeIntInt) { pos, args ->
    BinaryOpEnum.GtEq(args[0], args[1], pos = pos)
}

val DubCmp = PySeparateCode("float_cmp", RUNTIME, BuiltinOperatorId.CmpFltFlt)
val DubEq = PySeparateCode("float_eq", RUNTIME, BuiltinOperatorId.EqFltFlt)
val DubNotEq = PySeparateCode("float_not_eq", RUNTIME, BuiltinOperatorId.NeFltFlt)
val DubLtEq = PySeparateCode("float_lt_eq", RUNTIME, BuiltinOperatorId.LeFltFlt)
val DubLt = PySeparateCode("float_lt", RUNTIME, BuiltinOperatorId.LtFltFlt)
val DubGtEq = PySeparateCode("float_gt_eq", RUNTIME, BuiltinOperatorId.GeFltFlt)
val DubGt = PySeparateCode("float_gt", RUNTIME, BuiltinOperatorId.GtFltFlt)

val StrCmp = PyInlineSupportCode("str_cmp", -1, BuiltinOperatorId.EqStrStr) { pos, args ->
    BinaryOpEnum.Sub(
        BinaryOpEnum.Gt(args[0], args[1], pos = pos),
        BinaryOpEnum.Lt(args[0], args[1], pos = pos),
    )
}
val StrEq = PyInlineSupportCode("str_eq", -1, BuiltinOperatorId.EqStrStr) { pos, args ->
    BinaryOpEnum.Eq(args[0], args[1], pos = pos)
}
val StrNotEq = PyInlineSupportCode("str_not_eq", -1, BuiltinOperatorId.NeStrStr) { pos, args ->
    BinaryOpEnum.NotEq(args[0], args[1], pos = pos)
}
val StrLt = PyInlineSupportCode("str_lt", -1, BuiltinOperatorId.LtStrStr) { pos, args ->
    BinaryOpEnum.Lt(args[0], args[1], pos = pos)
}
val StrLtEq = PyInlineSupportCode("str_le", -1, BuiltinOperatorId.LeStrStr) { pos, args ->
    BinaryOpEnum.LtEq(args[0], args[1], pos = pos)
}
val StrGt = PyInlineSupportCode("str_gt", -1, BuiltinOperatorId.GtStrStr) { pos, args ->
    BinaryOpEnum.Gt(args[0], args[1], pos = pos)
}
val StrGtEq = PyInlineSupportCode("str_ge", -1, BuiltinOperatorId.GeStrStr) { pos, args ->
    BinaryOpEnum.GtEq(args[0], args[1], pos = pos)
}
val StringFromCodePoint = PySeparateCode("string_from_code_point", RUNTIME)
val StringFromCodePoints = PySeparateCode("string_from_code_points", RUNTIME)
val StringBegin = PyInlineSupportCode("string_begin", 0) { pos, _ ->
    Py.Num(pos, 0)
}
val StringSplit = PySeparateCode("string_split", RUNTIME)
val StringToFloat64 = PySeparateCode("string_to_float64", RUNTIME)
val StringToInt = PySeparateCode("string_to_int32", RUNTIME)
val StringToInt64 = PySeparateCode("string_to_int64", RUNTIME)
val StringGet = PySeparateCode("string_get", RUNTIME)
val StringCountBetween = PySeparateCode("string_count_between", RUNTIME)
val StringForEach = PySeparateCode("string_for_each", RUNTIME)
val StringHasAtLeast = PySeparateCode("string_has_at_least", RUNTIME)
val StringHasIndex = PyInlineSupportCode("string_has_index", 2..2, needsSelf = true) { pos, args, t ->
    // s.hasIndex(i) -> len(s) > i
    val (s, i) = args
    Py.BinExpr(
        pos,
        Py.Call(
            s.pos,
            Py.Name(s.pos.leftEdge, PyIdentifierName(t.request(LenFunction).outputNameText)),
            listOf(Py.CallArg(s)),
        ),
        Py.BinaryOp(
            s.pos.rightEdge,
            BinaryOpEnum.Gt,
            PyOperatorDefinition.Gt,
        ),
        i,
    )
}
val StringIndexOf = PyInlineSupportCode("String::indexOf", arity = 2..3, needsSelf = true) { pos, args, t ->
    args[0].method("find", args.subListToEnd(1), pos = pos)
}
val StringNext = PySeparateCode("string_next", RUNTIME)
val StringPrev = PySeparateCode("string_prev", RUNTIME)
val StringSlice = PyInlineSupportCode("string_substring", 3) { pos, args ->
    Py.Subscript(
        pos,
        args[0],
        listOf(
            Py.Slice(
                listOf(args[1]).spanningPosition(args[2].pos),
                args[1],
                args[2],
            ),
        ),
    )
}
val StringStep = PySeparateCode("string_step", RUNTIME)
val ListFilter = PySeparateCode("list_filter", RUNTIME)
val ListForEach = PySeparateCode("list_for_each", RUNTIME)
val ListGet = PySeparateCode("list_get", RUNTIME)
val ListBuilderSet = PySeparateCode("list_builder_set", RUNTIME)
val ListGetOr = PySeparateCode("list_get_or", RUNTIME)
val ListJoin = PySeparateCode("list_join", RUNTIME)
val ListMap = PySeparateCode("list_map", RUNTIME)
val ListMapDropping = PySeparateCode("list_map_dropping", RUNTIME)
val ListSlice = PySeparateCode("list_slice", RUNTIME)
val LenFunction = PySeparateCode("len", SYS_BUILTINS)

val ListedReduce = PySeparateCode("listed_reduce", RUNTIME)
val ListedReduceFrom = PySeparateCode("listed_reduce_from", RUNTIME)
val ListedSorted = PySeparateCode("listed_sorted", RUNTIME)
val ListedToList = PySeparateCode("listed_to_list", RUNTIME)

val ListBuilderAdd = PySeparateCode("list_builder_add", RUNTIME)
val ListBuilderAddInliner = PyInlineSupportCode("ListBuilder::add", arity = 2..3, needsSelf = true) { pos, args, t ->
    when (args.size) {
        // Add at end
        2 -> args[0].method("append", args[1], pos = pos)
        // Insert at location
        3 -> Py.Call(
            pos,
            t.request(ListBuilderAdd).asPyName(pos.leftEdge),
            args.map { Py.CallArg(it) },
        )
        else -> garbageExpr(pos, "Wrong number of arguments: $args", null)
    }
}
val ListBuilderAddAll = PySeparateCode("list_builder_add_all", RUNTIME)
val ListBuilderRemoveLast = PyInlineSupportCode(
    "ListBuilder::removeLast",
    arity = 1,
    needsSelf = true,
) { pos, arg ->
    arg[0].method("pop", pos = pos)
}
val ListBuilderReverse = PySeparateCode("list_builder_reverse", RUNTIME)
val ListBuilderSort = PySeparateCode("list_builder_sort", RUNTIME)
val ListBuilderSplice = PySeparateCode("list_builder_splice", RUNTIME)

val MapConstructor = PySeparateCode("map_constructor", RUNTIME)
val MapBuilderConstructor = PyInlineSupportCode("map_builder_constructor", 0) { pos, _ ->
    Py.Dict(pos, emptyList())
}
val MapBuilderRemove = PyInlineSupportCode(
    "MapBuilder::remove",
    arity = 2,
    needsSelf = true,
) { pos, arg ->
    arg[0].method("pop", arg[1], pos = pos)
}
val MapBuilderSet = PySeparateCode("map_builder_set", RUNTIME)
val PairConstructor = PySeparateCode("Pair", RUNTIME)
val PairType = PySeparateCode("Pair", RUNTIME)
val MappedType = PySeparateCode("Mapping", RUNTIME)
val MappedLength = PyInlineSupportCode("Mapped::length", arity = 1, needsSelf = true) { pos, args ->
    Py.Call(
        pos,
        func = Py.Name(pos.leftEdge, PyIdentifierName("len")),
        args = listOf(
            Py.CallArg(args[0].pos, value = args[0]),
        ),
    )
}
val MappedGet = PyInlineSupportCode("Mapped::get", arity = 2, needsSelf = true) { pos, args ->
    Py.Subscript(
        pos,
        args[0],
        listOf(args[1]),
    )
}
val MappedGetOr = PyInlineSupportCode("Mapped::getOr", arity = 3, needsSelf = true) { pos, args ->
    args[0].method("get", args[1], args[2], pos = pos)
}
val MappedHas = PySeparateCode("mapped_has", RUNTIME)
val MappedKeys = PyInlineSupportCode("Mapped::keys", arity = 1, needsSelf = true) { pos, args ->
    Py.Call(
        pos,
        func = Py.Name(pos.leftEdge, PyIdentifierName("list")),
        args = listOf(
            Py.CallArg(args[0].pos, value = args[0].method("keys")),
        ),
    )
}
val MappedValues = PyInlineSupportCode("Mapped::values", arity = 1, needsSelf = true) { pos, args ->
    Py.Call(
        pos,
        func = Py.Name(pos.leftEdge, PyIdentifierName("list")),
        args = listOf(
            Py.CallArg(args[0].pos, value = args[0].method("values")),
        ),
    )
}
val MappedToMap = PySeparateCode("mapped_to_map", RUNTIME)
val MappedToMapBuilder = PyInlineSupportCode(
    "Mapped::toMapBuilder",
    arity = 1,
    needsSelf = true,
) { pos, args ->
    Py.Call(
        pos,
        func = Py.Name(pos.leftEdge, PyIdentifierName("dict")),
        args = listOf(
            Py.CallArg(args[0].pos, value = args[0]),
        ),
    )
}
val MappedToList = PySeparateCode("mapped_to_list", RUNTIME)
val MappedToListBuilder = PySeparateCode("mapped_to_list_builder", RUNTIME)
val MappedToListWith = PySeparateCode("mapped_to_list_with", RUNTIME)
val MappedToListBuilderWith = PySeparateCode("mapped_to_list_builder_with", RUNTIME)
val MappedForEach = PySeparateCode("mapped_for_each", RUNTIME)

val DequeConstructor = PySeparateCode("deque", SYS_COLLECTIONS)
val DequeAdd = PyInlineSupportCode("Deque::add", arity = 2, needsSelf = true) { pos, args ->
    args[0].method("append", args[1], pos = pos)
}
val DequeRemoveFirst = PySeparateCode("deque_remove_first", RUNTIME)

val DenseBitVectorConstructor = PySeparateCode("DenseBitVector", RUNTIME)
val DenseBitVectorSet = PySeparateCode("dense_bit_vector_set", RUNTIME)
val DenseBitVectorGet = PyInlineSupportCode(
    "DenseBitVector::get",
    arity = 2,
    needsSelf = true,
) { pos, arg ->
    arg[0].method("get", arg[1], pos = pos)
}

val RegexCompiledFind = PySeparateCode("regex_compiled_find", RUNTIME_REGEX)
val RegexCompiledFound = PySeparateCode("regex_compiled_found", RUNTIME_REGEX)
val RegexCompiledReplace = PySeparateCode("regex_compiled_replace", RUNTIME_REGEX)
val RegexCompiledSplit = PySeparateCode("regex_compiled_split", RUNTIME_REGEX)
val RegexCompileFormatted = PySeparateCode("regex_compile_formatted", RUNTIME_REGEX)
val RegexFormatterPushCaptureName = PySeparateCode("regex_formatter_push_capture_name", RUNTIME_REGEX)
val RegexFormatterPushCodeTo = PySeparateCode("regex_formatter_push_code_to", RUNTIME_REGEX)

val BuiltinNext = PySeparateCode("next", SYS_BUILTINS)

val Async = PySeparateCode("async_launch", RUNTIME, BuiltinOperatorId.Async)
val AdaptGeneratorFactory = PySeparateCode(
    "adapt_generator_factory",
    RUNTIME,
    BuiltinOperatorId.Async,
)
val AwaitSafeToExit = PySeparateCode("await_safe_to_exit", RUNTIME)
val ConcurrentFuturesFuture = PyConnectedType("Future", CONCURRENT_FUTURES)
val PromiseBuilderBreakPromise = PySeparateCode("break_promise", RUNTIME)
val PromiseBuilderComplete = PySeparateCode("complete_promise", RUNTIME)
val PromiseBuilderConstructor = PySeparateCode("new_unbound_promise", RUNTIME)
val PromiseBuilderGetPromise = PyInlineSupportCode(
    "PromiseBuilder::getPromise",
    arity = 1,
    needsSelf = true,
) { _, args ->
    // PromiseBuilder and Promise connect to the same type, so
    // each "PromiseBuilder" is its own Promise.
    args[0]
}

val NetResponse = PyConnectedType("NetResponse", RUNTIME)

// docs.python.org/3/library/urllib.request.html#urllib.response.addinfourl.status
val NetResponseGetStatus =
    inlineAttribute("NetResponse::getStatus", PyIdentifierName("status"))
val NetResponseGetContentType = inlineAttribute("NetResponse::getContentType", PyIdentifierName("content_type"))
val NetResponseGetBodyContent = inlineAttribute("NetResponse::getBodyContent", PyIdentifierName("text"))
val StdNetSend = PySeparateCode("std_net_send", RUNTIME)

val mathInf = PySeparateCode("inf", MATH)
val mathNan = PySeparateCode("nan", MATH)

fun notSupported(name: String, builtin: NamedBuiltinFun, what: String = ""): PySupportCode {
    val msg = mutableListOf<String>()
    msg.add("Builtin(${builtin.name}, ${builtin.builtinOperatorId}, species=${builtin.functionSpecies})")
    if (what.isNotEmpty()) {
        msg.add(what)
    }

    return PyInlineSupportCode(
        name,
        arity = -1,
        needsSelf = false,
    ) { pos, _ ->
        garbageExpr(pos, "notSupported($name)", msg.joinToString("; "))
    }
}

val docPrintInliner = PyInlineSupportCode("print", 1, BuiltinOperatorId.Print) { pos, args ->
    Py.Call(
        pos,
        func = Py.Name(pos.leftEdge, PyIdentifierName("print")),
        args = args.map { Py.CallArg(it.pos, value = it) },
    )
}

/**
 * [PyInlineSupportCode] doesn't work here because of the context.
 * [lang.temper.be.tmpl.TmpLTranslator] tries to store things in temporaries then docgen complains.
 * So go to the effort of using [InlineTmpLSupportCode].
 */
object DocConsoleLogInliner : InlineTmpLSupportCode {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<TmpL.Tree>>,
        returnType: Type2,
        translator: TmpLTranslator,
    ): TmpL.Expression {
        val console = arguments[0].expr as TmpL.Expression
        val args = arguments.subListToEnd(1).mapNotNull { it.expr as? TmpL.Actual }
        // Call `print` only for very direct usage of `console.log`.
        val printSig =
            Signature2(WellKnownTypes.voidType2, false, listOf(), restInputsType = WellKnownTypes.anyValueType2)
        val callee = when (console is TmpL.Reference && console.id.name == consoleBuiltinName) {
            true -> TmpL.FnReference(
                pos,
                TmpL.Id(pos, BuiltinName("print")),
                printSig,
            )
            false -> TmpL.MethodReference(
                pos = pos,
                subject = console,
                methodName = TmpL.DotName(pos, "log"),
                method = null,
                type = printSig.copy(requiredInputTypes = listOf(console.type)),
            )
        }
        return TmpL.CallExpression(pos, callee, parameters = args, type = returnType)
    }

    override val needsThisEquivalent get() = true

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken("Console", OutputTokenType.Word))
        tokenSink.emit(OutputToken("::", OutputTokenType.Punctuation))
        tokenSink.emit(OutputToken("log", OutputTokenType.Word))
    }
}

typealias StmtFactory = (pos: Position, name: String) -> Iterable<Py.Stmt>

class PyGlobalCode(
    baseName: String,
    factoryFunction: StmtFactory,
) : PySupportCode(ParsedName(baseName), builtinOperatorId = null) {
    val factory = factoryFunction
}

internal val GetConsole = PyInlineSupportCode(
    "getConsole",
    arity = null,
    translatorFactory = { pos: Position, arguments, translator ->
        val loggerName = arguments.getOrNull(0) ?: Py.Name(pos, PyIdentifierName("__name__"))
        Py.Call(
            pos,
            func = translator.request(LoggingConsoleType).asPyName(pos),
            args = listOf(Py.CallArg(pos, value = loggerName)),
        )
    },
)

internal val BailCall = PyInlineSupportCode(
    "bail",
    arity = 1..1,
    translatorFactory = { pos, arguments, translator ->
        // Approximately `assert False, str(self.messages_combined())`.
        // Could instead say `raise AssertionError(self.messages_combined())`.
        // Would need to coordinate access to the unittest.TestCase instance to say `test.fail(...)`.
        Py.Assert(
            pos,
            PyConstant.False.at(pos),
            when (val test = arguments.getOrNull(0)) {
                null -> null
                else -> Py.Call(
                    pos,
                    // Call `str` for unexpected case of None. Could do better, but again, this is unexpected.
                    translator.request(StrType).asPyName(pos),
                    listOf(
                        Py.CallArg(pos, value = test.method("messages_combined")),
                    ),
                )
            },
        )
    },
)

val UnittestTestCase = PySeparateCode("TestCase", UNITTEST)

// things from generics

val CallableType = PySeparateCode("Callable", TYPING)
val OptionalType = PySeparateCode("Optional", TYPING)
val UnionType = PySeparateCode("Union", TYPING)
val AnyType = PySeparateCode("Any", TYPING)

// Introduced in Python 3.11.
// val NeverType = PySeparateCode("Never", TYPING)

val TraitDecorator = PySeparateCode("trait", MYPY_EXTENSIONS)
val ClassVarType = PySeparateCode("ClassVar", TYPING)
val SequenceType = PySeparateCode("Sequence", TYPING)
val NoReturnType = PySeparateCode("NoReturn", TYPING)
val TypeVarType = PySeparateCode("TypeVar", TYPING)
val GenericType = PySeparateCode("Generic", TYPING)
val MutableSequenceType = PySeparateCode("MutableSequence", TYPING)
val TypingDict = PySeparateCode("Dict", TYPING)
val TypingGenerator = PySeparateCode("Generator", TYPING)

private val pyConnections = mapOf(
    "::getConsole" to GetConsole,
    "Boolean::toString" to BooleanToString,
    "Date::constructor" to DateConstructor,
    "Date::fromIsoString" to DateFromIsoString,
    "Date::getDay" to DateGetDay,
    "Date::getDayOfWeek" to DateGetDayOfWeek,
    "Date::getMonth" to DateGetMonth,
    "Date::getYear" to DateGetYear,
    "Date::toString" to DateToString,
    "Date::today" to DateToday,
    "Date::yearsBetween" to DateYearsBetween,
    "DenseBitVector::constructor" to DenseBitVectorConstructor,
    "DenseBitVector::get" to DenseBitVectorGet,
    "DenseBitVector::set" to DenseBitVectorSet,
    "Deque::add" to DequeAdd,
    "Deque::constructor" to DequeConstructor,
    "Deque::isEmpty" to GenericIsEmpty,
    "Deque::removeFirst" to DequeRemoveFirst,
    "Float64::abs" to Float64Abs,
    "Float64::acos" to Float64Acos,
    "Float64::asin" to Float64Asin,
    "Float64::atan" to Float64Atan,
    "Float64::atan2" to Float64Atan2,
    "Float64::ceil" to Float64Ceil,
    "Float64::cos" to Float64Cos,
    "Float64::cosh" to Float64Cosh,
    "Float64::e" to Float64E,
    "Float64::exp" to Float64Exp,
    "Float64::expm1" to Float64Expm1,
    "Float64::floor" to Float64Floor,
    "Float64::log" to Float64Log,
    "Float64::log10" to Float64Log10,
    "Float64::log1p" to Float64Log1p,
    "Float64::max" to Float64Max,
    "Float64::min" to Float64Min,
    "Float64::near" to Float64Near,
    "Float64::pi" to Float64Pi,
    "Float64::round" to Float64Round,
    "Float64::sign" to Float64Sign,
    "Float64::sin" to Float64Sin,
    "Float64::sinh" to Float64Sinh,
    "Float64::sqrt" to Float64Sqrt,
    "Float64::tan" to Float64Tan,
    "Float64::tanh" to Float64Tanh,
    "Float64::toInt32" to Float64ToInt,
    "Float64::toInt32Unsafe" to Float64ToIntUnsafe,
    "Float64::toInt64" to Float64ToInt64,
    "Float64::toInt64Unsafe" to Float64ToInt64Unsafe,
    "Float64::toString" to Float64ToString,
    "Generator::next" to BuiltinNext,
    "Int32::max" to IntMax,
    "Int32::min" to IntMin,
    "Int32::toFloat64" to IntToFloat64,
    "Int32::toInt64" to Identity,
    "Int32::toString" to IntToString,
    "Int64::max" to IntMax,
    "Int64::min" to IntMin,
    "Int64::toInt32" to Int64ToInt32,
    "Int64::toInt32Unsafe" to Int64ToInt32Unsafe,
    "Int64::toFloat64" to Int64ToFloat64,
    "Int64::toFloat64Unsafe" to IntToFloat64,
    "Int64::toString" to IntToString,
    "List::forEach" to ListForEach,
    "List::get" to ListGet,
    "List::isEmpty" to GenericIsEmpty,
    "List::length" to LenFunction,
    "List::toList" to Identity,
    "List::toListBuilder" to ListType,
    "ListBuilder::add" to ListBuilderAddInliner,
    "ListBuilder::addAll" to ListBuilderAddAll,
    "ListBuilder::constructor" to ListType,
    "ListBuilder::length" to LenFunction,
    "ListBuilder::removeLast" to ListBuilderRemoveLast,
    "ListBuilder::reverse" to ListBuilderReverse,
    "ListBuilder::set" to ListBuilderSet,
    "ListBuilder::sort" to ListBuilderSort,
    "ListBuilder::splice" to ListBuilderSplice,
    "ListBuilder::toList" to TupleType,
    "ListBuilder::toListBuilder" to ListType,
    "Listed::filter" to ListFilter,
    "Listed::get" to ListGet,
    "Listed::getOr" to ListGetOr,
    "Listed::isEmpty" to GenericIsEmpty,
    "Listed::join" to ListJoin,
    "Listed::length" to LenFunction,
    "Listed::map" to ListMap,
    "Listed::mapDropping" to ListMapDropping,
    "Listed::reduce" to ListedReduce,
    "Listed::reduceFrom" to ListedReduceFrom,
    "Listed::slice" to ListSlice,
    "Listed::sorted" to ListedSorted,
    "Listed::toList" to ListedToList,
    "Listed::toListBuilder" to ListType,
    "Map::constructor" to MapConstructor,
    "MapBuilder::constructor" to MapBuilderConstructor,
    "MapBuilder::remove" to MapBuilderRemove,
    "MapBuilder::set" to MapBuilderSet,
    "Mapped::forEach" to MappedForEach,
    "Mapped::get" to MappedGet,
    "Mapped::getOr" to MappedGetOr,
    "Mapped::has" to MappedHas,
    "Mapped::keys" to MappedKeys,
    "Mapped::length" to MappedLength,
    "Mapped::toList" to MappedToList,
    "Mapped::toListBuilder" to MappedToListBuilder,
    "Mapped::toListBuilderWith" to MappedToListBuilderWith,
    "Mapped::toListWith" to MappedToListWith,
    "Mapped::toMap" to MappedToMap,
    "Mapped::toMapBuilder" to MappedToMapBuilder,
    "Mapped::values" to MappedValues,
    "NetResponse" to NetResponse,
    "NetResponse::getStatus" to NetResponseGetStatus,
    "NetResponse::getContentType" to NetResponseGetContentType,
    "NetResponse::getBodyContent" to NetResponseGetBodyContent,
    "Pair::constructor" to PairConstructor,
    "PromiseBuilder::breakPromise" to PromiseBuilderBreakPromise,
    "PromiseBuilder::complete" to PromiseBuilderComplete,
    "PromiseBuilder::constructor" to PromiseBuilderConstructor,
    "PromiseBuilder::getPromise" to PromiseBuilderGetPromise,
    "Regex::compileFormatted" to RegexCompileFormatted,
    "Regex::compiledFind" to RegexCompiledFind,
    "Regex::compiledFound" to RegexCompiledFound,
    "Regex::compiledReplace" to RegexCompiledReplace,
    "Regex::compiledSplit" to RegexCompiledSplit,
    "RegexFormatter::pushCaptureName" to RegexFormatterPushCaptureName,
    "RegexFormatter::pushCodeTo" to RegexFormatterPushCodeTo,
    "SafeGenerator::next" to BuiltinNext,
    "String::begin" to StringBegin,
    "String::countBetween" to StringCountBetween,
    "String::end" to LenFunction,
    "String::forEach" to StringForEach,
    "String::fromCodePoint" to StringFromCodePoint,
    "String::fromCodePoints" to StringFromCodePoints,
    "String::get" to StringGet,
    "String::hasAtLeast" to StringHasAtLeast,
    "String::hasIndex" to StringHasIndex,
    "String::indexOf" to StringIndexOf,
    "String::isEmpty" to GenericIsEmpty,
    "String::next" to StringNext,
    "String::prev" to StringPrev,
    "String::slice" to StringSlice,
    "String::split" to StringSplit,
    "String::step" to StringStep,
    "String::toFloat64" to StringToFloat64,
    "String::toInt32" to StringToInt,
    "String::toInt64" to StringToInt64,
    "String::toString" to Identity,
    "StringBuilder::append" to StringBuilderAppend,
    "StringBuilder::appendBetween" to StringBuilderAppendBetween,
    "StringBuilder::appendCodePoint" to StringBuilderAppendCodePoint,
    "StringBuilder::constructor" to StringBuilderConstructor,
    "StringBuilder::toString" to StringBuilderToString,
    "StringIndex::none" to StringIndexNone,
    "StringIndexOption::compareTo" to StringIndexOptionCompareTo,
    "StringIndexOption::compareTo::eq" to StringIndexOptionCompareToEq,
    "StringIndexOption::compareTo::ge" to StringIndexOptionCompareToGe,
    "StringIndexOption::compareTo::gt" to StringIndexOptionCompareToGt,
    "StringIndexOption::compareTo::le" to StringIndexOptionCompareToLe,
    "StringIndexOption::compareTo::lt" to StringIndexOptionCompareToLt,
    "StringIndexOption::compareTo::ne" to StringIndexOptionCompareToNe,
    "Test::bail" to BailCall,
    "empty" to EmptyInliner,
    "ignore" to Ignore,
    "stdNetSend" to StdNetSend,
)
