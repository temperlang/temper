package lang.temper.frontend.disambiguate

import lang.temper.common.putMulti
import lang.temper.frontend.prefixBlockWith
import lang.temper.interp.convertToErrorNode
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.TEdge
import lang.temper.value.TInt
import lang.temper.value.TVoid
import lang.temper.value.Tree
import lang.temper.value.freeTarget
import lang.temper.value.hoistToBlockSymbol
import lang.temper.value.valueContained

internal fun hoistDecls(root: BlockTree) {
    val toHoist = mutableMapOf<BlockTree, MutableList<TEdge>>()
    val blockStack = mutableListOf(root)

    // Walk to identify things that need to be hoisted
    fun walk(e: TEdge) {
        val t = e.target
        val indexToRemove = if (t is BlockTree) {
            val sizeBefore = blockStack.size
            blockStack.add(t)
            sizeBefore
        } else {
            null
        }

        if (t is DeclTree) {
            val parts = t.partsIgnoringName
            if (parts != null) {
                val hoistCountEdge = parts.metadataSymbolMap[hoistToBlockSymbol]
                if (hoistCountEdge != null) {
                    val hoistCount = hoistCountEdge.target
                    val hoistCountValue = hoistCount.valueContained
                    val blockIndex = when (hoistCountValue?.typeTag) {
                        TInt -> blockStack.size - TInt.unpack(hoistCountValue).toInt()
                        TVoid -> 0 // to root
                        else -> null
                    }
                    if (blockIndex != null && blockIndex in blockStack.indices) {
                        toHoist.putMulti(blockStack[blockIndex], e) { mutableListOf() }
                        // Remove the metadata
                        val hoistCountEdgeKeyIndex = hoistCountEdge.edgeIndex - 1
                        t.removeChildren(hoistCountEdgeKeyIndex..hoistCountEdgeKeyIndex + 1)
                    } else {
                        // TODO: log as internal compiler error?
                        convertToErrorNode(e)
                    }
                }
            }
        }

        t.edges.forEach { walk(it) }

        if (indexToRemove != null) {
            blockStack.removeAt(indexToRemove)
        }
    }
    root.edges.forEach { walk(it) }

    for ((block, hoistees) in toHoist.entries) {
        val preceders = mutableListOf<Tree>()
        // Preserve lexical order among declarations hoisted to the same level
        for (hoistee in hoistees) {
            preceders.add(freeTarget(hoistee))
        }
        prefixBlockWith(preceders, block)
    }
}
