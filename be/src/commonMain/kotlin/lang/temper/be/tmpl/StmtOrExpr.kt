package lang.temper.be.tmpl

import lang.temper.log.Position
import lang.temper.log.Positioned

internal sealed class StmtOrExpr : Positioned {
    abstract val rawNode: TmpL.Tree
    abstract val stmtList: List<TmpL.Statement>
}
internal data class OneExpr(val expr: TmpL.Expression) : StmtOrExpr() {
    override val pos: Position get() = expr.pos
    override val rawNode get() = expr
    override val stmtList: List<TmpL.Statement> get() = listOf(TmpL.ExpressionStatement(expr))
}
internal sealed class Stmt : StmtOrExpr() {
    abstract fun asStmt(): TmpL.Statement
    abstract fun asBlock(): TmpL.BlockStatement
    open fun asStmtList(): List<TmpL.Statement> = when (val stmt = asStmt()) {
        is TmpL.BlockStatement -> stmt.takeBody()
        else -> listOf(stmt)
    }
    override val rawNode get() = asStmt()
}
internal data class OneStmt(val stmt: TmpL.Statement) : Stmt() {
    override val stmtList: List<TmpL.Statement> get() = listOf(stmt)
    override val pos: Position get() = stmt.pos
    override fun asStmt() = stmt
    override fun asBlock(): TmpL.BlockStatement = stmt as? TmpL.BlockStatement
        ?: TmpL.BlockStatement(stmt.pos, listOf(stmt))
}
internal data class Stmts(
    override val pos: Position,
    override val stmtList: List<TmpL.Statement>,
) : Stmt() {
    override fun asStmt() = TmpL.BlockStatement(pos, stmtList)
    override fun asBlock(): TmpL.BlockStatement = asStmt()
    override fun asStmtList(): List<TmpL.Statement> = stmtList
}
