package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.value.BlockTree
import lang.temper.value.LinearFlow
import lang.temper.value.StructuredFlow
import lang.temper.value.Tree
import lang.temper.value.simplifyStructuredBlock

/**
 * After we've stitched subsystems together, it's common for [BlockTree]s to have children
 * that are not referenced within their subsystem.
 *
 * This pass walks the tree again to identify and remove such garbage subtrees†.
 *
 * † - available as a band name.
 */
internal fun simplifyFlow(
    tree: Tree,
    // See simplifyControlFlow for the meaning of these flags.
    assumeAllJumpsResolved: Boolean = false,
    assumeResultsCaptured: Boolean = false,
) {
    TreeVisit
        .startingAt(tree)
        .forEachContinuing {
            if (it is BlockTree) {
                when (val flow = it.flow) {
                    is LinearFlow -> {}
                    is StructuredFlow -> simplifyStructuredBlock(
                        block = it,
                        flow = flow,
                        assumeAllJumpsResolved = assumeAllJumpsResolved,
                        assumeResultsCaptured = assumeResultsCaptured,
                    )
                }
            }
        }
        .visitPreOrder()
}
