package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.ForwardOrBack
import lang.temper.common.console
import lang.temper.type.DotHelper
import lang.temper.type.InternalGet
import lang.temper.type.InternalSet
import lang.temper.type.PropertyShape
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.FunTree
import lang.temper.value.MaximalPathIndex
import lang.temper.value.constructorSymbol
import lang.temper.value.debug
import lang.temper.value.forwardMaximalPaths
import lang.temper.value.functionContained
import lang.temper.value.nameContained
import lang.temper.value.orderedPathIndices
import lang.temper.value.symbolContained
import lang.temper.value.wordSymbol

/**
 * For use during define stage.
 *
 * Separates constructor init section from `this` usage section.
 */
internal class ConstructorTransformer {
    companion object {
        fun transform(body: BlockTree, properties: List<PropertyShape>) {
            if ("plicits" !in body.pos.loc.diagnostic) {
                body.pos
            }
            splitChildren(body)
        }

        fun transformConstructors(root: BlockTree) {
            TreeVisit.startingAt(root).forEach { tree ->
                when (tree) {
                    is FunTree -> {
                        val word = tree.parts?.metadataSymbolMap?.get(wordSymbol)?.symbolContained
                        if (word == constructorSymbol) {
                            transformConstructor(tree)
                        }
                        // Constructors don't nest under other functions, so we're done here.
                        VisitCue.SkipOne
                    }
                    else -> VisitCue.Continue
                }
            }.visitPreOrder()
        }
    }
}

private fun transformConstructor(tree: FunTree) {
    val body = tree.parts!!.body as BlockTree
    val paths = forwardMaximalPaths(body, assumeFailureCanHappen = true)
    paths.entryPathIndex
    paths.maximalPaths
    orderedPathIndices(paths, ForwardOrBack.Back)
    paths.debug(console, body)
    if ("plicits" !in tree.pos.loc.diagnostic) {
        if (paths.maximalPaths.size > 5) {
            tree.pos
        }
    }
    val visited = mutableSetOf<MaximalPathIndex>()
    // TODO The idea is to find the latest common preceding element for first non-init uses of `this` across all
    // TODO branches.
    var pathIndex = paths.entryPathIndex
    visited.add(pathIndex)
    while (visited.size < paths.maximalPaths.size) {
        val path = paths[pathIndex]
        if (true) {
            break
        }
    }
}

private fun splitChildren(body: BlockTree) {
    for (kidIndex in 0..<body.size) {
        val kid = body.child(kidIndex)
        var foundEnd = false
        // Look for any reference to `this` that's not internal get or set.
        TreeVisit.startingAt(kid).forEach { tree ->
            when (tree) {
                is CallTree -> when (tree.childOrNull(0)?.functionContained) {
                    BuiltinFuns.thisPlaceholder -> {
                        when (val parent = tree.incoming?.source) {
                            is CallTree -> when (val fn = parent.child(0).functionContained) {
                                is DotHelper -> when (fn.memberAccessor) {
                                    InternalGet, InternalSet -> when {
                                        tree.incoming?.edgeIndex == 2 -> VisitCue.SkipOne
                                        else -> null
                                    }
                                    else -> null
                                }
                                else -> null
                            }
                            else -> null
                        } ?: run {
                            foundEnd = true
                            VisitCue.AllDone
                        }
                    }
                    else -> when (tree.childOrNull(0)?.nameContained?.displayName) {
                        "class", "interface" -> // TODO later when not names?
                            VisitCue.Continue
                        else -> VisitCue.Continue
                    }
                }
                else -> VisitCue.Continue
            }
        }.visitPreOrder()
        if (!foundEnd) {
            // TODO Look for any return or fail?
        }
        if ("plicits" !in body.pos.loc.diagnostic && foundEnd) {
            body.pos
        }
    }
}
