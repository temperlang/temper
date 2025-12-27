package lang.temper.frontend

import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.functionContained

/**
 * Convert
 *
 *     (Decl (RightName ...) ...)
 *
 * to
 *
 *     (Decl (LeftName ...) ...)
 *
 * and similarly for calls to functions known to require a left-name as their leftmost argument.
 */
internal fun flipDeclaredNames(tree: Tree) {
    for (i in tree.indices) {
        flipDeclaredNames(
            tree.edge(i),
            when {
                i == 0 -> tree is DeclTree
                i == 1 && tree is CallTree -> {
                    tree.child(0).functionContained?.assignsArgumentOne == true
                }
                else -> false
            },
        )
    }
}

private fun flipDeclaredNames(edge: TEdge, inLeftPosition: Boolean) {
    val target = edge.target
    if (inLeftPosition && target is RightNameLeaf) {
        edge.replace(
            LeftNameLeaf(target.document, target.pos, target.content),
        )
    }
    flipDeclaredNames(target)
}
