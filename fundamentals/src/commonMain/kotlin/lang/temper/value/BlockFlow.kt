package lang.temper.value

/**
 * Explains which block children to execute during interpretation.
 */
sealed class BlockFlow {
    abstract fun copy(): BlockFlow
}

object LinearFlow : BlockFlow() {
    override fun copy() = this
}

data class StructuredFlow(
    val controlFlow: ControlFlow.StmtBlock,
) : BlockFlow() {
    override fun copy(): BlockFlow = StructuredFlow(controlFlow.deepCopy())
}

fun StructuredFlow.blockChildReferenceToStmt(): Map<BlockChildReference, ControlFlow.Stmt> =
    buildMap {
        fun scan(cf: ControlFlow) {
            if (cf is ControlFlow.Stmt) {
                this[cf.ref] = cf
            }
            for (clause in cf.clauses) {
                scan(clause)
            }
        }
        scan(controlFlow)
    }
