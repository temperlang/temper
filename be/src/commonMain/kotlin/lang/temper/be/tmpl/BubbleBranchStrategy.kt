package lang.temper.be.tmpl

/** Strategy for how to decompile *Bubble* branches. */
enum class BubbleBranchStrategy {
    /**
     * Expressions like `hs(fail#1, possiblyBubblingOperation)` are followed
     * by boolean tests `if (fail#1) { ... }`
     */
    IfHandlerScopeVar,

    /**
     * Translations of operations that may bubble are assumed to raise an
     * exception when bubble is produced.
     *
     * So we can branch to bubble by catching exceptions and performing the
     * recovery code when one is caught.
     */
    CatchBubble,
}
