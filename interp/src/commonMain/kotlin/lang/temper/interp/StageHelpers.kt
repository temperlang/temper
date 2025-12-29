package lang.temper.interp

import lang.temper.builtin.BuiltinFuns
import lang.temper.log.LogEntry
import lang.temper.stage.Stage
import lang.temper.value.CallTree
import lang.temper.value.ErrorFn
import lang.temper.value.StayLeaf
import lang.temper.value.TEdge
import lang.temper.value.TProblem
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.errorFn
import lang.temper.value.freeTarget
import lang.temper.value.functionContained

fun isErrorNode(tree: Tree): Boolean {
    if (tree is CallTree && tree.size >= 1) {
        val callee = tree.child(0)
        return callee.functionContained == ErrorFn
    }
    return false
}

/**
 * `(macroCall(...),)` is an idiom for embedding a macro call that should not be marked as an
 * error node by code that tries to desugar parse trees, especially during [Stage.DisAmbiguate].
 */
fun mightBeMacroCallMeantToHappenLater(tree: Tree): Boolean {
    if (tree is CallTree && tree.size == 2) {
        val callee = tree.child(0)
        return callee.functionContained == BuiltinFuns.commaFn
    }
    return false
}

/**
 * Replaces the edge target with an error node.
 */
fun convertToErrorNode(edge: TEdge, cause: LogEntry? = null) {
    edge.replace(errorNodeFor(freeTarget(edge), cause))
}

/**
 * Given an edge that can be freed that has problems, returns a call to the error function that
 * [isErrorNode].
 */
fun errorNodeFor(tree: Tree, cause: LogEntry? = null): CallTree =
    tree.treeFarm.grow {
        Call(tree.pos, errorFn) {
            if (cause != null) {
                V(cause.pos, Value(cause, TProblem))
            }
            if (tree.hasStayLeaf) {
                Esc(tree.pos) {
                    Replant(tree)
                }
            }
        }
    }

private val Tree.hasStayLeaf: Boolean get() =
    this is StayLeaf || children.any { it.hasStayLeaf }
