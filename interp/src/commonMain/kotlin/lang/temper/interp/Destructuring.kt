package lang.temper.interp

import lang.temper.common.Log
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.value.CallTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.asSymbol
import lang.temper.value.curliesBuiltinName
import lang.temper.value.surpriseMeSymbol
import lang.temper.value.symbolContained

// Not inline because we'll want to expand this to recursive destructuring at some point.
/** Loop over members for destructuring. */
fun Tree.walkDestructuring(logSink: LogSink, action: DestructuringAction) {
    var childIndex = 1
    val metaNodes = mutableListOf<Tree>()
    kids@ while (childIndex < size) {
        val source = child(childIndex)
        childIndex += 1
        val sourceNameLeaf = source
        var renamed = false
        var targetNameLeaf = sourceNameLeaf as? LeftNameLeaf
        metaNodes.clear()
        // Move over non-nested metadata into our newly planned nested decl.
        meta@ while (childIndex < size) {
            val valueLeaf = (childOrNull(childIndex) as? ValueLeaf) ?: break@meta
            when (valueLeaf.symbolContained ?: break@meta) {
                // Special treatment for `as`.
                asSymbol -> {
                    val target = child(childIndex + 1)
                    if (target is LeftNameLeaf) {
                        if (renamed) {
                            logSink.log(Log.Error, MessageTemplate.MultipleRenames, valueLeaf.pos, emptyList())
                        } else {
                            targetNameLeaf = target
                            renamed = true
                        }
                    } // TODO(tjp, destructure): else check for Nested destructuring
                }
                // And for wildcard. It stands alone as the next target.
                surpriseMeSymbol -> break@meta
                // Copy other metadata.
                // `readChildrenIntoMetadataSymbolMap` says every symbol has a value node, so do that here, too.
                else -> {
                    metaNodes.add(valueLeaf)
                    metaNodes.add(child(childIndex + 1))
                }
            }
            childIndex += 2
        }
        action(targetNameLeaf, source, metaNodes)
    }
}

/** Any provided trees can be freed for use elsewhere, but the `metaNodes` list itself is owned by the caller. */
typealias DestructuringAction = (
    targetNameLeaf: LeftNameLeaf?,
    source: Tree,
    metaNodes: MutableList<Tree>,
) -> Unit

/** Returns the tree as a [CallTree] if it's a call to curlies, else null. */
fun Tree?.asCurliesCall(): CallTree? {
    val tree = this
    val calleeName = ((tree as? CallTree)?.childOrNull(0) as? RightNameLeaf)?.content
    return if (calleeName == curliesBuiltinName) {
        tree
    } else {
        null
    }
}
