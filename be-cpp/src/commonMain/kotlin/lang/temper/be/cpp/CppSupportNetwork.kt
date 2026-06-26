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
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedType
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.pureVirtualBuiltinName

internal const val TEMPER_CORE_NAMESPACE = "temper::core"

/**
 * Maps Temper builtin operations and runtime type operations to C++ implementations.
 *
 * Builtins are translated to calls into `temper::core::*` helper functions defined in
 * `core.hpp`. Runtime type operations (`is`, `as`, `assertAs`) use `dynamic_pointer_cast`
 * for reference types and `AnyValueBox` unboxing for value types.
 */
internal object CppSupportNetwork : SupportNetwork {
    override val backendDescription: String = "Cpp Backend"
    override val bubbleStrategy: BubbleBranchStrategy = BubbleBranchStrategy.CatchBubble
    override val coroutineStrategy: CoroutineStrategy = CoroutineStrategy.TranslateToRegularFunction
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
        BuiltinOperatorId.BitwiseShrUnsigned32 -> Like.core("Int::ushr")
        BuiltinOperatorId.BitwiseShrUnsigned64 -> Like.core("Int64::ushr")
        BuiltinOperatorId.IsNull -> Like.core("is_null")
        BuiltinOperatorId.NotNull -> Like.core("not_null")
        BuiltinOperatorId.DivFltFlt -> Like.binary("/")
        BuiltinOperatorId.DivIntInt -> Like.core("Int::div_wrap")
        BuiltinOperatorId.DivIntInt64 -> Like.core("Int64::div_wrap")
        BuiltinOperatorId.DivIntIntSafe -> Like.core("Int::div_safe")
        BuiltinOperatorId.DivIntInt64Safe -> Like.core("Int64::div_safe")
        BuiltinOperatorId.ModFltFlt -> Like.name("std", "fmod")
        BuiltinOperatorId.ModIntInt -> Like.core("Int::mod_wrap")
        BuiltinOperatorId.ModIntInt64 -> Like.core("Int64::mod_wrap")
        BuiltinOperatorId.ModIntIntSafe -> Like.core("Int::mod_safe")
        BuiltinOperatorId.ModIntInt64Safe -> Like.core("Int64::mod_safe")
        BuiltinOperatorId.MinusFlt -> Like.unary("-")
        BuiltinOperatorId.MinusFltFlt -> Like.binary("-")
        // Int arithmetic uses wrapping core helpers that implement well-defined
        // two's-complement overflow in source, so the build does not rely on any
        // compiler flag (such as `-fwrapv`) to define overflow behavior. Float keeps
        // native operators.
        BuiltinOperatorId.MinusInt -> Like.core("Int::neg")
        BuiltinOperatorId.MinusInt64 -> Like.core("Int64::neg")
        BuiltinOperatorId.MinusIntInt -> Like.core("Int::sub")
        BuiltinOperatorId.MinusIntInt64 -> Like.core("Int64::sub")
        BuiltinOperatorId.PlusFltFlt -> Like.binary("+")
        BuiltinOperatorId.PlusIntInt -> Like.core("Int::add")
        BuiltinOperatorId.PlusIntInt64 -> Like.core("Int64::add")
        BuiltinOperatorId.TimesIntInt -> Like.core("Int::mul")
        BuiltinOperatorId.TimesIntInt64 -> Like.core("Int64::mul")
        BuiltinOperatorId.TimesFltFlt -> Like.binary("*")
        BuiltinOperatorId.PowFltFlt -> Like.core("Float64::pow")
        BuiltinOperatorId.LtFltFlt -> Like.core("Float64::lt")
        BuiltinOperatorId.LtIntInt -> Like.binary("<")
        BuiltinOperatorId.LtStrStr -> Like.core("Compare::lt")
        BuiltinOperatorId.LtGeneric -> Like.core("Compare::lt")
        BuiltinOperatorId.LeFltFlt -> Like.core("Float64::le")
        BuiltinOperatorId.LeIntInt -> Like.binary("<=")
        BuiltinOperatorId.LeStrStr -> Like.core("Compare::le")
        BuiltinOperatorId.LeGeneric -> Like.core("Compare::le")
        BuiltinOperatorId.GtFltFlt -> Like.core("Float64::gt")
        BuiltinOperatorId.GtIntInt -> Like.binary(">")
        BuiltinOperatorId.GtStrStr -> Like.core("Compare::gt")
        BuiltinOperatorId.GtGeneric -> Like.core("Compare::gt")
        BuiltinOperatorId.GeFltFlt -> Like.core("Float64::ge")
        BuiltinOperatorId.GeIntInt -> Like.binary(">=")
        BuiltinOperatorId.GeStrStr -> Like.core("Compare::ge")
        BuiltinOperatorId.GeGeneric -> Like.core("Compare::ge")
        BuiltinOperatorId.EqFltFlt -> Like.core("Float64::eq")
        BuiltinOperatorId.EqIntInt -> Like.binary("==")
        BuiltinOperatorId.EqStrStr -> Like.core("Compare::eq")
        BuiltinOperatorId.EqGeneric -> Like.core("Compare::eq")
        BuiltinOperatorId.NeFltFlt -> Like.core("Float64::ne")
        BuiltinOperatorId.NeIntInt -> Like.binary("!=")
        BuiltinOperatorId.NeStrStr -> Like.core("Compare::ne")
        BuiltinOperatorId.NeGeneric -> Like.core("Compare::ne")
        BuiltinOperatorId.CmpFltFlt -> Like.core("Float64::cmp")
        BuiltinOperatorId.CmpIntInt -> Like.core("Compare::cmp")
        BuiltinOperatorId.CmpStrStr -> Like.core("Compare::cmp")
        BuiltinOperatorId.CmpGeneric -> Like.core("Compare::cmp")
        BuiltinOperatorId.Bubble -> handle {
            // bubble() is template<class T = void> — need explicit type when used in expression context
            val cppRetType = translator.translateType2(retType)
            cpp.callExpr(
                cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "bubble"),
                    listOf(cppRetType),
                ),
                values,
            )
        }
        BuiltinOperatorId.Print -> Like.core("print")
        BuiltinOperatorId.StrCat -> Like.core("cat")
        BuiltinOperatorId.Listify -> handle {
            // List::make needs explicit template parameter since Elem can't be deduced
            val elemType = (retType as? DefinedType)?.bindings?.firstOrNull()
            if (elemType != null) {
                cpp.callExpr(
                    cpp.template(
                        cpp.name(TEMPER_CORE_NAMESPACE, "List", "make"),
                        listOf(translator.translateType2(elemType)),
                    ),
                    values,
                )
            } else {
                cpp.callExpr(
                    cpp.name(TEMPER_CORE_NAMESPACE, "List", "make"),
                    values,
                )
            }
        }
        BuiltinOperatorId.AdaptGeneratorFn -> Like.core("adapt_generator_fn")
        BuiltinOperatorId.SafeAdaptGeneratorFn -> Like.core("safe_adapt_generator_fn")
        BuiltinOperatorId.Async -> Like.core("async_run")
        null -> when (builtin.name) {
            pureVirtualBuiltinName.builtinKey -> Like.core("pure_virtual")
            // Coroutine→control-flow lowering for `await`: awakeUpon(promise, generator)
            // registers the resume, getPromiseResultSync(fail, promise) reads the value.
            "awakeUpon" -> Like.core("awake_upon")
            "getPromiseResultSync" -> Like.core("get_promise_result_sync")
            // No C++ support code for this builtin: per the SupportNetwork contract, null means
            // "not handled here" and the caller falls back (e.g. to a normal method call).
            else -> null
        }

        BuiltinOperatorId.Panic -> handle {
            val cppRetType = translator.translateType2(retType)
            cpp.callExpr(
                cpp.template(
                    cpp.name(TEMPER_CORE_NAMESPACE, "panic"),
                    listOf(cppRetType),
                ),
                values,
            )
        }
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    private val connectedRefs: Map<String, SupportCode> by lazy {
        buildMap {
            // Fail loudly on a duplicate key rather than silently letting a later entry win:
            // the table is built from many loops and individual entries, so an accidental
            // collision (e.g. a name added both by a loop and an explicit cased override)
            // would otherwise be invisible. This local `put` shadows MutableMap.put for the
            // unqualified calls below.
            val table = this
            fun put(key: String, code: SupportCode) {
                require(table.put(key, code) == null) { "duplicate connectedRefs entry for '$key'" }
            }
            put("core.getConsole()", Like.core("Console::get_console"))
            put("core.empty()", Like.core("empty"))
            put("core.ignore()", Like.ignoring(theLastArg))
            put("core.type Boolean.toString()", Like.core("Boolean::toString"))
            put("std/temporal.type Date.get day()", Like.core("Date::getDay"))
            put("std/temporal.type Date.get month()", Like.core("Date::getMonth"))
            put("std/temporal.type Date.get year()", Like.core("Date::getYear"))
            put("std/temporal.type Date.get dayOfWeek()", Like.core("Date::getDayOfWeek"))
            put("std/temporal.type Date.toString()", Like.core("Date::toString"))
            put("std/temporal.type Date.yearsBetween()", Like.core("Date::yearsBetween"))
            for (prop in listOf("e", "pi")) {
                put("core.type Float64.$prop", Like.core("Float64::$prop"))
            }
            for (fn in listOf(
                "abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "cosh",
                "exp", "expm1", "floor", "log", "log10", "log1p", "max", "min", "near",
                "round", "sign", "sin", "sinh", "sqrt", "tan", "tanh",
                "toInt32", "toInt32Unsafe", "toInt64", "toInt64Unsafe", "toString",
            )) {
                put("core.type Float64.$fn()", Like.core("Float64::$fn"))
            }
            for (fn in listOf("max", "min", "toFloat64", "toFloat64Unsafe", "toString", "toInt64")) {
                put("core.type Int32.$fn()", Like.core("Int::$fn"))
            }
            for (fn in listOf("max", "min", "toFloat64", "toFloat64Unsafe", "toInt32", "toInt32Unsafe", "toString")) {
                put("core.type Int64.$fn()", Like.core("Int64::$fn"))
            }
            put("core.type PromiseBuilder.breakPromise()", Like.core("breakpromise"))
            put("core.type PromiseBuilder.complete()", Like.core("complete"))
            put("core.type PromiseBuilder.get promise()", Like.core("getpromise"))
            put("core.type PromiseBuilder.constructor()", Like.coreWithRetTypeArgs("PromiseBuilderNs::make"))
            put("core.type String.begin", Like.core("String::begin"))
            for (prop in listOf("isEmpty", "end")) {
                put("core.type String.get $prop()", Like.core("String::$prop"))
            }
            for (fn in listOf(
                "toInt64", "isEmpty", "begin", "end",
                "get", "countBetween", "forEach", "hasAtLeast", "hasIndex", "next", "prev",
                "slice", "split", "step", "toFloat64", "toInt32", "toString", "indexOf",
            )) {
                put("core.type String.$fn()", Like.core("String::$fn"))
            }
            // Cased separately: the C++ core symbols spell these `fromCodepoint`/`fromCodepoints`.
            put("core.type String.fromCodePoint()", Like.core("String::fromCodepoint"))
            put("core.type String.fromCodePoints()", Like.core("String::fromCodepoints"))
            put("core.type StringIndex.none", Like.core("String::none"))
            put("core.type StringIndexOption.compareTo()", Like.core("Compare::cmp"))
            for ((op, sym) in listOf(
                "eq" to "==", "ne" to "!=", "lt" to "<",
                "le" to "<=", "gt" to ">", "ge" to ">=",
            )) {
                put("core.type StringIndexOption.compareTo()::$op", Like.binary(sym))
            }
            put("core.type StringBuilder.constructor()", Like.coreWithRetTypeArgs("StringBuilder::make"))
            put("core.type StringBuilder.get end()", Like.core("StringBuilder::end"))
            for (fn in listOf("append", "appendBetween", "toString", "clear")) {
                put("core.type StringBuilder.$fn()", Like.core("StringBuilder::$fn"))
            }
            put("core.type StringBuilder.appendCodePoint()", Like.core("StringBuilder::appendCodepoint"))
            put("core.type Console.log()", Like.core("Console::log"))
            put("core.type List.get length()", Like.coreWithRetTypeArgs("List::length"))
            for (fn in listOf("forEach", "get", "toList", "toListBuilder")) {
                put("core.type List.$fn()", Like.core("List::$fn"))
            }
            for (prop in listOf("isEmpty", "length")) {
                put("core.type Listed.get $prop()", Like.core("List::$prop"))
            }
            for (fn in listOf(
                "filter", "join", "map", "slice", "get", "getOr",
                "reduce", "sorted", "toList", "toListBuilder", "indexOf",
            )) {
                put("core.type Listed.$fn()", Like.core("List::$fn"))
            }
            put("core.type ListBuilder.constructor()", Like.coreWithRetTypeArgs("ListBuilder::make"))
            for (fn in listOf("add", "addAll", "removeLast", "reverse", "splice", "set", "sort")) {
                put("core.type ListBuilder.$fn()", Like.coreWithFirstArgTypeArgs("ListBuilder::$fn"))
            }
            put("core.type ListBuilder.toList()", Like.core("List::toList"))
            put("core.type ListBuilder.toListBuilder()", Like.core("List::toListBuilder"))
            put("core.type ListBuilder.get length()", Like.core("List::length"))
            put("core.type Map.constructor()", Like.coreWithRetTypeArgs("Map::make"))
            put("core.type MapBuilder.constructor()", Like.coreWithRetTypeArgs("Map::make"))
            for (fn in listOf("clear", "remove", "set")) {
                put("core.type MapBuilder.$fn()", Like.coreWithFirstArgTypeArgs("MapBuilder::$fn"))
            }
            put("core.type Pair.constructor()", Like.coreWithRetTypeArgs("PairFactory::make"))
            put("core.type Mapped.get length()", Like.core("Mapped::length"))
            for (fn in listOf("get", "getOr", "has")) {
                put("core.type Mapped.$fn()", Like.coreWithFirstArgTypeArgs("Mapped::$fn"))
            }
            for (fn in listOf(
                "keys", "values", "toMap", "toMapBuilder", "toList",
                "toListBuilder", "toListWith", "toListBuilderWith", "forEach",
            )) {
                put("core.type Mapped.$fn()", Like.core("Mapped::$fn"))
            }
            put("core.type DenseBitVector.constructor()", Like.coreWithRetTypeArgs("DenseBitVector::make"))
            put("core.type DenseBitVector.get()", Like.core("DenseBitVector::get"))
            put("core.type DenseBitVector.set()", Like.core("DenseBitVector::set"))
            put("core.type Deque.constructor()", Like.coreWithRetTypeArgs("Deque::make"))
            put("core.type Deque.get isEmpty()", Like.coreWithRetTypeArgs("Deque::isEmpty"))
            for (fn in listOf("add", "removeFirst")) {
                put("core.type Deque.$fn()", Like.coreWithFirstArgTypeArgs("Deque::$fn"))
            }
            for (fn in listOf(
                "compiledFind", "compiledFound", "compiledReplace", "compiledSplit",
            )) {
                put("std/regex.type Regex.$fn()", Like.core("Regex::$fn"))
            }
            put("std/regex.type RegexFormatter.regexCompileFormatted()", Like.core("Regex::compileFormatted"))
            put("std/regex.type RegexFormatter.pushCaptureName()", Like.core("Regex::pushCaptureName"))
            put("std/regex.type RegexFormatter.pushCodeTo()", Like.core("Regex::pushCodeTo"))
            put("std/testing.type Test.bail()", Like.core("testBail"))
            put("core.type Generator.next()", Like.core("next"))
            put("core.type SafeGenerator.next()", Like.core("next"))
            put("core.doneResult()", Like.core("doneResult"))
        }
    }

    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode? = connectedRefs[connectedKey] ?: when (connectedKey) {
        "std/temporal.type Date.constructor()" -> handle {
            val innerType = retType.bindings.firstOrNull() ?: retType
            val rawDateName = translator.resolveTypeName(innerType.definition)
            cpp.callExpr(
                cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Date", "makeDate"), listOf(rawDateName)),
                values,
            )
        }
        "std/temporal.type Date.fromIsoString()" -> handle {
            val innerType = retType.bindings.firstOrNull() ?: retType
            val rawDateName = translator.resolveTypeName(innerType.definition)
            cpp.callExpr(
                cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Date", "fromIso"), listOf(rawDateName)),
                values,
            )
        }
        "std/temporal.type Date.today()" -> handle {
            val innerType = retType.bindings.firstOrNull() ?: retType
            val rawDateName = translator.resolveTypeName(innerType.definition)
            cpp.callExpr(
                cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "Date", "today"), listOf(rawDateName)),
                emptyList(),
            )
        }
        "core.type Listed.reduceFrom()" -> handle {
            val name = cpp.name(TEMPER_CORE_NAMESPACE, "List", "reduceFrom")
            val elemType = types.getOrNull(0)?.let { type ->
                type.bindings.firstOrNull()?.let { translator.translateType2(it) }
            }
            val accType = translator.translateType2(retType)
            if (elemType != null) {
                cpp.callExpr(cpp.template(name, listOf(elemType, accType)), values)
            } else {
                cpp.callExpr(name, values)
            }
        }
        // Unknown connected reference: null signals "not provided by this backend" per the
        // SupportNetwork contract, leaving the caller to fall back rather than emit broken code.
        else -> null
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
        val dest = translator.resolveTypeName(targetType.typeName.sourceDefinition)
        val targetDef = targetType.typeName.sourceDefinition
        val isTargetValueType = translator.isValueTypeDef(targetDef)
        val isSourceValueType = translator.isValueTypeDef(sourceType.typeName.sourceDefinition)
        val isCast = rto == RuntimeTypeOperation.As || rto == RuntimeTypeOperation.AssertAs
        val value = values[0]

        fun dynamicPtrCast(castType: Cpp.Type): Cpp.Expr = cpp.callExpr(
            cpp.template(cpp.name("std", "dynamic_pointer_cast"), listOf(castType)),
            listOf(value),
        )

        fun notNull(expr: Cpp.Expr): Cpp.Expr =
            cpp.op("!=", listOf(expr, cpp.literal(cpp.raw("nullptr"))))

        when (targetDef) {
            WellKnownTypes.stringIndexTypeDefinition -> cpp.callExpr(
                cpp.name(TEMPER_CORE_NAMESPACE, "String", if (isCast) "requireStringIndex" else "isStringIndex"),
                listOf(value),
            )
            WellKnownTypes.noStringIndexTypeDefinition -> cpp.callExpr(
                cpp.name(TEMPER_CORE_NAMESPACE, "String", if (isCast) "requireNoStringIndex" else "isNoStringIndex"),
                listOf(value),
            )
            else -> when {
                isTargetValueType && isSourceValueType -> if (isCast) value else cpp.literal(true)
                isTargetValueType && isCast -> cpp.op(
                    "->",
                    cpp.callExpr(
                        cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "checked_cast_box"), listOf(dest)),
                        listOf(value),
                    ),
                    cpp.singleName(CppName("value", allowKey = false)),
                )
                // `x is SomeValueType` where x is a boxed AnyValue: test the boxed payload's
                // type. `is_box<T>` wraps the dynamic_pointer_cast-to-AnyValueBox<T> check.
                isTargetValueType -> cpp.callExpr(
                    cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "is_box"), listOf(dest)),
                    listOf(value),
                )
                isCast -> {
                    val fullTargetType = translator.translateType(targetType)
                    val castTarget = (fullTargetType as? Cpp.TemplateType)
                        ?.args?.firstOrNull()
                        ?.takeIf { it !is Cpp.TemplateType }
                    if (castTarget != null) {
                        cpp.callExpr(
                            cpp.template(cpp.name(TEMPER_CORE_NAMESPACE, "checked_cast"), listOf(castTarget)),
                            listOf(value),
                        )
                    } else {
                        value
                    }
                }
                else -> notNull(dynamicPtrCast(dest))
            }
        }
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

    private fun coreWithTypeArgs(
        parts: List<String>,
        extractBindings: InlineContext.() -> List<Type2>,
    ): CppInlineSupportCode = handle {
        val name = cpp.name(
            (listOf(TEMPER_CORE_NAMESPACE) + parts).flatMap { it.split("::") },
        )
        val typeArgs = extractBindings().map { translator.translateType2(it) }
        if (typeArgs.isNotEmpty()) {
            cpp.callExpr(cpp.template(name, typeArgs), values)
        } else {
            cpp.callExpr(name, values)
        }
    }

    fun coreWithFirstArgTypeArgs(vararg parts: String): CppInlineSupportCode =
        coreWithTypeArgs(parts.toList()) { types.firstOrNull()?.bindings ?: emptyList() }

    fun coreWithRetTypeArgs(vararg parts: String): CppInlineSupportCode =
        coreWithTypeArgs(parts.toList()) { retType.bindings }

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
