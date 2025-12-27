package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TEdge
import lang.temper.value.functionContained

/**
 * Marks macro calls meant as left hand sides in an assignment
 * or assignment-like operation.
 *
 * The marked macro calls should know to look for this and replace their parent via
 * [leftHandOfMacroContext].
 *
 * *DesugarCompoundAssignmentMacro* creates these.
 */
object LeftHandOfMacro : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        if (macroEnv.stage == Stage.Type) {
            // Too late.
            // if some child macros need to wait for type info, we could
            // have a signature like <T> (left T, T) -> T and push this
            // check off to GenerateCode.
            val problem = LogEntry(
                Log.Error,
                MessageTemplate.MalformedAssignment,
                macroEnv.pos,
                listOf(),
            )
            macroEnv.replaceMacroCallWithErrorNode(problem)
            return Fail(problem)
        }
        return NotYet
    }

    override val name: String = "leftHandOf"

    override val sigs: List<Signature2>? get() = null
}

/**
 * Returns the edge to change if the macro call is in a left-hand context.
 *
 * Some macros desugar to reads of fields, indexed elements, or other parts of a whole.
 *
 * The `left_hand` macro marks a macro call that appears in the left-hand side of an assignment.
 *
 * Macros that can desugar to a read in a right-hand context or to a write instruction in a
 * left-hand context should call this function to get the edge to replace when they are in
 * a left-hand context.
 *
 * Complex expressions may duplicate a macro call.
 *
 *     myMacro(obj, propertyExpr) *= 2
 *
 *     // ->
 *
 *     do {
 *       let t#1 = obj;
 *       let t#2 = propertyExpr;
 *       left_hand(
 *           myMacro(t#1, t#2),
 *           nym`*`(
 *               myMacro(t#1, t#2),
 *               2
 *           )
 *       )
 *     }
 *
 * That desugaring of a compound macro has:
 *
 * 1. Some capturing in temporaries to preserve order and frequency of operations.
 * 2. A [`left_hand`] use that distinguishes the first call to myMacro from the second.
 * 3. The right hand available to myMacro as the second argument to left_hand.
 */
fun leftHandOfMacroContext(macroEnv: MacroEnvironment): LeftHandOfMacroContext? =
    leftHandOfMacroContext(macroEnv.call)

/** See [leftHandOfMacroContext] */
fun leftHandOfMacroContext(macroCall: CallTree?): LeftHandOfMacroContext? {
    val callEdge = macroCall?.incoming
    val parent = callEdge?.source
    val parentEdge = parent?.incoming
    if (
        parentEdge != null && parent is CallTree &&
        parent.size == CALLEE_AND_TWO_ARGS &&
        callEdge == parent.edge(1)
    ) {
        val callee = parent.child(0)
        if (callee.functionContained == LeftHandOfMacro) {
            return LeftHandOfMacroContext(
                edgeToReplace = parentEdge,
                assigned = parent.edge(2),
            )
        }
    }
    return null
}

data class LeftHandOfMacroContext(
    val edgeToReplace: TEdge,
    val assigned: TEdge,
)

private const val CALLEE_AND_TWO_ARGS = 3
