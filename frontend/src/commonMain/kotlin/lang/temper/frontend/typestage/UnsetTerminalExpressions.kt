package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.common.ForwardOrBack
import lang.temper.common.isNotEmpty
import lang.temper.frontend.syntax.isLeftHandSide
import lang.temper.name.ResolvedName
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.MaximalPathIndex
import lang.temper.value.NameLeaf
import lang.temper.value.TEdge
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.lookThroughDecorations
import lang.temper.value.orderedPathIndices

internal data class UnsetTerminalExpressions(
    val unsetTerminalExpressionEdges: List<TEdge>,
    val setsName: Boolean,
    val reachesExit: Boolean,
)

/**
 * If a name is set on one path to a terminal expression, then it's the programmer's
 * responsibility to set on all paths to that terminal expression.
 * For example, in
 *
 *     if (c) {
 *       return__123 = f();
 *       g();
 *     } else {
 *       h();
 *     }
 *
 * the terminal expression are `g()` and `h()`.
 * `return__123` is assigned preceding `g()` so the only "unset" terminal expression is `h()`.
 *
 * if (c) {
 *   return__123 = f();
 *   g();
 * } else {
 *   h();
 * }
 */
internal fun findUnsetTerminalExpressions(
    root: BlockTree,
    outputName: ResolvedName?,
): UnsetTerminalExpressions {
    val paths = forwardMaximalPaths(root, assumeFailureCanHappen = true)
    fun childSetsOutputName(ref: BlockChildReference?): Boolean {
        if (ref == null) { return false }
        val tEdge = root.dereference(ref) ?: return false
        var setsOutputName = false
        TreeVisit.startingAt(tEdge.target)
            .forEach { t ->
                if (
                    t is NameLeaf &&
                    t.content == outputName &&
                    isLeftHandSide(t, andAssigned = true)
                ) {
                    setsOutputName = true
                    VisitCue.AllDone
                } else if (t is FunTree) {
                    VisitCue.SkipOne
                } else {
                    VisitCue.Continue
                }
            }
            .visitPreOrder()
        return setsOutputName
    }

    // We want to know which paths are reliably assigned before, so that
    // we can exclude them when later finding terminal expressions.
    val pathIndicesThatFollowAssignmentToOutputName = mutableSetOf<MaximalPathIndex>()
    // To that end, we traverse paths preceders first below, and decrement
    // followers' counts when we find something is excluded.
    // If this count is zero, and it was not initially zero (is not the entry
    // path), then we can exclude it.
    val countOfNonAssigningPreceders = mutableMapOf<MaximalPathIndex, Int>()
    paths.pathIndices.forEach { pathIndex ->
        val path = paths[pathIndex]
        val count = path.preceders.count { it.dir == ForwardOrBack.Forward }
        countOfNonAssigningPreceders[pathIndex] = count
    }
    for (pathIndex in orderedPathIndices(paths, ForwardOrBack.Back)) {
        val path = paths[pathIndex]
        var follows = pathIndex != paths.entryPathIndex &&
            0 == countOfNonAssigningPreceders[pathIndex]
        if (follows) {
            // Don't need to examine trees for assignments to the output name
            for (f in path.followers) {
                if (f.dir == ForwardOrBack.Forward) {
                    val followerIndex = f.pathIndex
                    if (followerIndex != null) {
                        countOfNonAssigningPreceders[followerIndex] =
                            countOfNonAssigningPreceders.getValue(followerIndex) - 1
                    }
                }
            }
        } else {
            for (element in path.elements) {
                if (childSetsOutputName(element.ref)) {
                    follows = true
                    break
                }
            }
            for (f in path.followers) {
                if (f.dir == ForwardOrBack.Forward) {
                    if (!follows && childSetsOutputName(f.condition?.ref)) { follows = true }
                    val followerIndex = f.pathIndex
                    if (followerIndex != null && follows) {
                        countOfNonAssigningPreceders[followerIndex] =
                            countOfNonAssigningPreceders.getValue(followerIndex) - 1
                    }
                }
            }
        }
        if (follows) {
            pathIndicesThatFollowAssignmentToOutputName.add(pathIndex)
        }
    }
    val outputIsSetByAny = pathIndicesThatFollowAssignmentToOutputName.isNotEmpty()

    // Gather the terminal expressions
    val unsetTerminalExpressionEdges = buildSet {
        val deque = ArrayDeque(paths.exitPathIndices)
        while (deque.isNotEmpty()) {
            val pathIndex = deque.removeLast()
            if (pathIndex in pathIndicesThatFollowAssignmentToOutputName) {
                continue
            }
            val path = paths[pathIndex]
            // Find a statement-like edge
            var found = false
            for (element in path.elements.asReversed()) {
                if (!element.isCondition) {
                    // Is statement-like
                    val edge = root.dereference(element.ref)
                    if (edge != null && lookThroughDecorations(edge).target is DeclTree) {
                        // Declarations are not valid results.
                    } else {
                        if (edge != null) { add(edge) }
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                // Keep searching in case the path was all unconditional edges.
                for (p in path.preceders) {
                    if (p.dir == ForwardOrBack.Forward) {
                        deque.add(p.pathIndex)
                    }
                }
            }
        }
    }

    return UnsetTerminalExpressions(
        unsetTerminalExpressionEdges = unsetTerminalExpressionEdges.toList(),
        setsName = outputIsSetByAny,
        reachesExit = paths.exitPathIndices.isNotEmpty(),
    )
}
