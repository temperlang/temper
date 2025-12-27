package lang.temper.interp

import lang.temper.interp.docgenalts.isPreserveCall
import lang.temper.value.BlockTree
import lang.temper.value.PreserveFn
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTree

/**
 * Finds all calls to the special [*preserve*][PreserveFn] function, and replaces them with
 * either the original or the reduced based on [whichToUse].
 *
 * - When [whichToUse] returns [ReplacementPolicy.Preserve], the *preserve* call is replaced
 *   with the original.
 * - When [whichToUse] returns [ReplacementPolicy.Discard], the *preserve* call is replaced
 *   with the reduced.
 */
fun BlockTree.restorePreserved(
    whichToUse: (Tree, ValueLeaf) -> ReplacementPolicy = { _, _ -> ReplacementPolicy.Preserve },
) {
    fun restore(edge: TEdge) {
        val t = edge.target
        if (isPreserveCall(t)) {
            val preserved = t.child(1)
            val reduced = t.child(2) as ValueLeaf
            val replacement = when (whichToUse(preserved, reduced)) {
                ReplacementPolicy.Discard -> reduced
                ReplacementPolicy.Preserve -> preserved
            }
            edge.replace(freeTree(replacement))
            restore(edge)
            return
        }
        t.edges.forEach { restore(it) }
    }

    edges.forEach { restore(it) }
}
