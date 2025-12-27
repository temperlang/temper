package lang.temper.value

import lang.temper.common.AtomicCounter
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type.WellKnownTypes as WKT

sealed interface NullaryNeverFn : BuiltinStatelessCallableValue, NamedBuiltinFun

/**
 * Immediately propagates a `Bubble` value, unconditionally.
 *
 * <!-- snippet: builtin/bubble -->
 * # `bubble()`
 * A function that immediately bubbles.  Typically, this will result in execution
 * of the right argument to the closest containing [snippet/builtin/orelse].
 *
 * ⎀ failure
 */
object BubbleFn : NullaryNeverFn {
    override val name = "bubble"

    // () -> Never<Void> throws Bubble  &  <T>() -> Never<T> throws Bubble
    override val sigs = nullaryNeverReturnsSigs { MkType2.result(it, WKT.bubbleType2).get() }

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode) =
        Fail // Representation for bubbling in the interpreter.

    override val builtinOperatorId get() = BuiltinOperatorId.Bubble
}

/**
 * Panics immediately.
 *
 *  * <!-- snippet: builtin/panic -->
 * # `panic()`
 * A function that immediately panics, which is unrecoverable inside Temper.
 * It might be recoverable in backend code.
 */
object PanicFn : NullaryNeverFn {
    override val name = "panic"

    // () -> Void  &  <T>() -> Never<T>
    override val sigs = nullaryNeverReturnsSigs { it }

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode) =
        throw Panic()

    override val builtinOperatorId get() = BuiltinOperatorId.Panic

    override val callMayFailPerSe: Boolean get() = false
}

/**
 * Marker for bodies of abstract functions that must be overridden in all concrete subtypes of the
 * containing type.
 */
object PureVirtual : NullaryNeverFn {
    override val name: String = pureVirtualBuiltinName.builtinKey
    override val sigs = nullaryNeverReturnsSigs { it }

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        throw Panic("PureVirtual invoked @ ${cb.pos}")
    }

    override val isPure: Boolean = false // Do not try to inline

    override val callMayFailPerSe: Boolean = false // Panics are distinct from failure
}

fun Tree?.isPureVirtualBody(): Boolean = when (this) {
    null -> false
    is CallTree -> childOrNull(0)?.functionContained == PureVirtual
    is BlockTree -> this.children.any { it.isPureVirtualBody() }
    is DeclTree,
    is EscTree,
    is FunTree,
    is NameLeaf,
    is StayLeaf,
    is ValueLeaf,
    -> false
}

/**
 * A placeholder that stays in the AST to indicate that part of the AST could not be converted to
 * a valid construct.
 *
 * An AST that contains a call to this function is not ready for production.
 */
object ErrorFn : NullaryNeverFn, TokenSerializable {
    override val name: String = errorBuiltinName.builtinKey

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        maybePanic(interpMode, cb)
        return Fail
    }

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        maybePanic(interpMode, macroEnv)
        return NotYet
    }

    private fun maybePanic(interpMode: InterpMode, cb: InterpreterCallback) {
        if (interpMode == InterpMode.Full) {
            // Log it during run, but if evaluating a pure function for
            // a result during a larger partial interpretation, then explain
            // the problem.
            if (cb.stage >= Stage.Run) {
                cb.logSink.log(
                    Log.Error,
                    MessageTemplate.InterpreterCannotEvaluateErrorExpression,
                    cb.pos,
                    emptyList(),
                )
            } else {
                cb.explain(MessageTemplate.InterpreterCannotEvaluateErrorExpression)
            }
            throw Panic()
        }
    }

    // Does not follow normal failure path during runtime.
    override val callMayFailPerSe: Boolean = false

    override val sigs: List<Signature2> = this.nullaryNeverReturnsSigs { it }
        .map { it.copy(restInputsType = WKT.anyValueOrNullType2) }

    val voidSig: Signature2 = sigs[0]
    val genericSig: Signature2 = sigs[1]

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(
            OutputToken(errorBuiltinName.builtinKey, OutputTokenType.Word),
        )
    }

    override fun toString(): String = errorBuiltinName.builtinKey
}

private fun NamedBuiltinFun.nullaryNeverReturnsSigs(
    makeReturnType: (Type2) -> Type2,
): List<Signature2> {
    val nameKey = "${name}T"
    val typeFormal = TypeFormal(
        Position(ImplicitsCodeLocation, 0, 0),
        BuiltinName(nameKey),
        Symbol(nameKey),
        Variance.Invariant,
        AtomicCounter(),
        upperBounds = listOf(WKT.anyValueType),
    )
    val typeT = MkType2(typeFormal).get()

    val neverTReturnType = makeReturnType(
        MkType2(WKT.neverTypeDefinition).actuals(listOf(typeT)).get(),
    )
    val neverVoidReturnType = makeReturnType(
        MkType2(WKT.neverTypeDefinition).actuals(listOf(WKT.voidType2)).get(),
    )

    return listOf(
        Signature2(
            returnType2 = neverVoidReturnType,
            requiredInputTypes = listOf(),
            hasThisFormal = false,
            typeFormals = listOf(),
        ),
        Signature2(
            returnType2 = neverTReturnType,
            requiredInputTypes = listOf(),
            hasThisFormal = false,
            typeFormals = listOf(typeFormal),
        ),
    )
}

val errorFn = Value(
    ErrorFn,
    TFunction,
)

fun isErrorCall(t: Tree) =
    t is CallTree && t.childOrNull(0)?.functionContained is ErrorFn

/**
 * True for [NullaryNeverFn]s like `panic()`, and `bubble()`,
 * builtin zero argument calls whose return type is never, but
 * whose exact variant depends on the context in which they're called.
 */
fun isNullaryNeverCall(t: Tree): Boolean =
    (t as? CallTree)?.childOrNull(0)?.functionContained is NullaryNeverFn
