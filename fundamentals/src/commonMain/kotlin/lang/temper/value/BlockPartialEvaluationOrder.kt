package lang.temper.value

/**
 * The order in which to evaluate a block's children.
 *
 * For structured flows, we produce a sensible flow so that in
 *
 *     if (x) {
 *       compile.log("A");
 *     } else {
 *       compile.log("B");
 *     }
 *     compile.log("C");
 *
 * we would either see the sequence of log statements [A, B, C] or [B, A, C] but not
 * any that have C at the start or in the middle.
 *
 * The optionality comes in because compiler stages are allowed to invert if conditions
 * as long as they also then/else clauses.
 *
 * @return edge indices.  Each edge index appears once.
 */
fun blockPartialEvaluationOrder(
    block: BlockTree,
): Iterable<Int> = when (val flow = block.flow) {
    LinearFlow -> block.indices
    is StructuredFlow -> buildList {
        fun addFrom(controlFlow: ControlFlow) {
            controlFlow.ref?.index?.let { add(it) }
            for (clause in controlFlow.clauses) {
                addFrom(clause)
            }
        }
        addFrom(flow.controlFlow)
    }
}

fun firstInOrder(block: BlockTree) = when (val flow = block.flow) {
    LinearFlow -> block.parts.startIndex
    is StructuredFlow -> {
        fun first(controlFlow: ControlFlow): Int? {
            val index = controlFlow.ref?.index
            if (index != null) {
                return index
            }

            for (clause in controlFlow.clauses) {
                val index = first(clause)
                if (index != null) {
                    return index
                }
            }
            return null
        }
        first(flow.controlFlow) ?: 0
    }
}
