package lang.temper.frontend

import lang.temper.value.Tree

interface Advancable {
    val ok: Boolean

    /** The AST produced by the code generation phase. */
    val generatedCode: Tree?
    fun canAdvance(): Boolean
    fun advance()

    /** If it has advanced to a terminal state.
     * Note: There may be other states past that but any state where it could be considered 'done'
     */
    val finished: Boolean
}
