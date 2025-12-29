package lang.temper.interp

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.LeftHandOfMacro
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.SourceName
import lang.temper.name.Temporary
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.ActualValues
import lang.temper.value.CallTree
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
import lang.temper.value.RightNameLeaf
import lang.temper.value.Tree
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.and
import lang.temper.value.freeTree

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
        val left = args.valueTree(0)
        val right = args.valueTree(1)
        if (left is NameLeaf) {
            // No need to desugar
            macroEnv.replaceMacroCallWith {
                Call(macroEnv.pos) {
                    V(macroEnv.callee.pos, BuiltinFuns.vSetLocalFn)
                    Replant(left.copyLeft())
                    Call(macroEnv.pos) {
                        V(macroEnv.callee.pos, Value(simpleOp))
                        Replant(left.copyRight())
                        Replant(freeTree(right))
                    }
                }
            }
            return NotYet
        }

        // There are a number of kinds of complex left-hand sides:
        //
        //     left.prop += right     // See DotOperationDesugarer
        //     left[index] += right   // See SquareBracketFn
        //
        // Rather than special case handling here for these two cases,
        // we have a general mechanism that lets a macro behave one way
        // in a left-hand context and in a right-hand context.
        //
        //     leftHandOf(left.prop, left.prop + right)
        //
        // DotOperationDesugarer is an left-hand aware macro, so its
        // first application knows to generate a use of left.prop's setter
        // instead of its getter as for the second use.
        //
        // But first, we pull out temporaries.
        // We don't have enough context here to know that `left` stays stable
        // across all uses above.  Consider the below:
        //
        //      left[do { left = otherLeft; i++ }] += 1
        //
        // Obviously, we would want to avoid multiply evaluating that
        // `do` block, but we can't just convert that to:
        //
        //      do {
        //        let t#1 = do { left = otherLeft; i++ };
        //        leftHandOf(left[t#1], left[t#1] + right)
        //      }
        //
        // That is wrong because the evaluation of t#1 changes `left`'s referent.
        // The correct translation is:
        //
        //      do {
        //        let t#0 = left;
        //        let t#1 = do { left = otherLeft; i++ };
        //        leftHandOf(t#0[t#1], t#0[t#1] + right)
        //      }
        //
        // So we pull all non-value, non-builtin-name expressions out into
        // temporaries.
        if (left is CallTree) {
            val callee = left.childOrNull(0)
            if (isKnownStable(callee)) {
                // The callee is stable not an arbitrarily large sub-tree / copyable.
                // And we won't need to capture it in a temporary.
                macroEnv.replaceMacroCallWith {
                    Block(macroEnv.pos) {
                        for (i in 1 until left.size) {
                            val e = left.edge(i)
                            val child = e.target
                            if (!isKnownStable(child)) {
                                val t = macroEnv.nameMaker.unusedTemporaryName("t")
                                Decl(child.pos.leftEdge, t)
                                e.replace { Rn(t) }
                                Call(child.pos.leftEdge, BuiltinFuns.setLocalFn) {
                                    Ln(t)
                                    Replant(child)
                                }
                            }
                        }
                        Call(left.pos, LeftHandOfMacro) {
                            Replant(left.copy())
                            Call(macroEnv.pos) {
                                V(macroEnv.callee.pos, Value(simpleOp))
                                Replant(freeTree(left))
                                Replant(freeTree(right))
                            }
                        }
                    }
                }
            }
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

fun isKnownStable(t: Tree?) = when (t) {
    is ValueLeaf -> true
    is RightNameLeaf -> when (t.content) {
        is BuiltinName -> true
        is ExportedName -> true
        is Temporary, is ParsedName, is SourceName -> false
    }
    else -> false
}
