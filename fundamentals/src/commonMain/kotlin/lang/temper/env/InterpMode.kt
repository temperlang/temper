package lang.temper.env

/**
 * The kind of evaluation we're doing.
 *
 * Some discussion at https://hackmd.io/@ErrorIsNullError/SkX_D2Evu
 */
enum class InterpMode {
    /**
     * In partial evaluation mode, AST nodes are visited in roughly lexical order, and the focus is
     * on:
     *
     * - covering the whole tree
     * - not revisiting nodes (except where a macro replaces a call to it with unvisited content)
     * - simplifying the tree by
     *   - inlining known values
     *   - expanding macros and fully evaluating pure functions
     *   - producing partial values where useful
     */
    Partial,

    /**
     * In contrast to partial evaluation mode, in full node, the focus is on producing a result by:
     *
     * - visiting only nodes that are needed
     * - revisiting a node where necessary, e.g. in loops
     * - causing desired side effects and producing the desired result by
     *   - storing values in environment records
     *   - calling functions, even impure ones
     *   - failing when a full coherent value cannot be computed
     */
    Full,
}
