package lang.temper.be.elixir

import lang.temper.ast.boundaryDescent
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TMap
import lang.temper.value.TMapBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid

/**
 * Turns the modules of one library into one Elixir module.
 *
 * Temper is imperative and Elixir is not, so most of this file is lowering:
 *
 * - **A local is an Elixir variable, rebound.** Straight-line code needs
 *   nothing more. An `if` that assigns variables declared outside it is an
 *   expression that returns them: `{a, b} = if t do ...; {a, b} else ...; {a, b} end`.
 * - **A `while` is an anonymous function that calls itself**, passed to
 *   itself because an Elixir `fn` cannot name itself. It takes and returns
 *   the variables the loop assigns, and the recursive call is a tail call, so
 *   the stack does not grow.
 * - **Exits are folded where they can be.** A statement list is translated
 *   with an [End]: what falling off its end means (return nil, go round the
 *   loop again, hand back the assigned variables). An `if` one of whose
 *   branches always exits takes the rest of the list into its other branch,
 *   so `return`, `break` and `continue` usually become a value or a call.
 * - **Exits that cannot be folded throw**, and the function or loop they
 *   leave catches its own tag. The loop's recursive call stays outside the
 *   `try`, or it would not be a tail call.
 *
 * Module-level variables live in `TemperCore.Global`, because a `def` sees
 * no variables but its parameters.
 *
 * Unhandled nodes are `TODO()` carrying the node: a crash at build time is a
 * work item, plausible wrong output is a bug that hides.
 */
internal class ElixirTranslator(
    private val names: ElixirNames,
    /** Every module function of the library, so a call knows it is one, and its arity. */
    private val moduleFunctions: Map<ResolvedName, Int>,
    /** Every module-level variable of the library. */
    private val moduleGlobals: Set<ResolvedName>,
) {
    private val functions = mutableListOf<Elixir.ModuleItem>()
    private val mainBody = mutableListOf<Elixir.BlockItem>()

    data class Translated(val functions: List<Elixir.ModuleItem>, val mainBody: List<Elixir.BlockItem>)

    fun translateModule(module: TmpL.Module): Translated {
        functions.clear()
        mainBody.clear()
        for (topLevel in module.topLevels) {
            processTopLevel(topLevel)
        }
        return Translated(functions.toList(), mainBody.toList())
    }

    // ── Top levels ───────────────────────────────────────────────────────

    private fun processTopLevel(topLevel: TmpL.TopLevel) {
        when (topLevel) {
            is TmpL.ModuleInitBlock -> {
                val fn = FunctionContext(returnTag = null)
                mainBody.addAll(statements(topLevel.body.statements, End.Discard, fn))
            }
            is TmpL.ModuleLevelDeclaration -> processModuleLevelDeclaration(topLevel)
            is TmpL.ModuleFunctionDeclaration -> functions.add(translateFunction(topLevel))
            is TmpL.TypeDeclaration -> TODO("type declaration: $topLevel")
            is TmpL.Test -> TODO("test: $topLevel")
            // TypeConnection, PooledValueDeclaration, SupportCodeDeclaration,
            // comments and garbage carry no Elixir output
            else -> {}
        }
    }

    private fun processModuleLevelDeclaration(decl: TmpL.ModuleLevelDeclaration) {
        if (decl.isConsole()) return
        val fn = FunctionContext(returnTag = null)
        val value = decl.init?.let { expression(it, fn) } ?: Elixir.NilLit(decl.pos)
        mainBody.add(globalPut(decl.pos, decl.name.name, value))
    }

    // ── Functions ────────────────────────────────────────────────────────

    /** Per-function state: the tag its non-local returns carry, and whether one was thrown. */
    private inner class FunctionContext(val returnTag: String?) {
        var threwReturn = false
        val loops = ArrayDeque<LoopContext>()
    }

    /** A loop or labeled block that `break` and `continue` can target. */
    private inner class LoopContext(
        val label: ResolvedName?,
        val isLoop: Boolean,
        val tag: String,
        val carried: List<ResolvedName>,
        val self: OutName?,
    ) {
        var threw = false

        /** True while translating in try mode, where exits are tagged tuples. */
        var tryMode = false
    }

    private fun translateFunction(decl: TmpL.ModuleFunctionDeclaration): Elixir.FunDef {
        val pos = decl.pos
        if (decl.parameters.restParameter != null) TODO("rest parameter: $decl")
        if (decl.parameters.thisName != null) TODO("this parameter: $decl")
        // parameters are locals too: a loop that assigns one must carry it
        decl.parameters.parameters.forEach { declare(it.name.name) }
        val params = decl.parameters.parameters.map { idOf(it.name) as Elixir.Pattern }
        val body = decl.body ?: TODO("function without a body: $decl")
        return Elixir.FunDef(
            pos,
            id = Elixir.Id(decl.name.pos, functionName(decl.name.name)),
            params = params,
            body = functionBody(pos, body.statements),
        )
    }

    /** A body whose fall-through returns nil, wrapped in a catch only if a return had to throw. */
    private fun functionBody(pos: Position, body: List<TmpL.Statement>): Elixir.Block {
        val tag = names.gensym("return").outputNameText
        val fn = FunctionContext(returnTag = tag)
        val items = statements(body, End.Return, fn)
        if (!fn.threwReturn) return Elixir.Block(pos, items)
        val valueName = names.gensym("value")
        fun value() = Elixir.Id(pos, valueName)
        return Elixir.Block(
            pos,
            listOf(
                Elixir.Try(
                    pos,
                    body = Elixir.Block(pos, items),
                    catches = listOf(
                        Elixir.Clause(
                            pos,
                            pattern = taggedPattern(pos, RETURN, tag, value()),
                            body = Elixir.Block(pos, listOf(value())),
                        ),
                    ),
                ),
            ),
        )
    }

    // ── Statement lists ──────────────────────────────────────────────────

    /** What falling off the end of a statement list means. */
    private sealed interface End {
        /** The function returns nil. */
        object Return : End

        /** Nothing: module init code, whose value nobody reads. */
        object Discard : End

        /** The list is a branch: it hands back these variables, as one value or a tuple. */
        class Yield(val vars: List<ResolvedName>) : End

        /** The list is a loop body: go round again. */
        class Again(val loop: LoopContext) : End

        /** The list is a labeled block's body: leave the block. */
        class Leave(val block: LoopContext) : End
    }

    private fun statements(list: List<TmpL.Statement>, end: End, fn: FunctionContext): List<Elixir.BlockItem> {
        val flat = flatten(list)
        val out = mutableListOf<Elixir.BlockItem>()
        var i = 0
        while (i < flat.size) {
            val statement = flat[i]
            val rest = flat.subList(i + 1, flat.size)
            when {
                statement is TmpL.ReturnStatement || statement is TmpL.BreakStatement ||
                    statement is TmpL.ContinueStatement || statement is TmpL.ThrowStatement -> {
                    out.addAll(exit(statement, end, fn))
                    return out // anything after an exit is dead
                }
                statement is TmpL.IfStatement && rest.isNotEmpty() &&
                    (alwaysExits(statement.consequent) || statement.alternate?.let(::alwaysExits) == true) -> {
                    // fold the rest of the list into the branch that falls through
                    out.add(foldedIf(statement, rest, end, fn))
                    return out
                }
                statement is TmpL.IfStatement && rest.isEmpty() && endIsTail(end) -> {
                    out.add(tailIf(statement, end, fn))
                    return out
                }
                else -> out.addAll(statement(statement, fn))
            }
            i += 1
        }
        out.addAll(fallThrough(end, flat.lastOrNull()?.pos ?: lang.temper.log.unknownPos, fn))
        return out
    }

    /** An End whose fall-through is the whole list's value, so an `if` at the end can carry it in both arms. */
    private fun endIsTail(end: End) = end !is End.Discard

    private fun flatten(list: List<TmpL.Statement>): List<TmpL.Statement> = list.flatMap {
        when (it) {
            is TmpL.BlockStatement -> flatten(it.statements)
            is TmpL.EmbeddedComment, is TmpL.BoilerplateCodeFoldBoundary -> listOf()
            else -> listOf(it)
        }
    }

    private fun fallThrough(end: End, pos: Position, fn: FunctionContext): List<Elixir.BlockItem> = when (end) {
        End.Return -> listOf(Elixir.NilLit(pos))
        End.Discard -> listOf()
        is End.Yield -> listOf(packed(pos, end.vars))
        is End.Again -> listOf(again(pos, end.loop))
        is End.Leave -> listOf(leave(pos, end.block))
    }

    /** `{a, b}`, or `a` alone, or nil when nothing is carried. */
    private fun packed(pos: Position, vars: List<ResolvedName>): Elixir.Expr = when (vars.size) {
        0 -> Elixir.NilLit(pos)
        1 -> varRef(pos, vars[0])
        else -> Elixir.TupleLit(pos, vars.map { varRef(pos, it) })
    }

    private fun packedPattern(pos: Position, vars: List<ResolvedName>): Elixir.Pattern = when (vars.size) {
        1 -> varId(pos, vars[0])
        else -> Elixir.TuplePattern(pos, vars.map { varId(pos, it) })
    }

    /** Going round a loop again: a tail call, or a tagged value in try mode. */
    private fun again(pos: Position, loop: LoopContext): Elixir.Expr = when {
        loop.tryMode -> Elixir.TupleLit(pos, listOf(Elixir.Atom(pos, NEXT), packed(pos, loop.carried)))
        else -> Elixir.AnonCall(
            pos,
            fn = Elixir.Id(pos, loop.self!!),
            args = listOf(Elixir.Id(pos, loop.self)) + loop.carried.map { varRef(pos, it) },
        )
    }

    /** Leaving a loop or block with its variables. */
    private fun leave(pos: Position, loop: LoopContext): Elixir.Expr = when {
        loop.tryMode -> Elixir.TupleLit(pos, listOf(Elixir.Atom(pos, DONE), packed(pos, loop.carried)))
        else -> packed(pos, loop.carried)
    }

    // ── Exits ────────────────────────────────────────────────────────────

    private fun exit(statement: TmpL.Statement, end: End, fn: FunctionContext): List<Elixir.BlockItem> {
        val pos = statement.pos
        return when (statement) {
            is TmpL.ThrowStatement -> listOf(raiseBubble(pos))
            is TmpL.ReturnStatement -> {
                val value = statement.expression?.let { expression(it, fn) } ?: Elixir.NilLit(pos)
                when (end) {
                    End.Return -> listOf(value)
                    else -> {
                        val tag = fn.returnTag ?: TODO("return outside a function: $statement")
                        fn.threwReturn = true
                        listOf(throwOf(pos, RETURN, tag, value))
                    }
                }
            }
            is TmpL.BreakStatement -> {
                val target = target(statement.label?.id, isContinue = false, fn, statement)
                when {
                    (end is End.Again && end.loop === target) || (end is End.Leave && end.block === target) ->
                        listOf(leave(pos, target))
                    else -> {
                        target.threw = true
                        listOf(throwOf(pos, BREAK, target.tag, packed(pos, target.carried)))
                    }
                }
            }
            is TmpL.ContinueStatement -> {
                val target = target(statement.label?.id, isContinue = true, fn, statement)
                when {
                    end is End.Again && end.loop === target -> listOf(again(pos, target))
                    else -> {
                        target.threw = true
                        listOf(throwOf(pos, CONTINUE, target.tag, packed(pos, target.carried)))
                    }
                }
            }
            else -> error("not an exit: $statement")
        }
    }

    private fun target(label: TmpL.Id?, isContinue: Boolean, fn: FunctionContext, at: TmpL.Statement): LoopContext {
        val name = label?.let { nameOf(it) }
        return fn.loops.lastOrNull { loop ->
            (name == null || loop.label == name) && (loop.isLoop || (!isContinue && name != null))
        } ?: TODO("jump with no target: $at")
    }

    private fun throwOf(pos: Position, kind: String, tag: String, payload: Elixir.Expr): Elixir.Expr =
        localCall(pos, "throw", listOf(taggedTuple(pos, kind, tag, payload)))

    private fun raiseBubble(pos: Position): Elixir.Expr =
        localCall(pos, "raise", listOf(elixirModule(pos, "TemperCore", "Bubble")))

    /** Whether control can never fall out of the end of [statement]. */
    private fun alwaysExits(statement: TmpL.Statement): Boolean = when (statement) {
        is TmpL.ReturnStatement, is TmpL.BreakStatement, is TmpL.ContinueStatement, is TmpL.ThrowStatement -> true
        is TmpL.BlockStatement -> flatten(statement.statements).lastOrNull()?.let(::alwaysExits) == true
        is TmpL.IfStatement -> alwaysExits(statement.consequent) && statement.alternate?.let(::alwaysExits) == true
        else -> false
    }

    // ── if ───────────────────────────────────────────────────────────────

    /** `if` with the rest of the list folded into whichever branch falls through. */
    private fun foldedIf(
        statement: TmpL.IfStatement,
        rest: List<TmpL.Statement>,
        end: End,
        fn: FunctionContext,
    ): Elixir.Expr {
        val pos = statement.pos
        val consequent = listOf(statement.consequent) + if (alwaysExits(statement.consequent)) listOf() else rest
        val alternate = listOfNotNull(statement.alternate) +
            if (statement.alternate?.let(::alwaysExits) == true) listOf() else rest
        val test = expression(statement.test, fn)
        return ifOf(pos, test, statements(consequent, end, fn), statements(alternate, end, fn))
    }

    /** `if` as the last statement: both arms end the way the list does. */
    private fun tailIf(statement: TmpL.IfStatement, end: End, fn: FunctionContext): Elixir.Expr =
        ifOf(
            statement.pos,
            expression(statement.test, fn),
            statements(listOf(statement.consequent), end, fn),
            statements(listOfNotNull(statement.alternate), end, fn),
        )

    private fun ifOf(
        pos: Position,
        test: Elixir.Expr,
        then: List<Elixir.BlockItem>,
        otherwise: List<Elixir.BlockItem>,
    ): Elixir.Expr = Elixir.If(
        pos,
        test = test,
        then = Elixir.Block(pos, then.ifEmpty { listOf(Elixir.NilLit(pos)) }),
        otherwise = Elixir.Block(pos, otherwise.ifEmpty { listOf(Elixir.NilLit(pos)) }),
    )

    /** An `if` in the middle of a list: it hands back whatever it assigned. */
    private fun middleIf(statement: TmpL.IfStatement, fn: FunctionContext): List<Elixir.BlockItem> {
        val pos = statement.pos
        val vars = assignedOuter(statement, fn)
        val end = End.Yield(vars)
        val value = ifOf(
            pos,
            expression(statement.test, fn),
            statements(listOf(statement.consequent), end, fn),
            statements(listOfNotNull(statement.alternate), end, fn),
        )
        return listOf(if (vars.isEmpty()) value else Elixir.Match(pos, left = packedPattern(pos, vars), right = value))
    }

    // ── Single statements ────────────────────────────────────────────────

    /** Locals visible here, innermost scope last. */
    private val scopes = ArrayDeque<MutableSet<ResolvedName>>().apply { addLast(mutableSetOf()) }

    private fun declare(name: ResolvedName) {
        scopes.last().add(name)
    }

    private fun statement(statement: TmpL.Statement, fn: FunctionContext): List<Elixir.BlockItem> {
        val pos = statement.pos
        return when (statement) {
            is TmpL.ExpressionStatement -> listOf(expression(statement.expression, fn))
            is TmpL.LocalDeclaration -> {
                val name = statement.name.name
                declare(name)
                val value = statement.init?.let { expression(it, fn) } ?: Elixir.NilLit(pos)
                listOf(Elixir.Match(pos, left = varId(pos, name), right = value))
            }
            is TmpL.Assignment -> {
                val name = statement.left.name
                val value = expression(statement.right, fn)
                listOf(
                    if (name in moduleGlobals) {
                        globalPut(pos, name, value)
                    } else {
                        Elixir.Match(pos, left = varId(pos, name), right = value)
                    },
                )
            }
            is TmpL.IfStatement -> middleIf(statement, fn)
            is TmpL.WhileStatement -> loop(statement, label = null, fn)
            is TmpL.LabeledStatement -> when (val inner = statement.statement) {
                is TmpL.WhileStatement -> loop(inner, label = statement.label.id, fn)
                else -> labeledBlock(statement, fn)
            }
            is TmpL.TryStatement -> tryOf(statement, fn)
            else -> TODO("statement: $statement")
        }
    }

    // ── Loops ────────────────────────────────────────────────────────────

    /**
     * ```
     * loop = fn loop, a, b ->
     *   if test do
     *     ...body...
     *     loop.(loop, a, b)
     *   else
     *     {a, b}
     *   end
     * end
     * {a, b} = loop.(loop, a, b)
     * ```
     */
    private fun loop(statement: TmpL.WhileStatement, label: TmpL.Id?, fn: FunctionContext): List<Elixir.BlockItem> {
        val pos = statement.pos
        val carried = assignedOuter(statement, fn)
        val selfName = names.gensym("loop")
        val context = LoopContext(
            label = label?.let { nameOf(it) },
            isLoop = true,
            tag = names.gensym("loop").outputNameText,
            carried = carried,
            self = selfName,
        )
        fun self() = Elixir.Id(pos, selfName)
        fun body(): Elixir.Expr {
            fn.loops.addLast(context)
            scopes.addLast(mutableSetOf())
            try {
                val iteration = statements(listOf(statement.body), End.Again(context), fn)
                val test = expression(statement.test, fn)
                val exitValue = packed(pos, carried)
                return if (!context.tryMode) {
                    ifOf(pos, test, iteration, listOf(exitValue))
                } else {
                    val resultName = names.gensym("step")
                    val caughtName = names.gensym("vars")
                    fun result() = Elixir.Id(pos, resultName)
                    fun caught() = Elixir.Id(pos, caughtName)
                    ifOf(
                        pos,
                        test,
                        listOf(
                            Elixir.Match(
                                pos,
                                left = result(),
                                right = Elixir.Try(
                                    pos,
                                    body = Elixir.Block(pos, iteration),
                                    catches = listOf(
                                        tagged(pos, CONTINUE, context.tag, caughtName, NEXT),
                                        tagged(pos, BREAK, context.tag, caughtName, DONE),
                                    ),
                                ),
                            ),
                            Elixir.Case(
                                pos,
                                subject = result(),
                                clauses = listOf(
                                    Elixir.Clause(
                                        pos,
                                        pattern = Elixir.TuplePattern(
                                            pos,
                                            listOf(Elixir.Atom(pos, NEXT), packedPatternOrWild(pos, carried)),
                                        ),
                                        body = Elixir.Block(
                                            pos,
                                            listOf(
                                                Elixir.AnonCall(
                                                    pos, fn = self(),
                                                    args =
                                                    listOf(self()) + carried.map { varRef(pos, it) },
                                                ),
                                            ),
                                        ),
                                    ),
                                    Elixir.Clause(
                                        pos,
                                        pattern = Elixir.TuplePattern(pos, listOf(Elixir.Atom(pos, DONE), caught())),
                                        body = Elixir.Block(pos, listOf(caught())),
                                    ),
                                ),
                            ),
                        ),
                        listOf(exitValue),
                    )
                }
            } finally {
                scopes.removeLast()
                fn.loops.removeLast()
            }
        }
        var ifExpr = body()
        if (context.threw) {
            // an exit from somewhere the folding could not reach: go again in try mode
            context.tryMode = true
            ifExpr = body()
        }
        val params = listOf<Elixir.Pattern>(self()) + carried.map { varId(pos, it) }
        val fnValue = Elixir.Fn(pos, params = params, body = Elixir.Block(pos, listOf(ifExpr)))
        val call = Elixir.AnonCall(pos, fn = self(), args = listOf(self()) + carried.map { varRef(pos, it) })
        return listOf(
            Elixir.Match(pos, left = self(), right = fnValue),
            if (carried.isEmpty()) call else Elixir.Match(pos, left = packedPattern(pos, carried), right = call),
        )
    }

    /** `{:kind, :tag, payload}` */
    private fun taggedTuple(pos: Position, kind: String, tag: String, payload: Elixir.Expr): Elixir.Expr =
        Elixir.TupleLit(pos, listOf(Elixir.Atom(pos, kind), Elixir.Atom(pos, tag), payload))

    private fun taggedPattern(pos: Position, kind: String, tag: String, payload: Elixir.Pattern): Elixir.Pattern =
        Elixir.TuplePattern(pos, listOf(Elixir.Atom(pos, kind), Elixir.Atom(pos, tag), payload))

    /** `{a, b} = value`, or just `value` when nothing is carried. */
    private fun bindVars(pos: Position, vars: List<ResolvedName>, value: Elixir.Expr): Elixir.BlockItem =
        if (vars.isEmpty()) value else Elixir.Match(pos, left = packedPattern(pos, vars), right = value)

    private fun packedPatternOrWild(pos: Position, vars: List<ResolvedName>): Elixir.Pattern =
        if (vars.isEmpty()) Elixir.Wildcard(pos) else packedPattern(pos, vars)

    /** `{:kind, :tag, vars} -> {:result, vars}` */
    private fun tagged(pos: Position, kind: String, tag: String, vars: OutName, result: String): Elixir.Clause =
        Elixir.Clause(
            pos,
            pattern = taggedPattern(pos, kind, tag, Elixir.Id(pos, vars)),
            body = Elixir.Block(
                pos,
                listOf(Elixir.TupleLit(pos, listOf(Elixir.Atom(pos, result), Elixir.Id(pos, vars)))),
            ),
        )

    /** `label: { ... break label; ... }`: a block that `break` can leave. */
    private fun labeledBlock(statement: TmpL.LabeledStatement, fn: FunctionContext): List<Elixir.BlockItem> {
        val pos = statement.pos
        val carried = assignedOuter(statement.statement, fn)
        val context = LoopContext(
            label = nameOf(statement.label.id),
            isLoop = false,
            tag = names.gensym("block").outputNameText,
            carried = carried,
            self = null,
        )
        fn.loops.addLast(context)
        scopes.addLast(mutableSetOf())
        val items = try {
            statements(listOf(statement.statement), End.Leave(context), fn)
        } finally {
            scopes.removeLast()
            fn.loops.removeLast()
        }
        val value: Elixir.Expr = if (!context.threw) {
            Elixir.If(pos, test = Elixir.BoolLit(pos, true), then = Elixir.Block(pos, items), otherwise = null)
        } else {
            val caughtName = names.gensym("vars")
            Elixir.Try(
                pos,
                body = Elixir.Block(pos, items),
                catches = listOf(
                    Elixir.Clause(
                        pos,
                        pattern = Elixir.TuplePattern(
                            pos,
                            listOf(Elixir.Atom(pos, BREAK), Elixir.Atom(pos, context.tag), Elixir.Id(pos, caughtName)),
                        ),
                        body = Elixir.Block(pos, listOf(Elixir.Id(pos, caughtName))),
                    ),
                ),
            )
        }
        return listOf(bindVars(pos, carried, value))
    }

    // ── Bubbles ──────────────────────────────────────────────────────────

    /** `{a} = try do ... rescue _ in TemperCore.Bubble -> ... end` */
    private fun tryOf(statement: TmpL.TryStatement, fn: FunctionContext): List<Elixir.BlockItem> {
        val pos = statement.pos
        val vars = assignedOuter(statement, fn)
        val end = End.Yield(vars)
        val value = Elixir.Try(
            pos,
            body = Elixir.Block(pos, statements(listOf(statement.tried), end, fn)),
            rescues = listOf(
                Elixir.Clause(
                    pos,
                    pattern = Elixir.RescueIn(
                        pos,
                        binding = Elixir.Wildcard(pos),
                        module = elixirModule(pos, "TemperCore", "Bubble"),
                    ),
                    body = Elixir.Block(pos, statements(listOf(statement.recover), end, fn)),
                ),
            ),
        )
        return listOf(if (vars.isEmpty()) value else Elixir.Match(pos, left = packedPattern(pos, vars), right = value))
    }

    /**
     * Locals declared outside [statement] that it assigns, in a stable order.
     * Lambdas are skipped: what they assign is theirs.
     */
    private fun assignedOuter(statement: TmpL.Tree, fn: FunctionContext): List<ResolvedName> {
        val visible = scopes.flatten().toSet()
        val assigned = linkedSetOf<ResolvedName>()
        statement.boundaryDescent { node ->
            when (node) {
                is TmpL.LocalFunctionDeclaration -> false
                is TmpL.Assignment -> {
                    nameOf(node.left)?.let { if (it in visible) assigned.add(it) }
                    true
                }
                else -> true
            }
        }
        return assigned.sortedBy { names.outName(it).outputNameText }
    }

    // ── Expressions ──────────────────────────────────────────────────────

    private fun expression(expression: TmpL.Expression, fn: FunctionContext): Elixir.Expr = when (expression) {
        is TmpL.ValueReference -> translateValueReference(expression)
        is TmpL.CallExpression -> call(expression, fn)
        is TmpL.Reference -> reference(expression)
        is TmpL.InfixOperation -> infix(expression, fn)
        is TmpL.PrefixOperation -> when (expression.op.tmpLOperator) {
            TmpLOperator.Bang -> prefixOp(expression.pos, ElixirOperator.Not, expression(expression.operand, fn))
        }
        else -> TODO("expression: $expression")
    }

    private fun reference(expression: TmpL.Reference): Elixir.Expr {
        val name = expression.id.name
        return when (name) {
            in moduleGlobals -> globalGet(expression.pos, name)
            in moduleFunctions -> capture(expression.pos, name)
            else -> varRef(expression.pos, name)
        }
    }

    private fun fnValue(expression: TmpL.FnReference): Elixir.Expr {
        val name = expression.id.name
        return when (name) {
            in moduleFunctions -> capture(expression.pos, name)
            in moduleGlobals -> globalGet(expression.pos, name)
            else -> varRef(expression.pos, name)
        }
    }

    private fun capture(pos: Position, name: ResolvedName): Elixir.Expr =
        Elixir.Capture(
            pos,
            fn = Elixir.Id(pos, functionName(name)),
            arity = Elixir.NumberLit(pos, moduleFunctions.getValue(name)),
        )

    private fun infix(expression: TmpL.InfixOperation, fn: FunctionContext): Elixir.Expr {
        val pos = expression.pos
        val operator = when (expression.op.tmpLOperator) {
            // Elixir's `and` and `or` short-circuit and take only booleans,
            // which is exactly what Temper's && and || are
            TmpLOperator.AmpAmp -> ElixirOperator.And
            TmpLOperator.BarBar -> ElixirOperator.Or
            TmpLOperator.EqEqInt -> ElixirOperator.Equals
            TmpLOperator.GeInt -> ElixirOperator.GreaterEquals
            TmpLOperator.GtInt -> ElixirOperator.GreaterThan
            TmpLOperator.LeInt -> ElixirOperator.LessEquals
            TmpLOperator.LtInt -> ElixirOperator.LessThan
            // the frontend's own index arithmetic, which cannot overflow;
            // Temper's wrapping Int addition arrives as support code
            TmpLOperator.PlusInt -> ElixirOperator.Addition
        }
        return infixOp(pos, expression(expression.left, fn), operator, expression(expression.right, fn))
    }

    private fun call(call: TmpL.CallExpression, fn: FunctionContext): Elixir.Expr {
        val args = call.parameters.map { actual ->
            when (actual) {
                is TmpL.Expression -> expression(actual, fn)
                else -> TODO("actual: $actual")
            }
        }
        return when (val callee = call.fn) {
            is TmpL.InlineSupportCodeWrapper ->
                (callee.supportCode as ElixirInlineSupportCode).callFactory(call.pos, args)
            is TmpL.FnReference -> {
                val name = callee.id.name
                when (name) {
                    in moduleFunctions ->
                        Elixir.Call(call.pos, callee = Elixir.Id(callee.pos, functionName(name)), args = args)
                    in moduleGlobals -> Elixir.AnonCall(call.pos, fn = globalGet(callee.pos, name), args = args)
                    else -> Elixir.AnonCall(call.pos, fn = varRef(callee.pos, name), args = args)
                }
            }
            else -> TODO("callable: $callee")
        }
    }

    // ── Names ────────────────────────────────────────────────────────────

    private fun nameOf(id: TmpL.Id): ResolvedName? = runCatching { id.name }.getOrNull()

    private fun idOf(id: TmpL.Id): Elixir.Id = Elixir.Id(id.pos, names.outName(id.name))

    private fun varId(pos: Position, name: ResolvedName): Elixir.Id = Elixir.Id(pos, names.outName(name))

    private fun varRef(pos: Position, name: ResolvedName): Elixir.Expr = varId(pos, name)

    private fun functionName(name: ResolvedName): OutName = names.outName(name)

    private fun globalAtom(pos: Position, name: ResolvedName) = Elixir.Atom(pos, names.outName(name).outputNameText)

    private fun globalGet(pos: Position, name: ResolvedName): Elixir.Expr =
        coreGlobal(pos, "get", listOf(globalAtom(pos, name)))

    private fun globalPut(pos: Position, name: ResolvedName, value: Elixir.Expr): Elixir.Expr =
        coreGlobal(pos, "put", listOf(globalAtom(pos, name), value))

    private fun coreGlobal(pos: Position, fn: String, args: List<Elixir.Expr>): Elixir.Expr =
        remoteCall(pos, elixirModule(pos, "TemperCore", "Global"), fn, args)

    // ── Literals ─────────────────────────────────────────────────────────

    private fun translateValueReference(expression: TmpL.ValueReference): Elixir.Expr {
        val pos = expression.pos
        return when (val tag = expression.value.typeTag) {
            TBoolean -> Elixir.BoolLit(pos, TBoolean.unpack(expression.value))
            TFloat64 -> Elixir.NumberLit(pos, TFloat64.unpack(expression.value))
            TInt -> Elixir.NumberLit(pos, TInt.unpack(expression.value))
            TInt64 -> Elixir.NumberLit(pos, TInt64.unpack(expression.value))
            is TString -> Elixir.StringLit(pos, TString.unpack(expression.value))
            // RepresentationOfVoid.ReifyVoid: a void value really flows, and nil is it
            TNull, TVoid -> Elixir.NilLit(pos)
            is TClass, TClosureRecord, TFunction, TList, TListBuilder, TMap, TMapBuilder,
            TProblem, TStageRange, TSymbol, TType,
            -> TODO("value of type $tag: $expression")
        }
    }

    private companion object {
        const val RETURN = "temper_return"
        const val BREAK = "temper_break"
        const val CONTINUE = "temper_continue"
        const val NEXT = "temper_next"
        const val DONE = "temper_done"
    }
}
