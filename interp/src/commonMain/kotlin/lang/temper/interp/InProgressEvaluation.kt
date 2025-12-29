package lang.temper.interp

import lang.temper.common.LeftOrRight
import lang.temper.value.ControlFlow

/**
 * Tracks progress of the evaluation or a function or module body.
 * If a function yields, then this may be stored with the function value
 * to allow later resuming the evaluation.
 */
class InProgressEvaluation {
    /** A stack of the control flow statements */
    internal val stack = mutableListOf<InProgress>()

    /**
     * An environment for local declarations, and for each [ControlFlow.Loop]'s
     * body on stack, an environment for its locals so that lambdas can close
     * over the locals for the loop instance in which they're created.
     *
     * See https://craftinginterpreters.com/closures.html#design-note
     */
    internal val envStack = mutableListOf<MutableEnvironment<*>>()
}

class InProgress(
    val controlFlow: ControlFlow,
) {
    /**
     * This has a meaning specific to [controlFlow].
     * It only matters for elements that stay on the stack after being executed
     *
     * For a [ControlFlow.StmtBlock] it is the index of the next child to handle.
     *
     * For a [ControlFlow.Loop] it is one of the [InProgress.LoopState] constants
     * indicating the next part of the loop (condition, body, increment) to evaluate.
     *
     * [ControlFlow.Labeled] or [ControlFlow.OrElse] need to stay on the stack so
     * that we can determine where jumps and bubbles land.  For those, [int] is
     * zero if the clause that may jump/bubble has not been scheduled, and non-zero
     * otherwise.
     */
    var int: Int

    init {
        int = if (controlFlow is ControlFlow.Loop) {
            when (controlFlow.checkPosition) {
                LeftOrRight.Left -> LoopState.BEFORE_CONDITION
                LeftOrRight.Right -> LoopState.BEFORE_BODY
            }
        } else {
            0
        }
    }

    object LoopState {
        const val BEFORE_BODY = 0
        const val BEFORE_INCREMENT = 1
        const val BEFORE_CONDITION = 2

        private const val N_LOOP_STATES = 3

        fun next(loopState: Int): Int = (loopState + 1) % N_LOOP_STATES
    }

    override fun toString(): String = "LoopState($controlFlow, $int)"
}
