package lang.temper.be.cpp03

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.cpp.BinaryOpEnum
import lang.temper.be.cpp.Cpp
import lang.temper.be.cpp.CppBuilder
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TypedArg
import lang.temper.common.subListToEnd
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun

object CppSupportNetwork : SupportNetwork {
    override val backendDescription = "C++03 Backend"
    override val bubbleStrategy = BubbleBranchStrategy.IfHandlerScopeVar
    override val coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionalInterface

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
}

private fun supportCodeByOperatorId(builtinOperatorId: BuiltinOperatorId?): SupportCode? = run {
    when (builtinOperatorId) {
        BuiltinOperatorId.DivIntInt, BuiltinOperatorId.DivIntInt64 -> divIntInt
        BuiltinOperatorId.DivIntIntSafe, BuiltinOperatorId.DivIntInt64Safe -> divIntIntSafe
        BuiltinOperatorId.GtIntInt -> gtIntInt
        BuiltinOperatorId.MinusInt, BuiltinOperatorId.MinusInt64 -> minusInt
        BuiltinOperatorId.MinusIntInt, BuiltinOperatorId.MinusIntInt64 -> minusIntInt
        BuiltinOperatorId.ModIntInt, BuiltinOperatorId.ModIntInt64 -> modIntInt
        BuiltinOperatorId.ModIntIntSafe, BuiltinOperatorId.ModIntInt64Safe -> modIntIntSafe
        BuiltinOperatorId.PlusIntInt, BuiltinOperatorId.PlusIntInt64 -> plusIntInt
        BuiltinOperatorId.TimesIntInt, BuiltinOperatorId.TimesIntInt64 -> timesIntInt
        BuiltinOperatorId.StrCat -> strCat
        else -> null
    }
}

private val builtinFunSupportCode: Map<String, CppInlineSupportCode> = mapOf()

open class CppSupportCode(
    val connectedNames: List<String>,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : NamedSupportCode {
    override val baseName = ParsedName(connectedNames.first())
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

internal object ConsoleLog : CppInlineSupportCode("Console::log") {
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

private object GetConsole : CppInlineSupportCode("::getConsole") {
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

// TODO Might need to push custom overloads for too many cat args.
// TODO And for listify, might need to build dynamically with a method call chain.
private val strCat = FunctionCall("cat", listOf("StrCat"))

private val divIntInt = FunctionCall("div_checked", listOf("DivIntInt"), BuiltinOperatorId.DivIntInt)
private val divIntIntSafe = FunctionCall("div", listOf("DivIntIntSafe"), BuiltinOperatorId.DivIntIntSafe)
private val gtIntInt = Infix("GtIntInt", BinaryOpEnum.Gt, BuiltinOperatorId.GtIntInt)
private val intMax = FunctionCall("max", listOf("Int32::max", "Int64::max"), namespace = "std")
private val intMin = FunctionCall("min", listOf("Int32::min", "Int64::min"), namespace = "std")
private val int32ToInt64 = FunctionCall("int64_t", listOf("Int32::toInt64"), namespace = null)
private val int64ToInt32Unsafe = FunctionCall("int32_t", listOf("Int64::toInt32Unsafe"), namespace = null)
private val minusInt = FunctionCall("neg", listOf("MinusInt"), BuiltinOperatorId.MinusInt)
private val minusIntInt = FunctionCall("sub", listOf("MinusIntInt"), BuiltinOperatorId.MinusIntInt)
private val modIntInt = FunctionCall("mod_checked", listOf("ModIntInt"), BuiltinOperatorId.ModIntInt)
private val modIntIntSafe = FunctionCall("mod", listOf("ModIntIntSafe"), BuiltinOperatorId.ModIntIntSafe)
private val plusIntInt = FunctionCall("add", listOf("PlusIntInt"), BuiltinOperatorId.PlusIntInt)
private val timesIntInt = FunctionCall("mul", listOf("TimesIntInt"), BuiltinOperatorId.TimesIntInt)
private val toString = FunctionCall("toString", listOf("Int32::toString", "Int64::toString"))
private val toInt32 = FunctionCall("to_int32", listOf("Int64::toInt32", "String::toInt32"))
private val toInt64 = FunctionCall("to_int64", listOf("String::toInt64"))

private val connectedReferences = listOf(
    ConsoleLog,
    GetConsole,
    intMax,
    intMin,
    int32ToInt64,
    int64ToInt32Unsafe,
    toInt32,
    toInt64,
    toString,
).flatMap { ref -> ref.connectedNames.map { it to ref } }.toMap()
