package lang.temper.be.rust

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.ConvertedCoroutineAwakeUponFn
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.GetPromiseResultSyncFn
import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.typeOrInvalid
import lang.temper.builtin.RuntimeTypeOperation
import lang.temper.common.subListToEnd
import lang.temper.format.TokenSink
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.DefinedType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.withType
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PureVirtual
import lang.temper.value.pureVirtualBuiltinName

object RustSupportNetwork : SupportNetwork {
    override val backendDescription = "Rust Backend"

    override val bubbleStrategy = BubbleBranchStrategy.IfHandlerScopeVar
    override val coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction
    override val functionTypeStrategy = FunctionTypeStrategy.ToFunctionType

    override fun representationOfVoid(genre: Genre) = RepresentationOfVoid.ReifyVoid
    override val simplifyOrTypes: Boolean = true

    override fun getSupportCode(pos: Position, builtin: NamedBuiltinFun, genre: Genre): SupportCode? {
        return runCatching { supportCodeByOperatorId(builtin.builtinOperatorId) }.getOrElse {
            // Useful for placing a breakpoint.
            null
        } ?: builtinFunSupportCode[builtin.name] ?: run {
            // Also useful.
            null
        }
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    override fun translateConnectedReference(pos: Position, connectedKey: String, genre: Genre): SupportCode? {
        return connectedReferences[connectedKey] ?: run {
            // Useful for placing a breakpoint.
            null
        }
    }

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? {
        // TODO Type arg translation?
        return connectedTypes[connectedKey]?.let { it to temperType.bindings }
    }

    override fun translateRuntimeTypeOperation(
        pos: Position,
        rto: RuntimeTypeOperation,
        sourceType: TmpL.NominalType,
        targetType: TmpL.NominalType,
    ): SupportCode? {
        return when (rto) {
            RuntimeTypeOperation.Is -> when (sourceType.typeName.sourceDefinition) {
                WellKnownTypes.stringIndexOptionTypeDefinition -> when (targetType.typeName.sourceDefinition) {
                    WellKnownTypes.noStringIndexTypeDefinition -> isNull
                    WellKnownTypes.stringIndexTypeDefinition -> isNonNull
                    else -> null
                }

                else -> null
            }

            else -> null
        }
    }
}

private fun supportCodeByOperatorId(builtinOperatorId: BuiltinOperatorId?): SupportCode? {
    return when (builtinOperatorId) {
        BuiltinOperatorId.BooleanNegation -> booleanNegation
        BuiltinOperatorId.BitwiseAnd -> bitwiseAnd
        BuiltinOperatorId.BitwiseOr -> bitwiseOr
        BuiltinOperatorId.IsNull -> isNull
        BuiltinOperatorId.NotNull -> null
        BuiltinOperatorId.DivFltFlt -> divFltFlt
        BuiltinOperatorId.DivIntInt -> DivIntInt
        BuiltinOperatorId.DivIntInt64 -> DivIntInt64
        BuiltinOperatorId.DivIntIntSafe, BuiltinOperatorId.DivIntInt64Safe -> divIntIntSafe
        BuiltinOperatorId.ModFltFlt -> modFltFlt
        BuiltinOperatorId.ModIntInt -> ModIntInt
        BuiltinOperatorId.ModIntInt64 -> ModIntInt64
        BuiltinOperatorId.ModIntIntSafe, BuiltinOperatorId.ModIntInt64Safe -> modIntIntSafe
        BuiltinOperatorId.MinusFlt -> minusFlt
        BuiltinOperatorId.MinusFltFlt -> minusFltFlt
        BuiltinOperatorId.MinusInt, BuiltinOperatorId.MinusInt64 -> minusInt
        BuiltinOperatorId.MinusIntInt, BuiltinOperatorId.MinusIntInt64 -> minusIntInt
        BuiltinOperatorId.PlusFltFlt -> plusFltFlt
        BuiltinOperatorId.PlusIntInt, BuiltinOperatorId.PlusIntInt64 -> plusIntInt
        BuiltinOperatorId.TimesIntInt, BuiltinOperatorId.TimesIntInt64 -> timesIntInt
        BuiltinOperatorId.TimesFltFlt -> timesFltFlt
        BuiltinOperatorId.PowFltFlt -> powFltFlt
        BuiltinOperatorId.LtFltFlt -> ltFltFlt
        BuiltinOperatorId.LtIntInt -> ltIntInt
        BuiltinOperatorId.LtStrStr -> ltStrStr
        BuiltinOperatorId.LtGeneric -> ltGeneric
        BuiltinOperatorId.LeFltFlt -> leFltFlt
        BuiltinOperatorId.LeIntInt -> leIntInt
        BuiltinOperatorId.LeStrStr -> leStrStr
        BuiltinOperatorId.LeGeneric -> leGeneric
        BuiltinOperatorId.GtFltFlt -> gtFltFlt
        BuiltinOperatorId.GtIntInt -> gtIntInt
        BuiltinOperatorId.GtStrStr -> gtStrStr
        BuiltinOperatorId.GtGeneric -> gtGeneric
        BuiltinOperatorId.GeFltFlt -> geFltFlt
        BuiltinOperatorId.GeIntInt -> geIntInt
        BuiltinOperatorId.GeStrStr -> geStrStr
        BuiltinOperatorId.GeGeneric -> geGeneric
        BuiltinOperatorId.EqFltFlt -> eqFltFlt
        BuiltinOperatorId.EqIntInt -> eqIntInt
        BuiltinOperatorId.EqStrStr -> eqStrStr
        BuiltinOperatorId.EqGeneric -> eqGeneric
        BuiltinOperatorId.NeFltFlt -> neFltFlt
        BuiltinOperatorId.NeIntInt -> neIntInt
        BuiltinOperatorId.NeStrStr -> neStrStr
        BuiltinOperatorId.NeGeneric -> neGeneric
        BuiltinOperatorId.CmpFltFlt -> TODO()
        BuiltinOperatorId.CmpIntInt -> TODO()
        BuiltinOperatorId.CmpStrStr -> TODO()
        BuiltinOperatorId.CmpGeneric -> CmpGeneric
        BuiltinOperatorId.Bubble -> TODO() // bubble
        BuiltinOperatorId.Panic -> panic
        BuiltinOperatorId.Print -> TODO()
        BuiltinOperatorId.StrCat -> StrCat
        BuiltinOperatorId.Listify -> Listify
        BuiltinOperatorId.Async -> async
        // Should not be used with CoroutineStrategy.TranslateToGenerator, but we don't use that for now.
        BuiltinOperatorId.AdaptGeneratorFn -> adaptGeneratorFn
        BuiltinOperatorId.SafeAdaptGeneratorFn -> adaptGeneratorFnSafe

        null -> null
    }
}

private val builtinFunSupportCode = mapOf(
    PureVirtual.name to PureVirtualBuiltin,
    ConvertedCoroutineAwakeUponFn.name to AwakeUponSupportCode,
    GetPromiseResultSyncFn.name to GetPromiseResultSyncSupportCode,
)

open class RustSupportCode(
    val connectedNames: List<String>,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : NamedSupportCode {
    override val baseName = ParsedName(connectedNames.first())
    override fun renderTo(tokenSink: TokenSink) = tokenSink.name(baseName, inOperatorPosition = false)

    final override fun hashCode(): Int = baseName.hashCode()
    final override fun toString(): String = "RustSupportCode($baseName)"
    final override fun equals(other: Any?): Boolean =
        this === other || (other is RustSupportCode && baseName == other.baseName)
}

abstract class RustInlineSupportCode(
    connectedNames: List<String>,
    builtinOperatorId: BuiltinOperatorId? = null,
    val hasGeneric: Boolean = false,
    override val needsThisEquivalent: Boolean = false,
    val cloneEvenIfFirst: Boolean = false,
    val avoidTypeWrapping: Boolean = false,
    val wrapClosures: Boolean = false,
) : RustSupportCode(connectedNames, builtinOperatorId), InlineSupportCode<Rust.Tree, RustTranslator> {
    constructor(
        baseName: String,
        builtinOperatorId: BuiltinOperatorId? = null,
        cloneEvenIfFirst: Boolean = false,
        hasGeneric: Boolean = false,
        avoidTypeWrapping: Boolean = false,
        wrapClosures: Boolean = false,
    ) : this(
        listOf(baseName),
        builtinOperatorId,
        avoidTypeWrapping = avoidTypeWrapping,
        cloneEvenIfFirst = cloneEvenIfFirst,
        hasGeneric = hasGeneric,
        wrapClosures = wrapClosures,
    )

    open fun argType(returnType: Type2): Type2? = null

    open fun translateArg(actual: TmpL.Actual, wantedType: Type2? = null, translator: RustTranslator): Rust.Expr? =
        null
}

internal object AwakeUponSupportCode : RustInlineSupportCode(ConvertedCoroutineAwakeUponFn.name) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        error("Specially handled in translator")
    }
}

internal object GetPromiseResultSyncSupportCode : RustInlineSupportCode(GetPromiseResultSyncFn.name) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        error("Specially handled in translator")
    }
}

internal object GetResult : RustInlineSupportCode(ConvertedCoroutineAwakeUponFn.name) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        error("Specially handled in translator")
    }
}

private abstract class Cast(baseName: String) : RustInlineSupportCode(baseName) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ) = (arguments[0].expr as Rust.Expr).infix(RustOperator.As, buildType(pos))

    abstract fun buildType(pos: Position): Rust.Expr
}

private abstract class Constant(baseName: String) : RustInlineSupportCode(baseName = baseName) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ) = value(pos)

    abstract fun value(pos: Position): Rust.Expr
}

private class Float64Compare(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId? = null,
    val operator: RustOperator,
) : RustInlineSupportCode(baseName, builtinOperatorId, cloneEvenIfFirst = true) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Expr {
        return Rust.Call(
            pos,
            callee = "temper_core".toKeyId(pos).extendWith(listOf("float64", "cmp_option")),
            args = arguments.map { it.expr as Rust.Expr },
        ).infix(operator, Rust.NumberLiteral(pos, 0L))
    }
}

internal open class FunctionCall(
    connectedNames: List<String>,
    val functionName: String,
    builtinOperatorId: BuiltinOperatorId? = null,
    cloneEvenIfFirst: Boolean = false,
    hasGeneric: Boolean = false,
    wrapClosures: Boolean = false,
    // TODO Extras copied from c# but not currently in use.
    val extraArgs: (Position) -> List<Rust.Expr> = { emptyList() },
) : RustInlineSupportCode(
    connectedNames,
    builtinOperatorId,
    cloneEvenIfFirst = cloneEvenIfFirst,
    hasGeneric = hasGeneric,
    wrapClosures = wrapClosures,
) {
    constructor(
        baseName: String,
        functionName: String,
        builtinOperatorId: BuiltinOperatorId? = null,
        cloneEvenIfFirst: Boolean = false,
        hasGeneric: Boolean = false,
        wrapClosures: Boolean = false,
        extraArgs: (Position) -> List<Rust.Expr> = { emptyList() },
    ) : this(
        connectedNames = listOf(baseName),
        functionName = functionName,
        builtinOperatorId = builtinOperatorId,
        cloneEvenIfFirst = cloneEvenIfFirst,
        hasGeneric = hasGeneric,
        wrapClosures = wrapClosures,
        extraArgs = extraArgs,
    )

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Expr {
        return Rust.Call(
            pos = pos,
            callee = functionName.toId(pos),
            args = buildList args@{
                arguments.isEmpty() && return@args
                // Pass first arg as semi-self ref.
                val selfArg = arguments[0]
                val self = (selfArg.expr as Rust.Expr).let { self ->
                    // Except first deref interfaces, because we take non-wrapped in connected methods for efficiency.
                    // We don't do this in user code because we have less promises about how they intend to use it.
                    // TODO If we do add borrows to Temper, we could generalize better.
                    when {
                        selfArg.type.described().isInterface() -> self.deref()
                        else -> self
                    }
                }.let { self ->
                    when {
                        selfArg.type.isCopy() -> self
                        cloneEvenIfFirst -> self.wrapClone()
                        else -> self.ref()
                    }
                }
                add(self)
                // Other args.
                for (arg in arguments.subListToEnd(1)) {
                    addArg(arg, translator)
                }
                addAll(extraArgs(pos.rightEdge))
            },
        )
    }
}

private fun MutableList<Rust.Expr>.addArg(arg: TypedArg<Rust.Tree>, translator: RustTranslator) {
    val expr = arg.expr as Rust.Expr
    // Our connected methods expect non-boxed functions, unlike Temper-built user code.
    // We can do this because we know they're safe for that, but because of it, we need refs.
    add(
        withType(
            arg.type,
            fn = { _, _, _ ->
                // Except we wrap closures, so first we need to unwrap them.
                when {
                    translator.isClosure(expr) -> expr.deref()
                    else -> expr
                }.ref()
            },
            fallback = { expr },
        ),
    )
}

private open class Infix(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId,
    private val operator: RustOperator,
    avoidTypeWrapping: Boolean = false,
) : RustInlineSupportCode(baseName, builtinOperatorId, cloneEvenIfFirst = true, avoidTypeWrapping = avoidTypeWrapping) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        return Rust.Operation(
            pos,
            left = arguments[0].expr as Rust.Expr,
            operator = Rust.Operator(pos, operator),
            right = arguments[1].expr as Rust.Expr,
        )
    }
}

private open class MethodCall(
    connectedNames: List<String>,
    val memberName: String,
    builtinOperatorId: BuiltinOperatorId? = null,
    cloneEvenIfFirst: Boolean = false,
    // TODO Extras copied from c# but not currently in use.
    val extraArgs: (Position) -> List<Rust.Expr> = { emptyList() },
    hasGeneric: Boolean = false,
    val mapArg: (Rust.Expr) -> Rust.Expr = { it },
) : RustInlineSupportCode(
    connectedNames, builtinOperatorId, cloneEvenIfFirst = cloneEvenIfFirst, hasGeneric = hasGeneric,
) {
    constructor(
        baseName: String,
        memberName: String,
        builtinOperatorId: BuiltinOperatorId? = null,
        cloneEvenIfFirst: Boolean = false,
        extraArgs: (Position) -> List<Rust.Expr> = { emptyList() },
        hasGeneric: Boolean = false,
        mapArg: (Rust.Expr) -> Rust.Expr = { it },
    ) : this(
        cloneEvenIfFirst = cloneEvenIfFirst,
        connectedNames = listOf(baseName),
        memberName = memberName,
        builtinOperatorId = builtinOperatorId,
        extraArgs = extraArgs,
        hasGeneric = hasGeneric,
        mapArg = mapArg,
    )

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Expr {
        val self = arguments[0].expr.let { self ->
            when (self) {
                is Rust.NumberLiteral -> when (self.value) {
                    // Have to be explicit about types, but `-0x8000_0000_i32` fails, so use cast.
                    // TODO Alternatively, don't use method call syntax below?
                    is Int -> self.infix(RustOperator.As, "i32".toId(pos))
                    is Long -> self.infix(RustOperator.As, "i64".toId(pos))
                    else -> self
                }
                else -> self
            }
        }
        return (self as Rust.Expr).methodCall(
            memberName,
            buildList {
                arguments.subListToEnd(1).forEach { add(mapArg(it.expr as Rust.Expr)) }
                addAll(extraArgs(pos.rightEdge))
            },
        )
    }
}

private class Prefix(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId,
    private val operator: RustOperator,
) : RustInlineSupportCode(baseName, builtinOperatorId) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        return Rust.Operation(
            pos,
            left = null,
            operator = Rust.Operator(pos, operator),
            right = arguments[0].expr as Rust.Expr,
        )
    }
}

private val adaptGeneratorFn =
    FunctionCall("AdaptGeneratorFn", "temper_core::Generator::from_fn", cloneEvenIfFirst = true, hasGeneric = true)
private val adaptGeneratorFnSafe = FunctionCall(
    "SafeAdaptGeneratorFn",
    "temper_core::SafeGenerator::from_fn",
    cloneEvenIfFirst = true,
    hasGeneric = true,
)
private val async = FunctionCall("Async", "crate::run_async", cloneEvenIfFirst = true, wrapClosures = true)

private class CmpStrStr(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId,
    operator: RustOperator,
) : Infix(baseName, builtinOperatorId, operator) {
    override fun translateArg(actual: TmpL.Actual, wantedType: Type2?, translator: RustTranslator): Rust.Expr? {
        // Our options are making a separate helper function, or heap-allocating literals when present, or customizing
        // `&str` access here. The latter seems doable and more efficient than needless heap allocation.
        // Anyway, string literals are handled elsewhere.
        // And presume any raw value is a string. Anything else is a frontend error, so we don't need to accommodate.
        actual is TmpL.ValueReference && return null
        // Anything else is presumably a wrapped string (when no frontend errors), so get the string out.
        // And propagate `wantedType` because it might be nullable.
        val expr = (actual as? TmpL.Expression) ?: return null
        val outExpr = translator.translateExpression(expr, avoidClone = true).methodCall("as_str")
        return outExpr.maybeWrap(given = expr.type, wanted = wantedType, translator = translator)
    }
}

internal object ConsoleLog : RustInlineSupportCode("Console::log") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        val value = (arguments[1].expr as Rust.Expr).stripArc()
        // Prettify the common case of printing string cats.
        val values = when (value) {
            is Rust.Call -> when ((value.callee as? Rust.Id)?.outName?.outputNameText) {
                FORMAT_MACRO_NAME -> value.args.map { it.deepCopy() }
                else -> null
            }

            else -> null
        } ?: listOf(Rust.StringLiteral(pos, "{}"), value)
        // We have one or more values to print at this point.
        val callee = Rust.Id(pos, OutName("println!", null))
        return Rust.Call(pos, callee = callee, args = values)
    }
}

private object GetConsole : RustInlineSupportCode("::getConsole") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        return Rust.StringLiteral(pos, "TODO get console")
    }
}

private val bitwiseAnd = Infix("BitwiseAnd", BuiltinOperatorId.BitwiseAnd, RustOperator.And)
private val bitwiseOr = Infix("BitwiseOr", BuiltinOperatorId.BitwiseOr, RustOperator.Or)
private val booleanNegation =
    Prefix("BooleanNegation", BuiltinOperatorId.BooleanNegation, RustOperator.BoolComplement)

/** Probably need to customize for floats and strings in the future. */
private object CmpGeneric : MethodCall(
    listOf("CmpGeneric", "StringIndexOption::compareTo"),
    "cmp",
    cloneEvenIfFirst = true, // Hack around for this being typed to type awareness. Effectively treat as operator here.
    mapArg = { it.ref() },
) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Expr {
        // Work around StringIndexOption::compareTo typing if needed.
        val self = arguments.getOrNull(0)
        val effectiveArgs = when ((self?.type as? DefinedNonNullType)?.definition) {
            WellKnownTypes.stringIndexTypeDefinition -> buildList {
                // Left should be an option, not a string index, but we don't get the function type that way.
                val optionType = MkType2(WellKnownTypes.stringIndexOptionTypeDefinition).get()
                add(TypedArg((self.expr as Rust.Expr).wrapSome(), optionType))
                addAll(arguments.subListToEnd(1))
            }
            else -> arguments
        }
        // And cast result as i32 for be-rust int expectations.
        return super.inlineToTree(pos, effectiveArgs, returnType, translator).infix(RustOperator.As, "i32".toId(pos))
    }
}

private val dateToday = FunctionCall("Date::today", "temper_std::temporal::today")
private val denseBitVectorConstructor =
    FunctionCall("DenseBitVector::constructor", "temper_core::DenseBitVector::with_capacity")
private val denseBitVectorGet = MethodCall("DenseBitVector::get", "get")
private val denseBitVectorSet = MethodCall("DenseBitVector::set", "set")
private val dequeAdd = FunctionCall("Deque::add", "temper_core::deque::add", hasGeneric = true)
private val dequeConstructor = FunctionCall("Deque::constructor", "temper_core::deque::new")
private val dequeIsEmpty = FunctionCall("Deque::isEmpty", "temper_core::deque::is_empty")
private val dequeRemoveFirst = FunctionCall("Deque::removeFirst", "temper_core::deque::remove_first")
private val divFltFlt = FunctionCall("DivFltFlt", "temper_core::float64::div", BuiltinOperatorId.DivFltFlt)
private object DivIntInt : FunctionCall("DivIntInt", "temper_core::int_div", BuiltinOperatorId.DivIntInt)
private val divIntIntSafe = MethodCall("DivIntIntSafe", "wrapping_div", BuiltinOperatorId.DivIntIntSafe)
private object DivIntInt64 : FunctionCall("DivIntInt64", "temper_core::int64_div", BuiltinOperatorId.DivIntInt64)

private object DoneResult : Constant("doneResult") {
    override fun value(pos: Position) = "None".toId(pos)
}
object Empty : RustInlineSupportCode("empty") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        check(arguments.isEmpty())
        return Rust.Tuple(pos, emptyList())
    }
}

private val eqFltFlt = Float64Compare("EqFltFlt", BuiltinOperatorId.EqFltFlt, RustOperator.Equals)

private class EqNeGeneric(
    baseName: String,
    builtinOperatorId: BuiltinOperatorId,
    operator: RustOperator,
) : Infix(baseName, builtinOperatorId, operator, avoidTypeWrapping = true) {
    override fun translateArg(actual: TmpL.Actual, wantedType: Type2?, translator: RustTranslator): Rust.Expr? {
        return when {
            translator.isIdentifiable(actual.typeOrInvalid) -> {
                val expr = translator.translateExpression(actual as TmpL.Expression, avoidClone = true)
                // TODO Even if we keep ptr_id, call as `temper_core::AnyValueTrait::ptr_id(&*actual)`?
                // TODO We might need such trait call style throughout translation to avoid name collisions.
                expr.methodCall("ptr_id")
            }
            // Avoids type wrapping because this depends for now on types just already being PartialEq.
            else -> null
        }
    }
}

private val eqGeneric = EqNeGeneric("EqGeneric", BuiltinOperatorId.EqGeneric, RustOperator.Equals)
private val eqIntInt = Infix("EqIntInt", BuiltinOperatorId.EqIntInt, RustOperator.Equals)
private val eqStrStr = CmpStrStr("EqStrStr", BuiltinOperatorId.EqStrStr, RustOperator.Equals)
private val float64Expm1 = MethodCall("Float64::expm1", "exp_m1")
private val float64Log = MethodCall("Float64::log", "ln")
private val float64Log1p = MethodCall("Float64::log1p", "ln_1p")
private val float64Max = FunctionCall("Float64::max", "temper_core::float64::max")
private val float64Min = FunctionCall("Float64::min", "temper_core::float64::min")
private val float64Near = FunctionCall("Float64::near", "temper_core::float64::near")

private object Float64E : Constant("Float64::e") {
    override fun value(pos: Position) = makePath(pos, "std", "f64", "consts", "E")
}

private object Float64Pi : Constant("Float64::pi") {
    override fun value(pos: Position) = makePath(pos, "std", "f64", "consts", "PI")
}

private val float64Sign = FunctionCall("Float64::sign", "temper_core::float64::sign")
internal val float64ToInt = FunctionCall("Float64::toInt32", "temper_core::float64::to_int")
internal val float64ToInt64 = FunctionCall("Float64::toInt64", "temper_core::float64::to_int64")
internal val float64ToString = FunctionCall("Float64::toString", "temper_core::float64::to_string")

private object Float64ToIntUnsafe : Cast("Float64::toInt32Unsafe") {
    override fun buildType(pos: Position) = "i32".toId(pos)
}

private object Float64ToInt64Unsafe : Cast("Float64::toInt64Unsafe") {
    override fun buildType(pos: Position) = "i64".toId(pos)
}

private val geFltFlt = Float64Compare("GeFltFlt", BuiltinOperatorId.GeFltFlt, RustOperator.GreaterEquals)
private val geGeneric = Infix("GeGeneric", BuiltinOperatorId.GeGeneric, RustOperator.GreaterEquals)
private val geIntInt = Infix("GeIntInt", BuiltinOperatorId.GeIntInt, RustOperator.GreaterEquals)
private val geStrStr = Infix("GeStrStr", BuiltinOperatorId.GeStrStr, RustOperator.GreaterEquals)
private val gtFltFlt = Float64Compare("GtFltFlt", BuiltinOperatorId.GtFltFlt, RustOperator.GreaterThan)
private val gtGeneric = Infix("GtGeneric", BuiltinOperatorId.GtGeneric, RustOperator.GreaterThan)
private val gtIntInt = Infix("GtIntInt", BuiltinOperatorId.GtIntInt, RustOperator.GreaterThan)
private val gtStrStr = Infix("GtStrStr", BuiltinOperatorId.GtStrStr, RustOperator.GreaterThan)
private val ignore = FunctionCall("ignore", "temper_core::ignore")

private object IntToFloat64 : Cast("Int32::toFloat64") {
    override fun buildType(pos: Position) = "f64".toId(pos)
}

private object IntToInt64 : Cast("Int32::toInt64") {
    override fun buildType(pos: Position) = "i64".toId(pos)
}

private object Int64ToFloat64Unsafe : Cast("Int64::toFloat64Unsafe") {
    override fun buildType(pos: Position) = "f64".toId(pos)
}

private object Int64ToInt32Unsafe : Cast("Int64::toInt32Unsafe") {
    override fun buildType(pos: Position) = "i32".toId(pos)
}

internal val intToString = FunctionCall("Int32::toString", "temper_core::int_to_string")
private val int64ToFloat64 = FunctionCall("Int64::toFloat64", "temper_core::int64_to_float64")
private val int64ToInt32 = FunctionCall("Int64::toInt32", "temper_core::int64_to_int32")
internal val int64ToString = FunctionCall("Int64::toString", "temper_core::int64_to_string")
private val isNonNull = MethodCall("IsNonNull", "is_some")
private val isNull = MethodCall("IsNull", "is_none")
private val leFltFlt = Float64Compare("LeFltFlt", BuiltinOperatorId.LeFltFlt, RustOperator.LessEquals)
private val leGeneric = Infix("LeGeneric", BuiltinOperatorId.LeGeneric, RustOperator.LessEquals)
private val leIntInt = Infix("LeIntInt", BuiltinOperatorId.LeIntInt, RustOperator.LessEquals)
private val leStrStr = Infix("LeStrStr", BuiltinOperatorId.LeStrStr, RustOperator.LessEquals)

private val listedTypes = listOf("Listed", "List", "ListBuilder")

private val listForEach = FunctionCall("List::forEach", "temper_core::listed::list_for_each", hasGeneric = true)
private val listBuilderAdd = FunctionCall("ListBuilder::add", "temper_core::listed::add", hasGeneric = true)
private val listBuilderAddAll = FunctionCall("ListBuilder::addAll", "temper_core::listed::add_all")
private val listBuilderClear = FunctionCall("ListBuilder::clear", "temper_core::listed::clear")
private val listBuilderConstructor = FunctionCall("ListBuilder::constructor", "temper_core::listed::new_builder")
private val listBuilderRemoveLast = FunctionCall("ListBuilder::removeLast", "temper_core::listed::remove_last")
private val listBuilderReverse = FunctionCall("ListBuilder::reverse", "temper_core::listed::reverse")
private val listBuilderSet = FunctionCall("ListBuilder::set", "temper_core::listed::set", hasGeneric = true)
private val listBuilderSort = FunctionCall("ListBuilder::sort", "temper_core::listed::sort")
private val listBuilderSplice = FunctionCall("ListBuilder::splice", "temper_core::listed::splice")
private val listedFilter = FunctionCall("Listed::filter", "temper_core::listed::filter", hasGeneric = true)
private val listedGet = FunctionCall(listedTypes.map { "$it::get" }, "$LISTED_TRAIT_NAME::get", hasGeneric = true)
private val listedGetOr = FunctionCall("Listed::getOr", "$LISTED_TRAIT_NAME::get_or", hasGeneric = true)

private val listedIsEmpty =
    FunctionCall(listedTypes.map { "$it::isEmpty" }, "$LISTED_TRAIT_NAME::is_empty", hasGeneric = true)
private val listedJoin = FunctionCall("Listed::join", "temper_core::listed::join", hasGeneric = true)
private val listedLength = FunctionCall(listedTypes.map { "$it::length" }, "$LISTED_TRAIT_NAME::len", hasGeneric = true)
private val listedMap = FunctionCall("Listed::map", "temper_core::listed::map", hasGeneric = true)
private val listedReduce = FunctionCall("Listed::reduce", "temper_core::listed::reduce", hasGeneric = true)
private val listedReduceFrom =
    FunctionCall("Listed::reduceFrom", "temper_core::listed::reduce_from", hasGeneric = true)
private val listedSlice = FunctionCall("Listed::slice", "temper_core::listed::slice", hasGeneric = true)
private val listedSorted = FunctionCall("Listed::sorted", "temper_core::listed::sorted", hasGeneric = true)
private val listedToList =
    FunctionCall(listedTypes.map { "$it::toList" }, "$LISTED_TRAIT_NAME::to_list", hasGeneric = true)
private val listedToListBuilder = FunctionCall(
    connectedNames = listedTypes.map { "$it::toListBuilder" },
    functionName = "$LISTED_TRAIT_NAME::to_list_builder",
    hasGeneric = true,
)

internal object Listify : RustInlineSupportCode("Listify", cloneEvenIfFirst = true, hasGeneric = true) {
    override fun argType(returnType: Type2): Type2? {
        return (returnType as? DefinedType)?.bindings?.getOrNull(0)
    }

    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ) = Rust.MacroCall(
        pos,
        path = "vec!".toId(pos),
        args = Rust.Array(pos, values = arguments.map { it.expr as Rust.Expr }),
    ).wrapArc()
}

private val ltFltFlt = Float64Compare("LtFltFlt", BuiltinOperatorId.LtFltFlt, RustOperator.LessThan)
private val ltGeneric = Infix("LtGeneric", BuiltinOperatorId.LtGeneric, RustOperator.LessThan)
private val ltIntInt = Infix("LtIntInt", BuiltinOperatorId.LtIntInt, RustOperator.LessThan)
private val ltStrStr = Infix("LtStrStr", BuiltinOperatorId.LtStrStr, RustOperator.LessThan)
private val mapConstructor = FunctionCall("Map::constructor", "temper_core::Map::new")
private val mapBuilderClear = FunctionCall("MapBuilder::clear", "temper_core::MapBuilder::clear")
private val mapBuilderConstructor =
    FunctionCall("MapBuilder::constructor", "temper_core::MapBuilder::new", hasGeneric = true)

// Some of these function calls would be prettier as method calls, but we'd have to build more support network
// infrastructure for MethodCall to match things in FunctionCall, and FunctionCall works for now.
private val mapBuilderRemove =
    FunctionCall("MapBuilder::remove", "temper_core::MapBuilder::remove", hasGeneric = true)
private val mapBuilderSet = FunctionCall("MapBuilder::set", "temper_core::MapBuilder::set", hasGeneric = true)
private val mappedForEach = FunctionCall("Mapped::forEach", "temper_core::MappedTrait::for_each", hasGeneric = true)
private val mappedGet = FunctionCall("Mapped::get", "temper_core::MappedTrait::get", hasGeneric = true)
private val mappedGetOr = FunctionCall("Mapped::getOr", "temper_core::MappedTrait::get_or", hasGeneric = true)
private val mappedHas = FunctionCall("Mapped::has", "temper_core::MappedTrait::has", hasGeneric = true)
private val mappedLength = FunctionCall("Mapped::length", "temper_core::MappedTrait::len")
private val mappedKeys = FunctionCall("Mapped::keys", "temper_core::MappedTrait::keys")
private val mappedToList = FunctionCall("Mapped::toList", "temper_core::MappedTrait::to_list")
private val mappedToListBuilder = FunctionCall("Mapped::toListBuilder", "temper_core::MappedTrait::to_list_builder")
private val mappedToListBuilderWith =
    FunctionCall("Mapped::toListBuilderWith", "temper_core::MappedTrait::to_list_builder_with", hasGeneric = true)
private val mappedToListWith =
    FunctionCall("Mapped::toListWith", "temper_core::mapped_to_list_with", hasGeneric = true)
private val mappedToMap = FunctionCall("Mapped::toMap", "temper_core::MappedTrait::to_map")
private val mappedToMapBuilder = FunctionCall("Mapped::toMapBuilder", "temper_core::MappedTrait::to_map_builder")
private val mappedValues = FunctionCall("Mapped::values", "temper_core::MappedTrait::values")
private val minusFlt = Prefix("MinusFlt", BuiltinOperatorId.MinusFlt, RustOperator.Minus)
private val minusFltFlt = Infix("MinusFltFlt", BuiltinOperatorId.MinusFltFlt, RustOperator.Subtraction)
private val minusInt = MethodCall("MinusInt", "wrapping_neg", BuiltinOperatorId.MinusInt)
private val minusIntInt = MethodCall("MinusIntInt", "wrapping_sub", BuiltinOperatorId.MinusIntInt)
private val modFltFlt = FunctionCall("ModFltFlt", "temper_core::float64::rem", BuiltinOperatorId.ModFltFlt)
private object ModIntInt : FunctionCall("ModIntInt", "temper_core::int_rem", BuiltinOperatorId.ModIntInt)
private val modIntIntSafe = MethodCall("ModIntIntSafe", "wrapping_rem", BuiltinOperatorId.ModIntIntSafe)
private object ModIntInt64 : FunctionCall("ModIntInt64", "temper_core::int64_rem", BuiltinOperatorId.ModIntInt64)
private val neFltFlt = Float64Compare("NeFltFlt", BuiltinOperatorId.NeFltFlt, RustOperator.NotEquals)
private val neGeneric = EqNeGeneric("NeGeneric", BuiltinOperatorId.NeGeneric, RustOperator.NotEquals)
private val neIntInt = Infix("NeIntInt", BuiltinOperatorId.NeIntInt, RustOperator.NotEquals)
private val neStrStr = CmpStrStr("NeStrStr", BuiltinOperatorId.NeStrStr, RustOperator.NotEquals)
private val netSend = FunctionCall("stdNetSend", "send_request", cloneEvenIfFirst = true)

internal object PairConstructor : RustInlineSupportCode(
    "Pair::constructor",
    cloneEvenIfFirst = true,
    hasGeneric = true,
) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        return Rust.Tuple(pos, values = arguments.map { it.expr as Rust.Expr })
    }
}

private val panic = FunctionCall("Panic", "panic!", BuiltinOperatorId.Panic)
private val plusFltFlt = Infix("PlusFltFlt", BuiltinOperatorId.PlusFltFlt, RustOperator.Addition)
private val plusIntInt = MethodCall("PlusIntInt", "wrapping_add", BuiltinOperatorId.PlusIntInt)
private val powFltFlt = MethodCall("PowFltFlt", "powf", BuiltinOperatorId.PowFltFlt)
private val promiseBuilderComplete = MethodCall("PromiseBuilder::complete", "complete", hasGeneric = true)

internal object PureVirtualBuiltin : RustInlineSupportCode(pureVirtualBuiltinName.builtinKey) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ) = Rust.Call(pos, callee = "panic!".toId(pos), args = listOf())
}

private val regexCompileFormatted = FunctionCall("Regex::compileFormatted", "compile_formatted")
private val regexCompiledFind = FunctionCall("Regex::compiledFind", "compiled_find")
private val regexCompiledFound = FunctionCall("Regex::compiledFound", "compiled_found")
private val regexCompiledReplace = FunctionCall("Regex::compiledReplace", "compiled_replace")
private val regexCompiledSplit = FunctionCall("Regex::compiledSplit", "compiled_split")
private val regexFormatterPushCodeTo = FunctionCall("RegexFormatter::pushCodeTo", "push_code_to")

internal object SimpleToString : RustInlineSupportCode(listOf("Boolean::toString")) {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        return (arguments[0].expr as Rust.Expr).wrapArcString()
    }
}

internal object StrCat : RustInlineSupportCode("StrCat") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ) = when (arguments.size) {
        0 -> Rust.StringLiteral(pos, "").wrapArcString()
        else -> {
            Rust.Call(
                pos,
                callee = Rust.Id(pos, OutName(FORMAT_MACRO_NAME, null)),
                args = buildList {
                    // Inline string literal parts because that's nicer of us.
                    val format = arguments.joinToString("") { arg ->
                        when (val expr = arg.expr) {
                            is Rust.StringLiteral -> expr.value.replace("{", "{{").replace("}", "}}")
                            else -> "{}"
                        }
                    }
                    add(Rust.StringLiteral(pos, format))
                    for (arg in arguments) {
                        if (arg.expr !is Rust.StringLiteral) {
                            add((arg.expr as Rust.Expr).stripArc())
                        }
                    }
                },
            ).wrapArc()
        }
    }
}

private object StringBegin : Constant("String::begin") {
    override fun value(pos: Position) = "0usize".toId(pos)
}

private val stringCountBetween = FunctionCall("String::countBetween", "temper_core::string::count_between")
private val stringEnd = MethodCall("String::end", "len")
private val stringForEach = FunctionCall("String::forEach", "temper_core::string::for_each")
private val stringFromCodePoint = FunctionCall("String::fromCodePoint", "temper_core::string::from_code_point")
private val stringFromCodePoints = FunctionCall("String::fromCodePoints", "temper_core::string::from_code_points")
private val stringGet = FunctionCall("String::get", "temper_core::string::get")
private val stringHasAtLeast = FunctionCall("String::hasAtLeast", "temper_core::string::has_at_least")
private val stringHasIndex = FunctionCall("String::hasIndex", "temper_core::string::has_index")
private val stringIndexOf = FunctionCall("String::indexOf", "temper_core::string::index_of")
private val stringNext = FunctionCall("String::next", "temper_core::string::next")
private val stringPrev = FunctionCall("String::prev", "temper_core::string::prev")
private val stringSlice = FunctionCall("String::slice", "temper_core::string::slice")
private val stringSplit = FunctionCall("String::split", "temper_core::string::split")
private val stringStep = FunctionCall("String::step", "temper_core::string::step")
private val stringToFloat64 = FunctionCall("String::toFloat64", "temper_core::string::to_float64")
private val stringToInt = FunctionCall("String::toInt32", "temper_core::string::to_int")
private val stringToInt64 = FunctionCall("String::toInt64", "temper_core::string::to_int64")
private val stringToString = MethodCall("String::toString", "clone")

internal object StringBuilderConstructor : RustInlineSupportCode("StringBuilder::constructor") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ) = Rust.Call(pos, "String".toId(pos).extendWith("new"), listOf()).wrapLock().wrapArc()
}
private val stringBuilderAppend =
    FunctionCall(baseName = "StringBuilder::append", functionName = "temper_core::string::builder::append")
private val stringBuilderAppendBetween = FunctionCall(
    baseName = "StringBuilder::appendBetween",
    functionName = "temper_core::string::builder::append_between",
)
private val stringBuilderAppendCodePoint =
    FunctionCall("StringBuilder::appendCodePoint", "temper_core::string::builder::append_code_point")
private val stringBuilderToString =
    FunctionCall("StringBuilder::toString", "temper_core::string::builder::to_string")

private object StringIndexNone : Constant("StringIndex::none") {
    override fun value(pos: Position) = "()".toId(pos)
}

internal object TestBail : RustInlineSupportCode("Test::bail") {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Rust.Tree>>,
        returnType: Type2,
        translator: RustTranslator,
    ): Rust.Tree {
        val messages = "self".toKeyId(pos).methodCall("messages_combined")
        val makeError = "temper_core".toId(pos).extendWith(listOf("Error", "with_optional_message"))
        val error = Rust.Call(pos, makeError, listOf(messages))
        return Rust.Call(pos, "Err".toId(pos), listOf(error))
    }
}

private val timesFltFlt = Infix("TimesFltFlt", BuiltinOperatorId.TimesFltFlt, RustOperator.Multiplication)
private val timesIntInt = MethodCall("TimesIntInt", "wrapping_mul", BuiltinOperatorId.TimesIntInt)
private val valueResultConstructor =
    FunctionCall("ValueResult::constructor", "Some", cloneEvenIfFirst = true, hasGeneric = true)

private val connectedReferences = listOf(
    CmpGeneric,
    ConsoleLog,
    dateToday,
    denseBitVectorConstructor,
    denseBitVectorGet,
    denseBitVectorSet,
    dequeAdd,
    dequeConstructor,
    dequeIsEmpty,
    dequeRemoveFirst,
    DoneResult,
    Empty,
    float64Expm1,
    float64Log,
    float64Log1p,
    float64Min,
    float64Max,
    float64Near,
    float64Sign,
    Float64E,
    Float64Pi,
    float64ToInt,
    Float64ToIntUnsafe,
    float64ToInt64,
    Float64ToInt64Unsafe,
    float64ToString,
    GetConsole,
    ignore,
    IntToFloat64,
    IntToInt64,
    intToString,
    int64ToFloat64,
    Int64ToFloat64Unsafe,
    int64ToInt32,
    Int64ToInt32Unsafe,
    int64ToString,
    listForEach,
    listBuilderAdd,
    listBuilderAddAll,
    listBuilderClear,
    listBuilderConstructor,
    listBuilderRemoveLast,
    listBuilderReverse,
    listBuilderSet,
    listBuilderSort,
    listBuilderSplice,
    listedFilter,
    listedGet,
    listedGetOr,
    listedIsEmpty,
    listedJoin,
    listedLength,
    listedMap,
    listedReduce,
    listedReduceFrom,
    listedSlice,
    listedSorted,
    listedToList,
    listedToListBuilder,
    mapConstructor,
    mapBuilderClear,
    mapBuilderConstructor,
    mapBuilderRemove,
    mapBuilderSet,
    mappedForEach,
    mappedGet,
    mappedGetOr,
    mappedHas,
    mappedLength,
    mappedKeys,
    mappedToList,
    mappedToListBuilder,
    mappedToListBuilderWith,
    mappedToListWith,
    mappedToMap,
    mappedToMapBuilder,
    mappedValues,
    netSend,
    promiseBuilderComplete,
    PairConstructor,
    regexCompileFormatted,
    regexCompiledFind,
    regexCompiledFound,
    regexCompiledReplace,
    regexCompiledSplit,
    regexFormatterPushCodeTo,
    SimpleToString,
    StringBegin,
    stringCountBetween,
    stringEnd,
    stringForEach,
    stringFromCodePoint,
    stringFromCodePoints,
    stringGet,
    stringHasAtLeast,
    stringHasIndex,
    stringIndexOf,
    stringNext,
    stringPrev,
    stringSlice,
    stringSplit,
    stringStep,
    stringToFloat64,
    stringToInt,
    stringToInt64,
    stringToString,
    StringBuilderConstructor,
    stringBuilderAppend,
    stringBuilderAppendBetween,
    stringBuilderAppendCodePoint,
    stringBuilderToString,
    StringIndexNone,
    TestBail,
    valueResultConstructor,
).flatMap { ref -> ref.connectedNames.map { it to ref } }.toMap()

private const val FORMAT_MACRO_NAME = "format!"

private val connectedTypes = mapOf(
    "StringBuilder" to ConnectedType.StringBuilder,
)
