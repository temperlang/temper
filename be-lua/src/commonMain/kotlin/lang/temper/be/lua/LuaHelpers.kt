package lang.temper.be.lua

import lang.temper.be.tmpl.TmpL
import lang.temper.common.compatRemoveLast
import lang.temper.common.isEmpty
import lang.temper.log.Position

internal fun TmpL.RightHandSide.isPureVirtual(): Boolean {
    if (this !is TmpL.CallExpression) return false
    val fn = this.fn
    if (fn !is TmpL.InlineSupportCodeWrapper) return false
    val code = fn.supportCode
    if (code !is InlineLua) return false
    return code.name == "pure-virtual"
}

internal fun copyTree(tree: Lua.Expr): Lua.Expr = when (tree) {
    is Lua.BinaryExpr -> Lua.BinaryExpr(
        tree.pos,
        copyTree(tree.left),
        Lua.BinaryOp(tree.op.pos, tree.op.opEnum, tree.op.expressionOperatorDefinition),
        copyTree(tree.right),
    )
    is Lua.FunctionCallExpr -> Lua.FunctionCallExpr(
        tree.pos,
        copyTree(tree.func),
        Lua.Args(
            tree.args.pos,
            copyTree(tree.args.exprs),
        ),
    )
    is Lua.MethodCallExpr -> Lua.MethodCallExpr(
        tree.pos,
        copyTree(tree.func),
        copyTree(tree.method) as Lua.Name,
        Lua.Args(
            tree.args.pos,
            copyTree(tree.args.exprs),
        ),
    )
    is Lua.FunctionExpr -> Lua.FunctionExpr(
        tree.pos,
        copyTree(tree.params),
        copyTree(tree.body),
    )
    is Lua.DotIndexExpr -> Lua.DotIndexExpr(
        tree.pos,
        copyTree(tree.obj) as Lua.LiteralExpr,
        copyTree(tree.index) as Lua.Name,
    )
    is Lua.IndexExpr -> Lua.IndexExpr(
        tree.pos,
        copyTree(tree.obj) as Lua.LiteralExpr,
        copyTree(tree.index),
    )
    is Lua.Name -> Lua.Name(tree.pos, tree.id)
    is Lua.Num -> Lua.Num(tree.pos, tree.n)
    is Lua.Str -> Lua.Str(tree.pos, tree.s)
    is Lua.TableExpr -> Lua.TableExpr(
        tree.pos,
        tree.args.map { ent ->
            when (ent) {
                is Lua.IndexTableEntry -> Lua.IndexTableEntry(
                    ent.pos,
                    copyTree(ent.value),
                )
                is Lua.NamedTableEntry -> Lua.NamedTableEntry(
                    ent.pos,
                    copyTree(ent.key) as Lua.Name,
                    copyTree(ent.value),
                )
                is Lua.WrappedTableEntry -> Lua.WrappedTableEntry(
                    ent.pos,
                    copyTree(ent.key),
                    copyTree(ent.value),
                )
            }
        },
    )
    is Lua.WrappedExpr -> Lua.WrappedExpr(
        tree.pos,
        copyTree(tree.expr),
    )
    is Lua.RestExpr -> Lua.RestExpr(tree.pos)
    is Lua.UnaryExpr -> Lua.UnaryExpr(
        tree.pos,
        Lua.UnaryOp(tree.op.pos, tree.op.opEnum, tree.op.expressionOperatorDefinition),
        copyTree(tree.right),
    )
}

internal fun copyTree(tree: Lua.SetTargetOrWrapped): Lua.SetTargetOrWrapped = when (tree) {
    is Lua.SetTarget -> copyTree(tree)
    is Lua.WrappedExpr -> Lua.WrappedExpr(tree.pos, copyTree(tree.expr))
}

internal fun copyTree(tree: Lua.SetTargets): Lua.SetTargets = Lua.SetTargets(
    tree.pos,
    tree.targets.map(::copyTree),
)
internal fun copyTree(tree: Lua.SetTarget): Lua.SetTarget = when (tree) {
    is Lua.DotSetTarget -> Lua.DotSetTarget(
        tree.pos,
        copyTree(tree.obj),
        copyTree(tree.index) as Lua.Name,
    )
    is Lua.IndexSetTarget -> Lua.IndexSetTarget(
        tree.pos,
        copyTree(tree.obj),
        copyTree(tree.index),
    )
    is Lua.NameSetTarget -> Lua.NameSetTarget(
        tree.pos,
        copyTree(tree.target) as Lua.Name,
    )
}

internal fun copyTree(tree: Lua.Chunk): Lua.Chunk = Lua.Chunk(
    tree.pos,
    tree.body.map(::copyTree),
    copyTree(tree.last),
)

internal fun copyTree(tree: Lua.Exprs): Lua.Exprs = Lua.Exprs(
    tree.pos,
    tree.exprs.map(::copyTree),
)

internal fun copyTree(tree: Lua.LastStmt?): Lua.LastStmt? = when (tree) {
    null -> null
    is Lua.BreakStmt -> Lua.BreakStmt(tree.pos)
    is Lua.GotoStmt -> Lua.GotoStmt(tree.pos, copyTree(tree.name) as Lua.Name)
    is Lua.ReturnStmt -> Lua.ReturnStmt(tree.pos, copyTree(tree.exprs))
}

internal fun copyTree(tree: Lua.Params): Lua.Params = Lua.Params(
    tree.pos,
    tree.params.map { param ->
        when (param) {
            is Lua.Param -> Lua.Param(param.pos, copyTree(param.name) as Lua.Name)
            is Lua.RestExpr -> Lua.RestExpr(param.pos)
        }
    },
)

internal fun copyTree(tree: Lua.Stmt): Lua.Stmt = when (tree) {
    is Lua.GotoStmt -> Lua.GotoStmt(
        tree.pos,
        copyTree(tree.name) as Lua.Name,
    )
    is Lua.CallStmt -> Lua.CallStmt(
        tree.pos,
        copyTree(tree.callExpr) as Lua.CallExpr,
    )
    is Lua.Comment -> Lua.Comment(
        tree.pos,
        tree.text,
    )
    is Lua.DoStmt -> Lua.DoStmt(
        tree.pos,
        copyTree(tree.body),
    )
    is Lua.FunctionStmt -> Lua.FunctionStmt(
        tree.pos,
        copyTree(tree.dest),
        copyTree(tree.params),
        copyTree(tree.body),
    )
    is Lua.IfStmt -> {
        val condExpr = copyTree(tree.cond)
        val thenStmt = copyTree(tree.then)
        val elseIfClauses = tree.elseIfs.map {
            Lua.ElseIf(
                it.pos,
                copyTree(it.cond),
                copyTree(it.then),
            )
        }
        when (val els = tree.els) {
            is Lua.Else -> {
                val elseClause = Lua.Else(els.pos, copyTree(els.then))
                Lua.IfStmt(tree.pos, condExpr, thenStmt, elseIfClauses, elseClause)
            }
            else -> {
                Lua.IfStmt(tree.pos, condExpr, thenStmt, elseIfClauses, null)
            }
        }
    }
    is Lua.LabelStmt -> Lua.LabelStmt(
        tree.pos,
        copyTree(tree.name) as Lua.Name,
    )
    is Lua.LocalDeclStmt -> Lua.LocalDeclStmt(
        tree.pos,
        copyTree(tree.targets),
    )
    is Lua.LocalFunctionStmt -> Lua.LocalFunctionStmt(
        tree.pos,
        copyTree(tree.name) as Lua.Name,
        copyTree(tree.params),
        copyTree(tree.body),
    )
    is Lua.LocalStmt -> Lua.LocalStmt(
        tree.pos,
        Lua.SetTargets(
            tree.targets.pos,
            tree.targets.targets.map(::copyTree),
        ),
        copyTree(tree.exprs),
    )
    is Lua.SetStmt -> Lua.SetStmt(
        tree.pos,
        copyTree(tree.targets),
        copyTree(tree.exprs),
    )
    is Lua.WhileStmt -> Lua.WhileStmt(
        tree.pos,
        copyTree(tree.cond),
        copyTree(tree.body),
    )
}

/**
 * Rewrite elseif chains after the fact, which is O(n^2).
 * TODO Replace remaining uses of this with prebuilt elseif chains?
 */
internal fun ifStmt(
    pos: Position,
    cond: Lua.Expr,
    ifTrue: Iterable<Lua.Stmt>,
    ifTrueLast: Lua.LastStmt?,
    ifFalse0: Iterable<Lua.Stmt>,
    ifFalseLast0: Lua.LastStmt?,
): Lua.IfStmt {
    var ifFalse = ifFalse0
    var ifFalseLast = ifFalseLast0
    val elseIfs = mutableListOf<Lua.ElseIf>()
    while (ifFalse.count() == 1 && ifFalse.first() is Lua.IfStmt && ifFalseLast == null) {
        val ifStmt = ifFalse.first() as Lua.IfStmt
        val elseIf = Lua.ElseIf(
            ifStmt.pos,
            copyTree(ifStmt.cond),
            copyTree(ifStmt.then),
        )
        elseIfs.add(elseIf)
        elseIfs.addAll(ifStmt.elseIfs.map { it.deepCopy() })
        when (val els = ifStmt.els) {
            is Lua.Else -> {
                ifFalse = els.then.body.map(::copyTree)
                ifFalseLast = copyTree(els.then.last)
            }
            else -> {
                ifFalse = listOf()
                ifFalseLast = null
            }
        }
    }
    if (ifFalse.isEmpty() && ifFalseLast == null) {
        return Lua.IfStmt(
            pos,
            cond,
            luaChunk(
                pos,
                ifTrue,
                ifTrueLast,
            ),
            elseIfs,
            null,
        )
    }
    return Lua.IfStmt(
        pos,
        cond,
        luaChunk(
            pos,
            ifTrue,
            ifTrueLast,
        ),
        elseIfs,
        Lua.Else(
            pos,
            luaChunk(
                pos,
                ifFalse,
                ifFalseLast,
            ),
        ),
    )
}

internal fun basicIfStmt(
    pos: Position,
    cond: Lua.Expr,
    ifTrue: Iterable<Lua.Stmt>,
    ifFalse: Iterable<Lua.Stmt>,
): Lua.IfStmt = ifStmt(pos, cond, ifTrue, null, ifFalse, null)

/**
 * Simplifies chunks. For example, it turns `print(10); do print(20) end; return 30`
 * into `print(10); print(20); return 30`, or `do return 3 end; return 4` into `return 3`.
 */
internal fun luaChunk(pos: Position, stmts: Iterable<Lua.Stmt>, last: Lua.LastStmt?): Lua.Chunk {
    val stmtBuilt = mutableListOf<Lua.Stmt>()
    var disabled = false
    fun addLast(value: Lua.LastStmt?) {
        if (value != null) {
            stmtBuilt.add(
                Lua.DoStmt(
                    value.pos,
                    Lua.Chunk(
                        value.pos,
                        listOf(),
                        copyTree(value),
                    ),
                ),
            )
        }
    }
    fun addStmt(stmt: Lua.Stmt) {
        if (stmt is Lua.LabelStmt) {
            disabled = false
        }
        if (disabled) {
            return
        }
        when (stmt) {
            is Lua.DoStmt -> {
                for (stmt1 in stmt.body.body) {
                    addStmt(stmt1)
                }
                addLast(copyTree(stmt.body.last))
                if (stmt.body.last != null) {
                    disabled = true
                }
            }
            else -> {
                stmtBuilt.add(copyTree(stmt))
            }
        }
    }
    stmts.forEach(::addStmt)
    if (stmtBuilt.size == 1) {
        val first = stmtBuilt.first()
        if (first is Lua.DoStmt) {
            return Lua.Chunk(
                first.body.pos,
                first.body.body.map(::copyTree),
                copyTree(first.body.last),
            )
        }
    }
    if (last == null) {
        when (val lastBuilt = stmtBuilt.lastOrNull()) {
            is Lua.DoStmt -> {
                stmtBuilt.compatRemoveLast()
                stmtBuilt.addAll(lastBuilt.body.body.map(::copyTree))
                return Lua.Chunk(
                    pos,
                    stmtBuilt,
                    copyTree(lastBuilt.body.last),
                )
            }
            else -> {}
        }
    }
    return Lua.Chunk(
        pos,
        stmtBuilt,
        copyTree(last),
    )
}

internal fun buildLocalRequire(pos: Position, localName: LuaName, moduleName: String) = Lua.LocalStmt(
    pos,
    localName.asSetTargets(pos),
    Lua.FunctionCallExpr(pos, "require".asName(pos), moduleName.asStr(pos).asArgs()).asExprs(),
)

internal fun buildTableList(pos: Position, items: List<Lua.Expr>) = Lua.TableExpr(
    pos,
    items.map { Lua.IndexTableEntry(pos, it) },
)

// Lots of little helpers.
internal fun Lua.CallExpr.asStmt() = Lua.CallStmt(pos, this)
internal fun Lua.Expr.asArgs() = Lua.Args(pos, asExprs())
internal fun Lua.Expr.asExprs() = Lua.Exprs(pos, listOf(this))
internal fun Lua.Expr.call(arg: Lua.Expr) = call(listOf(arg))
internal fun Lua.Expr.call(args: List<Lua.Expr>) = Lua.FunctionCallExpr(pos, this, Lua.Args(pos, Lua.Exprs(pos, args)))
internal fun Lua.LiteralExpr.dot(index: String) = Lua.DotIndexExpr(pos, this, name(index).at(pos))
internal fun Lua.Name.asSetTarget() = Lua.NameSetTarget(pos, this)
internal fun Lua.Name.asSetTargets() = asSetTarget().asSetTargets()
internal fun Lua.Name.dotSet(index: LuaName) = Lua.DotSetTarget(pos, asSetTarget(), index.at(pos))
internal fun Lua.Name.dotSet(index: String) = Lua.DotSetTarget(pos, asSetTarget(), name(index).at(pos))
internal fun Lua.Name.dotSetTargets(index: String) = dotSet(index).asSetTargets()
internal fun Lua.SetTarget.asSetTargets() = Lua.SetTargets(pos, listOf(this))
internal fun LuaName.at(pos: Position) = Lua.Name(pos, this)
internal fun LuaName.asSetTargets(pos: Position) = at(pos).asSetTargets()
internal fun String.asName(pos: Position) = name(this).at(pos)
internal fun String.asStr(pos: Position) = Lua.Str(pos, this)
