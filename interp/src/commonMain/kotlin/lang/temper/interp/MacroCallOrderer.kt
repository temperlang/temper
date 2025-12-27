package lang.temper.interp

import lang.temper.type2.AnySignature
import lang.temper.value.CallTree
import lang.temper.value.DynamicMessage
import lang.temper.value.Fail
import lang.temper.value.FunctionSpecies
import lang.temper.value.InterpreterCallback
import lang.temper.value.ResolutionProblem
import lang.temper.value.Resolutions
import lang.temper.value.Tree
import lang.temper.value.functionContained
import lang.temper.value.unify

/**
 * Responsible for selectively evaluating arguments to a macro call early so that the children have
 * the shape needed by the macro Call.
 *
 * By default, we evaluate macros before evaluating their arguments.
 * This is usually the right thing to do because macros need to be able to treat their arguments
 * as trees.
 *
 * Sometimes though, a macro expects its argument to have a particular structure, and subtrees
 * would have that structure if macros they contain were evaluated.
 *
 * Staging saves us from some of this.  An outer macro may fail, upon which interpretation proceeds
 * to the inner macro which succeeds.  Next stage, the outer macro call succeeds.
 *
 * But this does not help when the depth of macro calls exceeds the number of remaining stages.
 *
 * This orderer uses type hints, [lang.temper.value.StructureExpectation]s, to figure out which macro
 * calls might help, and selectively forces early evaluation of macro arguments where it seems to
 * be beneficial.
 */
internal class MacroCallOrderer(
    private val macroCall: CallTree?,
    private val actuals: LazyActualsList,
    private val callback: InterpreterCallback,
    private val macroSignatures: List<AnySignature>,
) {

    fun preEvaluateAsNeeded() {
        val dynamicMessage = DynamicMessage(actuals, actuals.interpMode)
        var minActualToEvaluate = 0
        while (true) {
            var structureMismatch: ResolutionProblem.StructureExpectationMismatch? = null
            for (macroSignature in macroSignatures) {
                val resolutions = Resolutions(callback)
                unify(dynamicMessage, macroSignature, resolutions)
                if (!resolutions.contradiction) { // Ok.
                    return
                }
                val problem = resolutions.problem
                if (problem !is ResolutionProblem.StructureExpectationMismatch) {
                    // If it's not a structure mismatch, then forcing early evaluation won't lead
                    // to this type variant passing.
                    continue
                }
                if (
                    structureMismatch == null ||
                    problem.actualIndex < structureMismatch.actualIndex
                ) {
                    structureMismatch = problem
                }
            }
            if (structureMismatch == null) {
                // If none of the types failed due to a structure mismatch then forcing early
                // evaluation won't allow progress by this macro.
                return
            }
            val actualIndex = structureMismatch.actualIndex
            if (actualIndex < minActualToEvaluate) {
                break
            }
            val problemChild = actuals.valueTree(actualIndex)
            if (!mayBeMacroCall(problemChild)) {
                break
            }
            // We've found a problem, know which edge is responsible, and evaluating it early will
            // not require going backwards past things that matched in previous iterations.
            val actualResult = actuals.result(actualIndex, computeInOrder = true)
            if (actualResult is Fail) {
                // If macro evaluation failed, there's probably little progress we can make.
                break
            }
            // If the macro replaced itself, then maybe it replaced itself with yet another macro
            // that needs to be tried.
            val edgeIndex = actualIndex + 1 // Skip over callee
            if (macroCall != null && problemChild !== macroCall.childOrNull(edgeIndex)) {
                minActualToEvaluate = actualIndex
                macroCall.edge(edgeIndex).breadcrumb = null // Allow re-interpretation
                actuals.clearResult(actualIndex)
            } else {
                minActualToEvaluate = actualIndex + 1
            }
        }
    }
}

internal fun mayBeMacroCall(t: Tree) = when (t) {
    is CallTree -> {
        val fnCalled = t.childOrNull(0)?.functionContained
        fnCalled == null || fnCalled.functionSpecies == FunctionSpecies.Macro
    }
    else -> false
}
