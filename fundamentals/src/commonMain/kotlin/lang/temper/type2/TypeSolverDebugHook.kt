package lang.temper.type2

import lang.temper.common.ForwardOrBack
import lang.temper.format.TokenSerializable

/**
 * Allows debugging a specific [TypeSolver] run.
 *
 * This is implemented as a node builder.
 *
 * The hook will receive events like:
 * - [TypeSolverDebugHook.begin]
 * - Then zero or more sequences like:
 *   - [TypeSolverDebugHook.beginStep]
 *   - zero or more [TypeSolverDebugHook.node]
 *   - zero or more [TypeSolverDebugHook.edge] calls that use defined nodes
 *   - [TypeSolverDebugHook.endStep]
 * - [TypeSolverDebugHook.end]
 *
 * *D3TypeSolverDebugHook* in the test branch allows generating an
 * animated Graphviz graph in HTML.
 */
interface TypeSolverDebugHook {
    fun begin()
    fun end()

    fun beginStep()
    fun node(x: DebugHookNode)
    fun edge(x: DebugHookEdge)
    fun endStep()

    data class DebugHookStyles(
        val isDirty: Boolean = false,
    )

    interface DebugHookNode {
        val key: String
        val solution: TokenSerializable?
        val upperBounds: Iterable<TokenSerializable>
        val commonBounds: Iterable<TokenSerializable>
        val lowerBounds: Iterable<TokenSerializable>

        val description: TokenSerializable?
        val details: TokenSerializable?

        val styles: DebugHookStyles
    }

    interface DebugHookEdge {
        val aNodeKey: String
        val bNodeKey: String
        val dir: ForwardOrBack?
        val description: TokenSerializable?

        val styles: DebugHookStyles
    }
}
