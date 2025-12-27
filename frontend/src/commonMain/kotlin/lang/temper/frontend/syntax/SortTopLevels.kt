package lang.temper.frontend.syntax

import lang.temper.common.buildSetMultimap
import lang.temper.common.partiallyOrder
import lang.temper.common.putMultiList
import lang.temper.common.putMultiSet
import lang.temper.name.TemperName
import lang.temper.type.NominalType
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.importedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.typeDefinedSymbol

/**
 * Sort top-level kids of root based on the dependency graph between them.
 *
 * Expected that `flattenMultiInit` and `flattenMultiDeclarations` happen
 * before here in `DisAmbiguateStage`. Even in [SyntaxMacroStage], we call
 * [simplifyMultiAssignments] before this, though perhaps some of that
 * relates to the `DisAmbiguateStage` work as well.
 */
internal fun sortTopLevels(root: BlockTree) {
    // First find the names to look for.
    val topNames = findTopNames(root)
    // Then divide nodes into declarations, assignments, and other. Decls go before assigns.
    // In good code, there should be only one decl for any name, but be flexible for broken things.
    val decls = mutableMapOf<TemperName, MutableList<TEdge>>()
    val assigns = mutableMapOf<TemperName, MutableList<TEdge>>()
    val needs = mutableMapOf<TEdge, Set<TemperName>>()
    trackTops(topNames = topNames, root = root, decls = decls, assigns = assigns, needs = needs)
    // TODO If chunked, make a chunkNeeds map instead of an edgeNeeds map.
    val edgeNeeds = buildEdgeNeeds(decls = decls, assigns = assigns, needs = needs)
    // Sort and replace old with the new order.
    val resultEdges = partiallyOrder(edgeNeeds)
    root.removeChildren(0 until root.size)
    // And put in top-level imports first, since they're sometimes more finicky.
    // We especially don't handle them well if they get put into a module init block,
    // even if their only dependents come later.
    val (imports, others) = resultEdges.partition { isImport(it) }
    for (edge in imports) {
        root.add(edge.target)
    }
    for (edge in others) {
        root.add(edge.target)
    }
}

private fun buildEdgeNeeds(
    decls: MutableMap<TemperName, MutableList<TEdge>>,
    assigns: MutableMap<TemperName, MutableList<TEdge>>,
    needs: MutableMap<TEdge, Set<TemperName>>,
): Map<TEdge, Set<TEdge>> {
    val edgeNeeds = buildSetMultimap {
        // First loop through all the edges in order, adding their needs.
        // And put needed decls and assigns before anything else.
        // Ideally, where irrelevant, we retain original order. If our `partiallyOrder` does that
        // (does it?), we don't need to do extra work here on it.
        for ((edge, names) in needs) {
            for (name in names) {
                for (declEdge in decls[name] ?: emptyList()) {
                    putMultiSet(edge, declEdge)
                }
                for (declEdge in assigns[name] ?: emptyList()) {
                    putMultiSet(edge, declEdge)
                }
            }
            // Maybe we didn't add this edge in any loops above. If not, add it as an independent.
            // TODO Ensure any non-decl/assign, independent `do` blocks sink to the end?
            // TODO One current thought is that you're allowed to have exactly one, and it runs last.
            // TODO Could have logging in some assigned `do` block and also in the standalone.
            if (edge !in this) {
                put(edge, mutableSetOf())
            }
        }
        // Then also add needs for assignment on their declarations.
        for ((name, edges) in assigns) {
            for (edge in edges) {
                for (declEdge in decls[name] ?: emptyList()) {
                    putMultiSet(edge, declEdge)
                }
            }
        }
    }
    return edgeNeeds
}

private fun findTopNames(root: BlockTree): Set<TemperName> {
    val tops = buildSet {
        tops@ for (tree in root.children) {
            val declTree = (tree as? DeclTree) ?: continue@tops
            val left = declTree.childOrNull(0) ?: continue@tops
            if (left is LeftNameLeaf) {
                add(left.content)
            } // else if (isCommaCall(left)) ... TODO Should comma call decls exist by this point?
        }
    }
    return tops
}

fun isImport(edge: TEdge): Boolean {
    val tree = edge.target
    return tree is DeclTree && tree.parts?.metadataSymbolMap?.let { importedSymbol in it } == true
}

/**
 * @param needs filled with every top-level edge in order with the names it uses
 */
private fun trackTops(
    topNames: Set<TemperName>,
    root: BlockTree,
    decls: MutableMap<TemperName, MutableList<TEdge>>,
    assigns: MutableMap<TemperName, MutableList<TEdge>>,
    needs: MutableMap<TEdge, Set<TemperName>>,
) {
    fun findTopNames(tree: Tree, wantedKind: NameKind) = buildSet {
        fun walk(sub: Tree) {
            when (sub) {
                is FunTree -> sub.parts?.let parts@{ parts ->
                    if (wantedKind == NameKind.Left) {
                        // Class definitions are by `class` call with a `\typeDefined` function inside,
                        // rather than by assignment to a left name.
                        val typeEdge = parts.metadataSymbolMap[typeDefinedSymbol] ?: return@parts
                        val type = (typeEdge.target.staticTypeContained as? NominalType) ?: return@parts
                        if (type.definition.name in topNames) {
                            add(type.definition.name)
                        }
                    }
                }
                is NameLeaf -> if (sub.content in topNames && sub.kind == wantedKind) {
                    add(sub.content)
                }
                else -> {}
            }
            if (sub is NameLeaf && sub.content in topNames && sub.kind == wantedKind) {
                add(sub.content)
            }
            for (kid in sub.children) {
                walk(kid)
            }
        }
        walk(tree)
    }
    fun findNeeds(tree: Tree): Set<TemperName> = findTopNames(tree, NameKind.Right)
    tops@ for (edge in root.edges) {
        val tree = edge.target
        // First gather needs for each child.
        needs[edge] = findNeeds(tree)
        // Then see what we define, if anything.
        fun trackAssigns(tree: Tree) {
            for (name in findTopNames(tree, NameKind.Left)) {
                assigns.putMultiList(name, edge)
            }
        }
        if (tree is DeclTree) {
            // Track the declared name and separately any assignments within any init expression.
            tree.parts?.name?.let { decls.putMultiList(it.content, edge) }
            tree.parts?.metadataSymbolMap?.get(initSymbol)?.let { trackAssigns(it.target) }
        } else {
            trackAssigns(tree)
        }
    }
}

/** For ease in discussing left vs right names without LeftName or RightName instances. */
private enum class NameKind {
    Left,
    Right,
}

private val NameLeaf.kind get() = when (this) {
    is LeftNameLeaf -> NameKind.Left
    is RightNameLeaf -> NameKind.Right
}
