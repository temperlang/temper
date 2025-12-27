package lang.temper.frontend.parse

import lang.temper.ast.TreeVisit
import lang.temper.common.subListToEnd
import lang.temper.lexer.Genre
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.TSymbol
import lang.temper.value.ValueLeaf
import lang.temper.value.callJoinSymbol
import lang.temper.value.freeTree

/**
 * Continuing calls like
 *
 *     f(x, y) {
 *       trailing_block;
 *     } continued (z) {
 *       block_for_continued;
 *     } yet another (w) {
 *       block_for_yet_another;
 *     }
 *
 * need some processing so that `f`'s author can have a place to put a signature that groups
 * `(z) { block_for_continued }` separately from the signature for `(w) { block_for_yet_another }`.
 *
 * In [Genre.Library] we do that by allowing the named parameter `continued` to receive a function
 * that is called with `z` and the block.
 *
 * In [Genre.Documentation] we need to avoid substantial rewrites, and only need to support a few
 * continuing calls: `if` and `do`.  So we do a simpler rewriting.
 */
internal fun rewriteCallJoins(root: BlockTree) {
    val wrapInDelegatingBlock = when (root.document.context.genre) {
        Genre.Library -> true
        Genre.Documentation -> false
    }

    TreeVisit.startingAt(root)
        .forEachContinuing { t ->
            if (t is CallTree) {
                // Scan for a callJoin symbol in key position.
                val callJoinStarts = mutableListOf<Int>()
                var argIndex = 1 // Start after callee
                while (argIndex < t.size) {
                    val arg = t.child(argIndex)
                    val symbol = TSymbol.unpackOrNull((arg as? ValueLeaf)?.content)
                    if (symbol == null) {
                        argIndex += 1
                        continue
                    }
                    if (symbol != callJoinSymbol) {
                        argIndex += 2 // Skip over associated value
                        continue
                    }

                    // Make sure we have a parameter name following.
                    val next = TSymbol.unpackOrNull(
                        (t.childOrNull(argIndex + 1) as? ValueLeaf)?.content,
                    )
                    if (next == null) {
                        argIndex += 2
                        continue
                    }

                    callJoinStarts.add(argIndex)
                    // Skip callJoin, the next symbol, and its value position child.
                    argIndex += 1 + 2
                }

                callJoinStarts.asReversed().forEach { callJoinIndex ->
                    val joinPos = t.child(callJoinIndex).pos
                    t.removeChildren(callJoinIndex..callJoinIndex)
                    if (wrapInDelegatingBlock) {
                        val rangeToCollect = callJoinIndex + 1 until t.size
                        val childrenToCollect = t.children.subListToEnd(callJoinIndex + 1).toList()
                        val delegateArg = t.document.nameMaker.unusedTemporaryName(
                            NAME_HINT_FOR_DELEGATE_FN,
                        )
                        t.replace(rangeToCollect) {
                            Fn {
                                Decl(joinPos, delegateArg) {}
                                Call {
                                    Rn(delegateArg)
                                    childrenToCollect.forEach {
                                        Replant(freeTree(it))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .visitPostOrder()
}

private const val NAME_HINT_FOR_DELEGATE_FN = "f"
