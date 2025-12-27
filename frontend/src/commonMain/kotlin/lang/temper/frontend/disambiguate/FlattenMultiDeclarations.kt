package lang.temper.frontend.disambiguate

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.subListToEnd
import lang.temper.name.Temporary
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.lookThroughDecorations
import lang.temper.value.unpackUnappliedDecoration
import lang.temper.value.vInitSymbol
import lang.temper.value.void

/**
 * As part of parsing multiple-declarations as one statement like
 *
 *     let x, y;
 *
 * we produce a tree with a comma operation to collect the individual declarators
 *
 *     (Decl
 *         (Call
 *            nym`,`
 *            (Name x)
 *            (Name y)))
 *
 * These need to be split into multiple statements early, but this can be tricky when there are
 * decorations.
 *
 *     @foo @bar let x, y;
 *
 * This micro-pass looks in blocks for such constructs, and inlines them into the containing block.
 * It judiciously copied the decorators.
 *
 * This pass has to happen after [extractFlowInitDeclarations] so that it can reach
 * multi-declarations in
 *
 *     for (var i = 0, n = limit; i < n; ++i) { body }
 */
internal fun flattenMultiDeclarations(root: BlockTree) {
    val document = root.document
    val nameMaker = document.nameMaker

    TreeVisit.startingAt(root)
        .forEachContinuing { tree ->
            if (tree is BlockTree && tree.flow is LinearFlow) {
                for (edge in tree.edges.toList()) { // loop may mutate edge list
                    val decoratedEdge = lookThroughDecorations(edge)
                    val decorated = decoratedEdge.target
                    if (
                        decorated.isCommaCall &&
                        (1 until decorated.size).all { i ->
                            decorated.child(i) is DeclTree
                        }
                    ) {
                        val declarations = decorated.children.subListToEnd(1)
                        if (declarations.size == 1) {
                            decoratedEdge.replace {
                                Replant(freeTree(declarations[0]))
                            }
                            continue
                        }

                        // We need to find the element to replace, and we need to extract any
                        // complex decorator parts so that in
                        //     @decorator(f()) let x, y;
                        // we only evaluate `f()` once.
                        val extracted = mutableListOf<DeclTree>() // Decls of extracted temporaries
                        val pathToDecorated = run {
                            val indices = mutableListOf<Int>()
                            var e = edge
                            while (e != decoratedEdge) {
                                val unapplied = unpackUnappliedDecoration(e)!!
                                indices.add(unapplied.decoratedIndex)

                                for (parameterEdge in unapplied.parameterEdges) {
                                    when (val parameter = parameterEdge.target) {
                                        is ValueLeaf, is NameLeaf, is StayLeaf -> Unit
                                        is BlockTree, is CallTree, is DeclTree,
                                        is EscTree, is FunTree,
                                        -> {
                                            val temporary = nameMaker.unusedTemporaryName(
                                                Temporary.defaultNameHint,
                                            )
                                            parameterEdge.replace { pos -> Rn(pos, temporary) }
                                            extracted.add(
                                                document.treeFarm.grow(parameter.pos.leftEdge) {
                                                    Decl(temporary) {
                                                        V(vInitSymbol)
                                                        Replant(parameter)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }

                                e = e.target.edge(unapplied.decoratedIndex)
                            }
                            decoratedEdge.replace { pos -> V(pos, void) }
                            indices.toList()
                        }

                        val replacements = mutableListOf<Tree>()
                        if (pathToDecorated.isEmpty()) { // no decorations
                            declarations.mapTo(replacements) { freeTree(it) }
                        } else {
                            val lastDeclIndex = declarations.lastIndex
                            val template = edge.target
                            for (i in 0..lastDeclIndex) {
                                val declaration = declarations[i]
                                val replacement = if (i == lastDeclIndex) {
                                    freeTree(template)
                                } else {
                                    template.copy()
                                }
                                replacements.add(replacement)

                                // splice the declaration into replacement
                                var t = replacement
                                for (index in pathToDecorated) {
                                    t = t.child(index)
                                }
                                t.incoming!!.replace { Replant(freeTree(declaration)) }
                            }
                        }

                        val edgeIndex = edge.edgeIndex
                        tree.replace(edgeIndex..edgeIndex) {
                            extracted.forEach { Replant(it) }
                            replacements.forEach { Replant(it) }
                        }
                    }
                }
            }
        }
        .visitPostOrder()
}

internal val Tree.isCommaCall: Boolean
    get() = this is CallTree && childOrNull(0)?.functionContained == BuiltinFuns.commaFn
