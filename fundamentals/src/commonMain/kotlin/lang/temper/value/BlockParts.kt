package lang.temper.value

/** A snapshot of a block */
class BlockParts private constructor(
    /** The label of the block as used in `break` statements. */
    val label: TEdge?,
    /** The statements of the block. */
    val statements: List<TEdge>,
    /** The blocks flow which determines the visit order of its constituent [statements]. */
    val flow: BlockFlow,
) {
    /**
     * The index of the first non-label related child.
     * If [flow] is [ComplexFlow] this may not be the first child executed when interpreting
     * the block.
     *
     *     foo:
     * is represented as
     *     (Value \label) (LeftName foo)
     */
    val startIndex: Int get() = if (label != null) { 2 } else { 0 }

    companion object {
        operator fun invoke(t: BlockTree): BlockParts =
            when (val flow = t.flow) {
                LinearFlow -> {
                    if (t.size >= 2 && labelSymbol == t.child(0).symbolContained) {
                        BlockParts(
                            t.edge(1),
                            t.edges.subList(2, t.edges.size).toList(),
                            flow,
                        )
                    } else {
                        BlockParts(
                            null,
                            t.edges.toList(),
                            flow,
                        )
                    }
                }
                is StructuredFlow -> BlockParts(
                    null,
                    t.edges.toList(),
                    flow,
                )
            }
    }
}
