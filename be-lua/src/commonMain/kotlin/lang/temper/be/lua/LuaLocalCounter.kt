package lang.temper.be.lua

import kotlin.math.max

class LuaLocalCounter(val cb: (Int) -> Unit) {
    private fun countLocalsBy(expr: Lua.Expr) {
        when (expr) {
            is Lua.BinaryExpr -> {
                countLocalsBy(expr.left)
                countLocalsBy(expr.right)
            }
            is Lua.FunctionCallExpr -> {
                countLocalsBy(expr.func)
                countLocalsBy(expr.args.exprs)
            }
            is Lua.MethodCallExpr -> {
                countLocalsBy(expr.func)
                countLocalsBy(expr.args.exprs)
            }
            is Lua.FunctionExpr -> {
                cb(countLocalsBy(expr.body) + expr.params.params.size)
            }
            is Lua.DotIndexExpr -> {
                countLocalsBy(expr.obj)
            }
            is Lua.IndexExpr -> {
                countLocalsBy(expr.obj)
                countLocalsBy(expr.index)
            }
            is Lua.Name -> {}
            is Lua.Num -> {}
            is Lua.Str -> {}
            is Lua.TableExpr -> {
                expr.args.forEach {
                    when (it) {
                        is Lua.IndexTableEntry -> {
                            countLocalsBy(it.value)
                        }
                        is Lua.NamedTableEntry -> {
                            countLocalsBy(it.value)
                        }
                        is Lua.WrappedTableEntry -> {
                            countLocalsBy(it.key)
                            countLocalsBy(it.value)
                        }
                    }
                }
            }
            is Lua.WrappedExpr -> {
                countLocalsBy(expr.expr)
            }
            is Lua.RestExpr -> {}
            is Lua.UnaryExpr -> {
                countLocalsBy(expr.right)
            }
        }
    }

    private fun countLocalsBy(exprs: Lua.Exprs) {
        exprs.exprs.forEach {
            countLocalsBy(it)
        }
    }

    private fun countLocalsBy(callExpr: Lua.CallExpr) {
        when (callExpr) {
            is Lua.FunctionCallExpr -> {
                countLocalsBy(callExpr.func)
                countLocalsBy(callExpr.args.exprs)
            }
            is Lua.MethodCallExpr -> {
                countLocalsBy(callExpr.func)
                countLocalsBy(callExpr.args.exprs)
            }
        }
    }

    fun countLocalsBy(chunk: Lua.Chunk): Int {
        var ret = 0
        chunk.body.forEach {
            ret += countLocalsBy(it)
        }
        when (val last = chunk.last) {
            is Lua.BreakStmt -> {}
            is Lua.GotoStmt -> {}
            is Lua.ReturnStmt -> {
                countLocalsBy(last.exprs)
            }
            null -> {}
        }
        return ret
    }

    private fun countLocalsBy(target: Lua.SetTargetOrWrapped) {
        when (target) {
            is Lua.WrappedExpr -> {
                countLocalsBy(target.expr)
            }
            is Lua.DotSetTarget -> {
                countLocalsBy(target.obj)
            }
            is Lua.IndexSetTarget -> {
                countLocalsBy(target.obj)
                countLocalsBy(target.index)
            }
            is Lua.NameSetTarget -> {}
        }
    }

    private fun countLocalsBy(stmt: Lua.Stmt): Int = when (stmt) {
        is Lua.GotoStmt -> 0
        is Lua.LocalStmt -> stmt.targets.targets.size
        is Lua.LocalDeclStmt -> stmt.targets.targets.size
        is Lua.CallStmt -> {
            countLocalsBy(stmt.callExpr)
            0
        }
        is Lua.Comment -> 0
        is Lua.DoStmt -> countLocalsBy(stmt.body)
        is Lua.FunctionStmt -> {
            cb(countLocalsBy(stmt.body) + stmt.params.params.size)
            0
        }
        is Lua.IfStmt -> {
            countLocalsBy(stmt.cond)
            buildList {
                add(countLocalsBy(stmt.then))
                stmt.elseIfs.forEach {
                    countLocalsBy(it.cond)
                    add(countLocalsBy(it.then))
                }
                val els = stmt.els
                if (els is Lua.Else) {
                    add(countLocalsBy(els.then))
                }
            }.max()
        }
        is Lua.LabelStmt -> 0
        is Lua.LocalFunctionStmt -> {
            cb(countLocalsBy(stmt.body) + stmt.params.params.size)
            1
        }
        is Lua.SetStmt -> {
            stmt.targets.targets.forEach {
                countLocalsBy(it)
            }
            countLocalsBy(stmt.exprs)
            0
        }
        is Lua.WhileStmt -> {
            countLocalsBy(stmt.cond)
            countLocalsBy(stmt.body)
        }
    }

    fun countLocals(stmt: Lua.Program) {
        when (stmt) {
            is Lua.Chunk -> {
                cb(countLocalsBy(stmt))
            }
        }
    }
}

internal fun countLocalsDirectlyIn(expr: Lua.Chunk): Int = LuaLocalCounter {}.countLocalsBy(expr)
