package lang.temper.be.tmpl

import lang.temper.value.JumpLabel

/**
 * Keeps track of the [JumpLabel]s for the `...orelse...`s that
 * the TmpL we're building is in.
 *
 * This allows quick checks as to whether a label is that of
 * an `orelse` that's in scope, and also knowing the innermost.
 */
internal class OrElseJumpLabels {
    private val stack = mutableListOf<JumpLabel>()
    private val contained = mutableMapOf<JumpLabel, Int>()

    fun add(lbl: JumpLabel) {
        stack.add(lbl)
        contained[lbl] = (contained[lbl] ?: 0) + 1
    }

    fun remove(lbl: JumpLabel) {
        check(stack.last() == lbl)
        stack.removeLast()
        val n = contained.getValue(lbl)
        if (n == 1) {
            contained.remove(lbl)
        } else {
            contained[lbl] = n - 1
        }
    }

    operator fun contains(lbl: JumpLabel) = lbl in contained

    fun last(): JumpLabel? = stack.lastOrNull()
    fun isEmpty(): Boolean = stack.isEmpty()
}
