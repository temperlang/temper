package lang.temper.frontend.generate

import lang.temper.common.Log
import lang.temper.frontend.replaceWithError
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.value.BlockTree
import lang.temper.value.FunTree
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.yieldingCallKind

/**
 * Rejects `await` and `yield` calls whose nearest enclosing function is not
 * a generator function, that is one whose super types do not include
 * `GeneratorFn`.
 *
 * Only a generator function body is converted into something that can pause,
 * so a yielding call anywhere else has no translation: backends would
 * otherwise receive an await expression in a plain function or in module
 * initialization, which they cannot express.
 *
 * Each rejected call is logged and replaced with an `error` call that
 * carries the same diagnostic, as [lang.temper.frontend.UseBeforeInit] does
 * for uses before initialization.
 *
 * Nested functions are checked against their own super types, not those
 * of the function that contains them, because a nested plain function
 * cannot pause its container.
 */
internal class YieldingCallChecker(
    private val logSink: LogSink,
    /**
     * Whether module level code may yield, as in the REPL.
     * See [lang.temper.frontend.StagingFlags.allowTopLevelAwait].
     */
    private val allowTopLevelYielding: Boolean,
) {
    fun check(root: BlockTree) {
        val rejected = mutableListOf<TEdge>()
        findRejected(root, mayYield = allowTopLevelYielding, rejected)
        for (edge in rejected) {
            val call = edge.target
            val problem = LogEntry(
                level = Log.Error,
                template = MessageTemplate.YieldingOutsideGeneratorFn,
                pos = call.pos,
                values = listOf(call.yieldingCallKind()!!),
            )
            problem.logTo(logSink)
            edge.replaceWithError(problem)
        }
    }

    /**
     * @param mayYield whether the nearest enclosing function may yield,
     *     or null if that is not known because its super types are incomplete.
     */
    private fun findRejected(tree: Tree, mayYield: Boolean?, rejected: MutableList<TEdge>) {
        val mayYieldWithin = if (tree is FunTree) tree.parts?.mayYield else mayYield
        if (mayYieldWithin == false && tree.yieldingCallKind() != null) {
            val edge = tree.incoming
            if (edge != null) {
                rejected.add(edge)
                // Do not look inside the call; it is going away.
                return
            }
        }
        for (child in tree.children) {
            findRejected(child, mayYieldWithin, rejected)
        }
    }
}
