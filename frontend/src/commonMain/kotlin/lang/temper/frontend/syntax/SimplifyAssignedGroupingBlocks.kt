package lang.temper.frontend.syntax

import lang.temper.builtin.BuiltinFuns
import lang.temper.interp.ExtendsFn
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.Tree
import lang.temper.value.extendsBuiltinName
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.initSymbol
import lang.temper.value.unpackUnappliedDecoration
import lang.temper.value.void

/**
 * Generic function declarations expand to blocks like
 *
 *     let f__0 = do {
 *       @typeFormal(\T) @typeDecl(T__0) let T__0 = type(T__0);
 *       fn <T__0 extends AnyValue>(...): ... { ... }
 *     };
 *
 * Pre-typing based on [lang.temper.frontend.typestage.TyperPlan] does better
 * when a function tree is associated with its actual name.
 *
 * This pass looks for blocks where:
 *
 * - it has a linear flow and is unlabelled
 * - it appears as a declarations initializer expression
 * - its statements are declarations followed by a function
 *
 * For these, we rearrange the declaration thus:
 *
 *     let x = do {        let y = ...;     fn { ... } };
 *
 * transforms to
 *
 *             do { let x; let y = ...; x = fn { ... } };
 *
 * or inline into a block containing the declaration.
 *
 *     let x;              let y = ...; x = fn { ... }
 *
 * This has the effect of splitting the initializer but preserves order of operations
 * with declaration metadata, and initializers get split out soon anyway.
 */
internal fun simplifyAssignedGroupingBlocks(t: Tree) {
    for (i in t.indices.reversed()) {
        // Do depth-first, so that mucking with parents does not complicate
        // traversing to children.
        // Do in reverse so that we can inline the children into a LinearFlowed block
        // without affecting traversal to later children.
        simplifyAssignedGroupingBlocks(t.child(i))

        val edge = t.edge(i)
        // We can find the pattern in two contexts:
        // - A declaration with an initializer that is a block
        // - An assignment whose right-hand-side is a block
        val edgeTarget = edge.target
        val isDeclarationNotAssignment = edgeTarget is DeclTree

        val (nameLeaf: Tree, block: Tree) = when (edgeTarget) {
            is DeclTree -> {
                val decl = edge.target as? DeclTree
                val declParts = decl?.parts
                val inits = declParts?.metadataSymbolMultimap?.get(initSymbol)
                if (inits?.size == 1) {
                    val initEdge = inits[0]
                    declParts.name to initEdge.target
                } else {
                    null
                }
            }
            is CallTree ->
                if (isAssignment(edgeTarget)) {
                    edgeTarget.child(1) to edgeTarget.child(2)
                } else {
                    null
                }
            else -> null
        } ?: continue

        if (
            block !is BlockTree || nameLeaf !is NameLeaf ||
            block.flow !is LinearFlow || block.parts.label != null || block.size == 0
        ) {
            continue
        }

        fun allowedInDeclGroupingBlock(t: Tree): Boolean = when {
            t is DeclTree -> true
            isExtendsCall(t) -> true
            else -> false
        }

        val lastBlockChild = block.lastChild
        val prevChildren = block.children.subList(0, block.size - 1)
        if (lastBlockChild !is FunTree || !prevChildren.all(::allowedInDeclGroupingBlock)) {
            continue
        }

        // We've checked the pre-conditions above.
        // Now, rearrange.
        // If the owning declaration is in a block possibly with intervening unapplied decorations,
        // we just inline into that block.
        var nUp = 0
        var insertionPt = edge
        if (isDeclarationNotAssignment) { // Scan upwards for a block to insert it into
            while (insertionPt.source !is BlockTree) {
                val rootwardsEdge = insertionPt.source!!.incoming!!
                // We started at the declaration, and now we're going up through decorations.
                val dec = unpackUnappliedDecoration(rootwardsEdge)
                if (dec != null && rootwardsEdge.target.edge(dec.decoratedIndex) == insertionPt) {
                    insertionPt = rootwardsEdge
                    nUp += 1
                } else {
                    break
                }
            }
        }

        // Remove the block
        val initEdge = block.incoming!!
        val initEdgeIndex = initEdge.edgeIndex
        if (isDeclarationNotAssignment) {
            // We remove two edges, the \init metadata key and the block.
            //     let x = do {...}   ->   let x;
            initEdge.source!!.replace((initEdgeIndex - 1)..initEdgeIndex) {}
        } else {
            // Since assignment returns the value assigned, replacing with the
            // block is semantics preserving.
            //     x = do { ... }  ->  do { ... }
            edge.replace {
                Replant(freeTree(block))
            }
        }
        // Rewrite the last expression using the name
        //         fn () {...}   ->
        //     x = fn () {...}
        lastBlockChild.incoming!!.replace {
            Call(lastBlockChild.pos, BuiltinFuns.setLocalFn) {
                Replant(nameLeaf.copyLeft())
                Replant(freeTree(lastBlockChild))
            }
        }

        val insertInto = insertionPt.source!!
        if (!isDeclarationNotAssignment) {
            // We're done.  Just inline away the block if possible
            // which simplifies some searching for initializers like that
            // done by the define stage type processing.
            val blockEdge = block.incoming
            val parent = blockEdge?.source
            if (parent is BlockTree && parent.flow is LinearFlow) {
                val blockEdgeIndex = blockEdge.edgeIndex
                parent.replace(blockEdgeIndex..blockEdgeIndex) {
                    block.children.forEach {
                        Replant(freeTree(it))
                    }
                }
            }
        } else if (insertInto is BlockTree && insertInto.flow is LinearFlow) {
            // Inline the do{}-block's content into the parent block after `let x`
            val edgeIndexAfterDecl = insertionPt.edgeIndex + 1

            insertInto.insert(edgeIndexAfterDecl) {
                block.children.toList().forEach {
                    Replant(freeTree(it))
                }
                // If the original was an initialized declaration, then its result is `void`, so
                // insert a `void` here.
                V(block.pos.rightEdge, void)
            }
        } else {
            // Pull the declaration into the block and replace the declaration with the block.
            val declRoot = insertionPt.target
            insertionPt.replace(block)
            block.insert(0) {
                Replant(declRoot)
            }
        }
    }
}

fun isExtendsCall(t: Tree): Boolean = t is CallTree &&
    when (val callee = t.childOrNull(0)) {
        null -> false
        is NameLeaf -> callee.content == extendsBuiltinName
        else -> callee.functionContained == ExtendsFn
    }
