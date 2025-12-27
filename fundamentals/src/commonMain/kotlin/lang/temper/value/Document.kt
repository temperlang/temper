package lang.temper.value

import lang.temper.name.NameMaker
import lang.temper.name.ParsedNameMaker
import lang.temper.name.ResolvedNameMaker

/** A common class that collects information about trees. */
class Document(context: DocumentContext) {
    var context: DocumentContext = context
        private set

    var nameMaker: NameMaker = ParsedNameMaker(context.namingContext, context.genre)
        private set

    var isResolved = false
        private set

    /**
     * Temporarily swap ownership to [interloper] for the duration of a run of [action]
     * so that a Module may stage a subordinate module to its same stage and incorporate
     * its content without having to rewrite stays.
     * Used during mixin processing and reincorporation.
     */
    fun <T> hijackTo(interloper: DocumentContext, action: () -> T): T {
        // Store
        val oldContext = context
        val oldNameMaker = nameMaker
        val oldIsResolved = isResolved

        // Override
        context = interloper
        nameMaker = ParsedNameMaker(interloper.namingContext, interloper.genre)
        isResolved = false

        val result = try {
            action()
        } finally {
            // Restore
            isResolved = oldIsResolved
            nameMaker = oldNameMaker
            context = oldContext
        }
        return result
    }

    /**
     * Called at the end of the [syntax stage][lang.temper.stage.Stage.SyntaxMacro]
     * to prevent creation of new tree elements with [parsed names][lang.temper.name.ParsedName].
     *
     * This causes a one-way state transition; it cannot be reversed.
     */
    fun markNamesResolved() {
        if (!isResolved) {
            nameMaker = ResolvedNameMaker(context.namingContext, context.genre)
            isResolved = true
        }
    }

    val treeFarm get() = TreeFarm(this)
}
