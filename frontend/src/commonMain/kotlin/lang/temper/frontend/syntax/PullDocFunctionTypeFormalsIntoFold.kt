package lang.temper.frontend.syntax

import lang.temper.ast.TreeVisit
import lang.temper.common.Log
import lang.temper.interp.convertToErrorNode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.LinearFlow
import lang.temper.value.Tree
import lang.temper.value.freeTree
import lang.temper.value.typeFormalSymbol

internal fun pullDocFunctionTypeFormalsIntoFold(root: BlockTree) {
    val typeFormalScopingBlocks = mutableListOf<BlockTree>()

    TreeVisit.startingAt(root)
        .forEachContinuing { tree ->
            val incoming = tree.incoming
            // Look for blocks that are assigned to a function name.
            if (
                tree is BlockTree && incoming != null && tree.flow is LinearFlow &&
                isAssignment(incoming.source) && incoming.edgeIndex == 2 &&
                tree.parts.label == null &&
                // We need at least a formal declaration and a result.
                tree.size >= 2 && tree.child(0).isTypeFormalDecl
            ) {
                typeFormalScopingBlocks.add(tree)
            }
        }
        .visitPostOrder()

    // Make sure the parent's parent is a block, and splice all but the last
    // in front of it.
    //     let f = {
    //       @typeFormal let T = ...;
    //       fn { ... }
    //     }
    // becomes
    //     @typeFormal let T = ...;
    //     let f = fn { ... }
    typeFormalScopingBlocks.forEach { tree ->
        val edge = tree.incoming!! // Checked above
        val assignment = edge.source!! // Checked above
        val assignmentEdge = assignment.incoming!! // Safe b/c root is a block
        val assignmentParent = assignmentEdge.source!!
        if (assignmentParent !is BlockTree || assignmentParent.flow !is LinearFlow) {
            // Make sure the assignment is in statement position; is in a block.
            convertToErrorNode(
                assignmentParent.incoming!!,
                LogEntry(
                    level = Log.Error,
                    template = MessageTemplate.NotInStatementPosition,
                    pos = assignment.pos,
                    values = emptyList(),
                ),
            )
            return@forEach
        }

        val nTreeChildren = tree.size
        val resultExpr = tree.child(nTreeChildren - 1)
        val toPullIntoBlock = tree.children.subList(0, nTreeChildren - 1).toList()

        val insertionPosition = assignmentEdge.edgeIndex
        assignmentParent.replace(insertionPosition until insertionPosition) {
            toPullIntoBlock.forEach {
                Replant(freeTree(it))
            }
        }
        edge.replace(freeTree(resultExpr))
    }
}

private val Tree.isTypeFormalDecl: Boolean
    get() = this is DeclTree && parts?.metadataSymbolMap?.contains(typeFormalSymbol) == true
