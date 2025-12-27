package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.Fail
import lang.temper.value.InnerTreeType
import lang.temper.value.LeafTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.freeTree
import lang.temper.value.vInitSymbol

/**
 * Desugar `x++` to `{ t#0 = x; x = x + 1; t#0 }`
 */
class DesugarPostfixOperatorMacro(override val name: String) : MacroValue, NamedBuiltinFun {
    override val sigs: List<Signature2>? get() = null

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        if (args.size != 2) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(2))
        }
        val resultName = macroEnv.nameMaker.unusedTemporaryName("postfixReturn")
        return when (interpMode) {
            InterpMode.Partial -> {
                macroEnv.orderChildMacrosEarly(postfixOperatorTypes)
                val operator = args.valueTree(0) as? NameLeaf
                    // TODO: hoist complex left-hand-sides.
                    ?: return Fail
                val target = args.valueTree(1)

                macroEnv.replaceMacroCallWith(
                    macroEnv.treeFarm.grow {
                        Block(macroEnv.pos) {
                            // Set the temporary to the value
                            Decl(resultName) {
                                V(vInitSymbol)
                                Replant(freeTree(target))
                            }
                            Call {
                                // Move it to the other side as a prefix operator and let it
                                // resolve otherwise
                                Replant(operator.copyRight())
                                Replant(freeTree(target.copy()))
                            }
                            Rn(resultName)
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
        private val postfixOperatorTypes = listOf(
            MacroSignature(
                returnType = TreeTypeStructureExpectation(setOf(InnerTreeType.Block)),
                requiredValueFormals = listOf(
                    MacroValueFormal(
                        symbol = Symbol("operand"),
                        reifiedType = TreeTypeStructureExpectation(
                            setOf(
                                LeafTreeType.LeftName,
                                LeafTreeType.RightName,
                            ),
                        ),
                        ValueFormalKind.Required,
                    ),
                ),
            ),
        )
    }
}
