package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.value.BlockTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.Tree
import lang.temper.value.freeTarget

internal fun allRootsOf(moduleRoot: BlockTree): List<Tree> {
    val roots = mutableListOf<Tree>(moduleRoot)

    TreeVisit
        .startingAt(moduleRoot)
        .forEachContinuing {
            if (it is FunTree) {
                val parts = it.parts
                if (parts != null) {
                    roots.add(parts.body)
                }
            }
        }
        .visitPreOrder()

    return roots.toList()
}

internal fun allRootsOfAsBlocks(moduleRoot: BlockTree) =
    allRootsOf(moduleRoot).map { rootMaybeNotBlock ->
        val rootEdge = rootMaybeNotBlock.incoming
        if (rootMaybeNotBlock is BlockTree) {
            rootMaybeNotBlock
        } else {
            // This is null-safe because moduleRoot is a block.
            require(rootEdge != null)
            // Wrap non-block function bodies so that we have something to stitch nested
            // blocks into.
            rootEdge.replace(
                BlockTree(
                    rootMaybeNotBlock.document,
                    rootMaybeNotBlock.pos,
                    listOf(freeTarget(rootEdge)),
                    LinearFlow,
                ),
            )
            rootEdge.target as BlockTree
        }
    }
