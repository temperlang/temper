package lang.temper.be.cppv

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.cpp.BinaryOpEnum
import lang.temper.be.cpp.Cpp
import lang.temper.be.cpp.CppBuilder
import lang.temper.be.cpp.UnaryOpEnum
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.ComparisonKind
import lang.temper.be.tmpl.ComputedJumpStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TranslationAssistant
import lang.temper.be.tmpl.TypedArg
import lang.temper.common.subListToEnd
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.emptyValue

object CppSupportNetwork : SupportNetwork {
    override val backendDescription = "C++ Backend"
    override val bubbleStrategy = BubbleBranchStrategy.Results
    override val coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionalInterface
    override val computedJumpStrategy = ComputedJumpStrategy.IsDefaultBreakScope

    override fun representationOfVoid(genre: Genre) = RepresentationOfVoid.DoNotReifyVoid

    override fun getSupportCode(pos: Position, builtin: NamedBuiltinFun, genre: Genre): SupportCode? = run {
        runCatching { supportCodeByOperatorId(builtin.builtinOperatorId) }.getOrElse {
            // Useful for placing a breakpoint.
            null
        } ?: builtinFunSupportCode[builtin.name] ?: run {
            // Also useful.
            null
        }
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = run {
        null
    }

    override fun translateConnectedReference(
        pos: Position,
        connectedKey: String,
        genre: Genre,
    ): SupportCode? = run {
        connectedReferences[connectedKey] ?: run {
            // Useful for placing a breakpoint.
            null
        }
    }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? = run {
        null
    }

    override fun simplifyPossibleComparison(
        tmpl: TmpL.CallExpression,
        comparisonKind: ComparisonKind,
        translationAssistant: TranslationAssistant,
    ): TmpL.Expression? {
        val fn = tmpl.fn
        val supportCode = when (fn) {
            is TmpL.FnReference -> translationAssistant.supportCodeFromReference(fn.id)
            is TmpL.InlineSupportCodeWrapper -> fn.supportCode
            else -> null
        } as? CppSupportCode ?: return null
        if (supportCode.baseName.nameText == "core.type StringIndexOption.compareTo()") {
            // They're represented as ints, so just use the simple comparison below
        } else {
            when (supportCode.builtinOperatorId) {
                // These are ok to just replace with `<`, `<=, etc. below.
                BuiltinOperatorId.CmpIntInt,
                BuiltinOperatorId.CmpLongLong,
                BuiltinOperatorId.CmpBoolBool,
                -> {}
                // String and Float builtins require adjustment.
                else -> return null
            }
        }
        val freeParameters = tmpl.parameters.toList()
        tmpl.parameters = freeParameters.map {
            TmpL.ValueReference(it.pos, WellKnownTypes.emptyType2, emptyValue)
        }
        return TmpL.CallExpression(
            pos = tmpl.pos,
            fn = TmpL.InlineSupportCodeWrapper(
                fn.pos,
                fn.type.copy(returnType2 = WellKnownTypes.booleanType2),
                Infix(
                    "simpleComparison$comparisonKind",
                    when (comparisonKind) {
                        ComparisonKind.LessThan -> BinaryOpEnum.Lt
                        ComparisonKind.LessThanOrEqual -> BinaryOpEnum.Le
                        ComparisonKind.GreaterThanOrEqual -> BinaryOpEnum.Ge
                        ComparisonKind.GreaterThan -> BinaryOpEnum.Gt
                    },
                ),
            ),
            typeActuals = tmpl.typeActuals.deepCopy(),
            parameters = freeParameters,
        )
    }
}

private fun supportCodeByOperatorId(builtinOperatorId: BuiltinOperatorId?): SupportCode? = run {
    when (builtinOperatorId) {
        BuiltinOperatorId.DivIntInt, BuiltinOperatorId.DivIntInt64 -> divIntInt
        BuiltinOperatorId.DivIntIntSafe, BuiltinOperatorId.DivIntInt64Safe -> divIntIntSafe
        BuiltinOperatorId.EqIntInt -> eqIntInt
        BuiltinOperatorId.GeIntInt -> geIntInt
        BuiltinOperatorId.GtIntInt -> gtIntInt
        BuiltinOperatorId.LeIntInt -> leIntInt
        BuiltinOperatorId.LtIntInt -> ltIntInt
        BuiltinOperatorId.CmpBoolBool -> cmpBoolBool
        BuiltinOperatorId.CmpFltFlt -> cmpFltFlt
        BuiltinOperatorId.CmpIntInt -> cmpIntInt
        BuiltinOperatorId.CmpLongLong -> cmpLongLong
        BuiltinOperatorId.CmpStrStr -> cmpStrStr
        BuiltinOperatorId.Listify -> Listify
        BuiltinOperatorId.MinusInt, BuiltinOperatorId.MinusInt64 -> minusInt
        BuiltinOperatorId.MinusIntInt, BuiltinOperatorId.MinusIntInt64 -> minusIntInt
        BuiltinOperatorId.ModIntInt, BuiltinOperatorId.ModIntInt64 -> modIntInt
        BuiltinOperatorId.ModIntIntSafe, BuiltinOperatorId.ModIntInt64Safe -> modIntIntSafe
        BuiltinOperatorId.PlusIntInt, BuiltinOperatorId.PlusIntInt64 -> plusIntInt
        BuiltinOperatorId.TimesIntInt, BuiltinOperatorId.TimesIntInt64 -> timesIntInt
        BuiltinOperatorId.StrCat -> strCat
        BuiltinOperatorId.BooleanNegation -> boolNeg
        BuiltinOperatorId.IsOkResult -> isOkResult
        BuiltinOperatorId.PackOkResult -> packOkResult
        BuiltinOperatorId.RepackErrResult -> RepackErrResult
        BuiltinOperatorId.UnpackOkResult -> unpackOkResult
        else -> null
    }
}

private val builtinFunSupportCode: Map<String, CppInlineSupportCode> = mapOf()

open class CppSupportCode(
    val connectedNames: List<String>,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : NamedSupportCode {
    override val baseName = ParsedName(connectedNames.firstOrNull() ?: builtinOperatorId!!.name)
    override fun renderTo(tokenSink: TokenSink) = tokenSink.name(baseName, inOperatorPosition = false)

    final override fun hashCode(): Int = baseName.hashCode()
    final override fun toString(): String = "CSupportCode($baseName)"
    final override fun equals(other: Any?): Boolean =
        this === other || (other is CppSupportCode && baseName == other.baseName)
}

abstract class CppInlineSupportCode(
    connectedNames: List<String>,
    builtinOperatorId: BuiltinOperatorId? = null,
    override val needsThisEquivalent: Boolean = false,
) : CppSupportCode(connectedNames, builtinOperatorId), InlineSupportCode<Cpp.Tree, CppTranslator> {
    constructor(
        baseName: String,
        builtinOperatorId: BuiltinOperatorId? = null,
    ) : this(
        listOf(baseName),
        builtinOperatorId,
    )

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
    ): Cpp.Tree = translator.cpp.pos(pos) {
        inlineToTree(arguments, returnType, translator, translator.cpp)
    }

    abstract fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree
}

internal open class FunctionCall(
    val name: String,
    connectedNames: List<String>,
    builtinOperatorId: BuiltinOperatorId? = null,
    val namespace: String? = TEMPER_CORE_NAMESPACE,
) : CppInlineSupportCode(connectedNames, builtinOperatorId) {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree = run {
        val fullName = namespace?.let { cpp.name(namespace, name) } ?: cpp.singleName(name)
        cpp.callExpr(fullName, arguments.map { it.expr as Cpp.Expr })
    }
}

internal class Infix(
    connectedName: String,
    val op: BinaryOpEnum,
    builtinOperatorId: BuiltinOperatorId? = null,
) : CppInlineSupportCode(connectedName, builtinOperatorId) {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree = cpp.binaryExpr(
        arguments[0].expr as Cpp.Expr,
        Cpp.BinaryOp(cpp.pos, op),
        arguments[1].expr as Cpp.Expr,
    )
}

internal class Prefix(
    connectedName: String,
    val op: UnaryOpEnum,
    builtinOperatorId: BuiltinOperatorId? = null,
) : CppInlineSupportCode(connectedName, builtinOperatorId) {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree = cpp.unaryExpr(
        Cpp.UnaryOp(cpp.pos, op),
        arguments[0].expr as Cpp.Expr,
    )
}

internal class MethodCall(
    val name: String,
    connectedNames: List<String>,
    builtinOperatorId: BuiltinOperatorId? = null,
    val useDot: Boolean = false,
) : CppInlineSupportCode(connectedNames, builtinOperatorId) {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree = run {
        val callee = when {
            useDot -> cpp.memberExpr(arguments.first().expr as Cpp.Expr, cpp.singleName(name))
            else -> cpp.binaryExpr(
                arguments.first().expr as Cpp.Expr,
                Cpp.BinaryOp(cpp.pos, BinaryOpEnum.Arrow),
                cpp.singleName(name),
            )
        }
        cpp.callExpr(callee, arguments.subListToEnd(1).map { it.expr as Cpp.Expr })
    }
}

internal object ConsoleLog : CppInlineSupportCode("core.type Console.log()") {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree = run {
        // TODO Method call on console object.
        cpp.callExpr(
            cpp.name(TEMPER_CORE_NAMESPACE, "log"),
            arguments.subListToEnd(1).map { it.expr as Cpp.Expr },
        )
    }
}

private object GetConsole : CppInlineSupportCode("core.getConsole()") {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree = run {
        // TODO Actually get and use the console.
        cpp.literal("TODO get console")
    }
}

private object Listify : CppInlineSupportCode("Listify") {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree = run {
        val itemType = returnType.bindings.firstOrNull()
            ?: return@run cpp.literal("TODO $returnType")
        val itemTypeCpp = translator.translateType(itemType)
        cpp.callExpr(
            cpp.template(cpp.name("temper", "core", "listify"), itemTypeCpp),
            arguments.map { it.expr as Cpp.Expr },
        )
    }
}

// TODO Might need to push custom overloads for too many cat args.
// TODO And for listify, might need to build dynamically with a method call chain.
private val strCat = FunctionCall("cat", listOf("StrCat"))

private val divIntInt = FunctionCall("div_checked", listOf("DivIntInt"), BuiltinOperatorId.DivIntInt)
private val divIntIntSafe = FunctionCall("div", listOf("DivIntIntSafe"), BuiltinOperatorId.DivIntIntSafe)
private val cmpBoolBool = FunctionCall("cmp_bool", listOf(), BuiltinOperatorId.CmpBoolBool)
private val cmpFltFlt = FunctionCall("cmp_float64", listOf(), BuiltinOperatorId.CmpFltFlt)
private val cmpIntInt = FunctionCall("cmp_int32", listOf(), BuiltinOperatorId.CmpIntInt)
private val cmpLongLong = FunctionCall("cmp_int64", listOf(), BuiltinOperatorId.CmpLongLong)
private val cmpStrStr = FunctionCall("cmp_str", listOf(), BuiltinOperatorId.CmpStrStr)
private val eqIntInt = Infix("EqIntInt", BinaryOpEnum.Eq, BuiltinOperatorId.EqIntInt)
private val geIntInt = Infix("GeIntInt", BinaryOpEnum.Ge, BuiltinOperatorId.GeIntInt)
private val gtIntInt = Infix("GtIntInt", BinaryOpEnum.Gt, BuiltinOperatorId.GtIntInt)
private val intMax = FunctionCall("max", listOf("core.type Int32.max()", "core.type Int64.max()"), namespace = "std")
private val intMin = FunctionCall("min", listOf("core.type Int32.min()", "core.type Int64.min()"), namespace = "std")
private val int32ToInt64 = FunctionCall("int64_t", listOf("core.type Int32.toInt64()"), namespace = null)
private val int64ToInt32Unsafe = FunctionCall("int32_t", listOf("core.type Int64.toInt32Unsafe()"), namespace = null)
private val leIntInt = Infix("LeIntInt", BinaryOpEnum.Le, BuiltinOperatorId.LeIntInt)
private val listedTypes = listOf("Listed", "List", "ListBuilder")
private val listedIsEmpty = MethodCall("empty", listedTypes.map { "core.type $it.get isEmpty()" })
private val ltIntInt = Infix("LtIntInt", BinaryOpEnum.Lt, BuiltinOperatorId.LtIntInt)
private val minusInt = FunctionCall("neg", listOf("MinusInt"), BuiltinOperatorId.MinusInt)
private val minusIntInt = FunctionCall("sub", listOf("MinusIntInt"), BuiltinOperatorId.MinusIntInt)
private val modIntInt = FunctionCall("mod_checked", listOf("ModIntInt"), BuiltinOperatorId.ModIntInt)
private val modIntIntSafe = FunctionCall("mod", listOf("ModIntIntSafe"), BuiltinOperatorId.ModIntIntSafe)
private val plusIntInt = FunctionCall("add", listOf("PlusIntInt"), BuiltinOperatorId.PlusIntInt)
private val timesIntInt = FunctionCall("mul", listOf("TimesIntInt"), BuiltinOperatorId.TimesIntInt)
private val toString = FunctionCall("to_string", listOf("core.type Int32.toString()", "core.type Int64.toString()"))
private val toInt32 = FunctionCall("to_int32", listOf("core.type Int64.toInt32()", "core.type String.toInt32()"))
private val toInt64 = FunctionCall("to_int64", listOf("core.type String.toInt64()"))
private val boolNeg = Prefix("not", UnaryOpEnum.Not, BuiltinOperatorId.BooleanNegation)

private val isOkResult = MethodCall(
    name = "has_value",
    connectedNames = listOf("IsOk"),
    builtinOperatorId = BuiltinOperatorId.IsOkResult,
    useDot = true,
)

private val packOkResult = FunctionCall(
    name = "Expected",
    connectedNames = listOf("PackOkResult"),
    builtinOperatorId = BuiltinOperatorId.PackOkResult,
)

private object RepackErrResult : CppInlineSupportCode(
    connectedNames = listOf("RepackErrResult"),
    builtinOperatorId = BuiltinOperatorId.RepackErrResult,
) {
    override fun inlineToTree(
        arguments: List<TypedArg<Cpp.Tree>>,
        returnType: Type2,
        translator: CppTranslator,
        cpp: CppBuilder,
    ): Cpp.Tree {
        return cpp.callExpr(
            cpp.name(TEMPER_CORE_NAMESPACE, "Unexpected"),
            cpp.callExpr(cpp.memberExpr(arguments.first().expr as Cpp.Expr, cpp.singleName("error"))),
        )
    }
}

private val unpackOkResult = MethodCall(
    name = "value",
    connectedNames = listOf("UnpackOkResult"),
    builtinOperatorId = BuiltinOperatorId.UnpackOkResult,
    useDot = true,
)

private val connectedReferences = listOf(
    ConsoleLog,
    GetConsole,
    intMax,
    intMin,
    int32ToInt64,
    int64ToInt32Unsafe,
    listedIsEmpty,
    toInt32,
    toInt64,
    toString,
).flatMap { ref -> ref.connectedNames.map { it to ref } }.toMap()
