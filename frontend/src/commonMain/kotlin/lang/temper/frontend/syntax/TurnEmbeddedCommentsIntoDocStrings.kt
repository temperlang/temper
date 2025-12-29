package lang.temper.frontend.syntax

import lang.temper.ast.TreeVisit
import lang.temper.builtin.RemUnpacked
import lang.temper.builtin.docPartsList
import lang.temper.builtin.pickCommentToAttach
import lang.temper.builtin.unpackAsRemCall
import lang.temper.common.LeftOrRight
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.InnerTree
import lang.temper.value.LinearFlow
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.initSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.vDocStringSymbol

/**
 * Combines pairs of trees like the below into one tree with
 * [vDocStringSymbol] metadata that integrates with the REPL help system and
 * which may translate to attached documentation like Python doc strings.
 *
 *     /** A comment */
 *     let f() {}
 *
 * becomes
 *
 *     REM("A comment", true);
 *     let f = fn f() {};
 *
 * which this pass transforms to
 *
 *     @docString(...) let f = fn () {};
 *
 * The details of the metadata value (... above) are documented with
 * [vDocStringSymbol].
 */
internal fun turnEmbeddedCommentsIntoDocStrings(root: Tree) {
    TreeVisit.startingAt(root)
        .forEachContinuing { tree ->
            if (tree is BlockTree && tree.flow is LinearFlow) {
                var i = 0
                val n = tree.size
                while (i < n) {
                    val start = i
                    val stmt = tree.child(i)
                    i += 1

                    unpackAsRemCall(stmt)?.let handleRemCall@{ remCall ->
                        val association = remCall.association
                        // If it associates left, look left.
                        if (association == LeftOrRight.Left) {
                            val associated = tree.edgeOrNull(start - 1)
                            if (associated != null) {
                                maybeAttachEmbeddedComment(remCall, associated)
                            }

                            return@handleRemCall
                        }

                        // If it associates right, gather the maximal list of right-associated
                        // comments.
                        val rems = mutableListOf(remCall)
                        while (i < n) {
                            val moreRem = unpackAsRemCall(tree.child(i))
                            if (moreRem?.association != LeftOrRight.Right) {
                                break
                            }
                            rems.add(moreRem)
                            i += 1
                        }

                        tree.edgeOrNull(i)?.let { associated ->
                            val decl = lookThroughDecorations(associated).target as? DeclTree
                            val chosenRem = pickCommentToAttach(rems, decl)
                            if (chosenRem != null) {
                                maybeAttachEmbeddedComment(chosenRem, associated)
                            }
                        }
                    }
                }
            }
        }
        .visitPreOrder()
}

private fun maybeAttachEmbeddedComment(rem: RemUnpacked, associated: TEdge) {
    (lookThroughDecorations(associated).target as? DeclTree)?.let {
        maybeAttachEmbeddedComment(rem, it)
    }
}

internal fun maybeAttachEmbeddedComment(rem: RemUnpacked, decl: DeclTree, maybeDecorateFnInitializer: Boolean = true) {
    val parts = decl.parts
    if (parts != null) { // Well-formed decl
        val docStringText = rem.text
        val commentPos = rem.pos
        val docPartsList = docPartsList(docStringText, commentPos.loc)

        // If the declaration is a named function declaration, attach the metadata to
        // the FunTree.
        val insertedInto: InnerTree
        val insertionPoint: Int

        val initializer = parts.metadataSymbolMap[initSymbol]?.let { lookThroughDecorations(it).target }
        if (initializer is FunTree && initializer.parts != null && maybeDecorateFnInitializer) {
            // Well-formed function
            insertedInto = initializer
            insertionPoint = initializer.size - 1
        } else {
            insertedInto = decl
            insertionPoint = decl.size
        }

        insertedInto.insert(insertionPoint) {
            V(commentPos.leftEdge, vDocStringSymbol)
            V(commentPos, docPartsList)
        }
    }
}
