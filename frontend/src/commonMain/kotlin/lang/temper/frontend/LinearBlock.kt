package lang.temper.frontend

import lang.temper.value.BlockTree
import lang.temper.value.LinearFlow
import lang.temper.value.StructuredFlow
import lang.temper.value.TEdge
import lang.temper.value.forwardMaximalPaths

/**
 * The children, in evaluation order, of [block] if execution is simply linear.
 *
 * This is like getting the maximal paths and seeing if there is one basic block that
 * starts at the entry and ends at the exit.
 */
fun getBlockChildrenInOrderIfLinear(block: BlockTree): List<TEdge>? = when (block.flow) {
    is StructuredFlow -> {
        val paths = forwardMaximalPaths(block)
        if (paths.pathIndices.start == paths.pathIndices.endInclusive) {
            buildList {
                for (el in paths[paths.entryPathIndex].elementsAndConditions) {
                    block.dereference(el.ref)?.let { add(it) }
                }
            }
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
