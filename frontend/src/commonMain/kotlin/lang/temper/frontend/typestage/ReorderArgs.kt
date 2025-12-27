package lang.temper.frontend.typestage

import lang.temper.common.Either
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.interp.EmptyEnvironment
import lang.temper.interp.LazyActualsList
import lang.temper.type.FunctionType
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
    private val variantForCall: (CallTree) -> FunctionType? = { null },
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

    private fun reorderCallTreeArgs(tree: CallTree) {
        // First see if anything is out of order (which could include outright errors) before doing expensive work.
        val variant = variantForCall(tree)
            ?: tree.typeInferences?.variant as? FunctionType
            ?: return
        buildReorderedCall(tree, variant)
    }

    private fun buildReorderedCall(tree: CallTree, functionType: FunctionType) {
        // Adapt static type information to DynamicMessage to share reordering logic.
        val args = buildPositionalArgs(tree, functionType) ?: return
        tree.replace(tree.indices) {
            for (preArgIndex in 0 until tree.firstArgumentIndex) {
                Replant(freeTree(tree.child(preArgIndex)))
            }
            for (arg in args) {
                Replant(freeTree(arg))
            }
        }
    }

    fun buildPositionalArgs(callTree: CallTree, functionType: FunctionType): List<Tree>? {
        val order = (
            applicationOrderForActuals(actualsListFromTree(callTree), functionType)
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
