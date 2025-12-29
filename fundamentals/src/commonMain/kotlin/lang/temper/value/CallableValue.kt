package lang.temper.value

import lang.temper.env.InterpMode
import lang.temper.type2.Signature2

/**
 * A value that may be called.
 * Callable values are a subtype of macro values since callables are just macros that do not need
 * to access their arguments as syntax trees, and which want their arguments evaluated in order.
 */
interface CallableValue : MacroValue {
    val isPure get() = this is BuiltinStatelessMacroValue

    override val sigs: List<Signature2>?

    override val functionSpecies: FunctionSpecies
        get() = if (isPure) FunctionSpecies.Pure else FunctionSpecies.Normal

    fun mayReplaceCallWithArgs(args: ActualValues): Boolean =
        functionSpecies == FunctionSpecies.Pure

    operator fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        return if (interpMode == InterpMode.Full) {
            val macroArgs = macroEnv.args
            val results = macroArgs.indices.map { i ->
                when (val result = macroArgs.result(i, interpMode)) {
                    NotYet, is Fail -> return result
                    is Value<*> -> result
                }
            }
            invoke(
                EagerArgsWrapper(macroArgs, results),
                macroEnv as InterpreterCallback,
                interpMode,
            )
        } else {
            NotYet
        }
    }

    companion object {
        val noop = Value(
            object : BuiltinStatelessCallableValue {
                override fun invoke(
                    args: ActualValues,
                    cb: InterpreterCallback,
                    interpMode: InterpMode,
                ) = void
                override val sigs: List<Signature2>? get() = null
            },
            TFunction,
        )
    }
}

/** Builtin, stateless are stable so references to them can be aggressively inlined. */
interface BuiltinStatelessCallableValue : BuiltinStatelessMacroValue, CallableValue

internal class EagerArgsWrapper(
    val underlying: Actuals,
    val results: List<Value<*>>,
) : ActualValues, Actuals by underlying {
    override fun result(index: Int, computeInOrder: Boolean): Value<*> = results[index]
    override fun result(index: Int, interpMode: InterpMode, computeInOrder: Boolean) =
        results[index]
}
