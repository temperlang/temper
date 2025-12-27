package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InnerTreeType
import lang.temper.value.LeafTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TInt
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.Value

/**
 * Converts `++x` to `(x = x + 1)`.
 *
 * <!-- snippet: builtin/++ -->
 * # `++` operator
 * `++x` is equivalent to `x += 1`.
 * `x++` has the same effect as `++x`, but produces the value of x before incrementing.
 *
 * ```temper true
 * var x: Int = 0;
 * // when `x` comes after  `++`, produces value after  increment
 * console.log((++x).toString()); //!outputs "1"
 * // when `x` comes before `++`, produces value before increment
 * console.log((x++).toString()); //!outputs "1"
 * x == 2
 * ```
 *
 * The effects of `++x` and `x++` differ from `x = x + 1`, in that if `x` is a complex expression,
 * its parts are only evaluated once.
 * For example, in `++array[f()]`, the function call, `f()`, which computes the index,
 * only happens once.
 *
 * <!-- snippet: builtin/-- -->
 * # `--` operator
 * `--x` is equivalent to `x -= 1`.
 * `x--` has the same effect as `--x`, but produces the value of x before incrementing.
 *
 * ```temper true
 * var x: Int = 0;
 * // when `x` comes after  `--`, produces value after  increment
 * console.log((--x).toString()); //!outputs "-1"
 * // when `x` comes before `--`, produces value before increment
 * console.log((x--).toString()); //!outputs "-1"
 * x == -2
 * ```
 *
 * The effects of `--x` and `x--` differ from `x = x - 1`, in that if `x` is a complex expression,
 * its parts are only evaluated once.
 * For example, in `--array[f()]`, the function call, `f()`, which computes the index,
 * only happens once.
 */
class DesugarPrefixOperatorMacro(
    override val name: String,
    val op: CallableValue,
) : MacroValue, NamedBuiltinFun {
    override val sigs: List<Signature2>? get() = null
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        if (args.size != 1) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(1))
        }

        return when (interpMode) {
            InterpMode.Partial -> {
                macroEnv.orderChildMacrosEarly(prefixOperatorTypes)
                val left = args.valueTree(0) as? NameLeaf
                    // TODO: hoist complex left-hand-sides.
                    ?: return Fail
                macroEnv.replaceMacroCallWith(
                    macroEnv.treeFarm.grow {
                        Call(macroEnv.pos, BuiltinFuns.vSetLocalFn) {
                            Replant(left.copyLeft())
                            Call(op) {
                                Replant(left.copyRight())
                                V(Value(1, TInt))
                            }
                        }
                    },
                )
                NotYet
            }
            InterpMode.Full -> {
                macroEnv.explain(MessageTemplate.CannotInvokeMacroAsFunction)
                Fail
            }
        }
    }
    companion object {
        private val prefixOperatorTypes = listOf(
            MacroSignature(
                returnType = TreeTypeStructureExpectation(setOf(InnerTreeType.Call)),
                requiredValueFormals = listOf(
                    MacroValueFormal(
                        symbol = Symbol("operand"),
                        reifiedType = TreeTypeStructureExpectation(
                            setOf(
                                LeafTreeType.LeftName,
                                LeafTreeType.RightName,
                            ),
                        ),
                        kind = ValueFormalKind.Required,
                    ),
                ),
                restValuesFormal = null,
            ),
        )
    }
}
