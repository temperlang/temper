package lang.temper.lexer

enum class OperatorType(
    val typicalMinArity: Int,
    val typicalMaxArity: Int,
    /** True when the operator token may follow its first operand. */
    val follows: Boolean,
) {
    /** Appears in between one or more operands. */
    Infix(2, 2, true),

    /** Appears after its sole operand. */
    Postfix(1, 1, true),

    /** Appears before its sole operand. */
    Prefix(1, 1, false),

    /**
     * Appears between operands at the same level, but possible with gaps.
     * For example, in
     *
     * ```js
     * [ 0, , 2, 3, , , 6, ]
     * ```
     *
     * the comma separator has holes.
     *
     * There may be nothing before the first comma or after the last, and two commas may appear
     * adjacent with neither operand nor token between.
     */
    Separator(0, Int.MAX_VALUE, true),

    /** Appears by itself as it has no operands. */
    Nullary(0, 0, false),
}
