package lang.temper.value

fun insertBeforeAll(
    block: BlockTree,
    followerIndices: Collection<Int>,
    insertion: (Planting).() -> Unit,
) {
    when (val flow = block.flow) {
        LinearFlow -> {
            val insertionPoint = followerIndices.minOrNull() ?: block.size
            block.insert(insertionPoint, insertion)
        }
        is StructuredFlow -> {
            val nBefore = block.size
            block.insert(nBefore, insertion)
            val inserted = nBefore until block.size
            if (!inserted.isEmpty()) {
                val cf = flow.controlFlow
                val insertionIntoStmtBlock = cf.stmts.indices
                    .firstOrNull { i ->
                        cf.stmts[i].mentions(followerIndices)
                    }
                    ?: cf.stmts.size
                cf.withMutableStmtList {
                    it.addAll(
                        insertionIntoStmtBlock,
                        inserted.map { i ->
                            ControlFlow.Stmt(BlockChildReference(i, block.child(i).pos))
                        },
                    )
                }
            }
        }
    }
}

private fun ControlFlow.mentions(edgeIndices: Collection<Int>): Boolean {
    val ref = this.ref
    if (ref != null && ref.index in edgeIndices) {
        return true
    }
    return clauses.any { it.mentions(edgeIndices) }
}
