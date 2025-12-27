package lang.temper.be.lua

import lang.temper.common.compatRemoveLast
import lang.temper.log.Position

enum class LuaClosureMode {
    BasicFunction,
    InlineTableFunction,
    GlobalTableFunction,
}

class LuaClosure(val objName: LuaName, val importedNames: List<LuaName>) {
    val locals = mutableListOf<LuaName>()
    val allExterns = mutableListOf<LuaName>()
    val mutExterns = mutableListOf<LuaName>()

    private fun isDefined(name: LuaName): Boolean = locals.contains(name)

    private fun isRealName(name: LuaName): Boolean = when (name.text) {
        "true", "false", "nil" -> false
        else -> true
    }

    private fun forSet(pos: Position, name: LuaName): Lua.SetTarget {
        if (!isDefined(name)) {
            if (!allExterns.contains(name)) {
                allExterns.add(name)
            }
            if (!mutExterns.contains(name)) {
                mutExterns.add(name)
            }
        }
        return Lua.IndexSetTarget(
            pos,
            Lua.NameSetTarget(
                pos,
                Lua.Name(pos, objName),
            ),
            Lua.Num(pos, (allExterns.indexOf(name) + 1).toDouble()),
        )
    }

    private fun forUse(pos: Position, name: LuaName): Lua.Expr {
        return if (isRealName(name) && !importedNames.contains(name)) {
            if (isRealName(name) && !isDefined(name)) {
                if (!allExterns.contains(name)) {
                    allExterns.add(name)
                }
            }
            Lua.IndexExpr(
                pos,
                Lua.Name(pos, objName),
                Lua.Num(pos, (allExterns.indexOf(name) + 1).toDouble()),
            )
        } else {
            Lua.Name(pos, name)
        }
    }

    private fun<T> scoped(cb: () -> T): T {
        val len = locals.size
        val ret = cb()
        while (locals.size > len) {
            locals.compatRemoveLast()
        }
        return ret
    }

    private fun define(target: Lua.SetTarget) {
        when (target) {
            is Lua.DotSetTarget -> TODO()
            is Lua.IndexSetTarget -> TODO()
            is Lua.NameSetTarget -> locals.add(target.target.id)
        }
    }

    private fun set(target: Lua.SetTarget): Lua.SetTarget = when (target) {
        is Lua.DotSetTarget -> when (val obj = target.obj) {
            is Lua.SetTarget -> Lua.DotSetTarget(
                target.pos,
                set(obj),
                target.index,
            )
            is Lua.WrappedExpr -> Lua.DotSetTarget(
                target.pos,
                Lua.WrappedExpr(
                    obj.pos,
                    scan(obj.expr),
                ),
                target.index,
            )
        }
        is Lua.IndexSetTarget -> when (val obj = target.obj) {
            is Lua.SetTarget -> Lua.IndexSetTarget(
                target.pos,
                set(obj),
                scan(target.index),
            )
            is Lua.WrappedExpr -> Lua.IndexSetTarget(
                target.pos,
                Lua.WrappedExpr(
                    obj.pos,
                    scan(obj.expr),
                ),
                scan(target.index),
            )
        }
        is Lua.NameSetTarget -> {
            forSet(target.target.pos, target.target.id)
        }
    }

    private fun scan(expr: Lua.CallExpr): Lua.CallExpr = when (expr) {
        is Lua.FunctionCallExpr -> Lua.FunctionCallExpr(
            expr.pos,
            scan(expr.func),
            Lua.Args(
                expr.args.pos,
                Lua.Exprs(
                    expr.args.exprs.pos,
                    expr.args.exprs.exprs.map(::scan),
                ),
            ),
        )
        is Lua.MethodCallExpr -> Lua.MethodCallExpr(
            expr.pos,
            scan(expr.func),
            expr.method,
            Lua.Args(
                expr.args.pos,
                Lua.Exprs(
                    expr.args.exprs.pos,
                    expr.args.exprs.exprs.map(::scan),
                ),
            ),
        )
    }

    private fun scan(expr: Lua.Expr): Lua.Expr = when (expr) {
        is Lua.CallExpr -> scan(expr)
        is Lua.BinaryExpr -> Lua.BinaryExpr(
            expr.pos,
            scan(expr.left),
            expr.op,
            scan(expr.right),
        )
        is Lua.FunctionExpr -> Lua.FunctionExpr(
            expr.pos,
            expr.params,
            scoped {
                expr.params.params.forEach {
                    if (it is Lua.Param) {
                        locals.add(it.name.id)
                    }
                }
                luaChunk(
                    expr.body.pos,
                    expr.body.body.map(::scan),
                    scan(expr.body.last),
                )
            },
        )
        is Lua.DotIndexExpr -> Lua.DotIndexExpr(
            expr.pos,
            when (val got = scan(expr.obj)) {
                is Lua.LiteralExpr -> got
                else -> Lua.WrappedExpr(got.pos, got)
            },
            Lua.Name(expr.index.pos, expr.index.id),
        )
        is Lua.Name -> {
            forUse(expr.pos, expr.id)
        }
        is Lua.Num -> Lua.Num(expr.pos, expr.n)
        is Lua.Str -> Lua.Str(expr.pos, expr.s)
        is Lua.TableExpr -> Lua.TableExpr(
            expr.pos,
            expr.args.map {
                when (it) {
                    is Lua.IndexTableEntry -> Lua.IndexTableEntry(
                        it.pos,
                        scan(it.value),
                    )
                    is Lua.NamedTableEntry -> Lua.NamedTableEntry(
                        it.pos,
                        it.key,
                        scan(it.value),
                    )
                    is Lua.WrappedTableEntry -> Lua.WrappedTableEntry(
                        it.pos,
                        scan(it.key),
                        scan(it.value),
                    )
                }
            },
        )
        is Lua.WrappedExpr -> Lua.WrappedExpr(
            expr.pos,
            scan(expr.expr),
        )
        is Lua.RestExpr -> Lua.RestExpr(expr.pos)
        is Lua.UnaryExpr -> Lua.UnaryExpr(
            expr.pos,
            expr.op,
            scan(expr.right),
        )

        is Lua.IndexExpr -> Lua.IndexExpr(
            expr.pos,
            when (val got = scan(expr.obj)) {
                is Lua.LiteralExpr -> got
                else -> Lua.WrappedExpr(
                    got.pos,
                    got,
                )
            },
            scan(expr.index),
        )
    }

    private fun scan(stmt: Lua.LastStmt?): Lua.LastStmt? = stmt

    internal fun scan(stmt: Lua.Stmt): Lua.Stmt = when (stmt) {
        is Lua.LastStmt -> stmt
        is Lua.CallStmt -> Lua.CallStmt(
            stmt.pos,
            scan(stmt.callExpr),
        )
        is Lua.Comment -> Lua.Comment(stmt.pos, stmt.text)
        is Lua.DoStmt -> Lua.DoStmt(
            stmt.pos,
            scoped {
                luaChunk(
                    stmt.body.pos,
                    stmt.body.body.map(::scan),
                    scan(stmt.body.last),
                )
            },
        )
        is Lua.FunctionStmt -> Lua.FunctionStmt(
            stmt.pos,
            set(stmt.dest),
            stmt.params,
            scoped {
                stmt.params.params.forEach {
                    if (it is Lua.Param) {
                        locals.add(it.name.id)
                    }
                }
                luaChunk(
                    stmt.body.pos,
                    stmt.body.body.map(::scan),
                    scan(stmt.body.last),
                )
            },
        )
        is Lua.IfStmt -> Lua.IfStmt(
            stmt.pos,
            scan(stmt.cond),
            scoped {
                luaChunk(
                    stmt.then.pos,
                    stmt.then.body.map(::scan),
                    scan(stmt.then.last),
                )
            },
            stmt.elseIfs.map {
                Lua.ElseIf(
                    it.pos,
                    scan(it.cond),
                    luaChunk(
                        it.then.pos,
                        it.then.body.map(::scan),
                        scan(it.then.last),
                    ),
                )
            },
            when (val els = stmt.els) {
                is Lua.Else -> scoped {
                    Lua.Else(
                        els.pos,
                        luaChunk(
                            els.pos,
                            els.then.body.map(::scan),
                            scan(els.then.last),
                        ),
                    )
                }
                else -> null
            },
        )
        is Lua.LabelStmt -> Lua.LabelStmt(
            stmt.pos,
            stmt.name,
        )
        is Lua.LocalDeclStmt -> {
            stmt.targets.targets.forEach(::define)
            Lua.LocalDeclStmt(
                stmt.pos,
                stmt.targets,
            )
        }
        is Lua.LocalFunctionStmt -> {
            locals.add(stmt.name.id)
            Lua.LocalFunctionStmt(
                stmt.pos,
                stmt.name,
                stmt.params,
                scoped {
                    stmt.params.params.forEach {
                        if (it is Lua.Param) {
                            locals.add(it.name.id)
                        }
                    }
                    luaChunk(
                        stmt.body.pos,
                        stmt.body.body.map(::scan),
                        scan(stmt.body.last),
                    )
                },
            )
        }
        is Lua.LocalStmt -> {
            val ret = Lua.LocalStmt(
                stmt.pos,
                stmt.targets,
                Lua.Exprs(
                    stmt.exprs.pos,
                    stmt.exprs.exprs.map(::scan),
                ),
            )
            stmt.targets.targets.forEach(::define)
            ret
        }
        is Lua.SetStmt -> {
            Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.pos,
                    stmt.targets.targets.map(::set),
                ),
                Lua.Exprs(
                    stmt.exprs.pos,
                    stmt.exprs.exprs.map(::scan),
                ),
            )
        }
        is Lua.WhileStmt -> Lua.WhileStmt(
            stmt.pos,
            scan(stmt.cond),
            scoped {
                luaChunk(
                    stmt.body.pos,
                    stmt.body.body.map(::scan),
                    scan(stmt.body.last),
                )
            },
        )
    }
}
