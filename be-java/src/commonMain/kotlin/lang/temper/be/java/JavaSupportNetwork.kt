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
            BuiltinOperatorId.BitwiseAnd -> bitwiseAnd
            BuiltinOperatorId.BitwiseOr -> bitwiseOr
            BuiltinOperatorId.BooleanNegation -> booleanNegation
            BuiltinOperatorId.Listify -> listify
            BuiltinOperatorId.Bubble, BuiltinOperatorId.Panic -> throwBubble
            BuiltinOperatorId.AdaptGeneratorFn -> adaptGeneratorFn
            BuiltinOperatorId.SafeAdaptGeneratorFn -> safeAdaptGeneratorFn
            BuiltinOperatorId.Async -> runAsync
            null -> null
            else -> TODO("$opId not supported")
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
            "Date" -> javaTimeLocalDate
            "Promise", "PromiseBuilder" -> javaUtilConcurrentCompletableFuture
            "StringBuilder" -> javaLangStringBuilder
            "NetResponse" -> temperNetResponse
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
    inlineSupport(BuiltinOperatorId.BitwiseAnd, 2) { pos, args ->
        JavaOperator.And.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.bitwiseOr by receiver {
    inlineSupport(BuiltinOperatorId.BitwiseOr, 2) { pos, args ->
        JavaOperator.InclusiveOr.infix(args[0], args[1], pos = pos)
    }
}
val JavaLang.booleanToString by receiver {
    inlineSupport("Boolean::toString", 1, needsSelf = true) { pos, args ->
        javaLangBooleanToString.staticMethod(listOf(args[0].asArgument()), pos = pos)
    }
}
val JavaLang.intToFloat64 by receiver {
    inlineSupport("Int32::toFloat64", -1, needsSelf = true) { pos, args ->
        Primitive.JavaDouble.cast(args[0], pos)
    }
}
val JavaLang.intToInt64 by receiver {
    inlineSupport("Int32::toInt64", -1, needsSelf = true) { pos, args ->
        Primitive.JavaLong.cast(args[0], pos)
    }
}
val JavaLang.intToString by receiver {
    inlineSupport("Int32::toString", -1, needsSelf = true) { pos, args ->
        javaLangIntegerToString.staticMethod(args.map(J.Expression::asArgument), pos = pos)
    }
}
val JavaLang.int64ToFloat64 by receiver { separateCode(temperInt64ToFloat64) }
val JavaLang.int64ToFloat64Unsafe by receiver {
    inlineSupport("Int64::toFloat64Unsafe", -1, needsSelf = true) { pos, args ->
        Primitive.JavaDouble.cast(args[0], pos)
    }
}
val JavaLang.int64ToInt32 by receiver { separateCode(temperInt64ToInt) }
val JavaLang.int64ToInt32Unsafe by receiver {
    inlineSupport("Int64::toInt32Unsafe", -1, needsSelf = true) { pos, args ->
        Primitive.JavaInt.cast(args[0], pos)
    }
}
val JavaLang.int64ToString by receiver {
    inlineSupport("Int64::toString", -1, needsSelf = true) { pos, args ->
        javaLangLongToString.staticMethod(args.map(J.Expression::asArgument), pos = pos)
    }
}
val JavaLang.float64E by receiver {
    inlineSupport("Float64::e", 0) { pos, _ -> javaMathE.toNameExpr(pos) }
}
val JavaLang.float64Pi by receiver {
    inlineSupport("Float64::pi", 0) { pos, _ -> javaMathPi.toNameExpr(pos) }
}
val JavaLang.float64Abs by receiver {
    inlineSupport("Float64::abs", 1) { pos, args -> javaMathAbs.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Acos by receiver {
    inlineSupport("Float64::acos", 1) { pos, args -> javaMathAcos.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Asin by receiver {
    inlineSupport("Float64::asin", 1) { pos, args -> javaMathAsin.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Atan by receiver {
    inlineSupport("Float64::atan", 1) { pos, args -> javaMathAtan.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Atan2 by receiver {
    inlineSupport("Float64::atan2", 2) { pos, args -> javaMathAtan2.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.float64Ceil by receiver {
    inlineSupport("Float64::ceil", 1) { pos, args -> javaMathCeil.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Cos by receiver {
    inlineSupport("Float64::cos", 1) { pos, args -> javaMathCos.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Cosh by receiver {
    inlineSupport("Float64::cosh", 1) { pos, args -> javaMathCosh.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Exp by receiver {
    inlineSupport("Float64::exp", 1) { pos, args -> javaMathExp.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Expm1 by receiver {
    inlineSupport("Float64::expm1", 1) { pos, args -> javaMathExpm1.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Floor by receiver {
    inlineSupport("Float64::floor", 1) { pos, args -> javaMathFloor.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Log by receiver {
    inlineSupport("Float64::log", 1) { pos, args -> javaMathLog.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Log10 by receiver {
    inlineSupport("Float64::log10", 1) { pos, args -> javaMathLog10.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Log1p by receiver {
    inlineSupport("Float64::log1p", 1) { pos, args -> javaMathLog1p.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Max by receiver {
    inlineSupport("Float64::max", 2) { pos, args -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.float64Min by receiver {
    inlineSupport("Float64::min", 2) { pos, args -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.float64Near by receiver { separateCode(temperFloat64Near) }
val JavaLang.float64Round by receiver {
    inlineSupport("Float64::round", 1) { pos, args -> javaMathRound.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sign by receiver {
    inlineSupport("Float64::sign", 1) { pos, args -> javaMathSignum.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sin by receiver {
    inlineSupport("Float64::sin", 1) { pos, args -> javaMathSin.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sinh by receiver {
    inlineSupport("Float64::sinh", 1) { pos, args -> javaMathSinh.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Sqrt by receiver {
    inlineSupport("Float64::sqrt", 1) { pos, args -> javaMathSqrt.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Tan by receiver {
    inlineSupport("Float64::tan", 1) { pos, args -> javaMathTan.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64Tanh by receiver {
    inlineSupport("Float64::tanh", 1) { pos, args -> javaMathTanh.staticMethod(args[0], pos = pos) }
}
val JavaLang.float64ToInt by receiver { separateCode(temperFloat64ToInt) }
val JavaLang.float64ToIntUnsafe by receiver {
    inlineSupport("Float64::toInt32Unsafe", -1, needsSelf = true) { pos, args ->
        Primitive.JavaInt.cast(args[0], pos)
    }
}
val JavaLang.float64ToInt64 by receiver { separateCode(temperFloat64ToInt64) }
val JavaLang.float64ToInt64Unsafe by receiver {
    inlineSupport("Float64::toInt64Unsafe", -1, needsSelf = true) { pos, args ->
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
    inlineSupport("Int32::max", 2) { pos, args -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.intMin by receiver {
    inlineSupport("Int32::min", 2) { pos, args -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.int64Max by receiver {
    inlineSupport("Int64::max", 2) { pos, args -> javaMathMax.staticMethod(args[0], args[1], pos = pos) }
}
val JavaLang.int64Min by receiver {
    inlineSupport("Int64::min", 2) { pos, args -> javaMathMin.staticMethod(args[0], args[1], pos = pos) }
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
    inlineSupport("String::end", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("length", pos = pos)
    }
}
val JavaLang.stringBegin by receiver {
    inlineSupport("String::begin", 0) { pos, _ ->
        J.IntegerLiteral(pos, 0)
    }
}
val JavaLang.stringIndexNone by receiver {
    inlineSupport("StringIndex::none", 0) { pos, _ ->
        J.IntegerLiteral(pos, -1)
    }
}
val JavaLang.stringGet by receiver {
    inlineSupport("String::get", arity = 2, needsSelf = true) { pos, args ->
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
    inlineSupport("StringBuilder::constructor", arity = -1) { pos, _ ->
        J.InstanceCreationExpr(pos, type = javaLangStringBuilder.toClassType(pos), args = emptyList())
    }
}
val JavaLang.stringBuilderAppend by receiver {
    inlineSupport("StringBuilder::append", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("append", args[1], pos = pos)
    }
}
val JavaLang.stringBuilderAppendBetween by receiver {
    separateCode(temperStringBuilderAppendBetween)
}
val JavaLang.stringBuilderAppendCodePoint by receiver {
    separateCode(temperStringBuilderAppendCodePoint)
}
val JavaLang.stringBuilderToString by receiver {
    inlineSupport("StringBuilder::toString", arity = 1, needsSelf = true) { pos, args ->
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
    comparison("StringIndexOption::compareTo::eq", JavaOperator.Equals)
}
val JavaLang.stringIndexOptionCompareToGe by receiver {
    comparison("StringIndexOption::compareTo::ge", JavaOperator.GreaterEquals)
}
val JavaLang.stringIndexOptionCompareToGt by receiver {
    comparison("StringIndexOption::compareTo::gt", JavaOperator.GreaterThan)
}
val JavaLang.stringIndexOptionCompareToLe by receiver {
    comparison("StringIndexOption::compareTo::le", JavaOperator.LessEquals)
}
val JavaLang.stringIndexOptionCompareToLt by receiver {
    comparison("StringIndexOption::compareTo::lt", JavaOperator.LessThan)
}
val JavaLang.stringIndexOptionCompareToNe by receiver {
    comparison("StringIndexOption::compareTo::ne", JavaOperator.NotEquals)
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
    inlineSupport("Date::constructor", arity = 3) { pos, args ->
        // docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html#of-int-int-int-
        javaTimeLocalDateOf.staticMethod(args[0], args[1], args[2], pos = pos)
    }
}
val JavaLang.dateToString by receiver {
    inlineSupport("Date::toString", arity = 1) { pos, args ->
        args[0].method("toString", pos = pos)
    }
}
val JavaLang.dateGetYear by receiver {
    inlineSupport("Date::getYear", arity = 1, needsSelf = true) { pos, args ->
        // LocalDate.getYear returns a proleptic year.  2 BC and before are negative.
        args[0].method("getYear", pos = pos)
    }
}
val JavaLang.dateGetMonth by receiver {
    inlineSupport("Date::getMonth", arity = 1, needsSelf = true) { pos, args ->
        // LocalDate.getMonth returns an instance of the Month enumeration
        // .getMonthValue returns an int.
        args[0].method("getMonthValue", pos = pos)
    }
}
val JavaLang.dateGetDay by receiver {
    inlineSupport("Date::getDay", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("getDayOfMonth", pos = pos)
    }
}

val JavaLang.dateGetDayOfWeek by receiver {
    inlineSupport("Date::getDayOfWeek", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("getDayOfWeek", pos = pos)
            .method("getValue", pos = pos.rightEdge)
    }
}
val JavaLang.dateFromIsoString by receiver {
    inlineSupport("Date::fromIsoString", arity = 1) { pos, args ->
        javaTimeLocalDateParse.staticMethod(args[0], pos = pos)
    }
}

val JavaLang.dateToday by receiver {
    inlineSupport("Date::today", arity = 0, needsSelf = false) { pos, _ ->
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
    inlineSupport("Date::yearsBetween", arity = 2, needsSelf = false) { pos, args ->
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
    inlineSupport("PromiseBuilder::breakPromise", arity = 1, needsSelf = true) { pos, args ->
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
    inlineSupport("PromiseBuilder::complete", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("complete", args[1], pos = pos)
    }
}
val JavaLang.promiseBuilderGetPromise by receiver {
    inlineSupport("PromiseBuilder::getPromise", arity = 1, needsSelf = true) { _, args ->
        // PromiseBuilder and Promise both connect to CompletableFuture, so
        // `myPromiseBuilder.getPromise()` is just `myPromiseBuilder`.
        args[0]
    }
}

// Testing support
val JavaLang.bail by receiver {
    inlineSupport("Test::bail", arity = 1) { pos, args ->
        temperThrowAssertionError.staticMethod(args[0].method("messagesCombined"), pos = pos)
    }
}

val JavaLang.printFunction by receiver { separateCode(temperPrint, BuiltinOperatorId.Print) }
val JavaLang.getConsole by receiver {
    object : JavaInlineSupportCode(this, "::getConsole", arity = -1) {
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
    inlineSupport("empty", arity = 0) { pos, _ ->
        javaUtilOptionalEmpty.staticMethod(emptyList(), pos)
    }
}

// Dense bit vectors
val JavaLang.denseBitVectorConstructor by receiver {
    inlineSupport("DenseBitVector::constructor", arity = -1) { pos, args ->
        J.InstanceCreationExpr(pos, type = javaUtilBitSet.toClassType(pos), args = args.map(J.Expression::asArgument))
    }
}
val JavaLang.denseBitVectorGet by receiver {
    inlineSupport("DenseBitVector::get", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("get", args[1], pos = pos)
    }
}
val JavaLang.denseBitVectorSet by receiver {
    inlineSupport("DenseBitVector::set", arity = 3, needsSelf = true) { pos, args ->
        args[0].method("set", args[1], args[2], pos = pos)
    }
}

// Deques
val JavaLang.dequeConstructor by receiver {
    inlineSupport("Deque::constructor", arity = -1) { pos, args, resultType ->
        val implementation = if (resultType.hasNullableTypeActual) javaUtilLinkedList else javaUtilArrayDeque
        J.InstanceCreationExpr(
            pos,
            implementation.toClassType(pos, args = J.TypeArguments(pos)),
            args = args.map { it.expr.asArgument() },
        )
    }
}
val JavaLang.dequeAdd by receiver {
    inlineSupport("Deque::add", arity = 2, needsSelf = true) { pos, args ->
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
    inlineSupport("Generator::next", arity = 1, needsSelf = true) { pos, args ->
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
    inlineSupport("List::filter", 2, needsSelf = true) { pos, args, _ ->
        // listFilter(0=List<T>, 1=fun (T): Boolean)
        val sourceType: Jst = functionSimpleArgumentTypes(args[1].type).first
        temperListFilter.suffix(sourceType.shortCamelName).staticMethod(args.unpackArgs(), pos)
    }
}

@Suppress("MagicNumber") // arity
val JavaLang.listJoin by receiver {
    inlineSupport("List::join", 3, needsSelf = true) { pos, args, _ ->
        // listJoin(0=List<T>, 1=delimiter, 2=fun (T): String)
        val sourceType: Jst = functionSimpleArgumentTypes(args[2].type).first
        temperListJoin.suffix(sourceType.shortCamelName).staticMethod(args.unpackArgs(), pos)
    }
}
val JavaLang.listMap by receiver {
    inlineSupport("List::map", 2, needsSelf = true) { pos, args, _ ->
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
val JavaLang.listMapDropping by receiver {
    inlineSupport("List::mapDropping", 2, needsSelf = true) { pos, args, _ ->
        // listMapDropping(0=List<T>, 1=fun (T): U)
        val (inType, outType) = functionSimpleArgumentTypes(args[1].type)
        val fromType = when (val name = inType.shortCamelName) {
            "Bool", "Long" -> "Obj"
            else -> name
        }
        val toType = outType.shortCamelName
        temperListMapDropping.suffix("${fromType}To${toType}")
            .staticMethod(args.unpackArgs(), pos)
    }
}
val JavaLang.listedReduce by receiver {
    inlineSupport("Listed::reduce", 2, needsSelf = true) inline@{ pos, args, _ ->
        // listedReduce(0=List<T>, 1=fun (T, T): T)
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "Listed::reduce", "$args")
        val type = functionSimpleArgumentTypes(fnType).first
        // See `fun simpleType` for expected names.
        temperListedReduce.suffix(type.shortCamelName)
            .staticMethod(adjustedArgs, pos)
    }
}
val JavaLang.listedReduceFrom by receiver {
    @Suppress("MagicNumber")
    inlineSupport("Listed::reduceFrom", 3, needsSelf = true) inline@{ pos, args, _ ->
        // listedReduce(0=List<T>, 1=U, 2=fun (U, T): U)
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "Listed::reduceFrom", "$args")
        val (inType, outType) = functionSimpleArgumentTypes(fnType, inputIndex = 1)
        temperListedReduce.suffix("${inType.shortCamelName}To${outType.shortCamelName}")
            .staticMethod(adjustedArgs, pos)
    }
}
val JavaLang.listSlice by receiver { separateCode(temperListSlice) }
val JavaLang.listSorted by receiver {
    // TODO This could potentially be factored along with ListBuilder::sort.
    inlineSupport("Listed::sorted", 2, needsSelf = true) inline@{ pos, args, _ ->
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "Listed::sorted", "$args")
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
    inlineSupport("List::length", arity = 1, needsSelf = true) { pos, args ->
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
    inlineSupport("ListBuilder::constructor", arity = 0) { pos, _ ->
        J.InstanceCreationExpr(pos, javaUtilArrayList.toClassType(pos, args = J.TypeArguments(pos)), args = listOf())
    }
}
val JavaLang.listBuilderAdd by receiver { separateCode(temperListAdd) }
val JavaLang.listBuilderAddAll by receiver { separateCode(temperListAddAll) }
val JavaLang.listBuilderCopyOf by receiver {
    inlineSupport("ListBuilder::toListBuilder", arity = 1, needsSelf = false) { pos, args ->
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
    // TODO This could potentially be factored along with Listed::sorted.
    inlineSupport("ListBuilder::sort", 2, needsSelf = true) inline@{ pos, args, _ ->
        val (adjustedArgs, fnType) = adaptFn(args) ?: return@inline garbageExpr(pos, "ListBuilder::sort", "$args")
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
    inlineSupport("Pair::constructor", arity = 2) { pos, args ->
        J.InstanceCreationExpr(
            pos,
            type = javaUtilSimpleImmutableEntry.toClassType(pos, J.TypeArguments(pos)),
            args = args.map { it.asArgument() },
        )
    }
}
val JavaLang.mappedLength by receiver {
    inlineSupport("Mapped::length", arity = 1, needsSelf = true) { pos, args ->
        args[0].method("size", pos = pos)
    }
}
val JavaLang.mappedGet by receiver { separateCode(temperMappedGet) }
val JavaLang.mappedGetOr by receiver {
    inlineSupport("Mapped::getOr", arity = 3, needsSelf = true) { pos, args ->
        args[0].method("getOrDefault", args[1], args[2], pos = pos)
    }
}
val JavaLang.mappedHas by receiver {
    inlineSupport("Mapped::has", arity = 2, needsSelf = true) { pos, args ->
        args[0].method("containsKey", args[1], pos = pos)
    }
}
val JavaLang.mappedKeys by receiver {
    inlineSupport("Mapped::keys", arity = 1, needsSelf = true) { pos, args ->
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
    inlineSupport("Mapped::values", arity = 1, needsSelf = true) { pos, args ->
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
    inlineSupport("Mapped::toMapBuilder", arity = 1, needsSelf = true) { pos, args ->
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
    inlineSupport("MapBuilder::set", arity = 3, needsSelf = true) { pos, args ->
        args[0].method("put", args[1], args[2], pos = pos)
    }
}
val JavaLang.mapBuilderConstructor by receiver {
    inlineSupport("MapBuilder::constructor", arity = 0) { pos, _ ->
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
    "::getConsole" to { it.getConsole },
    "Boolean::toString" to { it.booleanToString },
    // "Console::log" to null,
    "Date::constructor" to { it.dateConstructor },
    "Date::fromIsoString" to { it.dateFromIsoString },
    "Date::getDay" to { it.dateGetDay },
    "Date::getDayOfWeek" to { it.dateGetDayOfWeek },
    "Date::getMonth" to { it.dateGetMonth },
    "Date::getYear" to { it.dateGetYear },
    "Date::toString" to { it.dateToString },
    "Date::today" to { it.dateToday },
    "Date::yearsBetween" to { it.dateYearsBetween },
    "DenseBitVector::constructor" to { it.denseBitVectorConstructor },
    "DenseBitVector::get" to { it.denseBitVectorGet },
    "DenseBitVector::set" to { it.denseBitVectorSet },
    "Deque::add" to { it.dequeAdd },
    "Deque::constructor" to { it.dequeConstructor },
    "Deque::isEmpty" to { it.genericIsEmpty },
    "Deque::removeFirst" to { it.dequeRemoveFirst },
    "Float64::abs" to { it.float64Abs },
    "Float64::acos" to { it.float64Acos },
    "Float64::asin" to { it.float64Asin },
    "Float64::atan" to { it.float64Atan },
    "Float64::atan2" to { it.float64Atan2 },
    "Float64::ceil" to { it.float64Ceil },
    "Float64::cos" to { it.float64Cos },
    "Float64::cosh" to { it.float64Cosh },
    "Float64::e" to { it.float64E },
    "Float64::exp" to { it.float64Exp },
    "Float64::expm1" to { it.float64Expm1 },
    "Float64::floor" to { it.float64Floor },
    "Float64::log" to { it.float64Log },
    "Float64::log10" to { it.float64Log10 },
    "Float64::log1p" to { it.float64Log1p },
    "Float64::max" to { it.float64Max },
    "Float64::min" to { it.float64Min },
    "Float64::near" to { it.float64Near },
    "Float64::pi" to { it.float64Pi },
    "Float64::round" to { it.float64Round },
    "Float64::sign" to { it.float64Sign },
    "Float64::sin" to { it.float64Sin },
    "Float64::sinh" to { it.float64Sinh },
    "Float64::sqrt" to { it.float64Sqrt },
    "Float64::tan" to { it.float64Tan },
    "Float64::tanh" to { it.float64Tanh },
    "Float64::toInt32" to { it.float64ToInt },
    "Float64::toInt32Unsafe" to { it.float64ToIntUnsafe },
    "Float64::toInt64" to { it.float64ToInt64 },
    "Float64::toInt64Unsafe" to { it.float64ToInt64Unsafe },
    "Float64::toString" to { it.float64ToString },
    "Generator::next" to { it.generatorNext },
    "Int32::max" to { it.intMax },
    "Int32::min" to { it.intMin },
    "Int32::toFloat64" to { it.intToFloat64 },
    "Int32::toInt64" to { it.intToInt64 },
    "Int32::toString" to { it.intToString },
    "Int64::max" to { it.int64Max },
    "Int64::min" to { it.int64Min },
    "Int64::toInt32" to { it.int64ToInt32 },
    "Int64::toInt32Unsafe" to { it.int64ToInt32Unsafe },
    "Int64::toFloat64" to { it.int64ToFloat64 },
    "Int64::toFloat64Unsafe" to { it.int64ToFloat64Unsafe },
    "Int64::toString" to { it.int64ToString },
    "List::get" to { it.listGet },
    "List::length" to { it.listLength },
    "List::toList" to { it.identity },
    "List::toListBuilder" to { it.listBuilderCopyOf },
    "ListBuilder::add" to { it.listBuilderAdd },
    "ListBuilder::addAll" to { it.listBuilderAddAll },
    "ListBuilder::constructor" to { it.listBuilderMake },
    "ListBuilder::length" to { it.listLength },
    "ListBuilder::removeLast" to { it.listBuilderRemoveLast },
    "ListBuilder::reverse" to { it.listBuilderReverse },
    "ListBuilder::sort" to { it.listBuilderSort },
    "ListBuilder::splice" to { it.listBuilderSplice },
    "ListBuilder::toList" to { it.listCopyOf },
    "ListBuilder::toListBuilder" to { it.listBuilderCopyOf },
    "Listed::filter" to { it.listFilter },
    "Listed::get" to { it.listGet },
    "Listed::getOr" to { it.listGetOr },
    "Listed::isEmpty" to { it.genericIsEmpty },
    "Listed::join" to { it.listJoin },
    "Listed::length" to { it.listLength },
    "Listed::map" to { it.listMap },
    "Listed::mapDropping" to { it.listMapDropping },
    "Listed::reduce" to { it.listedReduce },
    "Listed::reduceFrom" to { it.listedReduceFrom },
    "Listed::slice" to { it.listSlice },
    "Listed::sorted" to { it.listSorted },
    "Listed::toList" to { it.listedToList },
    "Listed::toListBuilder" to { it.listBuilderCopyOf },
    "Map::constructor" to { it.mapConstructor },
    "MapBuilder::constructor" to { it.mapBuilderConstructor },
    "MapBuilder::remove" to { it.mapBuilderRemove },
    "MapBuilder::set" to { it.mapBuilderSet },
    "Mapped::forEach" to { it.mappedForEach },
    "Mapped::get" to { it.mappedGet },
    "Mapped::getOr" to { it.mappedGetOr },
    "Mapped::has" to { it.mappedHas },
    "Mapped::keys" to { it.mappedKeys },
    "Mapped::length" to { it.mappedLength },
    "Mapped::toList" to { it.mappedToList },
    "Mapped::toListBuilder" to { it.mappedToListBuilder },
    "Mapped::toListBuilderWith" to { it.mappedToListBuilderWith },
    "Mapped::toListWith" to { it.mappedToListWith },
    "Mapped::toMap" to { it.mappedToMap },
    "Mapped::toMapBuilder" to { it.mappedToMapBuilder },
    "Mapped::values" to { it.mappedValues },
    "Pair::constructor" to { it.pairConstructor },
    "PromiseBuilder::breakPromise" to { it.promiseBuilderBreakPromise },
    "PromiseBuilder::complete" to { it.promiseBuilderComplete },
    "PromiseBuilder::getPromise" to { it.promiseBuilderGetPromise },
    "Regex::compileFormatted" to { it.regexCompiledFormatted },
    "Regex::compiledFind" to { it.regexCompiledFind },
    "Regex::compiledFound" to { it.regexCompiledFound },
    "Regex::compiledReplace" to { it.regexCompiledReplace },
    "Regex::compiledSplit" to { it.regexCompiledSplit },
    "Regex::format" to { it.regexFormat },
    // "RegexFormatter::adjustCodeSet" to null,
    // "RegexFormatter::pushCaptureName" to null,
    "RegexFormatter::pushCodeTo" to { it.regexFormatterPushCodeTo },
    "SafeGenerator::next" to { it.generatorNext },
    "String::begin" to { it.stringBegin },
    "String::countBetween" to { it.stringCountBetween },
    "String::end" to { it.stringEnd },
    "String::forEach" to { it.stringForEach },
    "String::fromCodePoint" to { it.stringFromCodePoint },
    "String::fromCodePoints" to { it.stringFromCodePoints },
    "String::get" to { it.stringGet },
    "String::hasAtLeast" to { it.stringHasAtLeast },
    "String::hasIndex" to { it.stringHasIndex },
    "String::isEmpty" to { it.genericIsEmpty },
    "String::next" to { it.stringNext },
    "String::prev" to { it.stringPrev },
    "String::step" to { it.stringStep },
    "String::slice" to { it.stringSlice },
    "String::split" to { it.stringSplit },
    "String::toFloat64" to { it.stringToFloat64 },
    "String::toInt32" to { it.stringToInt },
    "String::toInt64" to { it.stringToInt64 },
    "String::toString" to { it.identity },
    "StringBuilder::append" to { it.stringBuilderAppend },
    "StringBuilder::appendBetween" to { it.stringBuilderAppendBetween },
    "StringBuilder::appendCodePoint" to { it.stringBuilderAppendCodePoint },
    "StringBuilder::constructor" to { it.stringBuilderConstructor },
    "StringBuilder::toString" to { it.stringBuilderToString },
    "StringIndex::none" to { it.stringIndexNone },
    "StringIndexOption::compareTo" to { it.stringIndexOptionCompareTo },
    "StringIndexOption::compareTo::eq" to { it.stringIndexOptionCompareToEq },
    "StringIndexOption::compareTo::ge" to { it.stringIndexOptionCompareToGe },
    "StringIndexOption::compareTo::gt" to { it.stringIndexOptionCompareToGt },
    "StringIndexOption::compareTo::le" to { it.stringIndexOptionCompareToLe },
    "StringIndexOption::compareTo::lt" to { it.stringIndexOptionCompareToLt },
    "StringIndexOption::compareTo::ne" to { it.stringIndexOptionCompareToNe },
    "Test::bail" to { it.bail },
    "doneResult" to { it.doneResult },
    "empty" to { it.empty },
    "ignore" to { it.doNothing },
    "stdNetSend" to { it.netCoreStdNetSend },
)
