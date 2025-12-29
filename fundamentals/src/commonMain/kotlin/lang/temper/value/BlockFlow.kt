package lang.temper.value

import lang.temper.common.ForwardOrBack
import lang.temper.common.Freq3

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

/**
 * Makes an effort to find terminal expressions.
 * This may over-identify, possibly when there is an unexpanded macro
 * call that would eliminate itself like `f(); expandsToEmptyBlock()`
 *
 * In that case, this method correctly identifies the
 * *lexically terminal expression*.
 *
 * This will return empty results given an empty block.
 * The semantics of an empty block is to evaluate to `void`, but these
 * semantics do not compose as below because the compiler is allowed
 * to assume that a block that appears as the child of a block can be
 * inlined into its parent as long as names are first resolved according
 * to block-scope rules.
 *
 *     do {
 *       terminalExpression();
 *       do {}
 *     }
 *
 * `if (x) {...}` is equivalent to `if (x) {...} else {}`, so it is an
 * error for either to appear in terminal position when the surrounding
 * block is not *Void* typed.
 *
 * Callers should treat the lack of terminal expressions as equivalent to
 * void.
 *
 * The [Freq3] is [Freq3.Always] when every branch (assuming halting)
 * reaches a terminal expression that was listed.
 */
fun BlockTree.getTerminalExpressions(
    assumeFailureCanHappen: Boolean = false,
): Pair<List<ControlFlow.Stmt>, Freq3> {
    val terminalExpressions = mutableSetOf<ControlFlow.Stmt>()
    val maximalPaths = forwardMaximalPaths(this, assumeFailureCanHappen = assumeFailureCanHappen)
    // optimistic about finding terminal expressions until shown otherwise
    var foundOnAllBranches = maximalPaths.failExitPathIndices.isEmpty() &&
        maximalPaths.exitPathIndices.isNotEmpty()

    // Walk backwards from exit looking for terminal nodes.
    //
    // If we *just* walk backwards from exit, it's hard to avoid `notTerminal`
    // in the example below.
    //
    //     notTerminal();
    //     if (cond) {
    //       terminal();
    //     }
    //
    // So after finding a terminal, we walk backwards from
    data class PathInContext(
        val pathIndex: MaximalPathIndex,
        val followedByTerminal: Boolean,
    )

    val deque = ArrayDeque(
        maximalPaths.exitPathIndices.map { PathInContext(it, false) },
    )
    val enqueued = mutableSetOf<PathInContext>()
    enqueued.addAll(deque)

    while (deque.isNotEmpty()) {
        val pathInContext = deque.removeFirst()
        var followedByTerminal = pathInContext.followedByTerminal
        val path = maximalPaths[pathInContext.pathIndex]
        for (elementIndex in path.elements.indices.reversed()) {
            val element = path.elements[elementIndex]
            val stmt = element.stmt
            if (!element.isCondition && stmt != null) {
                if (!followedByTerminal) {
                    terminalExpressions.add(stmt)
                    followedByTerminal = true // for preceders below
                } else if (stmt in terminalExpressions) {
                    terminalExpressions.remove(stmt)
                    foundOnAllBranches = false
                }
                break
            }
        }
        for (preceder in path.preceders) {
            if (preceder.dir == ForwardOrBack.Forward) {
                val precederInContext =
                    PathInContext(preceder.pathIndex, followedByTerminal = followedByTerminal)
                if (precederInContext !in enqueued) {
                    deque.add(precederInContext)
                    enqueued.add(precederInContext)
                }
            }
        }
        if (pathInContext.pathIndex == maximalPaths.entryPathIndex) {
            if (!followedByTerminal) {
                foundOnAllBranches = false
            }
        }
    }

    val freq = if (foundOnAllBranches) {
        Freq3.Always
    } else if (maximalPaths.exitPathIndices.isNotEmpty()) {
        Freq3.Sometimes
    } else {
        Freq3.Never
    }

    return terminalExpressions.toList() to freq
}
