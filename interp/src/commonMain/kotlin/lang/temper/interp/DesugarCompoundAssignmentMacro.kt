package lang.temper.interp

import lang.temper.builtin.desugarCompoundOperation
import lang.temper.builtin.vDesugarOperation
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.LeafTreeType
import lang.temper.value.MacroActuals
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.Value
import lang.temper.value.and

internal class DesugarCompoundAssignmentMacro(
    /** `+` if we're desugaring `+=`. */
    override val name: String,
    private val simpleOp: CallableValue,
) : MacroValue, NamedBuiltinFun {
    // Whether the left-hand-side can be set is checked statically after desugaring to simple
    // assignments.
    override val sigs: List<Signature2>? get() = simpleOp.sigs

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size != 2) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(2))
        }
        return when (interpMode) {
            InterpMode.Full -> applyFull(macroEnv, args)
            InterpMode.Partial -> applyPartial(macroEnv, args)
        }
    }

    private fun applyPartial(macroEnv: MacroEnvironment, args: MacroActuals): PartialResult {
        macroEnv.orderChildMacrosEarly(partialCompoundAssignmentFunctionTypes)
        val left = args.valueTree(0).incoming!!
        val right = args.valueTree(1).incoming!!
        val plantSimpler = desugarCompoundOperation(macroEnv, left, right) { pos, plantOperands ->
            V(pos.leftEdge, vDesugarOperation)
            V(pos, Value(simpleOp))
            plantOperands()
        }
        if (plantSimpler != null) {
            macroEnv.replaceMacroCallWith(plantSimpler)
        }
        return NotYet
    }

    private fun applyFull(macroEnv: MacroEnvironment, args: MacroActuals): PartialResult {
        val nameLeaf = args.valueTree(0) as? NameLeaf ?: return Fail
        return args.result(0, InterpMode.Full).and { leftVal ->
            args.result(1, InterpMode.Full).and { rightVal ->
                val actuals = ActualValues.from(leftVal, rightVal)
                simpleOp(actuals, macroEnv, InterpMode.Full).and { newVal ->
                    macroEnv.setLocal(nameLeaf.copyLeft(), newVal).and {
                        newVal
                    }
                }
            }
        }
    }

    override val assignsArgumentOne get() = true

    companion object {
        private val partialCompoundAssignmentFunctionTypes = listOf(
            MacroSignature(
                returnType = null,
                requiredValueFormals = listOf(
                    MacroValueFormal(
                        symbol = null,
                        reifiedType = TreeTypeStructureExpectation(
                            setOf(
                                LeafTreeType.LeftName,
                                LeafTreeType.RightName,
                            ),
                        ),
                        kind = ValueFormalKind.Required,
                    ),
                    MacroValueFormal(
                        symbol = null,
                        reifiedType = null,
                        kind = ValueFormalKind.Required,
                    ),
                ),
            ),
        )
    }
}
