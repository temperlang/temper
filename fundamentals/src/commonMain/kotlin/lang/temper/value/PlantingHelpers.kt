package lang.temper.value

import lang.temper.log.Position
import lang.temper.log.spanningPosition

/**
 * Produces a block with `if (condition) { thenClause } else { elseClause }`
 * usable in an expression context.
 */
@Suppress("FunctionName") // TreeFarm convention
fun Planting.IfThenElse(
    cond: Planting.() -> UnpositionedTreeTemplate<*>,
    thenClause: Planting.() -> Unit,
    elseClause: Planting.() -> Unit,
): UnpositionedTreeTemplate<BlockTree> {
    // thenClause and elseClause can plant multiple statements.
    // capture the block child reference index between them.
    var startOfElse = -1
    return Block(flowMaker = { blockTree ->
        // Build the flow after the functions above are applied to plant the pieces.
        val conditionRef = BlockChildReference(0, blockTree.child(0).pos)

        // Bundle a run of statements together for a statement block.
        // If there are zero statements, use the position from the right edge of the
        // preceding element.
        fun stmts(range: IntRange, pos: Position): Pair<List<ControlFlow>, Position> =
            if (range.isEmpty()) {
                listOf<ControlFlow>() to pos.rightEdge
            } else {
                val stmts = range.map { i ->
                    ControlFlow.Stmt(BlockChildReference(i, blockTree.child(i).pos))
                }
                stmts to stmts.spanningPosition(stmts.first().pos)
            }
        val (thenStmts, thenPos) =
            stmts(1 until startOfElse, conditionRef.pos)
        val (elseStmts, elsePos) =
            stmts(startOfElse until blockTree.size, thenPos.pos)
        StructuredFlow(
            ControlFlow.StmtBlock.wrap(
                ControlFlow.If(
                    blockTree.pos,
                    conditionRef,
                    ControlFlow.StmtBlock(thenPos, thenStmts),
                    ControlFlow.StmtBlock(elsePos, elseStmts),
                ),
            ),
        )
    }) {
        cond()
        check(numPlanted == 1) // cond is typed as producing a tree template.
        thenClause()
        startOfElse = this.numPlanted
        elseClause()
    }
}
