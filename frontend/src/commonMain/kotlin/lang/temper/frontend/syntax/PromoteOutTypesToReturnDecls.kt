package lang.temper.frontend.syntax

import lang.temper.ast.TreeVisit
import lang.temper.value.BlockTree
import lang.temper.value.FunTree
import lang.temper.value.freeTarget
import lang.temper.value.outTypeSymbol
import lang.temper.value.returnParsedName
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vTypeSymbol

/**
 * Most function processing is done by the `let` and `fn` macros which produce [FunTree]s but some
 * [FunTree]s are produced directly by the grammar.
 * For example,
 * - arrow lambdas like `(): Int => 42`
 * - block lambdas like `{ (): Int => 42 }`
 *
 * This promotes the output type specification to a return type declaration.
 */
internal fun promoteOutTypesToReturnDecls(root: BlockTree) {
    TreeVisit.startingAt(root).forEachContinuing { t ->
        if (t is FunTree) {
            val parts = t.parts
            if (parts != null && parts.returnDecl == null) {
                val outTypeEdge = parts.metadataSymbolMap[outTypeSymbol]
                if (outTypeEdge != null) {
                    val keyEdgeIndex = outTypeEdge.edgeIndex - 1
                    val keyEdge = t.edge(keyEdgeIndex)

                    keyEdge.replace { V(vReturnDeclSymbol) }
                    val outTypeTree = freeTarget(outTypeEdge)
                    val pos = outTypeTree.pos
                    outTypeEdge.replace {
                        Decl(pos) {
                            Ln(pos.leftEdge, returnParsedName)
                            V(pos.leftEdge, vTypeSymbol)
                            Replant(outTypeTree)
                        }
                    }
                }
            }
        }
    }.visitPostOrder()
}
