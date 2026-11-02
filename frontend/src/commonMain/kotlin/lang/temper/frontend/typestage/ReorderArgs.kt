package lang.temper.frontend.typestage

import lang.temper.common.Either
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.interp.EmptyEnvironment
import lang.temper.interp.LazyActualsList
import lang.temper.type2.Signature2
import lang.temper.value.CallTree
import lang.temper.value.IdentityActualOrder
import lang.temper.value.TNull
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.actualsListFromTree
import lang.temper.value.applicationOrderForActuals
import lang.temper.value.firstArgumentIndex
import lang.temper.value.freeTree

internal class ReorderArgs(
    private val root: Tree,
    private val variantForCall: (CallTree) -> Signature2? = { null },
) {
    fun process() {
        walk(root)
    }

    private fun walk(tree: Tree) {
        when (tree) {
            is CallTree -> reorderCallTreeArgs(tree)
            else -> {}
        }
        for (child in tree.children) {
            walk(child)
        }
    }

    fun reorderCallTreeArgs(tree: CallTree) {
        // First see if anything is out of order (which could include outright errors) before doing expensive work.
        val variant = variantForCall(tree)
            ?: tree.typeInferences?.variant
            ?: return
        doReorderedCall(tree, variant)
    }

    internal fun doReorderedCall(tree: CallTree, sig: Signature2) {
        // Adapt static type information to DynamicMessage to share reordering logic.
        val args = buildPositionalArgs(tree, sig) ?: return
        tree.replace(tree.indices) {
            for (preArgIndex in 0 until tree.firstArgumentIndex) {
                Replant(freeTree(tree.child(preArgIndex)))
            }
            for (arg in args) {
                Replant(freeTree(arg))
            }
        }
    }

    fun buildPositionalArgs(callTree: CallTree, sig: Signature2): List<Tree>? {
        val order = (
            applicationOrderForActuals(actualsListFromTree(callTree), sig)
                as? Either.Left
            )?.item
        if (order is IdentityActualOrder?) { return null } // No reorder needed
        val actuals = LazyActualsList(
            callTree.children.subListToEnd(callTree.firstArgumentIndex),
            null,
            EmptyEnvironment,
            InterpMode.Partial,
        )
        val orderTrimmed = order.subList(
            0,
            order.indexOfLast { it != null } + 1,
        )
        return buildList {
            for (argIndex in orderTrimmed) {
                if (argIndex == null) {
                    add(ValueLeaf(callTree.document, callTree.pos, TNull.value))
                } else {
                    add(actuals.valueTree(argIndex))
                }
            }
        }
    }
}
