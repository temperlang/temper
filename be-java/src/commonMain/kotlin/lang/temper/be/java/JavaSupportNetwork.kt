package lang.temper.be.java

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.ConvertedCoroutineAwakeUponFn
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.GetPromiseResultSyncFn
import lang.temper.be.tmpl.GetStaticSupport
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SeparatelyCompiledSupportCode
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.common.subListToEnd
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.DashedIdentifier
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Descriptor
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.withType
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PureVirtual
import lang.temper.be.java.Java as J
import lang.temper.be.java.JavaSimpleType as Jst

class JavaSupportNetwork private constructor(private val javaLang: JavaLang) : SupportNetwork {
    override val backendDescription: String = "Java / JVM Backend"
    override val bubbleStrategy = BubbleBranchStrategy.CatchBubble
    override val coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionType // TODO: rework to use @fun interfaces
    override val mayAssignInBothTryAndRecover = false
    override val needsLabeledBreakFromSwitch = true

    override fun representationOfVoid(genre: Genre): RepresentationOfVoid =
        RepresentationOfVoid.DoNotReifyVoid

    override fun getSupportCode(
        pos: Position,
        builtin: NamedBuiltinFun,
        genre: Genre,
    ): SupportCode? = javaLang.byOpId(builtin.builtinOperatorId)
        ?: when (builtin) {
            GetPromiseResultSyncFn -> javaLang.getPromiseResultSyncSupport
            ConvertedCoroutineAwakeUponFn -> javaLang.convertedCoroutineAwakeUponSupport
            PureVirtual -> javaLang.pureVirtual
            is GetStaticOp -> GetStaticSupport
            else -> null
        }

    private fun JavaLang.byOpId(opId: BuiltinOperatorId?): JavaSupportCode? =
        when (opId) {
            null -> null
            BuiltinOperatorId.Print -> printFunction
            BuiltinOperatorId.IsNull -> isNull
            BuiltinOperatorId.StrCat -> strCatExpr
            BuiltinOperatorId.CmpIntInt -> integerCmp
            BuiltinOperatorId.CmpFltFlt -> doubleCmp
            BuiltinOperatorId.CmpStrStr -> comparableCmp
            BuiltinOperatorId.CmpGeneric -> genericCmp
            BuiltinOperatorId.GtIntInt -> operatorGt
            BuiltinOperatorId.GtFltFlt -> doubleGt
            BuiltinOperatorId.GtStrStr -> comparableGt
            BuiltinOperatorId.GtGeneric -> genericGt
            BuiltinOperatorId.LtIntInt -> operatorLt
            BuiltinOperatorId.LtFltFlt -> doubleLt
            BuiltinOperatorId.LtStrStr -> comparableLt
            BuiltinOperatorId.LtGeneric -> genericLt
            BuiltinOperatorId.GeIntInt -> operatorGe
            BuiltinOperatorId.GeFltFlt -> doubleGe
            BuiltinOperatorId.GeStrStr -> comparableGe
            BuiltinOperatorId.GeGeneric -> genericGe
            BuiltinOperatorId.LeIntInt -> operatorLe
            BuiltinOperatorId.LeFltFlt -> doubleLe
            BuiltinOperatorId.LeStrStr -> comparableLe
            BuiltinOperatorId.LeGeneric -> genericLe
            BuiltinOperatorId.EqIntInt -> operatorEq
            BuiltinOperatorId.EqFltFlt -> doubleEq
            BuiltinOperatorId.EqStrStr -> comparableEq
            BuiltinOperatorId.EqGeneric -> genericEq
            BuiltinOperatorId.NeIntInt -> operatorNe
            BuiltinOperatorId.NeFltFlt -> doubleNe
            BuiltinOperatorId.NeStrStr -> comparableNe
            BuiltinOperatorId.NeGeneric -> genericNe
            BuiltinOperatorId.PlusIntInt, BuiltinOperatorId.PlusIntInt64 -> plusIntInt
            BuiltinOperatorId.PlusFltFlt -> plusDubDub
            BuiltinOperatorId.MinusIntInt, BuiltinOperatorId.MinusIntInt64 -> minusIntInt
            BuiltinOperatorId.MinusFltFlt -> minusDubDub
            BuiltinOperatorId.MinusInt, BuiltinOperatorId.MinusInt64 -> minusInt
            BuiltinOperatorId.MinusFlt -> minusDub
            BuiltinOperatorId.TimesIntInt, BuiltinOperatorId.TimesIntInt64 -> timesIntInt
            BuiltinOperatorId.TimesFltFlt -> timesDubDub
            BuiltinOperatorId.PowFltFlt -> powDubDub
            BuiltinOperatorId.DivIntInt, BuiltinOperatorId.DivIntInt64 -> divIntInt
            BuiltinOperatorId.DivIntIntSafe, BuiltinOperatorId.DivIntInt64Safe -> divIntIntSafe
            BuiltinOperatorId.DivFltFlt -> divDubDub
            BuiltinOperatorId.ModIntInt, BuiltinOperatorId.ModIntInt64 -> modIntInt
            BuiltinOperatorId.ModIntIntSafe, BuiltinOperatorId.ModIntInt64Safe -> modIntIntSafe
            BuiltinOperatorId.ModFltFlt -> modDubDub
            BuiltinOperatorId.BitwiseAnd32,
            BuiltinOperatorId.BitwiseAnd64,
            -> bitwiseAnd
            BuiltinOperatorId.BitwiseOr32,
            BuiltinOperatorId.BitwiseOr64,
            -> bitwiseOr
            BuiltinOperatorId.BitwiseXor32,
            BuiltinOperatorId.BitwiseXor64,
            -> bitwiseXor
            BuiltinOperatorId.BitwiseShl32,
            BuiltinOperatorId.BitwiseShl64,
            -> bitwiseShl
            BuiltinOperatorId.BitwiseShr32,
            BuiltinOperatorId.BitwiseShr64,
            -> bitwiseShr
            BuiltinOperatorId.BitwiseShrUnsigned32,
            BuiltinOperatorId.BitwiseShrUnsigned64,
            -> bitwiseUShr
            BuiltinOperatorId.BitwiseNegation32,
            BuiltinOperatorId.BitwiseNegation64,
            -> bitwiseNegation
            BuiltinOperatorId.BooleanNegation -> booleanNegation
            BuiltinOperatorId.Listify -> listify
            BuiltinOperatorId.Bubble, BuiltinOperatorId.Panic -> throwBubble
            BuiltinOperatorId.AdaptGeneratorFn -> adaptGeneratorFn
            BuiltinOperatorId.SafeAdaptGeneratorFn -> safeAdaptGeneratorFn
            BuiltinOperatorId.Async -> runAsync
            BuiltinOperatorId.NotNull -> TODO("$opId not supported")
        }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    override fun translateConnectedReference(pos: Position, connectedKey: String, genre: Genre): SupportCode? =
        connections[connectedKey]?.invoke(javaLang)

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? =
        translatedConnectedTypeToJavaType(connectedKey, emptyList())
            ?.let { it.withPos(pos) to temperType.bindings }

    fun translatedConnectedTypeToJavaType(connectedKey: String, args: List<JavaTypeArg>): JavaType? =
        when (connectedKey) {
            "std/temporal.type Date" -> javaTimeLocalDate
            "core.type Promise", "core.type PromiseBuilder" -> javaUtilConcurrentCompletableFuture
            "StringBuilder" -> javaLangStringBuilder
            "std/net.type NetResponse" -> temperNetResponse
            else -> null
        }?.let {
            ReferenceType(it, isNullable = false, args = args)
        }

    override fun translateRuntimeTypeOperation(
        pos: Position,
        rto: RuntimeTypeOperation,
        sourceType: TmpL.NominalType,
        targetType: TmpL.NominalType,
    ): SupportCode? {
        if (rto.asLike) {
            when (targetType.typeName.sourceDefinition) {
                WellKnownTypes.noStringIndexTypeDefinition -> return javaLang.requireNoStringIndex
                WellKnownTypes.stringIndexTypeDefinition -> return javaLang.requireStringIndex
                else -> {}
            }
        }
        return super.translateRuntimeTypeOperation(pos, rto, sourceType, targetType)
    }

    companion object {
        private val supportNetworks = JavaLang.entries.associateWith { JavaSupportNetwork(it) }

        internal fun supportFor(javaLang: JavaLang) = supportNetworks.getValue(javaLang)
    }
}

val JavaLang.supportNetwork get() = JavaSupportNetwork.supportFor(this)

sealed class JavaSupportCode(
    val lang: JavaLang,
    final override val baseName: ParsedName,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : NamedSupportCode {
    final override fun equals(other: Any?): Boolean =
        this === other || (other is JavaSupportCode && baseName == other.baseName && lang == other.lang)
    final override fun hashCode(): Int = baseName.hashCode() * 31 + lang.hashCode()

    final override fun renderTo(tokenSink: TokenSink) =
        tokenSink.name(baseName, inOperatorPosition = false)
}

typealias ExprFactory = JavaLang.(pos: Position, args: List<J.Expression>) -> J.Expression
typealias ExprFactoryTyped =
    JavaLang.(pos: Position, args: List<TypedArg<J.Expression>>, type: Type2) -> J.Expression
typealias TreeFactoryTyped =
    JavaLang.(pos: Position, args: List<TypedArg<J.Expression>>, type: Type2) -> J.Tree

open class JavaInlineSupportCode(
    lang: JavaLang,
    baseName: String,
    private val arity: Int,
    builtinOperatorId: BuiltinOperatorId? = null,
    needsSelf: Boolean = false,
    val factory: TreeFactoryTyped? = null,
) : JavaSupportCode(
    lang = lang,
    baseName = ParsedName(baseName),
    builtinOperatorId = builtinOperatorId,
),
    InlineSupportCode<J.Tree, JavaTranslator.ModuleScope> {

    override val needsThisEquivalent: Boolean = needsSelf
    override fun toString(): String = "JavaInlineSupportCode($baseName)"

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<J.Tree>>,
        returnType: Type2,
        translator: JavaTranslator.ModuleScope,
    ): J.Tree =
        if (arity >= 0 && arguments.size != arity) {
            garbageExpr(
                pos,
                "inlineToTree",
                "$baseName expects $arity argument(s) but got ${arguments.joinToString(", ")}",
            )
        } else {
            lang.(factory!!)(
                pos,
                arguments.map {
                    TypedArg(it.expr as J.Expression, it.type)
                },
                returnType,
            )
        }
}

sealed class JavaSeparate(
    lang: JavaLang,
    val qualifiedName: QualifiedName,
    opId: BuiltinOperatorId?,
) : JavaSupportCode(lang = lang, baseName = ParsedName(qualifiedName.fullyQualified), builtinOperatorId = opId),
    SeparatelyCompiledSupportCode {
    override val source: DashedIdentifier get() = DashedIdentifier.temperCoreLibraryIdentifier
    override val stableKey: ParsedName get() = baseName
}

/** Represents a separately compiled static method. */
class JavaSeparateStatic(
    lang: JavaLang,
    qualifiedName: QualifiedName,
    opId: BuiltinOperatorId? = null,
) : JavaSeparate(lang, qualifiedName, opId) {
    override fun toString(): String = "JavaSeparateStatic($baseName)"
}

typealias StaticArgBuilder = (ModuleInfo, Position) -> List<J.Argument>

fun JavaLang.inlineSupport(
    baseName: String,
    arity: Int,
    builtinOperatorId: BuiltinOperatorId? = null,
    needsSelf: Boolean = false,
    factory: ExprFactoryTyped,
) = JavaInlineSupportCode(
    baseName = baseName,
    arity = arity,
    lang = this,
    builtinOperatorId = builtinOperatorId,
    needsSelf = needsSelf,
    factory = factory,
)
fun JavaLang.inlineSupport(
    builtinOperatorId: BuiltinOperatorId,
    arity: Int,
    needsSelf: Boolean = false,
    factory: ExprFactoryTyped,
) = JavaInlineSupportCode(
    baseName = builtinOperatorId.name,
    arity = arity,
    lang = this,
    builtinOperatorId = builtinOperatorId,
    needsSelf = needsSelf,
    factory = factory,
)
fun JavaLang.inlineSupport(
    baseName: String,
    arity: Int,
    builtinOperatorId: BuiltinOperatorId? = null,
    needsSelf: Boolean = false,
    factory: ExprFactory,
) = JavaInlineSupportCode(
    baseName = baseName,
    arity = arity,
    lang = this,
    builtinOperatorId = builtinOperatorId,
    needsSelf = needsSelf,
    factory = { p, a, _ -> factory(p, a.map { it.expr }) },
)
fun JavaLang.inlineSupport(
    builtinOperatorId: BuiltinOperatorId,
    arity: Int,
    needsSelf: Boolean = false,
    factory: ExprFactory,
) = JavaInlineSupportCode(
    baseName = builtinOperatorId.name,
    arity = arity,
    lang = this,
    builtinOperatorId = builtinOperatorId,
    needsSelf = needsSelf,
    factory = { p, a, _ -> factory(p, a.map { it.expr }) },
)

fun JavaLang.separateCode(
    methodName: QualifiedName,
    builtinOperatorId: BuiltinOperatorId? = null,
) = JavaSeparateStatic(
    lang = this,
    qualifiedName = methodName,
    opId = builtinOperatorId,
)

internal fun strongestType(args: List<TypedArg<*>>, resultType: Type2): Jst {
    var out = simpleType(resultType)
    for (arg in args) {
        out = out.strongest(simpleType(arg.type))
    }
    return out
}

private fun Iterable<TypedArg<J.Expression>>.unpackArgs() = map { it.expr.asArgument() }
private fun Iterable<TypedArg<J.Expression>>.unpackExpr() = map { it.expr }

// Relational operations
val JavaLang.genericCmp by receiver {
    inlineSupport(BuiltinOperatorId.CmpGeneric, 2) { pos, args, resultType ->
        val name: QualifiedName = when (strongestType(args, resultType)) {
            Jst.JstBool -> javaLangBooleanCompare
            Jst.JstDouble -> javaLangDoubleCompare
            Jst.JstInt -> javaLangIntegerCompare
            else -> temperGenericCompare
        }
        name.staticMethod(args.unpackArgs(), pos)
    }
}
val JavaLang.integerCmp by receiver { separateCode(javaLangIntegerCompare) }
val JavaLang.doubleCmp by receiver { separateCode(javaLangDoubleCompare) }
val JavaLang.comparableCmp by receiver {
    inlineSupport("comparableCmp", 2) { pos, args ->
        args[0].method("compareTo", args[1], pos = pos)
    }
}

private fun genericRelational(
    op: JavaOperator,
): ExprFactoryTyped =
    { pos, args, resultType ->
        when (strongestType(args, resultType)) {
            Jst.JstVoid -> garbageExpr(pos, "$op", "Unexpected void in argument")
            Jst.JstObject -> op.infix(
                temperGenericCompare.staticMethod(
                    args[0].expr,
                    args[1].expr,
                    pos = pos,
                ),
                J.IntegerLiteral(pos, 0),
                pos = pos,
            )
            Jst.JstBool -> op.infix(
                javaLangBooleanCompare.staticMethod(
                    args[0].expr,
                    args[1].expr,
                    pos = pos,
                ),
                J.IntegerLiteral(pos, 0),
                pos = pos,
            )
            Jst.JstDouble -> doubleRelational(op, pos, args.unpackExpr())
            Jst.JstInt, Jst.JstLong -> operatorRelational(op)(pos, args.unpackExpr())
        }
    }
val JavaLang.genericGt by receiver {
    inlineSupport(BuiltinOperatorId.GtGeneric, 2, factory = genericRelational(JavaOperator.GreaterThan))
}
val JavaLang.genericGe by receiver {
    inlineSupport(BuiltinOperatorId.GtGeneric, 2, factory = genericRelational(JavaOperator.GreaterEquals))
}
val JavaLang.genericLt by receiver {
    inlineSupport(BuiltinOperatorId.GtGeneric, 2, factory = genericRelational(JavaOperator.LessThan))
}
val JavaLang.genericLe by receiver {
    inlineSupport(BuiltinOperatorId.GtGeneric, 2, factory = genericRelational(JavaOperator.LessEquals))
}
val JavaLang.genericEq by receiver {
    inlineSupport(BuiltinOperatorId.EqGeneric, 2) { pos, args, resultType ->
        when (strongestType(args, resultType)) {
            Jst.JstVoid -> garbageExpr(pos, "genericEq", "unexpected void in argument")
            Jst.JstObject -> javaUtilObjectsEquals.staticMethod(args.unpackArgs(), pos = pos)
            Jst.JstDouble -> doubleRelational(JavaOperator.Equals, pos, args.unpackExpr())
            Jst.JstInt, Jst.JstLong, Jst.JstBool -> operatorRelational(JavaOperator.Equals)(pos, args.unpackExpr())
        }
    }
}
val JavaLang.genericNe by receiver {
    inlineSupport(BuiltinOperatorId.NeGeneric, 2) { pos, args, resultType ->
        when (strongestType(args, resultType)) {
            Jst.JstVoid -> garbageExpr(pos, "genericNe", "unexpected void in argument")
            Jst.JstObject -> JavaOperator.BoolComplement.prefix(
                javaUtilObjectsEquals.staticMethod(args.unpackArgs(), pos = pos),
            )
            Jst.JstDouble -> doubleRelational(JavaOperator.NotEquals, pos, args.unpackExpr())
            Jst.JstInt, Jst.JstLong, Jst.JstBool ->
                operatorRelational(JavaOperator.NotEquals)(pos, args.unpackExpr())
        }
    }
}

private fun operatorRelational(
    op: JavaOperator,
): ExprFactory =
    { pos, args ->
        op.infix(args[0], args[1], pos = pos)
    }
val JavaLang.operatorGt by receiver {
    inlineSupport("operatorGt", 2, factory = operatorRelational(JavaOperator.GreaterThan))
}
val JavaLang.operatorGe by receiver {
    inlineSupport("operatorGt", 2, factory = operatorRelational(JavaOperator.GreaterEquals))
}
val JavaLang.operatorLt by receiver {
    inlineSupport("operatorLt", 2, factory = operatorRelational(JavaOperator.LessThan))
}
val JavaLang.operatorLe by receiver {
    inlineSupport("operatorLe", 2, factory = operatorRelational(JavaOperator.LessEquals))
}

private fun operatorEquality(pos: Position, args: List<TypedArg<J.Expression>>): J.Expression {
    val null0 = args[0].isNullable
    val null1 = args[1].isNullable
    return when {
        !null0 && !null1 -> JavaOperator.Equals.infix(args[0].expr, args[1].expr, pos = pos)
        null0 && null1 -> javaUtilObjectsEquals.staticMethod(args.unpackArgs(), pos = pos)
        null0 -> temperBoxedEq.staticMethod(args.unpackArgs(), pos = pos)
        else -> temperBoxedEqRev.staticMethod(args.unpackArgs(), pos = pos)
    }
}
val JavaLang.operatorEq by receiver {
    inlineSupport("operatorEq", 2) { pos, args, _ ->
        operatorEquality(pos, args)
    }
}
val JavaLang.operatorNe by receiver {
    inlineSupport("operatorNe", 2) { pos, args, _ ->
        simplifiedComplement(operatorEquality(pos, args))
    }
}

private fun doubleRelational(
    op: JavaOperator,
    pos: Position,
    args: List<J.Expression>,
): J.Expression {
    return op.infix(
        javaLangDoubleToLongBits.staticMethod(
            args[0],
            pos = pos,
        ),
        javaLangDoubleToLongBits.staticMethod(
            args[1],
            pos = pos,
        ),
    )
}
val JavaLang.doubleGt by receiver {
    inlineSupport("doubleGt", 2) { pos, args ->
        doubleRelational(JavaOperator.GreaterThan, pos, args)
    }
}
val JavaLang.doubleGe by receiver {
    inlineSupport("doubleGe", 2) { pos, args ->
        doubleRelational(JavaOperator.GreaterEquals, pos, args)
    }
}
val JavaLang.doubleLt by receiver {
    inlineSupport("doubleLt", 2) { pos, args ->
        doubleRelational(JavaOperator.LessThan, pos, args)
    }
}
val JavaLang.doubleLe by receiver {
    inlineSupport("doubleLe", 2) { pos, args ->
        doubleRelational(JavaOperator.LessEquals, pos, args)
    }
}
private fun doubleEquality(pos: Position, args: List<TypedArg<J.Expression>>): J.Expression {
    val null0 = args[0].isNullable
    val null1 = args[1].isNullable
    return when {
        !null0 && !null1 -> doubleRelational(JavaOperator.Equals, pos, args.unpackExpr())
        null0 && null1 -> javaUtilObjectsEquals.staticMethod(args.unpackArgs(), pos = pos)
        null0 -> temperBoxedEq.staticMethod(args.unpackArgs(), pos = pos)
        else -> temperBoxedEqRev.staticMethod(args.unpackArgs(), pos = pos)
    }
}
val JavaLang.doubleEq by receiver {
    inlineSupport("doubleEq", 2) { pos, args, _ ->
        doubleEquality(pos, args)
    }
}
val JavaLang.doubleNe by receiver {
    inlineSupport("doubleNe", 2) { pos, args, _ ->
        simplifiedComplement(doubleEquality(pos, args))
    }
}

private fun comparableRelational(
    op: JavaOperator,
): ExprFactory =
    { pos, args ->
        op.infix(args[0].method("compareTo", args[1], pos = pos), J.IntegerLiteral(pos, 0))
    }
val JavaLang.comparableGt by receiver {
    inlineSupport("comparableGt", 2, factory = comparableRelational(JavaOperator.GreaterThan))
}
val JavaLang.comparableGe by receiver {
    inlineSupport("comparableGe", 2, factory = comparableRelational(JavaOperator.GreaterEquals))
}
val JavaLang.comparableLt by receiver {
    inlineSupport("comparableLt", 2, factory = comparableRelational(JavaOperator.LessThan))
}
val JavaLang.comparableLe by receiver {
    inlineSupport("comparableLe", 2, factory = comparableRelational(JavaOperator.LessEquals))
}
private fun comparableEquality(pos: Position, args: List<TypedArg<J.Expression>>): J.Expression =
    if (!args[0].isNullable) {
        args[0].expr.method("equals", args[1].expr, pos = pos)
    } else {
        javaUtilObjectsEquals.staticMethod(args.unpackArgs(), pos = pos)
    }
val JavaLang.comparableEq by receiver {
    inlineSupport("comparableEq", 2) { pos, args, _ ->
        comparableEquality(pos, args)
    }
}
val JavaLang.comparableNe by receiver {
    inlineSupport("comparableNe", 2) { pos, args, _ ->
        simplifiedComplement(comparableEquality(pos, args))
    }
}

// Miscellany

/** Just be yourself. */
val JavaLang.identity by receiver { inlineSupport("identity", 1) { _, args -> args[0] } }

val JavaLang.isNull by receiver {
    inlineSupport("isNull", 1, BuiltinOperatorId.IsNull) { pos, args ->
        J.InfixExpr(
            pos,
            args[0],
            J.Operator(pos.rightEdge, JavaOperator.Equals),
            J.NullLiteral(pos.rightEdge),
        )
    }
}

/** A placeholder to help detect if a method should be marked abstract; see [isPureVirtual] */
val JavaLang.pureVirtual by receiver { separateCode(temperPureVirtual) }

/** Throws Bubble, but may be used in an arbitrary expression. */
val JavaLang.throwBubble by receiver { separateCode(temperThrowBubble) }

/** Builds a Generator from a lambda. */
val JavaLang.adaptGeneratorFn by receiver { separateCode(temperAdaptGeneratorFn) }

/** Builds a Generator from a lambda. */
val JavaLang.safeAdaptGeneratorFn by receiver { separateCode(temperSafeAdaptGeneratorFn) }

// Typed arithmetic
val JavaLang.booleanNegation by receiver {
    inlineSupport(BuiltinOperatorId.BooleanNegation, 1) { pos, args ->
        simplifiedComplement(args[0], pos = pos)
    }
}
val JavaLang.plusIntInt by receiver {
    inlineSupport(BuiltinOperatorId.PlusIntInt, 2) { pos, args ->
        JavaOperator.Addition.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.plusDubDub by receiver {
    inlineSupport(BuiltinOperatorId.PlusFltFlt, 2) { pos, args ->
        JavaOperator.Addition.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.minusIntInt by receiver {
    inlineSupport(BuiltinOperatorId.MinusIntInt, 2) { pos, args ->
        JavaOperator.Subtraction.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.minusDubDub by receiver {
    inlineSupport(BuiltinOperatorId.MinusFltFlt, 2) { pos, args ->
        JavaOperator.Subtraction.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.minusInt by receiver {
    inlineSupport(BuiltinOperatorId.MinusInt, 1) { pos, args ->
        JavaOperator.Minus.prefix(args[0], pos = pos)
    }
}
val JavaLang.minusDub by receiver {
    inlineSupport(BuiltinOperatorId.MinusFlt, 1) { pos, args ->
        JavaOperator.Minus.prefix(args[0], pos = pos)
    }
}
val JavaLang.timesIntInt by receiver {
    inlineSupport(BuiltinOperatorId.TimesIntInt, 2) { pos, args ->
        JavaOperator.Multiplication.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.timesDubDub by receiver {
    inlineSupport(BuiltinOperatorId.TimesFltFlt, 2) { pos, args ->
        JavaOperator.Multiplication.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.powDubDub by receiver {
    inlineSupport(BuiltinOperatorId.PowFltFlt, 2) { pos, args ->
        javaMathPow.staticMethod(args[0], args[1], pos = pos)
    }
}
val JavaLang.divIntInt by receiver { separateCode(temperDivIntInt, BuiltinOperatorId.DivIntInt) }
val JavaLang.divIntIntSafe by receiver {
    inlineSupport(BuiltinOperatorId.DivIntIntSafe, 2) { pos, args ->
        JavaOperator.Division.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.divDubDub by receiver {
    inlineSupport(BuiltinOperatorId.DivFltFlt, 2) { pos, args ->
        JavaOperator.Division.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.modIntInt by receiver { separateCode(temperModIntInt, BuiltinOperatorId.ModIntInt) }
val JavaLang.modIntIntSafe by receiver {
    inlineSupport(BuiltinOperatorId.ModIntIntSafe, 2) { pos, args ->
        JavaOperator.Remainder.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.modDubDub by receiver {
    inlineSupport(BuiltinOperatorId.ModFltFlt, 2) { pos, args ->
        JavaOperator.Remainder.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseAnd by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseAnd32, 2) { pos, args ->
        JavaOperator.And.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseOr by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseOr32, 2) { pos, args ->
        JavaOperator.InclusiveOr.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseXor by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseXor32, 2) { pos, args ->
        JavaOperator.ExclusiveOr.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseNegation by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseNegation32, 1) { pos, args ->
        JavaOperator.BitwiseComplement.prefix(args[0], pos = pos)
    }
}
val JavaLang.bitwiseShl by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseShl32, 2) { pos, args ->
        JavaOperator.LeftShift.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseShr by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseShr32, 2) { pos, args ->
        JavaOperator.RightShift.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseUShr by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseShrUnsigned32, 2) { pos, args ->
        JavaOperator.LogicalRightShift.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.booleanToString by receiver {
    inlineSupport("core.type Boolean.toString()", 1, needsSelf = true) { pos, args ->
        javaLangBooleanToString.staticMethod(listOf(args[0].asArgument()), pos = pos)
    }
}
val JavaLang.intToFloat64 by receiver {
    inlineSupport("core.type Int32.toFloat64()", -1, needsSelf = true) { pos, args ->
        Primitive.JavaDouble.cast(args[0], pos)
    }
}
val JavaLang.intToInt64 by receiver {
    inlineSupport("core.type Int32.toInt64()", -1, needsSelf = true) { pos, args ->
        Primitive.JavaLong.cast(args[0], pos)
    }
}
val JavaLang.intToString by receiver {
    inlineSupport("core.type Int32.toString()", -1, needsSelf = true) { pos, args ->
        javaLangIntegerToString.staticMethod(args.map(J.Expression::asArgument), pos = pos)
    }
}
val JavaLang.int64ToFloat64 by receiver { separateCode(temperInt64ToFloat64) }
val JavaLang.int64ToFloat64Unsafe by receiver {
    inlineSupport("core.type Int64.toFloat64Unsafe()", -1, needsSelf = true) { pos, args ->
        Primitive.JavaDouble.cast(args[0], pos)
    }
}
val JavaLang.int64ToInt32 by receiver { separateCode(temperInt64ToInt) }
val JavaLang.int64ToInt32Unsafe by receiver {
    inlineSupport("core.type Int64.toInt32Unsafe()", -1, needsSelf = true) { pos, args ->
        Primitive.JavaInt.cast(args[0], pos)
    }
}
val JavaLang.int64ToString by receiver {
    inlineSupport("core.type Int64.toString()", -1, needsSelf = true) { pos, args ->
        javaLangLongToString.staticMethod(args.map(J.Expression::asArgument), pos = pos)
    }
}
val JavaLang.float64E by receiver {
    inlineSupport("core.type Float64.e", 0) { pos, _ -> javaMathE.toNameExpr(pos) }
}
val JavaLang.float64Pi by receiver {
    inlineSupport("core.type Float64.pi", 0) { pos, _ -> javaMathPi.toNameExpr(pos) }
}
val JavaLang.float64Abs by receiver {
    inlineSupport("core.type Float64.abs()", 1) { pos, args -> javaMathAbs.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Acos by receiver {
    inlineSupport("core.type Float64.acos()", 1) { pos, args -> javaMathAcos.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Asin by receiver {
    inlineSupport("core.type Float64.asin()", 1) { pos, args -> javaMathAsin.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Atan by receiver {
    inlineSupport("core.type Float64.atan()", 1) { pos, args -> javaMathAtan.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Atan2 by receiver {
    inlineSupport("core.type Float64.atan2()", 2) { pos, args -> javaMathAtan2.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.float64Ceil by receiver {
    inlineSupport("core.type Float64.ceil()", 1) { pos, args -> javaMathCeil.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Cos by receiver {
    inlineSupport("core.type Float64.cos()", 1) { pos, args -> javaMathCos.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Cosh by receiver {
    inlineSupport("core.type Float64.cosh()", 1) { pos, args -> javaMathCosh.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Exp by receiver {
    inlineSupport("core.type Float64.exp()", 1) { pos, args -> javaMathExp.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Expm1 by receiver {
    inlineSupport("core.type Float64.expm1()", 1) { pos, args -> javaMathExpm1.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Floor by receiver {
    inlineSupport("core.type Float64.floor()", 1) { pos, args -> javaMathFloor.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Log by receiver {
    inlineSupport("core.type Float64.log()", 1) { pos, args -> javaMathLog.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Log10 by receiver {
    inlineSupport("core.type Float64.log10()", 1) { pos, args -> javaMathLog10.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Log1p by receiver {
    inlineSupport("core.type Float64.log1p()", 1) { pos, args -> javaMathLog1p.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Max by receiver {
    inlineSupport("core.type Float64.max()", 2) { pos, args -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.float64Min by receiver {
    inlineSupport("core.type Float64.min()", 2) { pos, args -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.float64Near by receiver { separateCode(temperFloat64Near) }
val JavaLang.float64Round by receiver {
    inlineSupport("core.type Float64.round()", 1) { pos, args -> javaMathRound.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sign by receiver {
    inlineSupport("core.type Float64.sign()", 1) { pos, args -> javaMathSignum.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sin by receiver {
    inlineSupport("core.type Float64.sin()", 1) { pos, args -> javaMathSin.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sinh by receiver {
    inlineSupport("core.type Float64.sinh()", 1) { pos, args -> javaMathSinh.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sqrt by receiver {
    inlineSupport("core.type Float64.sqrt()", 1) { pos, args -> javaMathSqrt.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Tan by receiver {
    inlineSupport("core.type Float64.tan()", 1) { pos, args -> javaMathTan.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Tanh by receiver {
    inlineSupport("core.type Float64.tanh()", 1) { pos, args -> javaMathTanh.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64ToInt by receiver { separateCode(temperFloat64ToInt) }
val JavaLang.float64ToIntUnsafe by receiver {
    inlineSupport("core.type Float64.toInt32Unsafe()", -1, needsSelf = true) { pos, args ->
        Primitive.JavaInt.cast(args[0], pos)
    }
}
val JavaLang.float64ToInt64 by receiver { separateCode(temperFloat64ToInt64) }
val JavaLang.float64ToInt64Unsafe by receiver {
    inlineSupport("core.type Float64.toInt64Unsafe()", -1, needsSelf = true) { pos, args ->
        Primitive.JavaLong.cast(args[0], pos)
    }
}
val JavaLang.float64ToString by receiver { separateCode(temperFloat64ToString) }
val JavaLang.genericIsEmpty by receiver {
    inlineSupport("*::isEmpty", 1, needsSelf = true) { pos, args ->
        args[0].method("isEmpty", pos = pos)
    }
}
val JavaLang.intMax by receiver {
    inlineSupport("core.type Int32.max()", 2) { pos, args -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.intMin by receiver {
    inlineSupport("core.type Int32.min()", 2) { pos, args -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.int64Max by receiver {
    inlineSupport("core.type Int64.max()", 2) { pos, args -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.int64Min by receiver {
    inlineSupport("core.type Int64.min()", 2) { pos, args -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}

// String operations
val JavaLang.strCatExpr by receiver {
    inlineSupport("strcat", -1, BuiltinOperatorId.StrCat) { pos, args ->
        when (args.size) {
            0 -> J.StringLiteral(pos, "")
            else -> args.subListToEnd(1).fold(args[0]) {
                    a, b ->
                JavaOperator.Addition.infix(a, b)
            }
        }
    }
}
val JavaLang.stringFromCodePoint by receiver { separateCode(temperStringFromCodePoint) }
val JavaLang.stringFromCodePoints by receiver { separateCode(temperStringFromCodePoints) }
val JavaLang.stringSplit by receiver { separateCode(temperStringSplit) }
val JavaLang.stringToFloat64 by receiver { separateCode(temperStringToFloat64) }
val JavaLang.stringToInt by receiver { separateCode(temperStringToInt) }
val JavaLang.stringToInt64 by receiver { separateCode(temperStringToInt64) }
val JavaLang.stringEnd by receiver {
    inlineSupport("core.type String.get end()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("length", pos = pos)
    }
}
val JavaLang.stringBegin by receiver {
    inlineSupport("core.type String.begin", 0) { pos, _ ->
        J.IntegerLiteral(pos, 0)
    }
}
val JavaLang.stringIndexNone by receiver {
    inlineSupport("core.type StringIndex.none", 0) { pos, _ ->
        J.IntegerLiteral(pos, -1)
    }
}
val JavaLang.stringGet by receiver {
    inlineSupport("core.type String.get()", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("codePointAt", args[1], pos = pos)
    }
}
val JavaLang.stringCountBetween by receiver { separateCode(temperStringCountBetween) }
val JavaLang.stringForEach by receiver { separateCode(temperStringForEach) }
val JavaLang.stringHasAtLeast by receiver { separateCode(temperStringHasAtLeast) }
val JavaLang.stringHasIndex by receiver { separateCode(temperStringHasIndex) }
val JavaLang.stringNext by receiver { separateCode(temperStringNext) }
val JavaLang.stringPrev by receiver { separateCode(temperStringPrev) }
val JavaLang.stringStep by receiver { separateCode(temperStringStep) }
val JavaLang.stringSlice by receiver { separateCode(temperStringSlice) }
val JavaLang.stringBuilderConstructor by receiver {
    inlineSupport("core.type StringBuilder.constructor()", arity = -1) { pos, _ ->
        J.InstanceCreationExpr(pos, type = javaLangStringBuilder.toClassType(pos), args = emptyList())
    }
}
val JavaLang.stringBuilderAppend by receiver {
    inlineSupport("core.type StringBuilder.append()", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("append", args[1], pos = pos)
    }
}
val JavaLang.stringBuilderAppendBetween by receiver {
    separateCode(temperStringBuilderAppendBetween)
}
val JavaLang.stringBuilderAppendCodePoint by receiver {
    separateCode(temperStringBuilderAppendCodePoint)
}
val JavaLang.stringBuilderClear by receiver {
    inlineSupport("core.type StringBuilder.clear()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("setLength", J.IntegerLiteral(pos.rightEdge, 0), pos = pos)
    }
}
val JavaLang.stringBuilderEnd by receiver {
    inlineSupport("core.type StringBuilder.end()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("length", pos = pos)
    }
}
val JavaLang.stringBuilderToString by receiver {
    inlineSupport("core.type StringBuilder.toString()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("toString", pos = pos)
    }
}
val JavaLang.stringIndexOptionCompareTo by receiver {
    separateCode(javaLangIntegerCompare)
}
private fun JavaLang.comparison(baseName: String, operator: JavaOperator): JavaInlineSupportCode =
    inlineSupport(baseName, arity = 2, needsSelf = true) { pos, (a, b) ->
        J.InfixExpr(pos, a, J.Operator(pos.leftEdge, operator), b)
    }
val JavaLang.stringIndexOptionCompareToEq by receiver {
    comparison("core.type StringIndexOption.compareTo()::eq", JavaOperator.Equals)
}
val JavaLang.stringIndexOptionCompareToGe by receiver {
    comparison("core.type StringIndexOption.compareTo()::ge", JavaOperator.GreaterEquals)
}
val JavaLang.stringIndexOptionCompareToGt by receiver {
    comparison("core.type StringIndexOption.compareTo()::gt", JavaOperator.GreaterThan)
}
val JavaLang.stringIndexOptionCompareToLe by receiver {
    comparison("core.type StringIndexOption.compareTo()::le", JavaOperator.LessEquals)
}
val JavaLang.stringIndexOptionCompareToLt by receiver {
    comparison("core.type StringIndexOption.compareTo()::lt", JavaOperator.LessThan)
}
val JavaLang.stringIndexOptionCompareToNe by receiver {
    comparison("core.type StringIndexOption.compareTo()::ne", JavaOperator.NotEquals)
}
val JavaLang.requireNoStringIndex by receiver {
    separateCode(temperRequireNoStringIndex)
}
val JavaLang.requireStringIndex by receiver {
    separateCode(temperRequireStringIndex)
}

// Regex support
val JavaLang.regexFormat by receiver { separateCode(temperRegexFormat) }
val JavaLang.regexCompiledFormatted by receiver { separateCode(temperRegexCompiledFormatted) }
val JavaLang.regexCompiledFind by receiver { separateCode(temperRegexCompiledFind) }
val JavaLang.regexCompiledFound by receiver { separateCode(temperRegexCompiledFound) }
val JavaLang.regexCompiledReplace by receiver { separateCode(temperRegexCompiledReplace) }
val JavaLang.regexCompiledSplit by receiver { separateCode(temperRegexCompiledSplit) }
val JavaLang.regexFormatterPushCodeTo by receiver { separateCode(temperRegexFormatterPushCodeTo) }

// Temporal support
val JavaLang.dateConstructor by receiver {
    inlineSupport("std/temporal.type Date.constructor()", arity = 3) { pos, args ->
        // docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html#of-int-int-int-
        javaTimeLocalDateOf.staticMethod(args[0], args[1], args[2], pos = pos)
    }
}
val JavaLang.dateToString by receiver {
    inlineSupport("std/temporal.type Date.toString()", arity = 1) { pos, args ->
        args[0].method("toString", pos = pos)
    }
}
val JavaLang.dateGetYear by receiver {
    inlineSupport("std/temporal.type Date.year", arity = 1, needsSelf = true) { pos, args ->
        // LocalDate.getYear returns a proleptic year.  2 BC and before are negative.
        args[0].method("getYear", pos = pos)
    }
}
val JavaLang.dateGetMonth by receiver {
    inlineSupport("std/temporal.type Date.month", arity = 1, needsSelf = true) { pos, args ->
        // LocalDate.getMonth returns an instance of the Month enumeration
        // .getMonthValue returns an int.
        args[0].method("getMonthValue", pos = pos)
    }
}
val JavaLang.dateGetDay by receiver {
    inlineSupport("std/temporal.type Date.day", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("getDayOfMonth", pos = pos)
    }
}

val JavaLang.dateGetDayOfWeek by receiver {
    inlineSupport("std/temporal.type Date.get dayOfWeek()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("getDayOfWeek", pos = pos)
            .method("getValue", pos = pos.rightEdge)
    }
}
val JavaLang.dateFromIsoString by receiver {
    inlineSupport("std/temporal.type Date.fromIsoString()", arity = 1) { pos, args ->
        javaTimeLocalDateParse.staticMethod(args[0], pos = pos)
    }
}

val JavaLang.dateToday by receiver {
    inlineSupport("std/temporal.type Date.today()", arity = 0, needsSelf = false) { pos, _ ->
        // java.time.ZoneId.ofOffset("UTC", java.time.ZoneOffset.UTC)
        val rightEdge = pos.rightEdge
        javaTimeLocalDateNow.staticMethod(
            javaTimeZoneIdOfOffset.staticMethod(
                J.StringLiteral(rightEdge, "UTC"),
                javaTimeZoneOffsetUtc.toNameExpr(rightEdge),
                pos = rightEdge,
            ),
            pos = pos,
        )
    }
}

val JavaLang.dateYearsBetween by receiver {
    inlineSupport("std/temporal.type Date.yearsBetween()", arity = 2, needsSelf = false) { pos, args ->
        J.CastExpr(
            // ChronoUnit.between returns a long because you might be asking about nanoseconds.
            // Here, we're asking about years which fit in 31b.
            pos,
            J.PrimitiveType(pos.leftEdge, Primitive.JavaInt),
            javaTimeTemporalChronoUnitYears.staticField(pos = pos)
                .method(
                    methodName = "between",
                    args = args.map { J.Argument(it.pos, it) },
                    pos = pos,
                ),
        )
    }
}

// Promise support
val JavaLang.promiseBuilderBreakPromise by receiver {
    inlineSupport("core.type PromiseBuilder.breakPromise()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method(
            pos = pos,
            methodName = "completeExceptionally",
            args = listOf(
                J.Argument(pos, temperBubbleMethod.staticMethod(listOf(), pos)),
            ),
        )
    }
}
val JavaLang.promiseBuilderComplete by receiver {
    inlineSupport("core.type PromiseBuilder.complete()", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("complete", args[1], pos = pos)
    }
}
val JavaLang.promiseBuilderGetPromise by receiver {
    inlineSupport("core.type PromiseBuilder.get promise()", arity = 1, needsSelf = true) { _, args ->
        // PromiseBuilder and Promise both connect to CompletableFuture, so
        // `myPromiseBuilder.getPromise()` is just `myPromiseBuilder`.
        args[0]
    }
}

// Testing support
val JavaLang.bail by receiver {
    inlineSupport("std/testing.type Test.bail()", arity = 1) { pos, args ->
        temperThrowAssertionError.staticMethod(args[0].method("messagesCombined"), pos = pos)
    }
}

val JavaLang.printFunction by receiver { separateCode(temperPrint, BuiltinOperatorId.Print) }
val JavaLang.getConsole by receiver {
    object : JavaInlineSupportCode(this, "core.getConsole()", arity = -1) {
        override fun inlineToTree(
            pos: Position,
            arguments: List<TypedArg<J.Tree>>,
            returnType: Type2,
            translator: JavaTranslator.ModuleScope,
        ): J.Tree {
            val loggerName = when {
                arguments.isEmpty() -> J.StringLiteral(
                    pos,
                    translator.moduleInfo.packageName.parts.joinToString(".") { it.outputNameText },
                )
                else -> arguments.first().expr as J.Expression
            }
            val logger = javaUtilLoggingLoggerGetLogger.staticMethod(loggerName, pos = pos)
            return temperGetConsoleMethod.staticMethod(logger, pos = pos)
        }
    }
}
val JavaLang.doNothing by receiver { separateCode(temperDoNothing) }

val JavaLang.empty by receiver {
    inlineSupport("core.empty()", arity = 0) { pos, _ ->
        javaUtilOptionalEmpty.staticMethod(emptyList(), pos)
    }
}

// Dense bit vectors
val JavaLang.denseBitVectorConstructor by receiver {
    inlineSupport("core.type DenseBitVector.constructor()", arity = -1) { pos, args ->
        J.InstanceCreationExpr(pos, type = javaUtilBitSet.toClassType(pos), args = args.map(J.Expression::asArgument))
    }
}
val JavaLang.denseBitVectorGet by receiver {
    inlineSupport("core.type DenseBitVector.get()", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("get", args[1], pos = pos)
    }
}
val JavaLang.denseBitVectorSet by receiver {
    inlineSupport("core.type DenseBitVector.set()", arity = 3, needsSelf = true) { pos, args ->
        args[0].method("set", args[1], args[2], pos = pos)
    }
}

// Deques
val JavaLang.dequeConstructor by receiver {
    inlineSupport("core.type Deque.constructor()", arity = -1) { pos, args, resultType ->
        val implementation = if (resultType.hasNullableTypeActual) javaUtilLinkedList else javaUtilArrayDeque
        J.InstanceCreationExpr(
            pos,
            implementation.toClassType(pos, args = J.TypeArguments(pos)),
            args = args.map { it.expr.asArgument() },
        )
    }
}
val JavaLang.dequeAdd by receiver {
    inlineSupport("core.type Deque.add()", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("addLast", args[1], pos = pos)
    }
}
val JavaLang.dequeRemoveFirst by receiver { separateCode(temperDequeRemoveFirst) }

// Listed, List, ListBuilder
val JavaLang.listify: JavaSupportCode by receiver {
    if (atLeastJdk(JAVA9)) {
        // The Java immutable collections API, unfortunately, does not allow null elements.
        inlineSupport("listify", arity = -1) { pos, args, resultType ->
            val implementation = if (resultType.hasNullableTypeActual) temperListOf else javaUtilListOf
            implementation.staticMethod(args.unpackArgs(), pos)
        }
    } else {
        separateCode(temperListOf)
    }
}

// Generator support
val JavaLang.generatorNext by receiver {
    inlineSupport("core.type Generator.next()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("get", pos = pos)
    }
}

val JavaLang.doneResult by receiver {
    separateCode(temperGeneratorDoneResultGet)
}

// Async support
val JavaLang.runAsync by receiver { separateCode(temperRunAsync) }

// std/net support
val JavaLang.netCoreStdNetSend by receiver { separateCode(temperNetCoreStdNetSend) }

/** Get the input and output types of a simple lambda. */
private fun functionSimpleArgumentTypes(descriptor: Descriptor, inputIndex: Int = 0): Pair<Jst, Jst> {
    var input: Jst = Jst.JstObject
    var output: Jst = Jst.JstObject
    val sig = when (descriptor) {
        is Signature2 -> descriptor
        is Type2 -> withType(
            descriptor,
            fallback = { null },
            fn = { _, sig, _ -> sig },
        )
    }
    if (sig != null) {
        val jsig = signature(sig)
        output = jsig.returnType
        if (inputIndex < jsig.formals.size) {
            input = jsig.formals[inputIndex]
        }
    }
    return input to output
}

val JavaLang.listFilter by receiver {
    inlineSupport("core.type List.filter()", 2, needsSelf = true) { pos, args, _ ->
        // listFilter(0=List<T>, 1=fun (T): Boolean)
        val sourceType: Jst = functionSimpleArgumentTypes(args[1].type).first
        temperListFilter.suffix(sourceType.shortCamelName).staticMethod(args.unpackArgs(), pos)
    }
}

@Suppress("MagicNumber") // arity
val JavaLang.listJoin by receiver {
    inlineSupport("core.type List.join()", 3, needsSelf = true) { pos, args, _ ->
        // listJoin(0=List<T>, 1=delimiter, 2=fun (T): String)
        val sourceType: Jst = functionSimpleArgumentTypes(args[2].type).first
        temperListJoin.suffix(sourceType.shortCamelName).staticMethod(args.unpackArgs(), pos)
    }
}
val JavaLang.listMap by receiver {
    inlineSupport("core.type List.map()", 2, needsSelf = true) { pos, args, _ ->
        // listMap(0=List<T>, 1=fun (T): U)
        val (inType, outType) = functionSimpleArgumentTypes(args[1].type)
        val fromType = when (val name = inType.shortCamelName) {
            "Bool", "Long" -> "Obj"
            else -> name
        }
        val toType = outType.shortCamelName
        temperListMap.suffix("${fromType}To${toType}")
            .staticMethod(args.unpackArgs(), pos)
    }
}
val JavaLang.listedReduce by receiver {
    inlineSupport("core.type Listed.reduce()", 2, needsSelf = true) inline@{ pos, args, _ ->
        // listedReduce(0=List<T>, 1=fun (T, T): T)
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "core.type Listed.reduce()", "$args")
        val type = functionSimpleArgumentTypes(fnType).first
        // See `fun simpleType` for expected names.
        temperListedReduce.suffix(type.shortCamelName)
            .staticMethod(adjustedArgs, pos)
    }
}
val JavaLang.listedReduceFrom by receiver {
    @Suppress("MagicNumber")
    inlineSupport("core.type Listed.reduceFrom()", 3, needsSelf = true) inline@{ pos, args, _ ->
        // listedReduce(0=List<T>, 1=U, 2=fun (U, T): U)
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "core.type Listed.reduceFrom()", "$args")
        val (inType, outType) = functionSimpleArgumentTypes(fnType, inputIndex = 1)
        temperListedReduce.suffix("${inType.shortCamelName}To${outType.shortCamelName}")
            .staticMethod(adjustedArgs, pos)
    }
}
val JavaLang.listSlice by receiver { separateCode(temperListSlice) }
val JavaLang.listSorted by receiver {
    // TODO This could potentially be factored along with core.type ListBuilder.sort().
    inlineSupport("core.type Listed.sorted()", 2, needsSelf = true) inline@{ pos, args, _ ->
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "core.type Listed.sorted()", "$args")
        val (inType, _) = functionSimpleArgumentTypes(fnType)
        when (inType) {
            Jst.JstInt -> temperListSorted.suffix(inType.shortCamelName).staticMethod(args.unpackArgs(), pos)
            else -> temperListSorted.staticMethod(adjustedArgs, pos)
        }
    }
}
val JavaLang.listGet by receiver { separateCode(temperListGet) }
val JavaLang.listGetOr by receiver { separateCode(temperListGetOr) }
val JavaLang.listLength by receiver {
    inlineSupport("core.type List.get length()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("size", pos = pos)
    }
}
val JavaLang.listCopyOf by receiver {
    if (atLeastJdk(JAVA9)) {
        inlineSupport("listCopyOf", arity = 1) { pos, args, resultType ->
            val implementation = if (resultType.hasNullableTypeActual) temperListCopyOf else javaUtilListCopyOf
            implementation.staticMethod(args.unpackArgs(), pos)
        }
    } else {
        separateCode(temperListCopyOf)
    }
}
val JavaLang.listedToList by receiver { separateCode(temperListedToList) }
val JavaLang.listBuilderMake by receiver {
    inlineSupport("core.type ListBuilder.constructor()", arity = 0) { pos, _ ->
        J.InstanceCreationExpr(pos, javaUtilArrayList.toClassType(pos, args = J.TypeArguments(pos)), args = listOf())
    }
}
val JavaLang.listBuilderAdd by receiver { separateCode(temperListAdd) }
val JavaLang.listBuilderAddAll by receiver { separateCode(temperListAddAll) }
val JavaLang.listBuilderCopyOf by receiver {
    inlineSupport("core.type ListBuilder.toListBuilder()", arity = 1, needsSelf = false) { pos, args ->
        J.InstanceCreationExpr(
            pos,
            javaUtilArrayList.toClassType(pos, args = J.TypeArguments(pos)),
            args = args.map { it.asArgument() },
        )
    }
}
val JavaLang.listBuilderRemoveLast by receiver { separateCode(temperListRemoveLast) }
val JavaLang.listBuilderReverse by receiver { separateCode(javaUtilCollectionsReverse) }
val JavaLang.listBuilderSort by receiver {
    // TODO This could potentially be factored along with core.type Listed.sorted().
    inlineSupport("core.type ListBuilder.sort()", 2, needsSelf = true) inline@{ pos, args, _ ->
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "core.type ListBuilder.sort()", "$args")
        val (inType, _) = functionSimpleArgumentTypes(fnType)
        when (inType) {
            Jst.JstInt -> temperListSort.suffix(inType.shortCamelName).staticMethod(args.unpackArgs(), pos)
            else -> temperListSort.staticMethod(adjustedArgs, pos)
        }
    }
}
val JavaLang.listBuilderSplice by receiver { separateCode(temperListSplice) }

// Map, MapBuilder
val JavaLang.mapConstructor by receiver { separateCode(temperMapConstructor) }
val JavaLang.pairConstructor by receiver {
    inlineSupport("core.type Pair.constructor()", arity = 2) { pos, args ->
        J.InstanceCreationExpr(
            pos,
            type = javaUtilSimpleImmutableEntry.toClassType(pos, J.TypeArguments(pos)),
            args = args.map { it.asArgument() },
        )
    }
}
val JavaLang.mappedLength by receiver {
    inlineSupport("core.type Mapped.length()", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("size", pos = pos)
    }
}
val JavaLang.mappedGet by receiver { separateCode(temperMappedGet) }
val JavaLang.mappedGetOr by receiver {
    inlineSupport("core.type Mapped.getOr()", arity = 3, needsSelf = true) { pos, args ->
        args[0].method("getOrDefault", args[1], args[2], pos = pos)
    }
}
val JavaLang.mappedHas by receiver {
    inlineSupport("core.type Mapped.has()", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("containsKey", args[1], pos = pos)
    }
}
val JavaLang.mappedKeys by receiver {
    inlineSupport("core.type Mapped.keys()", arity = 1, needsSelf = true) { pos, args ->
        J.InstanceCreationExpr(
            pos = pos,
            type = javaUtilArrayList.toClassType(pos, J.TypeArguments(pos)),
            args = listOf(
                J.Argument(
                    pos = pos,
                    expr = args[0].method("keySet", pos = pos),
                ),
            ),
        )
    }
}
val JavaLang.mappedValues by receiver {
    inlineSupport("core.type Mapped.values()", arity = 1, needsSelf = true) { pos, args ->
        J.InstanceCreationExpr(
            pos = pos,
            type = javaUtilArrayList.toClassType(pos, J.TypeArguments(pos)),
            args = listOf(
                J.Argument(
                    pos = pos,
                    expr = args[0].method("values", pos = pos),
                ),
            ),
        )
    }
}
val JavaLang.mappedToMap by receiver { separateCode(temperMappedToMap) }
val JavaLang.mappedToMapBuilder by receiver {
    inlineSupport("core.type Mapped.toMapBuilder()", arity = 1, needsSelf = true) { pos, args ->
        J.InstanceCreationExpr(
            pos = pos,
            type = javaUtilLinkedHashMap.toClassType(pos, J.TypeArguments(pos)),
            args = listOf(J.Argument(args[0].pos, args[0])),
        )
    }
}
val JavaLang.mappedToList by receiver { separateCode(temperMappedToList) }
val JavaLang.mappedToListBuilder by receiver { separateCode(temperMappedToListBuilder) }
val JavaLang.mappedToListWith by receiver { separateCode(temperMappedToListWith) }
val JavaLang.mappedToListBuilderWith by receiver { separateCode(temperMappedToListBuilderWith) }
val JavaLang.mappedForEach by receiver { separateCode(temperMappedForEach) }
val JavaLang.mapBuilderRemove by receiver { separateCode(temperMapBuilderRemove) }
val JavaLang.mapBuilderSet by receiver {
    inlineSupport("core.type MapBuilder.set()", arity = 3, needsSelf = true) { pos, args ->
        args[0].method("put", args[1], args[2], pos = pos)
    }
}
val JavaLang.mapBuilderConstructor by receiver {
    inlineSupport("core.type MapBuilder.constructor()", arity = 0) { pos, _ ->
        J.InstanceCreationExpr(pos, javaUtilLinkedHashMap.toClassType(pos, J.TypeArguments(pos)), args = listOf())
    }
}
fun printExpr(pos: Position, arg: J.Expression) =
    javaLangSystem.toNameExpr(pos)
        .field("out")
        .method("println", arg)

internal val JavaLang.getPromiseResultSyncSupport by receiver {
    separateCode(coroPromiseResultAsync)
}
internal val JavaLang.convertedCoroutineAwakeUponSupport by receiver {
    separateCode(coroAwakeUpon)
}

fun JavaLang.notSupported(name: String, builtin: NamedBuiltinFun, what: String = ""): JavaSupportCode {
    val msg = mutableListOf<String>()
    msg.add("Builtin(${builtin.name}, ${builtin.builtinOperatorId}, species=${builtin.functionSpecies})")
    if (what.isNotEmpty()) {
        msg.add(what)
    }

    return inlineSupport(
        name,
        arity = -1,
        needsSelf = false,
    ) { pos, _ ->
        garbageExpr(pos, "notSupported($name)", msg.joinToString("; "))
    }
}

/** If possible, always wraps the last arg as an instance method reference. */
internal fun adaptFn(args: List<TypedArg<J.Expression>>): Pair<List<J.Argument>, Signature2>? {
    val fnArg = args.last()
    val fnExpr = fnArg.expr
    val fnType = withType(
        fnArg.type,
        fn = { _, sig, _ -> sig },
        fallback = { null },
    ) ?: return null
    val adjustedArgs = when {
        validInstanceMethodReferenceSubject(fnExpr) -> {
            val fnSig = signature(fnType)
            args.subList(0, args.size - 1).unpackArgs() + listOf(
                J.InstanceMethodReferenceExpr(
                    fnExpr.pos,
                    fnExpr,
                    J.Identifier(fnExpr.pos, fnSig.returnType.samMethodName),
                ).asArgument(),
            )
        }
        else -> args.unpackArgs()
    }
    return adjustedArgs to fnType
}

private val connections: Map<String, ((JavaLang) -> SupportCode)> = mapOf(
    "core.getConsole()" to { it.getConsole },
    "core.type Boolean.toString()" to { it.booleanToString },
    // "core.type Console.log()" to null,
    "std/temporal.type Date.constructor()" to { it.dateConstructor },
    "std/temporal.type Date.fromIsoString()" to { it.dateFromIsoString },
    "std/temporal.type Date.day" to { it.dateGetDay },
    "std/temporal.type Date.get dayOfWeek()" to { it.dateGetDayOfWeek },
    "std/temporal.type Date.month" to { it.dateGetMonth },
    "std/temporal.type Date.year" to { it.dateGetYear },
    "std/temporal.type Date.toString()" to { it.dateToString },
    "std/temporal.type Date.today()" to { it.dateToday },
    "std/temporal.type Date.yearsBetween()" to { it.dateYearsBetween },
    "core.type DenseBitVector.constructor()" to { it.denseBitVectorConstructor },
    "core.type DenseBitVector.get()" to { it.denseBitVectorGet },
    "core.type DenseBitVector.set()" to { it.denseBitVectorSet },
    "core.type Deque.add()" to { it.dequeAdd },
    "core.type Deque.constructor()" to { it.dequeConstructor },
    "core.type Deque.get isEmpty()" to { it.genericIsEmpty },
    "core.type Deque.removeFirst()" to { it.dequeRemoveFirst },
    "core.type Float64.abs()" to { it.float64Abs },
    "core.type Float64.acos()" to { it.float64Acos },
    "core.type Float64.asin()" to { it.float64Asin },
    "core.type Float64.atan()" to { it.float64Atan },
    "core.type Float64.atan2()" to { it.float64Atan2 },
    "core.type Float64.ceil()" to { it.float64Ceil },
    "core.type Float64.cos()" to { it.float64Cos },
    "core.type Float64.cosh()" to { it.float64Cosh },
    "core.type Float64.e" to { it.float64E },
    "core.type Float64.exp()" to { it.float64Exp },
    "core.type Float64.expm1()" to { it.float64Expm1 },
    "core.type Float64.floor()" to { it.float64Floor },
    "core.type Float64.log()" to { it.float64Log },
    "core.type Float64.log10()" to { it.float64Log10 },
    "core.type Float64.log1p()" to { it.float64Log1p },
    "core.type Float64.max()" to { it.float64Max },
    "core.type Float64.min()" to { it.float64Min },
    "core.type Float64.near()" to { it.float64Near },
    "core.type Float64.pi" to { it.float64Pi },
    "core.type Float64.round()" to { it.float64Round },
    "core.type Float64.sign()" to { it.float64Sign },
    "core.type Float64.sin()" to { it.float64Sin },
    "core.type Float64.sinh()" to { it.float64Sinh },
    "core.type Float64.sqrt()" to { it.float64Sqrt },
    "core.type Float64.tan()" to { it.float64Tan },
    "core.type Float64.tanh()" to { it.float64Tanh },
    "core.type Float64.toInt32()" to { it.float64ToInt },
    "core.type Float64.toInt32Unsafe()" to { it.float64ToIntUnsafe },
    "core.type Float64.toInt64()" to { it.float64ToInt64 },
    "core.type Float64.toInt64Unsafe()" to { it.float64ToInt64Unsafe },
    "core.type Float64.toString()" to { it.float64ToString },
    "core.type Generator.next()" to { it.generatorNext },
    "core.type Int32.max()" to { it.intMax },
    "core.type Int32.min()" to { it.intMin },
    "core.type Int32.toFloat64()" to { it.intToFloat64 },
    "core.type Int32.toInt64()" to { it.intToInt64 },
    "core.type Int32.toString()" to { it.intToString },
    "core.type Int64.max()" to { it.int64Max },
    "core.type Int64.min()" to { it.int64Min },
    "core.type Int64.toInt32()" to { it.int64ToInt32 },
    "core.type Int64.toInt32Unsafe()" to { it.int64ToInt32Unsafe },
    "core.type Int64.toFloat64()" to { it.int64ToFloat64 },
    "core.type Int64.toFloat64Unsafe()" to { it.int64ToFloat64Unsafe },
    "core.type Int64.toString()" to { it.int64ToString },
    "core.type List.get()" to { it.listGet },
    "core.type List.get length()" to { it.listLength },
    "core.type List.toList()" to { it.identity },
    "core.type List.toListBuilder()" to { it.listBuilderCopyOf },
    "core.type ListBuilder.add()" to { it.listBuilderAdd },
    "core.type ListBuilder.addAll()" to { it.listBuilderAddAll },
    "core.type ListBuilder.constructor()" to { it.listBuilderMake },
    "core.type ListBuilder.length()" to { it.listLength },
    "core.type ListBuilder.removeLast()" to { it.listBuilderRemoveLast },
    "core.type ListBuilder.reverse()" to { it.listBuilderReverse },
    "core.type ListBuilder.sort()" to { it.listBuilderSort },
    "core.type ListBuilder.splice()" to { it.listBuilderSplice },
    "core.type ListBuilder.toList()" to { it.listCopyOf },
    "core.type ListBuilder.toListBuilder()" to { it.listBuilderCopyOf },
    "core.type Listed.filter()" to { it.listFilter },
    "core.type Listed.get()" to { it.listGet },
    "core.type Listed.getOr()" to { it.listGetOr },
    "core.type Listed.isEmpty()" to { it.genericIsEmpty },
    "core.type Listed.join()" to { it.listJoin },
    "core.type Listed.get length()" to { it.listLength },
    "core.type Listed.map()" to { it.listMap },
    "core.type Listed.reduce()" to { it.listedReduce },
    "core.type Listed.reduceFrom()" to { it.listedReduceFrom },
    "core.type Listed.slice()" to { it.listSlice },
    "core.type Listed.sorted()" to { it.listSorted },
    "core.type Listed.toList()" to { it.listedToList },
    "core.type Listed.toListBuilder()" to { it.listBuilderCopyOf },
    "core.type Map.constructor()" to { it.mapConstructor },
    "core.type MapBuilder.constructor()" to { it.mapBuilderConstructor },
    "core.type MapBuilder.remove()" to { it.mapBuilderRemove },
    "core.type MapBuilder.set()" to { it.mapBuilderSet },
    "core.type Mapped.forEach()" to { it.mappedForEach },
    "core.type Mapped.get()" to { it.mappedGet },
    "core.type Mapped.getOr()" to { it.mappedGetOr },
    "core.type Mapped.has()" to { it.mappedHas },
    "core.type Mapped.keys()" to { it.mappedKeys },
    "core.type Mapped.length()" to { it.mappedLength },
    "core.type Mapped.toList()" to { it.mappedToList },
    "core.type Mapped.toListBuilder()" to { it.mappedToListBuilder },
    "core.type Mapped.toListBuilderWith()" to { it.mappedToListBuilderWith },
    "core.type Mapped.toListWith()" to { it.mappedToListWith },
    "core.type Mapped.toMap()" to { it.mappedToMap },
    "core.type Mapped.toMapBuilder()" to { it.mappedToMapBuilder },
    "core.type Mapped.values()" to { it.mappedValues },
    "core.type Pair.constructor()" to { it.pairConstructor },
    "core.type PromiseBuilder.breakPromise()" to { it.promiseBuilderBreakPromise },
    "core.type PromiseBuilder.complete()" to { it.promiseBuilderComplete },
    "core.type PromiseBuilder.get promise()" to { it.promiseBuilderGetPromise },
    "std/regex.type Regex.compileFormatted()" to { it.regexCompiledFormatted },
    "std/regex.type Regex.compiledFind()" to { it.regexCompiledFind },
    "std/regex.type Regex.compiledFound()" to { it.regexCompiledFound },
    "std/regex.type Regex.compiledReplace()" to { it.regexCompiledReplace },
    "std/regex.type Regex.compiledSplit()" to { it.regexCompiledSplit },
    "std/regex.type Regex.format()" to { it.regexFormat },
    // "std/regex.type RegexFormatter.adjustCodeSet()" to null,
    // "std/regex.type RegexFormatter.pushCaptureName()" to null,
    "std/regex.type RegexFormatter.pushCodeTo()" to { it.regexFormatterPushCodeTo },
    "core.type SafeGenerator.next()" to { it.generatorNext },
    "core.type String.begin" to { it.stringBegin },
    "core.type String.countBetween()" to { it.stringCountBetween },
    "core.type String.get end()" to { it.stringEnd },
    "core.type String.forEach()" to { it.stringForEach },
    "core.type String.fromCodePoint()" to { it.stringFromCodePoint },
    "core.type String.fromCodePoints()" to { it.stringFromCodePoints },
    "core.type String.get()" to { it.stringGet },
    "core.type String.hasAtLeast()" to { it.stringHasAtLeast },
    "core.type String.hasIndex()" to { it.stringHasIndex },
    "core.type String.isEmpty()" to { it.genericIsEmpty },
    "core.type String.next()" to { it.stringNext },
    "core.type String.prev()" to { it.stringPrev },
    "core.type String.step()" to { it.stringStep },
    "core.type String.slice()" to { it.stringSlice },
    "core.type String.split()" to { it.stringSplit },
    "core.type String.toFloat64()" to { it.stringToFloat64 },
    "core.type String.toInt32()" to { it.stringToInt },
    "core.type String.toInt64()" to { it.stringToInt64 },
    "core.type String.toString()" to { it.identity },
    "core.type StringBuilder.append()" to { it.stringBuilderAppend },
    "core.type StringBuilder.appendBetween()" to { it.stringBuilderAppendBetween },
    "core.type StringBuilder.appendCodePoint()" to { it.stringBuilderAppendCodePoint },
    "core.type StringBuilder.clear()" to { it.stringBuilderClear },
    "core.type StringBuilder.constructor()" to { it.stringBuilderConstructor },
    "core.type StringBuilder.end()" to { it.stringBuilderEnd },
    "core.type StringBuilder.toString()" to { it.stringBuilderToString },
    "core.type StringIndex.none" to { it.stringIndexNone },
    "core.type StringIndexOption.compareTo()" to { it.stringIndexOptionCompareTo },
    "core.type StringIndexOption.compareTo()::eq" to { it.stringIndexOptionCompareToEq },
    "core.type StringIndexOption.compareTo()::ge" to { it.stringIndexOptionCompareToGe },
    "core.type StringIndexOption.compareTo()::gt" to { it.stringIndexOptionCompareToGt },
    "core.type StringIndexOption.compareTo()::le" to { it.stringIndexOptionCompareToLe },
    "core.type StringIndexOption.compareTo()::lt" to { it.stringIndexOptionCompareToLt },
    "core.type StringIndexOption.compareTo()::ne" to { it.stringIndexOptionCompareToNe },
    "std/testing.type Test.bail()" to { it.bail },
    "core.doneResult()" to { it.doneResult },
    "core.empty()" to { it.empty },
    "core.ignore()" to { it.doNothing },
    "std/net.sendRequest()" to { it.netCoreStdNetSend },
)
