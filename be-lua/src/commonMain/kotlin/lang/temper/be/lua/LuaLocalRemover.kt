package lang.temper.be.lua

// Could go up to 200 in theory, but smaller numbers allow for larger closures.
const val MAX_ALLOWABLE_LOCALS = 128

// When wrapping is needed, keep first N locals as real locals for performance.
// The rest overflow into the env table. Budget: LOCALS_TO_KEEP + 1 (env table) < MAX_ALLOWABLE_LOCALS.
private const val LOCALS_TO_KEEP = 100

class LuaLocalRemover {
    private var locals = mutableMapOf<LuaName, LuaName>()
    private var luaName = LuaName("_G")
    private var numLocalTables = 0
    private var needsEnvTable = false
    private var localsKept = 0
    private var localsToDecl = mutableListOf<LuaName>()

    private fun allocLocalTableName(): LuaName = LuaName("env_t${++numLocalTables}")

    private fun scoped(cb: () -> Lua.Chunk): Lua.Chunk {
        val lastLocals = locals.toMutableMap()
        val lastLuaName = luaName
        val lastNeedsEnvTable = needsEnvTable
        val lastLocalsKept = localsKept
        val lastLocalsToDecl = localsToDecl
        localsToDecl = mutableListOf()
        val ret = cb()
        locals = lastLocals
        luaName = lastLuaName
        needsEnvTable = lastNeedsEnvTable
        localsKept = lastLocalsKept
        val localsToWrap = localsToDecl
        localsToDecl = lastLocalsToDecl
        return luaChunk(
            ret.pos,
            buildList {
                if (localsToWrap.size != 0) {
                    add(
                        Lua.LocalDeclStmt(
                            ret.pos,
                            Lua.SetTargets(
                                ret.pos,
                                localsToWrap.map {
                                    Lua.NameSetTarget(
                                        ret.pos,
                                        Lua.Name(ret.pos, it),
                                    )
                                },
                            ),
                        ),
                    )
                }

                addAll(ret.body)
            },
            ret.last,
        )
    }

    private fun define(target: Lua.SetTarget, asEnvField: Boolean) {
        when (target) {
            is Lua.DotSetTarget -> TODO()
            is Lua.IndexSetTarget -> TODO()
            is Lua.NameSetTarget -> {
                if (asEnvField) {
                    locals[target.target.id] = luaName
                } else {
                    locals.remove(target.target.id)
                }
            }
        }
    }

    // Decide whether a batch of N new locals should overflow to the env table.
    private fun shouldOverflow(count: Int): Boolean =
        needsEnvTable && localsKept + count > LOCALS_TO_KEEP

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
        is Lua.NameSetTarget -> when (val name = locals[target.target.id]) {
            null -> Lua.NameSetTarget(
                target.pos,
                Lua.Name(target.target.pos, target.target.id),
            )
            else -> Lua.DotSetTarget(
                target.pos,
                Lua.NameSetTarget(
                    target.target.pos,
                    Lua.Name(target.target.pos, name),
                ),
                Lua.Name(target.target.pos, target.target.id),
            )
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
                        locals.remove(it.name.id)
                    }
                }
                luaChunk(
                    expr.body.pos,
                    buildList {
                        addAll(wrapLocals(expr.body))
                        addAll(expr.body.body.mapNotNull(::scan))
                    },
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
        is Lua.Name -> when (val name = locals[expr.id]) {
            null -> Lua.Name(expr.pos, expr.id)
            else -> Lua.DotIndexExpr(
                expr.pos,
                Lua.Name(expr.pos, name),
                Lua.Name(expr.pos, expr.id),
            )
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

    private fun wrapLocals(chunk: Lua.Chunk): List<Lua.Stmt> {
        luaName = allocLocalTableName()
        val numLocals = countLocalsDirectlyIn(chunk)
        needsEnvTable = numLocals >= MAX_ALLOWABLE_LOCALS
        if (needsEnvTable) {
            localsKept = 0
            return listOf(
                Lua.LocalStmt(
                    chunk.pos,
                    Lua.SetTargets(
                        chunk.pos,
                        listOf(
                            Lua.NameSetTarget(
                                chunk.pos,
                                Lua.Name(chunk.pos, luaName),
                            ),
                        ),
                    ),
                    Lua.Exprs(
                        chunk.pos,
                        listOf(
                            Lua.TableExpr(chunk.pos, listOf()),
                        ),
                    ),
                ),
            )
        } else {
            return listOf()
        }
    }

    private fun scan(stmt: Lua.LastStmt?): Lua.LastStmt? = when (stmt) {
        null -> null
        is Lua.ReturnStmt -> Lua.ReturnStmt(
            stmt.pos,
            Lua.Exprs(stmt.exprs.pos, stmt.exprs.exprs.map(::scan)),
        )
        is Lua.BreakStmt -> Lua.BreakStmt(
            stmt.pos,
        )
        is Lua.GotoStmt -> Lua.GotoStmt(
            stmt.pos,
            Lua.Name(stmt.name.pos, stmt.name.id),
        )
    }

    internal fun scan(stmt: Lua.Stmt): Lua.Stmt? = when (stmt) {
        is Lua.Connected -> stmt.deepCopy() // TODO Anything we can/should do here?
        is Lua.GotoStmt -> Lua.GotoStmt(
            stmt.pos,
            Lua.Name(stmt.name.pos, stmt.name.id),
        )
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
                    buildList {
                        addAll(wrapLocals(stmt.body))
                        addAll(stmt.body.body.mapNotNull(::scan))
                    },
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
                        locals.remove(it.name.id)
                    }
                }
                luaChunk(
                    stmt.body.pos,
                    buildList {
                        addAll(wrapLocals(stmt.body))
                        addAll(stmt.body.body.mapNotNull(::scan))
                    },
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
                    stmt.then.body.mapNotNull(::scan),
                    scan(stmt.then.last),
                )
            },
            stmt.elseIfs.map {
                Lua.ElseIf(
                    it.pos,
                    scan(it.cond),
                    luaChunk(
                        it.then.pos,
                        it.then.body.mapNotNull(::scan),
                        scan(it.then.last),
                    ),
                )
            },
            when (val els = stmt.els) {
                is Lua.Else -> Lua.Else(
                    els.pos,
                    scoped {
                        luaChunk(
                            els.pos,
                            els.then.body.mapNotNull(::scan),
                            scan(els.then.last),
                        )
                    },
                )
                else -> null
            },
        )
        is Lua.LabelStmt -> Lua.LabelStmt(
            stmt.pos,
            Lua.Name(stmt.name.pos, stmt.name.id),
        )
        is Lua.LocalDeclStmt -> {
            val overflow = shouldOverflow(stmt.targets.targets.size)
            stmt.targets.targets.forEach { define(it, asEnvField = overflow) }
            if (!overflow) {
                if (needsEnvTable) localsKept += stmt.targets.targets.size
                stmt.targets.targets.forEach {
                    localsToDecl.add((it as Lua.NameSetTarget).target.id)
                }
            }
            null
        }
        is Lua.LocalFunctionStmt -> if (shouldOverflow(1)) {
            locals[stmt.name.id] = luaName
            Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.name.pos,
                    listOf(
                        Lua.DotSetTarget(
                            stmt.name.pos,
                            Lua.NameSetTarget(stmt.name.pos, Lua.Name(stmt.name.pos, luaName)),
                            Lua.Name(stmt.name.pos, stmt.name.id),
                        ),
                    ),
                ),
                Lua.Exprs(
                    stmt.body.pos,
                    listOf(
                        Lua.FunctionExpr(
                            stmt.body.pos,
                            stmt.params,
                            scoped {
                                stmt.params.params.forEach {
                                    if (it is Lua.Param) {
                                        locals.remove(it.name.id)
                                    }
                                }
                                luaChunk(
                                    stmt.body.pos,
                                    buildList {
                                        addAll(wrapLocals(stmt.body))
                                        addAll(stmt.body.body.mapNotNull(::scan))
                                    },
                                    scan(stmt.body.last),
                                )
                            },
                        ),
                    ),
                ),
            )
        } else {
            if (needsEnvTable) localsKept++
            localsToDecl.add(stmt.name.id)
            Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.name.pos,
                    listOf(
                        Lua.NameSetTarget(
                            stmt.name.pos,
                            Lua.Name(stmt.name.pos, stmt.name.id),
                        ),
                    ),
                ),
                Lua.Exprs(
                    stmt.pos,
                    listOf(
                        Lua.FunctionExpr(
                            stmt.pos,
                            stmt.params,
                            scoped {
                                stmt.params.params.forEach {
                                    if (it is Lua.Param) {
                                        locals.remove(it.name.id)
                                    }
                                }
                                luaChunk(
                                    stmt.body.pos,
                                    stmt.body.body.mapNotNull(::scan),
                                    scan(stmt.body.last),
                                )
                            },
                        ),
                    ),
                ),
            )
        }
        is Lua.LocalStmt -> if (shouldOverflow(stmt.targets.targets.size)) {
            val ret = Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.pos,
                    stmt.targets.targets.map { setTarget ->
                        Lua.DotSetTarget(
                            setTarget.pos,
                            Lua.NameSetTarget(
                                setTarget.pos,
                                Lua.Name(setTarget.pos, luaName),
                            ),
                            Lua.Name(setTarget.pos, (setTarget as Lua.NameSetTarget).target.id),
                        )
                    },
                ),
                Lua.Exprs(
                    stmt.exprs.pos,
                    stmt.exprs.exprs.map(::scan),
                ),
            )
            stmt.targets.targets.forEach { define(it, asEnvField = true) }
            ret
        } else {
            if (needsEnvTable) localsKept += stmt.targets.targets.size
            val ret = Lua.SetStmt(
                stmt.pos,
                Lua.SetTargets(
                    stmt.pos,
                    stmt.targets.targets.map {
                        val name = it as Lua.NameSetTarget
                        localsToDecl.add(name.target.id)
                        set(name)
                    },
                ),
                Lua.Exprs(
                    stmt.exprs.pos,
                    stmt.exprs.exprs.map(::scan),
                ),
            )
            stmt.targets.targets.forEach { define(it, asEnvField = false) }
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
                    stmt.exprs.exprs.mapNotNull(::scan),
                ),
            )
        }
        is Lua.WhileStmt -> Lua.WhileStmt(
            stmt.pos,
            scan(stmt.cond),
            scoped {
                luaChunk(
                    stmt.body.pos,
                    stmt.body.body.mapNotNull(::scan),
                    scan(stmt.body.last),
                )
            },
        )
    }
}
