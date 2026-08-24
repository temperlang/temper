package lang.temper.frontend

import lang.temper.value.BlockTree
import lang.temper.value.ControlFlow
import lang.temper.value.LinearFlow
import lang.temper.value.StructuredFlow
import lang.temper.value.TEdge

/**
 * The children, in evaluation order, of [block] if execution is simply linear.
 *
 * This is like getting the maximal paths and seeing if there is one basic block that
 * starts at the entry and ends at the exit.
 */
fun getBlockChildrenInOrderIfLinear(
    block: BlockTree,
): List<TEdge>? = when (val flow = block.flow) {
    is StructuredFlow -> {
        val edges = mutableListOf<TEdge>()
        fun walk(cf: ControlFlow): Boolean {
            when (cf) {
                is ControlFlow.StmtBlock -> for (s in cf.stmts) {
                    if (!walk(s)) { return false }
                }
                is ControlFlow.Labeled -> return walk(cf.stmts)
                is ControlFlow.Stmt ->
                    edges.add(block.dereference(cf.ref) ?: return false)
                else -> return false
            }
            return true
        }
        if (walk(flow.controlFlow)) {
            edges.toList()
        } else {
            null
        }
    }
    is LinearFlow ->
        if (block.parts.label == null) {
            block.edges.toList()
        } else {
            null
        }
}
