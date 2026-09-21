package lang.temper.be.java

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.ComparisonKind
import lang.temper.be.tmpl.ComputedJumpStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.GetStaticSupport
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SeparatelyCompiledSupportCode
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TranslationAssistant
import lang.temper.be.tmpl.TypedArg
import lang.temper.builtin.GetStaticOp
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.common.subListToEnd
import lang.temper.format.TokenSink
import lang.temper.frontend.coroutine.CoroHelperSpecials.ConvertedCoroutineAwakeUponFn
import lang.temper.frontend.coroutine.CoroHelperSpecials.GetPromiseResultSyncFn
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.DashedIdentifier
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.WellKnownTypes
import lang.temper.type.excludeNullAndBubble
import lang.temper.type2.Descriptor
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.withType
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PureVirtual
import lang.temper.value.emptyValue
import lang.temper.be.java.Java as J
import lang.temper.be.java.JavaSimpleType as Jst

class JavaSupportNetwork private constructor(private val javaLang: JavaLang) : SupportNetwork {
    override val backendDescription: String = "Java / JVM Backend"
    override val bubbleStrategy = BubbleBranchStrategy.Exceptions
    override val coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionType // TODO: rework to use @fun interfaces
    override val computedJumpStrategy = ComputedJumpStrategy.IsDefaultBreakScope
    override val mayAssignInBothTryAndRecover = false

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
            BuiltinOperatorId.CmpLongLong -> int64Cmp
            BuiltinOperatorId.CmpBoolBool -> boolCmp
            BuiltinOperatorId.GtIntInt -> operatorGt
            BuiltinOperatorId.LtIntInt -> operatorLt
            BuiltinOperatorId.GeIntInt -> operatorGe
            BuiltinOperatorId.LeIntInt -> operatorLe
            BuiltinOperatorId.EqIntInt -> operatorEq
            BuiltinOperatorId.EqFltFlt -> doubleEq
            BuiltinOperatorId.EqStrStr -> comparableEq
            BuiltinOperatorId.EqBoolBool -> operatorEq
            BuiltinOperatorId.EqLongLong -> operatorEq
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
            // using Exceptions not Results
            BuiltinOperatorId.IsOkResult,
            BuiltinOperatorId.PackOkResult,
            BuiltinOperatorId.RepackErrResult,
            BuiltinOperatorId.UnpackOkResult,
            -> null
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
        } as? JavaSupportCode ?: return null
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
                javaLang.inlineSupport(
                    "simple${comparisonKind.name}",
                    arity = 2,
                    factory = operatorRelational(comparisonKind, calleePos = fn.pos),
                ),
            ),
            typeActuals = tmpl.typeActuals.deepCopy(),
            parameters = freeParameters,
        )
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

typealias ExprFactory = JavaLang.(
    pos: Position,
    args: List<J.Expression>,
    translator: JavaTranslator.ModuleScope,
) -> J.Expression
typealias ExprFactoryTyped =
    JavaLang.(
        pos: Position,
        args: List<TypedArg<J.Expression>>,
        type: Type2,
        translator: JavaTranslator.ModuleScope,
    ) -> J.Expression
typealias TreeFactoryTyped =
    JavaLang.(
        pos: Position,
        args: List<TypedArg<J.Expression>>,
        type: Type2,
        translator: JavaTranslator.ModuleScope,
    ) -> J.Tree

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
                translator,
            )
        }
}

sealed class JavaSeparate(
    lang: JavaLang,
    val qualifiedName: QualifiedName,
    opId: BuiltinOperatorId?,
    connectedKey: String? = null,
) : JavaSupportCode(
    lang = lang,
    baseName = ParsedName(connectedKey ?: qualifiedName.fullyQualified),
    builtinOperatorId = opId,
),
    SeparatelyCompiledSupportCode {
    override val source: DashedIdentifier get() = DashedIdentifier.temperCoreLibraryIdentifier
    override val stableKey: ParsedName get() = baseName
}

/** Represents a separately compiled static method. */
class JavaSeparateStatic(
    lang: JavaLang,
    qualifiedName: QualifiedName,
    opId: BuiltinOperatorId? = null,
    connectedKey: String? = null,
) : JavaSeparate(lang, qualifiedName, opId, connectedKey) {
    override fun toString(): String = "JavaSeparateStatic($baseName)"
}

internal fun JavaSupportCtx.inlineSupport(
    arity: Int,
    builtinOperatorId: BuiltinOperatorId? = null,
    needsSelf: Boolean = false,
    factory: ExprFactoryTyped,
) = JavaInlineSupportCode(
    baseName = baseName,
    arity = arity,
    lang = lang,
    builtinOperatorId = builtinOperatorId,
    needsSelf = needsSelf,
    factory = factory,
)

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
    factory = { p, a, _, t -> factory(p, a.map { it.expr }, t) },
)

internal fun JavaSupportCtx.inlineSupport(
    arity: Int,
    builtinOperatorId: BuiltinOperatorId? = null,
    needsSelf: Boolean = false,
    factory: ExprFactory,
) = JavaInlineSupportCode(
    baseName = baseName,
    arity = arity,
    lang = lang,
    builtinOperatorId = builtinOperatorId,
    needsSelf = needsSelf,
    factory = { p, a, _, t -> factory(p, a.map { it.expr }, t) },
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
    factory = { p, a, _, t -> factory(p, a.map { it.expr }, t) },
)

fun JavaLang.separateCode(
    methodName: QualifiedName,
    builtinOperatorId: BuiltinOperatorId? = null,
    connectedKey: String? = null,
) = JavaSeparateStatic(
    lang = this,
    qualifiedName = methodName,
    opId = builtinOperatorId,
    connectedKey = connectedKey,
)

internal fun JavaSupportCtx.separateCode(
    methodName: QualifiedName,
    builtinOperatorId: BuiltinOperatorId? = null,
) = JavaSeparateStatic(
    lang = lang,
    qualifiedName = methodName,
    opId = builtinOperatorId,
    connectedKey = connectedKey,
)

private fun Iterable<TypedArg<J.Expression>>.unpackArgs() = map { it.expr.asArgument() }
private fun Iterable<TypedArg<J.Expression>>.unpackExpr() = map { it.expr }

// Relational operations
val JavaLang.integerCmp by receiver {
    separateCode(javaLangIntegerCompare, BuiltinOperatorId.CmpIntInt)
}
val JavaLang.doubleCmp by receiver {
    separateCode(javaLangDoubleCompare, BuiltinOperatorId.CmpFltFlt)
}
val JavaLang.int64Cmp by receiver {
    separateCode(javaLangLongCompare, BuiltinOperatorId.CmpLongLong)
}
val JavaLang.boolCmp by receiver {
    separateCode(javaLangBooleanCompare, BuiltinOperatorId.CmpBoolBool)
}
val JavaLang.comparableCmp by receiver {
    inlineSupport("comparableCmp", 2) { pos, args, _ ->
        args[0].method("compareTo", args[1], pos = pos)
    }
}

private fun operatorRelational(
    kind: ComparisonKind,
    calleePos: Position? = null,
) = operatorRelational(
    when (kind) {
        ComparisonKind.LessThan -> JavaOperator.LessThan
        ComparisonKind.LessThanOrEqual -> JavaOperator.LessEquals
        ComparisonKind.GreaterThanOrEqual -> JavaOperator.GreaterEquals
        ComparisonKind.GreaterThan -> JavaOperator.GreaterThan
    },
    calleePos = calleePos,
)

private fun operatorRelational(
    op: JavaOperator,
    calleePos: Position? = null,
): ExprFactory =
    { pos, args, _ ->
        op.infix(args[0], args[1], pos = pos, calleePos = calleePos)
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

private fun operatorEquality(pos: Position, args: List<TypedArg<J.Expression>>, names: JavaNames): J.Expression {
    // JLS 15.21.1 Numerical Equality Operators == and !=
    // says:
    // > f the operands of an equality operator are both of numeric type, or one
    // > is of numeric type and the other is convertible (§5.1.8) to numeric type,
    // > binary numeric promotion is performed on the operands (§5.6.2).
    //
    // §5.1.8 is the section on "Unboxing conversion."

    // That means that the first two `==`s below does numeric comparison,
    // but the last does not.
    //
    //      Integer a = new Integer(123);
    //      Integer b = new Integer(123);
    //
    //      a == 123;  // integer equality
    //      123 == b;  // integer equality
    //      a == b;    // reference equality

    val (leftArg, rightArg) = args
    val ref0 = leftArg.isReferenceType(names)
    val ref1 = rightArg.isReferenceType(names)
    val left = leftArg.expr
    var right = rightArg.expr
    if (ref0 && ref1) {
        val primitiveType = JavaType.fromFrontend(excludeNullAndBubble(rightArg.type), names)
        if (primitiveType is Primitive) {
            right = unboxToPrimitive(right, primitiveType)
        } else {
            // Fallback to Objects.equals instead.
            return javaUtilObjectsEquals.staticMethod(left, right, pos = pos)
        }
    }
    return JavaOperator.Equals.infix(left, right, pos = pos)
}
val JavaLang.operatorEq by receiver {
    inlineSupport("operatorEq", 2) { pos, args, _, t ->
        operatorEquality(pos, args, t.names)
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
private fun doubleEquality(pos: Position, args: List<TypedArg<J.Expression>>): J.Expression {
    return doubleRelational(JavaOperator.Equals, pos, args.unpackExpr())
}
val JavaLang.doubleEq by receiver {
    inlineSupport("doubleEq", 2) { pos, args, _, _ ->
        doubleEquality(pos, args)
    }
}

private fun comparableEquality(pos: Position, args: List<TypedArg<J.Expression>>): J.Expression =
    if (!args[0].isNullable) {
        args[0].expr.method("equals", args[1].expr, pos = pos)
    } else {
        javaUtilObjectsEquals.staticMethod(args.unpackArgs(), pos = pos)
    }
val JavaLang.comparableEq by receiver {
    inlineSupport("comparableEq", 2) { pos, args, _, _ ->
        comparableEquality(pos, args)
    }
}

// Miscellany

/** Just be yourself. */
internal val JavaLang.identity by receiver { inlineSupport("identity", 1) { _, args, _ -> args[0] } }
internal val JavaSupportCtx.identity by receiver { lang.identity }

internal val JavaLang.isNull by receiver {
    inlineSupport("isNull", 1, BuiltinOperatorId.IsNull) { pos, args, _ ->
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
    inlineSupport(BuiltinOperatorId.BooleanNegation, 1) { pos, args, _ ->
        simplifiedComplement(args[0], pos = pos)
    }
}
val JavaLang.plusIntInt by receiver {
    inlineSupport(BuiltinOperatorId.PlusIntInt, 2) { pos, args, _ ->
        JavaOperator.Addition.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.plusDubDub by receiver {
    inlineSupport(BuiltinOperatorId.PlusFltFlt, 2) { pos, args, _ ->
        JavaOperator.Addition.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.minusIntInt by receiver {
    inlineSupport(BuiltinOperatorId.MinusIntInt, 2) { pos, args, _ ->
        JavaOperator.Subtraction.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.minusDubDub by receiver {
    inlineSupport(BuiltinOperatorId.MinusFltFlt, 2) { pos, args, _ ->
        JavaOperator.Subtraction.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.minusInt by receiver {
    inlineSupport(BuiltinOperatorId.MinusInt, 1) { pos, args, _ ->
        JavaOperator.Minus.prefix(args[0], pos = pos)
    }
}
val JavaLang.minusDub by receiver {
    inlineSupport(BuiltinOperatorId.MinusFlt, 1) { pos, args, _ ->
        JavaOperator.Minus.prefix(args[0], pos = pos)
    }
}
val JavaLang.timesIntInt by receiver {
    inlineSupport(BuiltinOperatorId.TimesIntInt, 2) { pos, args, _ ->
        JavaOperator.Multiplication.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.timesDubDub by receiver {
    inlineSupport(BuiltinOperatorId.TimesFltFlt, 2) { pos, args, _ ->
        JavaOperator.Multiplication.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.powDubDub by receiver {
    inlineSupport(BuiltinOperatorId.PowFltFlt, 2) { pos, args, _ ->
        javaMathPow.staticMethod(args[0], args[1], pos = pos)
    }
}
val JavaLang.divIntInt by receiver { separateCode(temperDivIntInt, BuiltinOperatorId.DivIntInt) }
val JavaLang.divIntIntSafe by receiver {
    inlineSupport(BuiltinOperatorId.DivIntIntSafe, 2) { pos, args, _ ->
        JavaOperator.Division.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.divDubDub by receiver {
    inlineSupport(BuiltinOperatorId.DivFltFlt, 2) { pos, args, _ ->
        JavaOperator.Division.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.modIntInt by receiver { separateCode(temperModIntInt, BuiltinOperatorId.ModIntInt) }
val JavaLang.modIntIntSafe by receiver {
    inlineSupport(BuiltinOperatorId.ModIntIntSafe, 2) { pos, args, _ ->
        JavaOperator.Remainder.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.modDubDub by receiver {
    inlineSupport(BuiltinOperatorId.ModFltFlt, 2) { pos, args, _ ->
        JavaOperator.Remainder.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseAnd by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseAnd32, 2) { pos, args, _ ->
        JavaOperator.And.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseOr by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseOr32, 2) { pos, args, _ ->
        JavaOperator.InclusiveOr.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseXor by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseXor32, 2) { pos, args, _ ->
        JavaOperator.ExclusiveOr.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseNegation by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseNegation32, 1) { pos, args, _ ->
        JavaOperator.BitwiseComplement.prefix(args[0], pos = pos)
    }
}
val JavaLang.bitwiseShl by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseShl32, 2) { pos, args, _ ->
        JavaOperator.LeftShift.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseShr by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseShr32, 2) { pos, args, _ ->
        JavaOperator.RightShift.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseUShr by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseShrUnsigned32, 2) { pos, args, _ ->
        JavaOperator.LogicalRightShift.infix(args[0], args[1], pos = pos)
    }
}
internal val JavaSupportCtx.booleanToString by receiver {
    inlineSupport(1, needsSelf = true) { pos, args, _ ->
        javaLangBooleanToString.staticMethod(listOf(args[0].asArgument()), pos = pos)
    }
}
internal val JavaSupportCtx.intToFloat64 by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        Primitive.JavaDouble.cast(args[0], pos)
    }
}
internal val JavaSupportCtx.intToInt64 by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        Primitive.JavaLong.cast(args[0], pos)
    }
}
internal val JavaSupportCtx.intToString by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        javaLangIntegerToString.staticMethod(args.map(J.Expression::asArgument), pos = pos)
    }
}
internal val JavaSupportCtx.int64ToFloat64 by receiver { separateCode(temperInt64ToFloat64) }
internal val JavaSupportCtx.int64ToFloat64Unsafe by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        Primitive.JavaDouble.cast(args[0], pos)
    }
}
internal val JavaSupportCtx.int64ToInt32 by receiver { separateCode(temperInt64ToInt) }
internal val JavaSupportCtx.int64ToInt32Unsafe by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        Primitive.JavaInt.cast(args[0], pos)
    }
}
internal val JavaSupportCtx.int64ToString by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        javaLangLongToString.staticMethod(args.map(J.Expression::asArgument), pos = pos)
    }
}
internal val JavaSupportCtx.float64E by receiver {
    inlineSupport(0) { pos, _, _ -> javaMathE.toNameExpr(pos) }
}
internal val JavaSupportCtx.float64Pi by receiver {
    inlineSupport(0) { pos, _, _ -> javaMathPi.toNameExpr(pos) }
}
internal val JavaSupportCtx.float64Abs by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathAbs.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Acos by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathAcos.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Asin by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathAsin.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Atan by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathAtan.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Atan2 by receiver {
    inlineSupport(2) { pos, args, _ ->
        javaMathAtan2.staticMethod(args[0], args[1], pos = pos)
    }
}
internal val JavaSupportCtx.float64Ceil by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathCeil.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Cos by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathCos.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Cosh by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathCosh.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Exp by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathExp.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Expm1 by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathExpm1.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Floor by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathFloor.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Log by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathLog.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Log10 by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathLog10.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Log1p by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathLog1p.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Max by receiver {
    inlineSupport(2) { pos, args, _ -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
internal val JavaSupportCtx.float64Min by receiver {
    inlineSupport(2) { pos, args, _ -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}
internal val JavaSupportCtx.float64Near by receiver { separateCode(temperFloat64Near) }
internal val JavaSupportCtx.float64Round by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathRound.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Sign by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathSignum.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Sin by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathSin.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Sinh by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathSinh.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Sqrt by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathSqrt.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Tan by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathTan.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64Tanh by receiver {
    inlineSupport(1) { pos, args, _ -> javaMathTanh.staticMethod(args[0], pos = pos) }
}
internal val JavaSupportCtx.float64ToInt by receiver { separateCode(temperFloat64ToInt) }
internal val JavaSupportCtx.float64ToIntUnsafe by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        Primitive.JavaInt.cast(args[0], pos)
    }
}
internal val JavaSupportCtx.float64ToInt64 by receiver { separateCode(temperFloat64ToInt64) }
internal val JavaSupportCtx.float64ToInt64Unsafe by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        Primitive.JavaLong.cast(args[0], pos)
    }
}
internal val JavaSupportCtx.float64ToString by receiver { separateCode(temperFloat64ToString) }
internal val JavaSupportCtx.genericIsEmpty by receiver {
    lang.genericIsEmpty
}
internal val JavaLang.genericIsEmpty by receiver {
    inlineSupport("*::isEmpty", 1, needsSelf = true) { pos, args, _ ->
        args[0].method("isEmpty", pos = pos)
    }
}
internal val JavaSupportCtx.intMax by receiver {
    inlineSupport(2) { pos, args, _ -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
internal val JavaSupportCtx.intMin by receiver {
    inlineSupport(2) { pos, args, _ -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}
internal val JavaSupportCtx.intSignum by receiver {
    inlineSupport(-1, needsSelf = true) { pos, args, _ ->
        javaLangIntegerSignum.staticMethod(args[0], pos = pos)
    }
}
internal val JavaSupportCtx.int64Max by receiver {
    inlineSupport(2) { pos, args, _ -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
internal val JavaSupportCtx.int64Min by receiver {
    inlineSupport(2) { pos, args, _ -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}

// String operations
val JavaLang.strCatExpr by receiver {
    inlineSupport("strcat", -1, BuiltinOperatorId.StrCat) { pos, args, _ ->
        when (args.size) {
            0 -> J.StringLiteral(pos, "")
            else -> args.subListToEnd(1).fold(args[0]) {
                    a, b ->
                JavaOperator.Addition.infix(a, b)
            }
        }
    }
}
internal val JavaSupportCtx.stringFromCodePoint by receiver { separateCode(temperStringFromCodePoint) }
internal val JavaSupportCtx.stringFromCodePoints by receiver { separateCode(temperStringFromCodePoints) }
internal val JavaSupportCtx.stringSplit by receiver { separateCode(temperStringSplit) }
internal val JavaSupportCtx.stringToFloat64 by receiver { separateCode(temperStringToFloat64) }
internal val JavaSupportCtx.stringToInt by receiver { separateCode(temperStringToInt) }
internal val JavaSupportCtx.stringToInt64 by receiver { separateCode(temperStringToInt64) }
internal val JavaSupportCtx.stringEnd by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("length", pos = pos)
    }
}
internal val JavaSupportCtx.stringBegin by receiver {
    inlineSupport(0) { pos, _, _ ->
        J.IntegerLiteral(pos, 0)
    }
}
internal val JavaSupportCtx.stringIndexNone by receiver {
    inlineSupport(0) { pos, _, _ ->
        J.IntegerLiteral(pos, -1)
    }
}
internal val JavaSupportCtx.stringGet by receiver {
    inlineSupport(arity = 2, needsSelf = true) { pos, args, _ ->
        args[0].method("codePointAt", args[1], pos = pos)
    }
}
internal val JavaSupportCtx.stringCountBetween by receiver { separateCode(temperStringCountBetween) }
internal val JavaSupportCtx.stringForEach by receiver { separateCode(temperStringForEach) }
internal val JavaSupportCtx.stringHasAtLeast by receiver { separateCode(temperStringHasAtLeast) }
internal val JavaSupportCtx.stringHasIndex by receiver { separateCode(temperStringHasIndex) }
internal val JavaSupportCtx.stringNext by receiver { separateCode(temperStringNext) }
internal val JavaSupportCtx.stringPrev by receiver { separateCode(temperStringPrev) }
internal val JavaSupportCtx.stringStep by receiver { separateCode(temperStringStep) }
internal val JavaSupportCtx.stringSlice by receiver { separateCode(temperStringSlice) }
internal val JavaSupportCtx.stringBuilderConstructor by receiver {
    inlineSupport(arity = -1) { pos, _, _ ->
        J.InstanceCreationExpr(pos, type = javaLangStringBuilder.toClassType(pos), args = emptyList())
    }
}
internal val JavaSupportCtx.stringBuilderAppend by receiver {
    inlineSupport(arity = 2, needsSelf = true) { pos, args, _ ->
        args[0].method("append", args[1], pos = pos)
    }
}
internal val JavaSupportCtx.stringBuilderAppendBetween by receiver {
    separateCode(temperStringBuilderAppendBetween)
}
internal val JavaSupportCtx.stringBuilderAppendCodePoint by receiver {
    separateCode(temperStringBuilderAppendCodePoint)
}
internal val JavaSupportCtx.stringBuilderClear by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("setLength", J.IntegerLiteral(pos.rightEdge, 0), pos = pos)
    }
}
internal val JavaSupportCtx.stringBuilderEnd by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("length", pos = pos)
    }
}
internal val JavaSupportCtx.stringBuilderToString by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("toString", pos = pos)
    }
}
internal val JavaSupportCtx.stringIndexOptionCompareTo by receiver {
    separateCode(javaLangIntegerCompare)
}
private fun JavaLang.comparison(baseName: String, operator: JavaOperator): JavaInlineSupportCode =
    inlineSupport(baseName, arity = 2, needsSelf = true) { pos, (a, b), _ ->
        J.InfixExpr(pos, a, J.Operator(pos.leftEdge, operator), b)
    }
internal val JavaSupportCtx.stringIndexOptionCompareToEq by receiver {
    lang.comparison(baseName, JavaOperator.Equals)
}
val JavaLang.requireNoStringIndex by receiver {
    separateCode(temperRequireNoStringIndex)
}
val JavaLang.requireStringIndex by receiver {
    separateCode(temperRequireStringIndex)
}

// Regex support
internal val JavaSupportCtx.regexFormat by receiver { separateCode(temperRegexFormat) }
internal val JavaSupportCtx.regexCompiledFormatted by receiver { separateCode(temperRegexCompiledFormatted) }
internal val JavaSupportCtx.regexCompiledFind by receiver { separateCode(temperRegexCompiledFind) }
internal val JavaSupportCtx.regexCompiledFound by receiver { separateCode(temperRegexCompiledFound) }
internal val JavaSupportCtx.regexCompiledReplace by receiver { separateCode(temperRegexCompiledReplace) }
internal val JavaSupportCtx.regexCompiledSplit by receiver { separateCode(temperRegexCompiledSplit) }
internal val JavaSupportCtx.regexFormatterPushCodeTo by receiver { separateCode(temperRegexFormatterPushCodeTo) }

// Temporal support
internal val JavaSupportCtx.dateConstructor by receiver {
    inlineSupport(arity = 3) { pos, args, _ ->
        // docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html#of-int-int-int-
        javaTimeLocalDateOf.staticMethod(args[0], args[1], args[2], pos = pos)
    }
}
internal val JavaSupportCtx.dateToString by receiver {
    inlineSupport(arity = 1) { pos, args, _ ->
        args[0].method("toString", pos = pos)
    }
}
internal val JavaSupportCtx.dateGetYear by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        // LocalDate.getYear returns a proleptic year.  2 BC and before are negative.
        args[0].method("getYear", pos = pos)
    }
}
internal val JavaSupportCtx.dateGetMonth by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        // LocalDate.getMonth returns an instance of the Month enumeration
        // .getMonthValue returns an int.
        args[0].method("getMonthValue", pos = pos)
    }
}
internal val JavaSupportCtx.dateGetDay by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("getDayOfMonth", pos = pos)
    }
}

internal val JavaSupportCtx.dateGetDayOfWeek by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("getDayOfWeek", pos = pos)
            .method("getValue", pos = pos.rightEdge)
    }
}
internal val JavaSupportCtx.dateFromIsoString by receiver {
    inlineSupport(arity = 1) { pos, args, _ ->
        javaTimeLocalDateParse.staticMethod(args[0], pos = pos)
    }
}

internal val JavaSupportCtx.dateToday by receiver {
    inlineSupport(arity = 0, needsSelf = false) { pos, _, _ ->
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

internal val JavaSupportCtx.dateYearsBetween by receiver {
    inlineSupport(arity = 2, needsSelf = false) { pos, args, _ ->
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
internal val JavaSupportCtx.promiseBuilderBreakPromise by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method(
            pos = pos,
            methodName = "completeExceptionally",
            args = listOf(
                J.Argument(pos, temperBubbleMethod.staticMethod(listOf(), pos)),
            ),
        )
    }
}
internal val JavaSupportCtx.promiseBuilderComplete by receiver {
    inlineSupport(arity = 2, needsSelf = true) { pos, args, _ ->
        args[0].method("complete", args[1], pos = pos)
    }
}
internal val JavaSupportCtx.promiseBuilderGetPromise by receiver {
    inlineSupport(arity = 1, needsSelf = true) { _, args, _ ->
        // PromiseBuilder and Promise both connect to CompletableFuture, so
        // `myPromiseBuilder.getPromise()` is just `myPromiseBuilder`.
        args[0]
    }
}

// Testing support
internal val JavaSupportCtx.bail by receiver {
    inlineSupport(arity = 1) { pos, args, _ ->
        temperThrowAssertionError.staticMethod(args[0].method("messagesCombined"), pos = pos)
    }
}

val JavaLang.printFunction by receiver { separateCode(temperPrint, BuiltinOperatorId.Print) }
internal val JavaSupportCtx.getConsole by receiver {
    object : JavaInlineSupportCode(lang, baseName, arity = -1) {
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
internal val JavaSupportCtx.doNothing by receiver { separateCode(temperDoNothing) }

internal val JavaSupportCtx.empty by receiver {
    inlineSupport(arity = 0) { pos, _, _ ->
        javaUtilOptionalEmpty.staticMethod(emptyList(), pos)
    }
}

// Dense bit vectors
internal val JavaSupportCtx.denseBitVectorConstructor by receiver {
    inlineSupport(arity = -1) { pos, args, _ ->
        J.InstanceCreationExpr(pos, type = javaUtilBitSet.toClassType(pos), args = args.map(J.Expression::asArgument))
    }
}
internal val JavaSupportCtx.denseBitVectorGet by receiver {
    inlineSupport(arity = 2, needsSelf = true) { pos, args, _ ->
        args[0].method("get", args[1], pos = pos)
    }
}
internal val JavaSupportCtx.denseBitVectorSet by receiver {
    inlineSupport(arity = 3, needsSelf = true) { pos, args, _ ->
        args[0].method("set", args[1], args[2], pos = pos)
    }
}

// Deques
internal val JavaSupportCtx.dequeConstructor by receiver {
    inlineSupport(arity = -1) { pos, args, resultType, _ ->
        val implementation = if (resultType.hasNullableTypeActual) javaUtilLinkedList else javaUtilArrayDeque
        J.InstanceCreationExpr(
            pos,
            implementation.toClassType(pos, args = J.TypeArguments(pos)),
            args = args.map { it.expr.asArgument() },
        )
    }
}
internal val JavaSupportCtx.dequeAdd by receiver {
    inlineSupport(arity = 2, needsSelf = true) { pos, args, _ ->
        args[0].method("addLast", args[1], pos = pos)
    }
}
internal val JavaSupportCtx.dequeRemoveFirst by receiver { separateCode(temperDequeRemoveFirst) }

// Listed, List, ListBuilder
val JavaLang.listify: JavaSupportCode by receiver {
    if (atLeastJdk(JAVA9)) {
        // The Java immutable collections API, unfortunately, does not allow null elements.
        inlineSupport("listify", arity = -1) { pos, args, resultType, _ ->
            val implementation = if (resultType.hasNullableTypeActual) temperListOf else javaUtilListOf
            implementation.staticMethod(args.unpackArgs(), pos)
        }
    } else {
        separateCode(temperListOf)
    }
}

// Generator support
internal val JavaSupportCtx.generatorNext by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("get", pos = pos)
    }
}

internal val JavaSupportCtx.doneResult by receiver {
    separateCode(temperGeneratorDoneResultGet)
}

// Async support
internal val JavaLang.runAsync by receiver { separateCode(temperRunAsync) }

// std/net support
internal val JavaSupportCtx.netCoreStdNetSend by receiver { separateCode(temperNetCoreStdNetSend) }

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

internal val JavaSupportCtx.listFilter by receiver {
    inlineSupport(2, needsSelf = true) { pos, args, _, _ ->
        // listFilter(0=List<T>, 1=fun (T): Boolean)
        val sourceType: Jst = functionSimpleArgumentTypes(args[1].type).first
        temperListFilter.suffix(sourceType.shortCamelName).staticMethod(args.unpackArgs(), pos)
    }
}

@Suppress("MagicNumber") // arity
internal val JavaSupportCtx.listJoin by receiver {
    inlineSupport(3, needsSelf = true) { pos, args, _, _ ->
        // listJoin(0=List<T>, 1=delimiter, 2=fun (T): String)
        val sourceType: Jst = functionSimpleArgumentTypes(args[2].type).first
        temperListJoin.suffix(sourceType.shortCamelName).staticMethod(args.unpackArgs(), pos)
    }
}
internal val JavaSupportCtx.listMap by receiver {
    inlineSupport(2, needsSelf = true) { pos, args, _, _ ->
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
internal val JavaSupportCtx.listedReduce by receiver {
    inlineSupport(2, needsSelf = true) inline@{ pos, args, _, _ ->
        // listedReduce(0=List<T>, 1=fun (T, T): T)
        val (adjustedArgs, fnType) = adaptFn(args)
            ?: return@inline garbageExpr(pos, connectedKey!!, "$args")
        val type = functionSimpleArgumentTypes(fnType).first
        // See `fun simpleType` for expected names.
        temperListedReduce.suffix(type.shortCamelName)
            .staticMethod(adjustedArgs, pos)
    }
}
internal val JavaSupportCtx.listedReduceFrom by receiver {
    @Suppress("MagicNumber")
    inlineSupport(3, needsSelf = true) inline@{ pos, args, _, _ ->
        // listedReduce(0=List<T>, 1=U, 2=fun (U, T): U)
        val (adjustedArgs, fnType) = adaptFn(args)
            ?: return@inline garbageExpr(pos, connectedKey!!, "$args")
        val (inType, outType) = functionSimpleArgumentTypes(fnType, inputIndex = 1)
        temperListedReduce.suffix("${inType.shortCamelName}To${outType.shortCamelName}")
            .staticMethod(adjustedArgs, pos)
    }
}
internal val JavaSupportCtx.listSlice by receiver { separateCode(temperListSlice) }
internal val JavaSupportCtx.listSorted by receiver {
    // TODO This could potentially be factored along with core.type ListBuilder.sort().
    inlineSupport(2, needsSelf = true) inline@{ pos, args, _, _ ->
        val (adjustedArgs, fnType) = adaptFn(args)
            ?: return@inline garbageExpr(pos, connectedKey!!, "$args")
        val (inType, _) = functionSimpleArgumentTypes(fnType)
        when (inType) {
            Jst.JstInt -> temperListSorted.suffix(inType.shortCamelName).staticMethod(args.unpackArgs(), pos)
            else -> temperListSorted.staticMethod(adjustedArgs, pos)
        }
    }
}
internal val JavaSupportCtx.listGet by receiver { separateCode(temperListGet) }
internal val JavaSupportCtx.listGetOr by receiver { separateCode(temperListGetOr) }
internal val JavaSupportCtx.listLength by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("size", pos = pos)
    }
}
internal val JavaSupportCtx.listCopyOf by receiver {
    if (lang.atLeastJdk(JAVA9)) {
        inlineSupport(arity = 1) { pos, args, resultType, _ ->
            val implementation = if (resultType.hasNullableTypeActual) temperListCopyOf else javaUtilListCopyOf
            implementation.staticMethod(args.unpackArgs(), pos)
        }
    } else {
        separateCode(temperListCopyOf)
    }
}
internal val JavaSupportCtx.listedToList by receiver { separateCode(temperListedToList) }
internal val JavaSupportCtx.listBuilderMake by receiver {
    inlineSupport(arity = 0) { pos, _, _ ->
        J.InstanceCreationExpr(pos, javaUtilArrayList.toClassType(pos, args = J.TypeArguments(pos)), args = listOf())
    }
}
internal val JavaSupportCtx.listBuilderAdd by receiver { separateCode(temperListAdd) }
internal val JavaSupportCtx.listBuilderAddAll by receiver { separateCode(temperListAddAll) }
internal val JavaSupportCtx.listBuilderCopyOf by receiver {
    inlineSupport(arity = 1, needsSelf = false) { pos, args, _ ->
        J.InstanceCreationExpr(
            pos,
            javaUtilArrayList.toClassType(pos, args = J.TypeArguments(pos)),
            args = args.map { it.asArgument() },
        )
    }
}
internal val JavaSupportCtx.listBuilderRemoveLast by receiver { separateCode(temperListRemoveLast) }
internal val JavaSupportCtx.listBuilderReverse by receiver { separateCode(javaUtilCollectionsReverse) }
internal val JavaSupportCtx.listBuilderSort by receiver {
    // TODO This could potentially be factored along with core.type Listed.sorted().
    inlineSupport(2, needsSelf = true) inline@{ pos, args, _, _ ->
        val (adjustedArgs, fnType) = adaptFn(args)
            ?: return@inline garbageExpr(pos, "core.type ListBuilder.sort()", "$args")
        val (inType, _) = functionSimpleArgumentTypes(fnType)
        when (inType) {
            Jst.JstInt -> temperListSort.suffix(inType.shortCamelName).staticMethod(args.unpackArgs(), pos)
            else -> temperListSort.staticMethod(adjustedArgs, pos)
        }
    }
}
internal val JavaSupportCtx.listBuilderSplice by receiver { separateCode(temperListSplice) }

// Map, MapBuilder
internal val JavaSupportCtx.mapConstructor by receiver { separateCode(temperMapConstructor) }
internal val JavaSupportCtx.pairConstructor by receiver {
    inlineSupport(arity = 2) { pos, args, _ ->
        J.InstanceCreationExpr(
            pos,
            type = javaUtilSimpleImmutableEntry.toClassType(pos, J.TypeArguments(pos)),
            args = args.map { it.asArgument() },
        )
    }
}
internal val JavaSupportCtx.mappedLength by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        args[0].method("size", pos = pos)
    }
}
internal val JavaSupportCtx.mappedGet by receiver { separateCode(temperMappedGet) }
internal val JavaSupportCtx.mappedGetOr by receiver {
    inlineSupport(arity = 3, needsSelf = true) { pos, args, _ ->
        args[0].method("getOrDefault", args[1], args[2], pos = pos)
    }
}
internal val JavaSupportCtx.mappedHas by receiver {
    inlineSupport(arity = 2, needsSelf = true) { pos, args, _ ->
        args[0].method("containsKey", args[1], pos = pos)
    }
}
internal val JavaSupportCtx.mappedKeys by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
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
internal val JavaSupportCtx.mappedValues by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
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
internal val JavaSupportCtx.mappedToMap by receiver { separateCode(temperMappedToMap) }
internal val JavaSupportCtx.mappedToMapBuilder by receiver {
    inlineSupport(arity = 1, needsSelf = true) { pos, args, _ ->
        J.InstanceCreationExpr(
            pos = pos,
            type = javaUtilLinkedHashMap.toClassType(pos, J.TypeArguments(pos)),
            args = listOf(J.Argument(args[0].pos, args[0])),
        )
    }
}
internal val JavaSupportCtx.mappedToList by receiver { separateCode(temperMappedToList) }
internal val JavaSupportCtx.mappedToListBuilder by receiver { separateCode(temperMappedToListBuilder) }
internal val JavaSupportCtx.mappedToListWith by receiver { separateCode(temperMappedToListWith) }
internal val JavaSupportCtx.mappedToListBuilderWith by receiver { separateCode(temperMappedToListBuilderWith) }
internal val JavaSupportCtx.mappedForEach by receiver { separateCode(temperMappedForEach) }
internal val JavaSupportCtx.mapBuilderRemove by receiver { separateCode(temperMapBuilderRemove) }
internal val JavaSupportCtx.mapBuilderSet by receiver {
    inlineSupport(arity = 3, needsSelf = true) { pos, args, _ ->
        args[0].method("put", args[1], args[2], pos = pos)
    }
}
internal val JavaSupportCtx.mapBuilderConstructor by receiver {
    inlineSupport(arity = 0) { pos, _, _ ->
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

private val connections: Map<String, ((JavaLang) -> JavaSupportCode)> = buildMap {
    fun define(connectedKey: String, factory: (JavaSupportCtx) -> JavaSupportCode) {
        this[connectedKey] = { factory(JavaSupportCtx(it, connectedKey, null)) }
    }
    define("core.getConsole()") { it.getConsole }
    define("core.type Boolean.toString()") { it.booleanToString }
    // "core.type Console.log()" to null,
    define("std/temporal.type Date.constructor()") { it.dateConstructor }
    define("std/temporal.type Date.fromIsoString()") { it.dateFromIsoString }
    define("std/temporal.type Date.day") { it.dateGetDay }
    define("std/temporal.type Date.get dayOfWeek()") { it.dateGetDayOfWeek }
    define("std/temporal.type Date.month") { it.dateGetMonth }
    define("std/temporal.type Date.year") { it.dateGetYear }
    define("std/temporal.type Date.toString()") { it.dateToString }
    define("std/temporal.type Date.today()") { it.dateToday }
    define("std/temporal.type Date.yearsBetween()") { it.dateYearsBetween }
    define("core.type DenseBitVector.constructor()") { it.denseBitVectorConstructor }
    define("core.type DenseBitVector.get()") { it.denseBitVectorGet }
    define("core.type DenseBitVector.set()") { it.denseBitVectorSet }
    define("core.type Deque.add()") { it.dequeAdd }
    define("core.type Deque.constructor()") { it.dequeConstructor }
    define("core.type Deque.get isEmpty()") { it.genericIsEmpty }
    define("core.type Deque.removeFirst()") { it.dequeRemoveFirst }
    define("core.type Float64.abs()") { it.float64Abs }
    define("core.type Float64.acos()") { it.float64Acos }
    define("core.type Float64.asin()") { it.float64Asin }
    define("core.type Float64.atan()") { it.float64Atan }
    define("core.type Float64.atan2()") { it.float64Atan2 }
    define("core.type Float64.ceil()") { it.float64Ceil }
    define("core.type Float64.cos()") { it.float64Cos }
    define("core.type Float64.cosh()") { it.float64Cosh }
    define("core.type Float64.e") { it.float64E }
    define("core.type Float64.exp()") { it.float64Exp }
    define("core.type Float64.expm1()") { it.float64Expm1 }
    define("core.type Float64.floor()") { it.float64Floor }
    define("core.type Float64.log()") { it.float64Log }
    define("core.type Float64.log10()") { it.float64Log10 }
    define("core.type Float64.log1p()") { it.float64Log1p }
    define("core.type Float64.max()") { it.float64Max }
    define("core.type Float64.min()") { it.float64Min }
    define("core.type Float64.near()") { it.float64Near }
    define("core.type Float64.pi") { it.float64Pi }
    define("core.type Float64.round()") { it.float64Round }
    define("core.type Float64.sign()") { it.float64Sign }
    define("core.type Float64.sin()") { it.float64Sin }
    define("core.type Float64.sinh()") { it.float64Sinh }
    define("core.type Float64.sqrt()") { it.float64Sqrt }
    define("core.type Float64.tan()") { it.float64Tan }
    define("core.type Float64.tanh()") { it.float64Tanh }
    define("core.type Float64.toInt32()") { it.float64ToInt }
    define("core.type Float64.toInt32Unsafe()") { it.float64ToIntUnsafe }
    define("core.type Float64.toInt64()") { it.float64ToInt64 }
    define("core.type Float64.toInt64Unsafe()") { it.float64ToInt64Unsafe }
    define("core.type Float64.toString()") { it.float64ToString }
    define("core.type Generator.next()") { it.generatorNext }
    define("core.type Int32.max()") { it.intMax }
    define("core.type Int32.min()") { it.intMin }
    define("core.type Int32.signum()") { it.intSignum }
    define("core.type Int32.toFloat64()") { it.intToFloat64 }
    define("core.type Int32.toInt64()") { it.intToInt64 }
    define("core.type Int32.toString()") { it.intToString }
    define("core.type Int64.max()") { it.int64Max }
    define("core.type Int64.min()") { it.int64Min }
    define("core.type Int64.toInt32()") { it.int64ToInt32 }
    define("core.type Int64.toInt32Unsafe()") { it.int64ToInt32Unsafe }
    define("core.type Int64.toFloat64()") { it.int64ToFloat64 }
    define("core.type Int64.toFloat64Unsafe()") { it.int64ToFloat64Unsafe }
    define("core.type Int64.toString()") { it.int64ToString }
    define("core.type List.get()") { it.listGet }
    define("core.type List.get length()") { it.listLength }
    define("core.type List.toList()") { it.identity }
    define("core.type List.toListBuilder()") { it.listBuilderCopyOf }
    define("core.type ListBuilder.add()") { it.listBuilderAdd }
    define("core.type ListBuilder.addAll()") { it.listBuilderAddAll }
    define("core.type ListBuilder.constructor()") { it.listBuilderMake }
    define("core.type ListBuilder.get length()") { it.listLength }
    define("core.type ListBuilder.removeLast()") { it.listBuilderRemoveLast }
    define("core.type ListBuilder.reverse()") { it.listBuilderReverse }
    define("core.type ListBuilder.sort()") { it.listBuilderSort }
    define("core.type ListBuilder.splice()") { it.listBuilderSplice }
    define("core.type ListBuilder.toList()") { it.listCopyOf }
    define("core.type ListBuilder.toListBuilder()") { it.listBuilderCopyOf }
    define("core.type Listed.filter()") { it.listFilter }
    define("core.type Listed.get()") { it.listGet }
    define("core.type Listed.getOr()") { it.listGetOr }
    define("core.type Listed.get isEmpty()") { it.genericIsEmpty }
    define("core.type Listed.join()") { it.listJoin }
    define("core.type Listed.get length()") { it.listLength }
    define("core.type Listed.map()") { it.listMap }
    define("core.type Listed.reduce()") { it.listedReduce }
    define("core.type Listed.reduceFrom()") { it.listedReduceFrom }
    define("core.type Listed.slice()") { it.listSlice }
    define("core.type Listed.sorted()") { it.listSorted }
    define("core.type Listed.toList()") { it.listedToList }
    define("core.type Listed.toListBuilder()") { it.listBuilderCopyOf }
    define("core.type Map.constructor()") { it.mapConstructor }
    define("core.type MapBuilder.constructor()") { it.mapBuilderConstructor }
    define("core.type MapBuilder.remove()") { it.mapBuilderRemove }
    define("core.type MapBuilder.set()") { it.mapBuilderSet }
    define("core.type Mapped.forEach()") { it.mappedForEach }
    define("core.type Mapped.get()") { it.mappedGet }
    define("core.type Mapped.getOr()") { it.mappedGetOr }
    define("core.type Mapped.has()") { it.mappedHas }
    define("core.type Mapped.keys()") { it.mappedKeys }
    define("core.type Mapped.get length()") { it.mappedLength }
    define("core.type Mapped.toList()") { it.mappedToList }
    define("core.type Mapped.toListBuilder()") { it.mappedToListBuilder }
    define("core.type Mapped.toListBuilderWith()") { it.mappedToListBuilderWith }
    define("core.type Mapped.toListWith()") { it.mappedToListWith }
    define("core.type Mapped.toMap()") { it.mappedToMap }
    define("core.type Mapped.toMapBuilder()") { it.mappedToMapBuilder }
    define("core.type Mapped.values()") { it.mappedValues }
    define("core.type Pair.constructor()") { it.pairConstructor }
    define("core.type PromiseBuilder.breakPromise()") { it.promiseBuilderBreakPromise }
    define("core.type PromiseBuilder.complete()") { it.promiseBuilderComplete }
    define("core.type PromiseBuilder.get promise()") { it.promiseBuilderGetPromise }
    define("std/regex.type RegexFormatter.regexCompileFormatted()") { it.regexCompiledFormatted }
    define("std/regex.type Regex.compiledFind()") { it.regexCompiledFind }
    define("std/regex.type Regex.compiledFound()") { it.regexCompiledFound }
    define("std/regex.type Regex.compiledReplace()") { it.regexCompiledReplace }
    define("std/regex.type Regex.compiledSplit()") { it.regexCompiledSplit }
    define("std/regex.type Regex.format()") { it.regexFormat }
    // "std/regex.type RegexFormatter.adjustCodeSet()" to null,
    // "std/regex.type RegexFormatter.pushCaptureName()" to null,
    define("std/regex.type RegexFormatter.pushCodeTo()") { it.regexFormatterPushCodeTo }
    define("core.type SafeGenerator.next()") { it.generatorNext }
    define("core.type SafeGenerator.nextSafe()") { it.generatorNext }
    define("core.type String.begin") { it.stringBegin }
    define("core.type String.countBetween()") { it.stringCountBetween }
    define("core.type String.get end()") { it.stringEnd }
    define("core.type String.forEach()") { it.stringForEach }
    define("core.type String.fromCodePoint()") { it.stringFromCodePoint }
    define("core.type String.fromCodePoints()") { it.stringFromCodePoints }
    define("core.type String.get()") { it.stringGet }
    define("core.type String.hasAtLeast()") { it.stringHasAtLeast }
    define("core.type String.hasIndex()") { it.stringHasIndex }
    define("core.type String.get isEmpty()") { it.genericIsEmpty }
    define("core.type String.next()") { it.stringNext }
    define("core.type String.prev()") { it.stringPrev }
    define("core.type String.step()") { it.stringStep }
    define("core.type String.slice()") { it.stringSlice }
    define("core.type String.split()") { it.stringSplit }
    define("core.type String.toFloat64()") { it.stringToFloat64 }
    define("core.type String.toInt32()") { it.stringToInt }
    define("core.type String.toInt64()") { it.stringToInt64 }
    define("core.type String.toString()") { it.identity }
    define("core.type StringBuilder.append()") { it.stringBuilderAppend }
    define("core.type StringBuilder.appendBetween()") { it.stringBuilderAppendBetween }
    define("core.type StringBuilder.appendCodePoint()") { it.stringBuilderAppendCodePoint }
    define("core.type StringBuilder.clear()") { it.stringBuilderClear }
    define("core.type StringBuilder.constructor()") { it.stringBuilderConstructor }
    define("core.type StringBuilder.get end()") { it.stringBuilderEnd }
    define("core.type StringBuilder.toString()") { it.stringBuilderToString }
    define("core.type StringIndex.none") { it.stringIndexNone }
    define("core.type StringIndexOption.compareTo()") { it.stringIndexOptionCompareTo }
    define("core.type StringIndexOption.eq()") { it.stringIndexOptionCompareToEq }
    define("std/testing.type Test.bail()") { it.bail }
    define("core.doneResult()") { it.doneResult }
    define("core.empty()") { it.empty }
    define("core.ignore()") { it.doNothing }
    define("std/net.sendRequest()") { it.netCoreStdNetSend }
}

internal data class JavaSupportCtx(
    val lang: JavaLang,
    val connectedKey: String?,
    val builtinOperatorId: BuiltinOperatorId?,
) {
    val baseName: String get() = connectedKey ?: builtinOperatorId!!.name
}
