package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.value.ActualValues
import lang.temper.value.InterpreterCallback
import lang.temper.value.MacroEnvironment
import lang.temper.value.PartialResult
import lang.temper.value.Result
import lang.temper.value.Value
import lang.temper.value.and
import lang.temper.value.valueContained
import lang.temper.value.void

/**
 * The comma operator which yields the result of its last operand.
 *
 * The comma operator evaluates its operands in order and yields the result of its last operand.
 *
 * It can be used to perform a side effect followed by an expression.
 *
 * ```temper 8
 * var i = 0;
 *
 * i += 4,  // For side-effect
 * i * 2    // Result
 * ```
 *
 * `,` is provided for familiarity with JS/TS.
 * Prefer [`do {⋯}` syntax][snippet/builtin/do] syntax.
 */
internal object CommaFn : BuiltinFun(",", null), PureCallableValue {
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val lastIndex = args.lastIndex
        return if (lastIndex >= 0) {
            args[lastIndex].and { it }
        } else {
            void
        }
    }

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        val result = super<PureCallableValue>.invoke(macroEnv, interpMode)
        // Even if we only managed to convert the last operand to a value, we still know the result.
        if (result !is Value<*> && interpMode == InterpMode.Partial) {
            val lastIndex = args.size - 1
            if (lastIndex < 0) {
                return void
            } else {
                val lastTree = args.valueTree(lastIndex)
                val lastValue = lastTree.valueContained
                if (lastValue != null) {
                    return lastValue
                }
            }
        }
        return result
    }

    override val callMayFailPerSe: Boolean get() = false
}
