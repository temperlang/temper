package lang.temper.frontend

import lang.temper.common.console
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.ControlFlow
import lang.temper.value.RightNameLeaf
import lang.temper.value.ValueLeaf
import lang.temper.value.toPseudoCode
import lang.temper.value.void

private const val DEBUG = false

/**
 * Merges one [BlockTree] into another so that we can flatten all nested
 * blocks into one module/function root with a unified, standalone [ControlFlow].
 */
internal class Stitcher(
    private val outerBlock: BlockTree,
    private val pulledBlock: BlockTree,
    /** The edge index within [outerBlock] where [pulledBlock] is situated. */
    private val edgeIndex: Int,
) {
    private val outerFlow = structureBlock(outerBlock)
    private val pulledFlow = structureBlock(pulledBlock)

    fun stitch() {
        if (pulledFlow.controlFlow.isNoopBlock(pulledBlock)) {
            return
        }

        if (DEBUG) {
            console.group("Stitching #$edgeIndex") {
                console.group("Outer") {
                    console.group("Flow") {
                        console.log("$outerFlow")
                    }
                    console.group("Pseudo") {
                        outerBlock.toPseudoCode(console.textOutput)
                    }
                }
                console.group("Pulled") {
                    console.group("Flow") {
                        console.log("$pulledFlow")
                    }
                    console.group("Pseudo") {
                        pulledBlock.toPseudoCode(console.textOutput)
                    }
                }
            }
        }

        adjustOuterFlow(outerFlow.controlFlow)

        if (DEBUG) {
            console.group("After") {
                console.group("Flow") {
                    console.log("${outerBlock.flow}")
                }
                console.group("Pseudo") {
                    outerBlock.toPseudoCode(console.textOutput)
                }
            }
        }
    }

    /**
     * Given a BlockChildReference from the pulled flow, return an equivalent
     * one that makes sense in the outerBlock which requires adopting trees
     * from the pulledBlock into the outerBlock.
     */
    private fun BlockChildReference.adjustPulled(): BlockChildReference {
        val edge = pulledBlock.dereference(this)
            ?: return BlockChildReference(null, pos)
        val outerIndex = outerBlock.size
        val adoptedTree = edge.target
        edge.replace(ValueLeaf(pulledBlock.document, pos, void))
        outerBlock.add(adoptedTree)
        return BlockChildReference(outerIndex, pos)
    }

    /** Recursively walks the pulled block to adjust all the BlockChildReferences contained */
    private fun ControlFlow.StmtBlock.adjustPulledBlockFlow(): ControlFlow.StmtBlock =
        ControlFlow.StmtBlock(
            pos = pos,
            stmts = stmts.map { it.adjustPulledFlow() },
        )

    private fun ControlFlow.Labeled.adjustedPulledLabeledFlow() = ControlFlow.Labeled(
        pos = pos,
        breakLabel = breakLabel,
        continueLabel = continueLabel,
        stmts = stmts.adjustPulledBlockFlow(),
    )

    private fun ControlFlow.adjustPulledFlow(): ControlFlow {
        return when (this) {
            is ControlFlow.If -> ControlFlow.If(
                pos = pos,
                condition = condition.adjustPulled(),
                thenClause = thenClause.adjustPulledBlockFlow(),
                elseClause = elseClause.adjustPulledBlockFlow(),
            )
            is ControlFlow.Loop -> ControlFlow.Loop(
                pos = pos,
                label = label,
                checkPosition = checkPosition,
                condition = condition.adjustPulled(),
                body = body.adjustPulledBlockFlow(),
                increment = increment.adjustPulledBlockFlow(),
            )
            is ControlFlow.Break -> ControlFlow.Break(pos, target)
            is ControlFlow.Continue -> ControlFlow.Continue(pos, target)
            is ControlFlow.Labeled -> adjustedPulledLabeledFlow()
            is ControlFlow.OrElse -> ControlFlow.OrElse(
                pos = pos,
                orClause = orClause.adjustPulledFlow() as ControlFlow.Labeled,
                elseClause = elseClause.adjustPulledBlockFlow(),
            )
            is ControlFlow.Stmt -> ControlFlow.Stmt(ref.adjustPulled())
            is ControlFlow.StmtBlock -> adjustPulledBlockFlow()
        }
    }

    /** Looks for [edgeIndex] in the outer flow to identify how to incorporate the pulled flow. */
    private fun adjustOuterFlow(cf: ControlFlow.StmtBlock): Boolean {
        var i = 0
        while (i in cf.stmts.indices) {
            val stmt = cf.stmts[i]
            if (stmt is ControlFlow.StmtBlock) {
                if (adjustOuterFlow(stmt)) {
                    return true
                }
            } else {
                val ref = stmt.ref
                if (ref?.index == edgeIndex) {
                    // Stitch the pulled flow in before this and
                    // use the result to rebuild the condition.
                    cf.withMutableStmtList { mutStmts ->
                        val adaptedPulledFlow = pulledFlow.controlFlow.adjustPulledFlow()
                        val atEdge = outerBlock.dereference(ref)?.target
                        if (stmt is ControlFlow.Stmt && (atEdge is ValueLeaf? || atEdge is RightNameLeaf)) {
                            // Replace any inserted result expression
                            mutStmts[i] = adaptedPulledFlow
                        } else {
                            mutStmts.add(i, adaptedPulledFlow)
                        }
                    }
                    return true
                }
                for (clause in stmt.clauses) {
                    val stmtBlock = clause as? ControlFlow.StmtBlock
                        // `orelse` clauses have a top-level clause that is a labeled block.
                        ?: (clause as ControlFlow.Labeled).stmts
                    if (adjustOuterFlow(stmtBlock)) {
                        return true
                    }
                }
            }

            i += 1
        }
        return false
    }
}
