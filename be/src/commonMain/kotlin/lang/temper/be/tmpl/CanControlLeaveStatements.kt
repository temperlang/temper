package lang.temper.be.tmpl

import lang.temper.name.ResolvedName
import lang.temper.value.TBoolean

/**
 * False if all paths obviously end in jumps, throws of exceptions, explicit
 * returns, or other statements so that control does not flow out of [stmts]
 * to any following flow control.
 *
 * True when putting [stmts] in a block followed by another statement could
 * result in that statement being evaluated.
 */
internal fun canControlLeaveStatements(stmts: List<TmpL.Statement>): Boolean =
    NormalExit in exitsOf(stmts)

private sealed interface ExitKind
private object NormalExit : ExitKind
private object ReturnExit : ExitKind
private object BubbleExit : ExitKind
private data class BreakExit(val label: ResolvedName?) : ExitKind
private data class ContinueExit(val label: ResolvedName?) : ExitKind

private fun exitsOf(stmts: List<TmpL.Statement>): Set<ExitKind> {
    val exits = mutableSetOf<ExitKind>(NormalExit)
    for (stmt in stmts) {
        if (NormalExit !in exits) { break }
        val stmtExits: Set<ExitKind> = when (stmt) {
            is TmpL.BreakStatement -> setOf(BreakExit(stmt.label?.id?.name))
            is TmpL.ContinueStatement -> setOf(ContinueExit(stmt.label?.id?.name))
            is TmpL.ModuleInitFailed -> setOf(BubbleExit)
            is TmpL.ReturnStatement -> setOf(ReturnExit)
            is TmpL.ThrowStatement -> setOf(BubbleExit)

            is TmpL.EmbeddedComment,
            is TmpL.BoilerplateCodeFoldBoundary,
            is TmpL.Assignment,
            is TmpL.ExpressionStatement,
            is TmpL.GarbageStatement,
            is TmpL.HandlerScope,
            is TmpL.LocalDeclaration,
            is TmpL.LocalFunctionDeclaration,
            is TmpL.SetAbstractProperty,
            is TmpL.SetBackedProperty,
            is TmpL.YieldStatement,
            -> setOf(NormalExit)

            // Check sub-statements
            is TmpL.BlockStatement -> exitsOf(stmt.statements)
            is TmpL.ComputedJumpStatement -> {
                var caseExits: Set<ExitKind>? = null
                for (case in stmt.cases) {
                    val exitsForCase = exitsOf(listOf(case.body))
                    caseExits = caseExits?.let { it + exitsForCase } ?: exitsForCase
                }
                // TODO: need to know whether jump covers all inputs
                caseExits ?: setOf(NormalExit)
            }
            is TmpL.IfStatement ->
                exitsOf(listOf(stmt.consequent)) + exitsOf(listOfNotNull(stmt.alternate))
            is TmpL.LabeledStatement -> {
                var bodyExits = exitsOf(listOf(stmt.statement))
                val label = stmt.label.id.name
                val breakExit = BreakExit(label)
                if (breakExit in bodyExits) {
                    bodyExits += NormalExit
                }
                // Assume control can jump to end
                bodyExits - setOf(breakExit, ContinueExit(label))
            }
            is TmpL.TryStatement -> {
                val t = exitsOf(listOf(stmt.tried))
                val r = exitsOf(listOf(stmt.recover))
                (t - BubbleExit) + r
            }
            is TmpL.WhileStatement -> {
                var bodyExits = exitsOf(listOf(stmt.body))
                val defaultBreak = BreakExit(null)
                if (defaultBreak in bodyExits || !stmt.test.isAlwaysTrue) {
                    bodyExits += NormalExit
                }
                bodyExits - setOf(defaultBreak, ContinueExit(null))
            }
        }
        exits.remove(NormalExit)
        exits.addAll(stmtExits)
    }
    return exits.toSet()
}

private val TmpL.Expression.isAlwaysTrue
    get() = this !is TmpL.ValueReference || this.value != TBoolean.valueTrue
