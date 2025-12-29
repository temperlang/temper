package lang.temper.frontend.syntax

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.defensiveListCopy
import lang.temper.lexer.Operator
import lang.temper.lexer.TokenType
import lang.temper.log.spanningPosition
import lang.temper.name.BuiltinName
import lang.temper.name.Temporary
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.EscTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.initSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vTypeSymbol
import kotlin.math.max

/**
 * Converts
 *
 *     [a[b++], let x: T, c] = ...
 *
 * into
 *
 *     {
 *       // Pull these out into temporaries to ensure order of operations.
 *       let t#0 = a;
 *       let t#1 = b++;
 *       // Pull declaration out.
 *       let x: T;
 *       // Replace the declaration with a reference to its declarant
 *       (t#0[t#1], x, c) = ...
 *     }
 *
 * Similarly converts
 *
 *     let a, [x, y] = f(), b = ...
 *
 * into
 *
 *     {
 *       let a, x, y, b;
 *       [x, y] = f();
 *       b = ();
 *     }
 *
 * This has to happen once we have trees with resolved names so that moving declarations around
 * doesn't break masking relationships.
 */
fun simplifyMultiAssignments(root: BlockTree) {
    SimplifyMultiAssignments(root).simplify()
}

private class SimplifyMultiAssignments(
    val root: BlockTree,
) {
    private val nameMaker = root.document.nameMaker

    fun simplify() {
        walk(root)
    }

    private fun walk(t: Tree) {
        // Post-order so that any declarations are simple.
        for (i in t.indices) {
            walk(t.child(i))
        }

        if (t is CallTree && isAssignment(t) && isCommaCall(t.childOrNull(1))) {
            simplifyMultiAssignment(t)
        }
        if (t is DeclTree && isCommaCall(t.childOrNull(0))) {
            simplifyMultiDeclaration(t)
        }
    }

    private fun simplifyMultiAssignment(t: CallTree) {
        val leftHandSide = t.child(1)
        require(isCommaCall(leftHandSide))
        val e = t.incoming!! // Root !is CallTree, so t is strictly under it.

        var maxDeclIndex = Int.MIN_VALUE
        for (i in leftHandSide.indices) {
            val c = leftHandSide.child(i)
            if (c is DeclTree) {
                if (c.childOrNull(0) is NameLeaf) {
                    maxDeclIndex = max(i, maxDeclIndex)
                } else {
                    // We don't know what to do at this stage.
                    // Do not interfere with any pattern-decomposing assignments until the
                    // appropriate stage has desugared them.
                    return
                }
            }
        }
        if (maxDeclIndex < 0) {
            // Nothing to move.
            return
        }

        val hoisted = mutableListOf<Tree>()

        for (i in 0..maxDeclIndex) {
            val leftHandSideEdge = leftHandSide.edge(i)
            val target = leftHandSideEdge.target
            if (target is DeclTree) {
                // Extract and replace with a reference to the declared name.
                val name = (target.child(0) as NameLeaf).copyLeft()
                leftHandSideEdge.replace(name)
                hoisted.add(target)
            } else {
                hoist(leftHandSideEdge, hoisted, true)
            }
        }

        freeTarget(e)
        val replacement = BlockTree(t.document, t.pos, hoisted, LinearFlow)
        e.replace(replacement)
        // Now add the assignment back.
        replacement.replace(replacement.size until replacement.size) {
            Replant(t)
        }
    }

    private fun simplifyMultiDeclaration(t: DeclTree) {
        //     let [a, b, c]: T = initializer
        // becomes
        //     let t#0 = T; // Avoid multiply evaluating (T)
        //     let a: t#0;
        //     let b: t#0;
        //     let c: t#0;
        //     [a, b, c] = initializer
        val e = t.incoming!! // Root !is DeclTree, so t is strictly under it.
        val declared = t.child(0) as CallTree
        require(isCommaCall(declared))

        val (type, initializer, declarationEnd) = declParts(t, 0)
        if (declarationEnd != t.size) {
            // Malformed.  Don't muck with it.  Maybe a later stage can make sense of it.
            return
        }

        // Edges of declared that we need in the comma operator that we're going to need in
        // the left-hand side of the eventual multi-assignment.
        val keepInCommaExpr = mutableSetOf(
            declared.edge(0), // The callee
        )
        val splitDeclarations = mutableListOf<List<TEdge>>()

        // Walk the embedded declarations generating replacements.
        var i = 1 // Skip `,`
        val n = declared.size
        while (i < n) {
            val (_, _, end) = declParts(declared, i)
            val declChildren = declared.edges.subList(i, end)
            splitDeclarations.add(declChildren)
            if (initializer != null) {
                keepInCommaExpr.add(declared.edge(i))
            }
            i = end
        }
        if (i != n) {
            // Abort on a weird tree structure.
            // We haven't freed any nodes above, so have not yet mutated the AST.
            return
        }

        val doc = t.document
        // We're going to produce a block.  These will be its children.
        val replacement = mutableListOf<Tree>()

        // For each declaration from the comma expression, create one simpler declaration, but
        // hoist out any possibly complex expression to preserve order of operations.
        val declarationsWithOrderPreserved = splitDeclarations.map { declarationEdgeList ->
            val declChildren = declarationEdgeList.mapIndexed { edgeIndex, edge ->
                // Let the left-hand side of the
                if (edge in keepInCommaExpr) {
                    // We need to keep the comma expression around so that we can assign to it.
                    edge.target.copy()
                } else {
                    hoist(edge, replacement, edgeIndex == 0)
                    freeTarget(edge)
                }
            }
            DeclTree(
                doc,
                declChildren.spanningPosition(declared.pos),
                declChildren.mapIndexed { i, it ->
                    if (i == 0 && it is RightNameLeaf) { // Declared name is a left name
                        it.copyLeft()
                    } else {
                        it.copy()
                    }
                },
            )
        }

        // We need a way to refer to the shared type, if any.
        val hoistedTypeTemporary = if (type != null) {
            // We don't just hoist it since it'd be evaluated multiply.
            val typeExpr = type.target
            val typeTemporary = nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
            val pos = typeExpr.pos
            replacement.add(
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, typeTemporary),
                        ValueLeaf(doc, pos, vInitSymbol),
                        freeTree(typeExpr),
                    ),
                ),
            )
            typeTemporary
        } else {
            null
        }

        // Now that we've hoisted out the parts to ensure order of evaluation, and we've got a
        // type temporary, we can actually create our declarations.
        for (simpleDeclaration in declarationsWithOrderPreserved) {
            if (hoistedTypeTemporary != null) {
                addTypeToDeclaration(
                    simpleDeclaration,
                    RightNameLeaf(doc, type!!.target.pos, hoistedTypeTemporary),
                )
            }
            replacement.add(simpleDeclaration)
        }

        if (initializer != null) {
            // Remove the bits from the comma
            for (declaredEdge in defensiveListCopy(declared.edges)) {
                if (declaredEdge !in keepInCommaExpr) {
                    declaredEdge.replace(null)
                }
            }
            replacement.add(
                doc.treeFarm.grow {
                    Call(t.pos, BuiltinFuns.vSetLocalFn) {
                        Replant(freeTree(declared))
                        Replant(freeTarget(initializer))
                    }
                },
            )
        }

        e.replace {
            Block(t.pos) {
                replacement.forEach { Replant(it) }
            }
        }
    }

    private fun hoist(
        e: TEdge,
        out: MutableList<Tree>,
        isLeftHandSide: Boolean,
    ) {
        val target = e.target
        when (target) {
            is BlockTree -> {
                if (target.flow == LinearFlow) {
                    // Hoist everything but the last out.
                    // This should help us when we end up hoisting into the left-hand-side of a
                    // nested complex assignment.
                    // TODO: more testing around this.  Do we need to re-apply?
                    when (val last = target.size - 1) {
                        -1 -> return
                        else -> {
                            for (i in 0 until last) {
                                out.add(freeTarget(target.edge(i)))
                            }
                            e.replace(freeTarget(target.edge(last)))
                            hoist(e, out, isLeftHandSide)
                            return
                        }
                    }
                }
            }
            is StayLeaf, is ValueLeaf -> return // No need to hoist
            is NameLeaf -> {
                if (isLeftHandSide) {
                    // No need to hoist
                    return
                }
            }
            is CallTree, is DeclTree, is EscTree, is FunTree -> {}
        }
        if (isLeftHandSide) {
            val isAssign = isAssignment(target)
            for (i in target.indices) {
                hoist(
                    target.edge(i),
                    out,
                    isLeftHandSide = isAssign && i == 1,
                )
            }
        } else {
            val doc = target.document
            val pos = target.pos
            val temporary = nameMaker.unusedTemporaryName(Temporary.defaultNameHint)
            e.replace(RightNameLeaf(doc, pos, temporary))
            out.add(
                DeclTree(
                    doc,
                    pos,
                    listOf(
                        LeftNameLeaf(doc, pos, temporary),
                        ValueLeaf(doc, pos, vInitSymbol),
                        target,
                    ),
                ),
            )
        }
    }
}

internal fun isCommaCall(t: Tree?): Boolean {
    if (t is CallTree) {
        val callee = t.childOrNull(0)
        return callee?.functionContained == BuiltinFuns.commaFn
    }
    return false
}

private const val ASSIGN_CHILD_COUNT = 3 // callee left right

internal fun isAssignment(t: Tree?, orCompoundAssignment: Boolean = false): Boolean {
    if (t is CallTree && t.size == ASSIGN_CHILD_COUNT) {
        val callee = t.childOrNull(0)
        if (callee is NameLeaf) {
            val builtinKey = callee.content.builtinKey
            if (builtinKey != null) {
                if (builtinKey == Operator.Eq.text) {
                    return true
                }
                if (
                    orCompoundAssignment &&
                    Operator.isProbablyAssignmentOperator(builtinKey, TokenType.Punctuation)
                ) {
                    return true
                }
            }
        } else {
            val macro = callee?.functionContained
            return if (orCompoundAssignment) {
                macro?.assignsArgumentOne == true
            } else {
                macro == BuiltinFuns.setLocalFn
            }
        }
    }
    return false
}

/** True if tree is the target of an assignment. */
internal fun isLeftHandSide(tree: Tree, andAssigned: Boolean = false): Boolean {
    val incoming = tree.incoming ?: return false
    val parent = incoming.source ?: return false
    val indexInParent = parent.edges.indexOf(incoming)
    return indexInParent >= 0 && isLeftHandSide(parent, indexInParent, andAssigned = andAssigned)
}

/** True if parent's childIndex-th child is the target of an assignment. */
internal fun isLeftHandSide(
    parent: Tree,
    childIndex: Int,
    andAssigned: Boolean = false,
): Boolean {
    if (parent is DeclTree && childIndex == 0) {
        return !andAssigned
    }
    if (childIndex == 1 && isAssignment(parent, orCompoundAssignment = true)) {
        return true
    }
    if (isCommaCall(parent) && isLeftHandSide(parent, andAssigned = andAssigned)) {
        return true
    }
    return false
}

// TODO: merge this into DeclParts.kt
internal fun declParts(
    t: Tree,
    start: Int,
): Triple<TEdge?, TEdge?, Int> {
    var type: TEdge? = null
    var initializer: TEdge? = null
    var i = start + 1 // Skip over the name.
    val n = t.size
    partLoop@
    while (i + 1 < n) {
        val ci = t.child(i)
        when (ci.symbolContained) {
            initSymbol -> {
                initializer = t.edge(i + 1)
                i += 2
                continue@partLoop
            }
            typeSymbol -> {
                type = t.edge(i + 1)
                i += 2
                continue@partLoop
            }
        }
        break
    }

    return Triple(type, initializer, i)
}

private fun addTypeToDeclaration(d: DeclTree, type: Tree) {
    val (typeEdge, initEdge, end) = declParts(d, 0)
    if (typeEdge != null) {
        // TODO: if we have two type specifiers, should we intersect them?
        //     let [a: T]: S = ...
        // to
        //     let a: (T & S);
        //     [a] = ...
        // For now (Jul 2020) just add a call to `&`.
        typeEdge.replace { p ->
            Call(p) {
                Rn(p.leftEdge, ampName)
                Replant(freeTarget(typeEdge))
                Replant(type)
            }
        }
        return
    }
    val insertionPoint = if (initEdge != null) {
        d.edges.indexOf(initEdge) - 1 // At symbol \init.
    } else {
        end
    }
    d.replace(insertionPoint until insertionPoint) {
        V(type.pos.leftEdge, vTypeSymbol)
        Replant(type)
    }
}

private val ampName = BuiltinName(Operator.Amp.text!!)
