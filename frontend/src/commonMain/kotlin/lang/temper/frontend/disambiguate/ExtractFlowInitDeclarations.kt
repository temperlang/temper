package lang.temper.frontend.disambiguate

import lang.temper.ast.TreeVisit
import lang.temper.common.Log
import lang.temper.common.subListToEnd
import lang.temper.interp.convertToErrorNode
import lang.temper.interp.emptyValue
import lang.temper.interp.isOfCall
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.spanningPosition
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.TEdge
import lang.temper.value.flowInitSymbol
import lang.temper.value.freeTree
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.symbolContained

/**
 * Extract declarations from flow-control like calls like
 *
 *     for (var x, y = ...; test; increment) { body }
 *
 * This is not tied to `for` in particular which is what allows us to do this before name resolution
 * so this also works for
 *
 *     if (let x; x != null;) {
 *         // code that uses x
 *     }
 *
 * This rewrites
 *
 *     callee(/* declarations */; rest;)
 *
 * to
 *
 *     {
 *         /* declarations */
 *         callee(void; rest;)
 *     }
 *
 * The block boundary that's added means that the declarations do not bleed out into larger scopes.
 *
 * ----
 *
 * Additionally, it restructures for...of loops by moving the declaration to the trailing function block.
 *
 *     for (let x of xs) { ... }
 *
 * The above becomes the below where `let x` is not in the scope for `xs` and is in scope for the body.
 *
 *     for (void of xs) { x => ... }
 */
internal fun extractFlowInitDeclarations(root: BlockTree, logSink: LogSink) {
    TreeVisit.startingAt(root)
        .forEachContinuing { tree ->
            if (tree !is CallTree) { return@forEachContinuing }
            val flowInitEdge = flowInitParameterOf(tree)
            if (flowInitEdge != null) {
                val flowInitDecorated = lookThroughDecorations(flowInitEdge).target
                val needToExtractInitializer = when {
                    flowInitDecorated is DeclTree -> true
                    flowInitDecorated.isCommaCall ->
                        flowInitDecorated.children.subListToEnd(1).all { it is DeclTree }
                    else -> false
                }
                if (needToExtractInitializer) {
                    val toExtract = flowInitEdge.target
                    flowInitEdge.replace { pos ->
                        V(pos, emptyValue)
                    }
                    val incoming = tree.incoming!! // safe since root is not a call
                    val parent = incoming.source
                    val labelParts = if (
                        parent is BlockTree &&
                        parent.flow is LinearFlow &&
                        parent.parts.label != null &&
                        parent.size == LABELED_CALL_SIZE
                    ) {
                        // Keep the label associated with the call
                        val labelParts = parent.children.subList(0, LABELED_CALL_SIZE - 1).toList()
                        parent.removeChildren(0 until LABELED_CALL_SIZE - 1)
                        labelParts
                    } else {
                        null
                    }

                    // Just replace the call
                    incoming.replace { pos ->
                        Block(pos.leftEdge) {
                            Replant(toExtract)
                            if (labelParts != null) {
                                Block(pos.leftEdge) {
                                    labelParts.forEach { Replant(it) }
                                    Replant(freeTree(tree))
                                }
                            } else {
                                Replant(freeTree(tree))
                            }
                        }
                    }
                }
            } else {
                // for...of
                val firstArg = tree.childOrNull(1)
                val lastArg = tree.childOrNull(tree.size - 1)
                if (firstArg != null && firstArg.isOfCall && lastArg is FunTree) {
                    val declEdge = firstArg.edge(1)
                    val decl = declEdge.target
                    val declParts = (decl as? DeclTree)?.parts
                    if (declParts != null) {
                        run {
                            // If the declaration has an initializer, that would be odd.
                            // Function parameters have default expressions, not initializers.
                            // And `for (let x = 1 of otherInts) { ... }` doesn't make sense.
                            val parts = decl.partsIgnoringName
                            val metadata = parts?.metadataSymbolMultimap
                            val initEdges = metadata?.get(initSymbol)
                            if (!initEdges.isNullOrEmpty()) {
                                val problem = LogEntry(
                                    Log.Error,
                                    MessageTemplate.OfDeclarationInitializerDisallowed,
                                    initEdges.map { it.target }.spanningPosition(decl.pos),
                                    listOf(),
                                )
                                initEdges.forEach { e ->
                                    convertToErrorNode(e, problem)
                                }
                                problem.logTo(logSink)
                            }
                        }

                        declEdge.replace {
                            V(emptyValue)
                        }
                        lastArg.insert(0) {
                            Replant(decl)
                        }
                    } else {
                        val problem = LogEntry(
                            Log.Error,
                            MessageTemplate.ExpectedDeclarationForOf,
                            decl.pos,
                            listOf(),
                        )
                        convertToErrorNode(declEdge, problem)
                        problem.logTo(logSink)
                    }
                }
            }
        }
        .visitPostOrder()
}

// Walk over named parameter style.
// Since syntax like
//     foo bar(...)
// desugars to
//     foo(\word, bar, ...)
// we walk over arguments looking for named parameters to find one named
// \__flowInit and then extract that if it is a declaration, or a comma expression
// of declarations.
// We look through decorations to find declarations.
private fun flowInitParameterOf(call: CallTree): TEdge? {
    var parameterIndex = 1 // Skip over callee
    val n = call.size
    while (parameterIndex + 1 < n) {
        val paramTree = call.child(parameterIndex)
        val parameterName = paramTree.symbolContained
        parameterIndex += when (parameterName) {
            flowInitSymbol -> return call.edge(parameterIndex + 1)
            null -> 1 // paramTree is a value
            else -> 2 // paramTree is a key, and the next is a value to skip
        }
    }
    return null
}

const val LABELED_CALL_SIZE = 3 // \label, labelSymbol, call
