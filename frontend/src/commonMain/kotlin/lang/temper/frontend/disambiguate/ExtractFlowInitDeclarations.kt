package lang.temper.frontend.disambiguate

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.common.Log
import lang.temper.common.subListToEnd
import lang.temper.interp.convertToErrorNode
import lang.temper.interp.emptyValue
import lang.temper.interp.isOfCall
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.spanningPosition
import lang.temper.name.TemperName
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.atBuiltinName
import lang.temper.value.flowInitSymbol
import lang.temper.value.freeTree
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.nameContained
import lang.temper.value.symbolContained
import lang.temper.value.varSymbol

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
    TreeVisit.startingAt(root) //
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

                    injectInitVarsForCapture(toExtract, tree)

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

/** Inject local declarations for better scope of init vars where capture might happen. */
private fun injectInitVarsForCapture(init: Tree, call: CallTree) {
    // Find vars that we might need to inject in lower scopes.
    val vars = findVars(init)
    if (vars.isEmpty()) {
        return
    }
    // Find lower scopes for injecting into.
    // We never need to inject into the extracted flow init, because we know that runs only once.
    // Anything else, we really only care if it repeats, but we don't know what macros might do.
    // TODO Instead:
    // TODO - Explicitly find captured vars inside closures, and return a list of such RightNameLeaf nodes
    // TODO - Generate local vars instead of lets
    // TODO - Literally just clone the whole init tree with new Temporary names?
    // TODO - Change all assignments to assign new also, such as from `i = 3` to `i = (i#1 = 3)`
    // TODO - Change all captured RightNameLeaf refs to the new temporary
    for (kid in call.children) {
        val captures = findVarCaptures(vars, kid)
        if (captures.isNotEmpty()) {
            val loc = kid.pos.loc.diagnostic
            if ("plicits" !in loc && "std" !in loc) {
                captures.size
            }
        }
//        when (kid) {
//            is BlockTree -> {
//                // Presume all blocks could possibly capture the var.
//                // TODO Is searching for capture cheaper than always injecting?
//                injectInitVarsForCapture(vars, kid)
//            }
//            is FunTree -> when {
//                // Presume all functions could possibly capture the var.
//                // TODO Is searching for capture cheaper than always injecting?
//                kid.parts?.formals?.isEmpty() == true -> {
//                    when (val body = kid.parts?.body) {
//                        is BlockTree -> injectInitVarsForCapture(vars, body)
//                        null -> {}
//                        else -> wrapInjectVarsForCapture(vars, body)
//                    }
//                }
//                else -> {
//                    // The formals could have defaults with captures, so wrap the whole thing.
//                    wrapInjectVarsForCapture(vars, kid)
//                }
//            }
//            else -> {
//                // For simpler nodes, presume they're small, and only provide new local when nested functions exist.
//                // Even here, we aren't bothering to check for actual capture.
//                // TODO Again, is this cheaper than doing a formal search for captur?
//                var anyFns = false
//                TreeVisit.startingAt(kid).forEach node@{ node ->
//                    when (node) {
//                        is FunTree -> {
//                            // TODO Use fold instead of var?
//                            anyFns = true
//                            VisitCue.AllDone
//                        }
//                        else -> VisitCue.Continue
//                    }
//                }.visitPreOrder()
//                if (anyFns) {
//                    wrapInjectVarsForCapture(vars, kid)
//                }
//            }
//        }
    }
}

fun findVars(tree: Tree): List<LeftNameLeaf> = run {
    // Check @var call.
    // TODO Deeper decorations with any as `var`.
    val call = tree as? CallTree ?: return listOf()
    call.childOrNull(0)?.nameContained?.builtinKey == atBuiltinName.builtinKey || return listOf()
    call.childOrNull(1)?.nameContained?.builtinKey == varSymbol.text || return listOf()
    // Get decl names.
    val decl = call.childOrNull(2) ?: return listOf()
    buildList {
        // To handle commaFn or otherwise, look for any decls that aren't under new scope.
        TreeVisit.startingAt(decl).forEach subs@{ sub ->
            when (sub) {
                is DeclTree -> {
                    (sub.childOrNull(0) as? LeftNameLeaf)?.also { add(it) }
                    return@subs VisitCue.SkipOne
                }
                is BlockTree, is FunTree -> return@subs VisitCue.SkipOne
                else -> {}
            }
            VisitCue.Continue
        }.visitPreOrder()
    }
}

/**
 * Find references to the given vars underneath nested functions.
 * Can have false positives due to FunTree nodes that don't actually end up functions,
 * such as in `if` blocks.
 */
fun findVarCaptures(vars: List<LeftNameLeaf>, tree: Tree): List<RightNameLeaf> = buildList {
    val varNames = vars.mapTo(mutableSetOf()) { it.content }
    // TODO Find assignments to varNames in this same pass?
    fun dig(sub: Tree, subVarNames: MutableSet<TemperName>, enclosed: Boolean, top: Boolean) {
        var subEnclosed = enclosed
        var subSubVarNames = subVarNames
        when (sub) {
            is BlockTree -> if (!top) {
                subSubVarNames = subVarNames.toMutableSet()
            }
            is FunTree -> if (!top) {
                subEnclosed = true
                subSubVarNames = subVarNames.toMutableSet()
            }
            is LeftNameLeaf -> {
                subVarNames.add(sub.content)
            }
            is RightNameLeaf -> {
                if (enclosed && sub.content in varNames && sub.content !in subVarNames) {
                    add(sub)
                }
            }
            else -> {}
        }
        for (kid in sub.children) {
            dig(kid, subSubVarNames, enclosed = subEnclosed, top = false)
        }
    }
    // Special handling of top-level kids so we don't treat main blocks as closures.
    for (kid in tree.children) {
        dig(kid, mutableSetOf(), enclosed = false, top = true)
    }
}

/** Inject rescoped decls at top of existing block. */
private fun injectInitVarsForCapture(vars: List<LeftNameLeaf>, tree: BlockTree) {
    tree.insert(0) {
        injectInitVarsForCapture(vars)
    }
    if ("plicits" !in tree.pos.loc.diagnostic) {
        tree.pos
    }
}

/** Wrap a tree with a block with injected rescoped decls at top. */
private fun wrapInjectVarsForCapture(vars: List<LeftNameLeaf>, tree: Tree) {
    tree.incoming!!.replace {
        Block {
            injectInitVarsForCapture(vars)
            Replant(tree)
        }
    }
    if ("plicits" !in tree.pos.loc.diagnostic) {
        tree.pos
    }
}

private fun Planting.injectInitVarsForCapture(vars: List<LeftNameLeaf>) {
    for (v in vars) {
        Decl(v.pos) {
            Replant(v.copy())
            V(initSymbol)
            Rn(v.content)
        }
    }
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
