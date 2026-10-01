package lang.temper.be.elixir

import lang.temper.ast.boundaryDescent
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.parameterDefaultStatementsInfo
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.name.DashedIdentifier
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
/** A function or value that another Temper library exports, and the module that holds it. */
internal sealed interface External {
    val module: List<String>
}

/** `Temper.Std.parseJson/1`. */
internal data class ExternalFunction(override val module: List<String>, val arity: Int) : External

/** A module-level value, in `TemperCore.Global` under its library's key. */
internal data class ExternalValue(override val module: List<String>) : External

internal class ElixirTranslator(
    private val names: ElixirNames,
    /** This library's root module, `Temper.Std` for std: its functions, and its classes' parent. */
    private val root: List<String>,
    /** What other libraries export to this one, by the name its own library declared. */
    private val externals: Map<ResolvedName, External>,
    /** The library each other translated library's root directory holds. */
    private val libraryRoots: Map<FilePath, DashedIdentifier>,
    /** Every module function of the library, so a call knows it is one, and its arity. */
    private val moduleFunctions: Map<ResolvedName, Int>,
    /** Every module-level variable of the library. */
    private val moduleGlobals: Set<ResolvedName>,
    /** Every class and interface of the library, by its source name. */
    private val types: Map<String, TmpL.TypeDeclaration>,
    /** An imported name, mapped to the exporting module's own name for the same thing. */
    private val imports: Map<ResolvedName, ResolvedName>,
    /** Translating Temper's standard library, whose @connected functions are support code. */
    private val isStdLib: Boolean = false,
) {
    /** The name a value was declared under, seen through any imports. */
    private fun canonical(name: ResolvedName): ResolvedName {
        var current = name
        repeat(imports.size + 1) { current = imports[current] ?: return current }
        return current
    }

    private val functions = mutableListOf<Elixir.ModuleItem>()
    private val mainBody = mutableListOf<Elixir.BlockItem>()
    private val modules = mutableListOf<Elixir.ModuleDef>()

    data class Translated(
        val functions: List<Elixir.ModuleItem>,
        val mainBody: List<Elixir.BlockItem>,
        val modules: List<Elixir.ModuleDef>,
        /** Each test's function name, which is also the name the JUnit report gives it. */
        val tests: List<String>,
    )

    private val tests = mutableListOf<String>()

    /**
     * Locals a closure reads that are also assigned somewhere. Elixir closures
     * capture values and Temper's capture variables, so these live in a heap
     * cell that the closure and its enclosing function share.
     */
    private val boxed = mutableSetOf<ResolvedName>()

    /** A local function that calls itself: the self-passing name its body calls through, and its arity. */
    private val recursiveLocals = mutableMapOf<ResolvedName, Pair<OutName, Int>>()

    private val localFunctions = mutableSetOf<ResolvedName>()

    private fun collectBoxed(module: TmpL.Module) {
        val assigned = mutableSetOf<ResolvedName>()
        val captured = mutableSetOf<ResolvedName>()
        for (topLevel in module.topLevels) {
            topLevel.boundaryDescent { node ->
                when (node) {
                    is TmpL.Assignment -> nameOf(node.left)?.let(assigned::add)
                    is TmpL.LocalFunctionDeclaration -> {
                        localFunctions.add(node.name.name)
                        val declaredInside = mutableSetOf<ResolvedName>()
                        val usedInside = mutableSetOf<ResolvedName>()
                        node.parameters.parameters.forEach { f -> nameOf(f.name)?.let(declaredInside::add) }
                        node.parameters.restParameter?.let { r -> nameOf(r.name)?.let(declaredInside::add) }
                        node.body.boundaryDescent { inner ->
                            when (inner) {
                                is TmpL.LocalDeclaration -> nameOf(inner.name)?.let(declaredInside::add)
                                is TmpL.LocalFunctionDeclaration -> {
                                    nameOf(inner.name)?.let(declaredInside::add)
                                    inner.parameters.parameters.forEach { f ->
                                        nameOf(f.name)?.let(declaredInside::add)
                                    }
                                }
                                is TmpL.Reference -> nameOf(inner.id)?.let(usedInside::add)
                                is TmpL.FnReference -> nameOf(inner.id)?.let(usedInside::add)
                                is TmpL.Assignment -> nameOf(inner.left)?.let(usedInside::add)
                                else -> {}
                            }
                            true
                        }
                        captured.addAll(usedInside - declaredInside)
                    }
                    else -> {}
                }
                true
            }
        }
        boxed.addAll((captured intersect assigned) - moduleGlobals)
        // a local function another closure calls lives in a cell made at the
        // top of its block, so a closure defined earlier can call one defined
        // later: putter calling walker, walker calling putter
        boxed.addAll(captured intersect localFunctions)
    }

    fun translateModule(module: TmpL.Module): Translated {
        functions.clear()
        mainBody.clear()
        modules.clear()
        tests.clear()
        collectBoxed(module)
        for (topLevel in module.topLevels) {
            processTopLevel(topLevel)
        }
        return Translated(functions.toList(), mainBody.toList(), modules.toList(), tests.toList())
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
            is TmpL.TypeDeclaration -> modules.add(translateType(topLevel))
            is TmpL.Test -> functions.add(translateTest(topLevel))
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
    private inner class FunctionContext(
        val returnTag: String?,
        /** The class whose method or constructor this is, if any. */
        val cls: ClassContext? = null,
    ) {
        var threwReturn = false
        val loops = ArrayDeque<LoopContext>()

        /** What `return;` and falling off the end give: nil, or a constructor's `this`. */
        fun returnValue(pos: Position): Elixir.Expr = when {
            cls?.isConstructor == true -> varRef(pos, cls.thisName!!)
            else -> Elixir.NilLit(pos)
        }
    }

    /** The class a method belongs to, and how its objects keep their fields. */
    private class ClassContext(
        val module: List<String>,
        val isStruct: Boolean,
        val thisName: ResolvedName?,
        val isConstructor: Boolean,
    )

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
        if (decl.parameters.thisName != null) TODO("this parameter: $decl")
        // parameters are locals too: a loop that assigns one must carry it;
        // a rest parameter arrives as one list
        val formals = decl.parameters.parameters.map { it.name } + listOfNotNull(decl.parameters.restParameter?.name)
        formals.forEach { declare(it.name) }
        val params = formals.map { idOf(it) as Elixir.Pattern }
        val body = decl.body ?: TODO("function without a body: $decl")
        if (decl.metadata.any { it.key.symbol == lang.temper.value.connectedSymbol } && !isStdLib) {
            return Elixir.FunDef(
                pos,
                id = Elixir.Id(decl.name.pos, functionName(decl.name.name)),
                params = params,
                body = connectedBody(decl),
            )
        }
        return Elixir.FunDef(
            pos,
            id = Elixir.Id(decl.name.pos, functionName(decl.name.name)),
            params = params,
            body = functionBody(pos, body.statements, prelude = boxParams(pos, formals.map { it.name })),
        )
    }

    /**
     * A `@test` is a function of one argument, the `Test` that collects its
     * asserts. Its name is the one the JUnit report carries; the harness strips
     * the `__12` suffix and turns camel case back into the test's sentence.
     */
    private fun translateTest(test: TmpL.Test): Elixir.FunDef {
        val pos = test.pos
        val formals = test.parameters.parameters.map { it.name }
        formals.forEach { declare(it.name) }
        val name = functionName(test.name.name).outputNameText
        tests.add(name)
        return Elixir.FunDef(
            pos,
            id = Elixir.Id(pos, OutName(name, null)),
            params = formals.map { idOf(it) },
            body = functionBody(pos, test.body.statements, prelude = boxParams(pos, formals.map { it.name })),
        )
    }

    /**
     * A user `@connected` function: Temper's own parameter defaulting, then a
     * call to the library's `_connected.ex`, which defines `TemperConnected`.
     * The frontend gives such a function a body that only panics.
     */
    private fun connectedBody(decl: TmpL.ModuleFunctionDeclaration): Elixir.Block {
        val pos = decl.pos
        val defaulting = decl.parameterDefaultStatementsInfo()
        val fn = FunctionContext(returnTag = null)
        val items = defaulting.defaultStatements.flatMap { statement(it, fn) }
        val args = decl.parameters.parameters.map { formal ->
            val name = defaulting.parameterMapping[formal.name.name] ?: formal.name.name
            varRef(pos, name)
        }
        val baseName = (decl.name.name as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText
            ?: functionName(decl.name.name).outputNameText
        // qualified, so a Kernel name like `length` needs no trailing underscore
        val fnName = if (Regex("^[a-z_][a-zA-Z0-9_]*$").matches(baseName)) baseName else names.sanitize(baseName)
        return Elixir.Block(pos, items + remoteCall(pos, elixirModule(pos, CONNECTED_MODULE), fnName, args))
    }

    /** A body whose fall-through returns nil, wrapped in a catch only if a return had to throw. */
    private fun functionBody(
        pos: Position,
        body: List<TmpL.Statement>,
        cls: ClassContext? = null,
        prelude: List<Elixir.BlockItem> = listOf(),
    ): Elixir.Block {
        val tag = names.gensym("return").outputNameText
        val fn = FunctionContext(returnTag = tag, cls = cls)
        val items = prelude + statements(body, End.Return, fn)
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
        for (decl in flat.filterIsInstance<TmpL.LocalFunctionDeclaration>()) {
            if (decl.name.name in boxed) {
                declare(decl.name.name)
                val empty = cell(decl.pos, Elixir.NilLit(decl.pos))
                out.add(Elixir.Match(decl.pos, left = varId(decl.pos, decl.name.name), right = empty))
            }
        }
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
        End.Return -> listOf(fn.returnValue(pos))
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
                val value = when {
                    fn.cls?.isConstructor == true -> fn.returnValue(pos)
                    else -> statement.expression?.let { expression(it, fn) } ?: Elixir.NilLit(pos)
                }
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
                listOf(
                    Elixir.Match(
                        pos, left = varId(pos, name),
                        right = if (name in
                            boxed
                        ) {
                            cell(pos, value)
                        } else {
                            value
                        },
                    ),
                )
            }
            is TmpL.Assignment -> {
                val name = canonical(statement.left.name)
                val value = expression(statement.right, fn)
                listOf(
                    if (name in moduleGlobals) {
                        globalPut(pos, name, value)
                    } else if (name in boxed) {
                        heapCall(pos, "put", listOf(varRef(pos, name), Elixir.Atom(pos, CELL), value))
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
            is TmpL.SetBackedProperty -> listOf(setBacked(statement, fn))
            is TmpL.LocalFunctionDeclaration -> localFunction(statement, fn)
            is TmpL.SetAbstractProperty -> {
                val subject = statement.left.subject as? TmpL.Expression ?: TODO("setter subject: $statement")
                listOf(
                    coreCall(
                        pos,
                        "call",
                        listOf(
                            expression(subject, fn),
                            Elixir.Atom(pos, setterName(propertyText(statement.left.property))),
                            Elixir.ListLit(pos, listOf(expression(statement.right, fn))),
                        ),
                    ),
                )
            }
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
                    nameOf(node.left)?.let { if (it in visible && it !in boxed) assigned.add(it) }
                    true
                }
                // a struct's constructor rebinds `this` for every field it sets
                is TmpL.SetBackedProperty -> {
                    val cls = fn.cls
                    if (cls != null && cls.isStruct && node.left.subject is TmpL.This) {
                        cls.thisName?.let { if (it in visible) assigned.add(it) }
                    }
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
        is TmpL.This -> varRef(expression.pos, expression.id.name)
        is TmpL.GetBackedProperty -> getBacked(expression, fn)
        is TmpL.GetAbstractProperty -> getAbstract(expression, fn)
        is TmpL.InstanceOfExpression ->
            instanceOf(expression.pos, expression(expression.expr, fn), expression.checkedType)
        is TmpL.CastExpression -> cast(expression, fn)
        is TmpL.UncheckedNotNullExpression -> expression(expression.expression, fn)
        is TmpL.FunInterfaceExpression -> when (val callable = expression.callable) {
            is TmpL.FnReference -> {
                val name = canonical(callable.id.name)
                when (name) {
                    in externals -> externalReference(expression.pos, name)
                    in moduleFunctions -> capture(expression.pos, name)
                    in moduleGlobals -> globalGet(expression.pos, name)
                    else -> varRef(expression.pos, name)
                }
            }
            else -> TODO("function value: $callable")
        }
        is TmpL.PrefixOperation -> when (expression.op.tmpLOperator) {
            TmpLOperator.Bang -> prefixOp(expression.pos, ElixirOperator.Not, expression(expression.operand, fn))
        }
        else -> TODO("expression: $expression")
    }

    private fun reference(expression: TmpL.Reference): Elixir.Expr {
        val name = canonical(expression.id.name)
        return when (name) {
            in externals -> externalReference(expression.pos, name)
            in moduleGlobals -> globalGet(expression.pos, name)
            in moduleFunctions -> capture(expression.pos, name)
            in boxed -> cellGet(expression.pos, name)
            else -> varRef(expression.pos, name)
        }
    }

    /** `&Temper.Lib.name/2` */
    private fun capture(pos: Position, name: ResolvedName): Elixir.Expr =
        capture(pos, root, name, moduleFunctions.getValue(name))

    private fun capture(pos: Position, module: List<String>, name: ResolvedName, arity: Int): Elixir.Expr =
        Elixir.Capture(
            pos,
            fn = Elixir.Field(pos, obj = moduleOf(pos, module), id = Elixir.Id(pos, functionName(name))),
            arity = Elixir.NumberLit(pos, arity),
        )

    /** Another library's function as a value, or its module-level value. */
    private fun externalReference(pos: Position, name: ResolvedName): Elixir.Expr =
        when (val external = externals.getValue(name)) {
            is ExternalFunction -> capture(pos, external.module, name, external.arity)
            is ExternalValue -> globalGet(pos, name)
        }

    private fun mainModule(pos: Position) = moduleOf(pos, root)

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
        val pos = call.pos
        val given = call.parameters.map { actual ->
            when (actual) {
                is TmpL.Expression -> expression(actual, fn)
                else -> TODO("actual: $actual")
            }
        }
        return when (val callee = call.fn) {
            is TmpL.InlineSupportCodeWrapper ->
                (callee.supportCode as ElixirInlineSupportCode).callFactory(pos, given)
            is TmpL.FnReference -> {
                val name = canonical(callee.id.name)
                val args = arguments(pos, callee.type, given)
                when (name) {
                    // qualified, so it works from inside a class module too, and
                    // never meets a Kernel import of the same name
                    in moduleFunctions -> remoteCall(pos, mainModule(pos), functionName(name).outputNameText, args)
                    in externals -> when (val external = externals.getValue(name)) {
                        is ExternalFunction ->
                            remoteCall(pos, moduleOf(pos, external.module), functionName(name).outputNameText, args)
                        is ExternalValue -> Elixir.AnonCall(pos, fn = globalGet(callee.pos, name), args = args)
                    }
                    in moduleGlobals -> Elixir.AnonCall(pos, fn = globalGet(callee.pos, name), args = args)
                    in recursiveLocals -> {
                        val self = recursiveLocals.getValue(name).first
                        Elixir.AnonCall(pos, fn = Elixir.Id(pos, self), args = listOf(Elixir.Id(pos, self)) + args)
                    }
                    in boxed -> Elixir.AnonCall(
                        pos,
                        fn = heapCall(pos, "get", listOf(varRef(callee.pos, name), Elixir.Atom(pos, CELL))),
                        args = args,
                    )
                    else -> Elixir.AnonCall(pos, fn = varRef(callee.pos, name), args = args)
                }
            }
            is TmpL.ConstructorReference -> remoteCall(
                pos,
                moduleOf(pos, typeNameModule(callee.typeName)),
                CONSTRUCTOR,
                arguments(pos, callee.type, given),
            )
            is TmpL.MethodReference -> {
                val args = arguments(pos, callee.type, given)
                val method = names.sanitize(callee.methodName.dotNameText)
                when (val subject = callee.subject) {
                    is TmpL.TypeSubject -> remoteCall(pos, moduleOf(pos, typeSubjectModule(subject)), method, args)
                    is TmpL.Expression -> {
                        // a method on a builtin type that has no support code would
                        // only fail at run time in TemperCore.call; fail here instead
                        val owner = (callee.method?.enclosingType?.name as? lang.temper.name.ResolvedParsedName)
                            ?.baseName?.nameText
                        if (owner != null && owner !in types && !isExternal(callee.method?.enclosingType?.name)) {
                            val builtin = builtinMethods["$owner.${callee.methodName.dotNameText}"]
                                ?: TODO("method $owner.${callee.methodName.dotNameText} has no Elixir support code")
                            return remoteCall(
                                pos, elixirModule(pos, "TemperCore", builtin.first), builtin.second,
                                listOf(expression(subject, fn)) + args,
                            )
                        }
                        coreCall(
                            pos,
                            "call",
                            listOf(expression(subject, fn), Elixir.Atom(pos, method), Elixir.ListLit(pos, args)),
                        )
                    }
                }
            }
            is TmpL.FunInterfaceCallable ->
                Elixir.AnonCall(pos, fn = expression(callee.expr, fn), args = given)
            else -> TODO("callable: $callee")
        }
    }

    /**
     * The arguments a call passes, from those it was given: omitted optional
     * ones become nil (the body tests for null itself), and anything past the
     * fixed parameters is packed into the rest parameter's list.
     */
    private fun arguments(
        pos: Position,
        sig: lang.temper.type2.Signature2,
        given: List<Elixir.Expr>,
    ): List<Elixir.Expr> {
        val fixed = sig.requiredInputTypes.size - (if (sig.hasThisFormal) 1 else 0) + sig.optionalInputTypes.size
        val padded = if (given.size < fixed) given + List(fixed - given.size) { Elixir.NilLit(pos) } else given
        return when (sig.restInputsType) {
            null -> padded
            else -> padded.take(fixed) + Elixir.ListLit(pos, padded.drop(fixed))
        }
    }

    // ── Closures ─────────────────────────────────────────────────────────

    private fun cellGet(pos: Position, name: ResolvedName): Elixir.Expr =
        heapCall(pos, "get", listOf(varRef(pos, name), Elixir.Atom(pos, CELL)))

    /** A parameter a closure shares goes into a cell before the body runs. */
    private fun boxParams(pos: Position, params: List<ResolvedName>): List<Elixir.BlockItem> =
        params.filter { it in boxed }.map { p ->
            Elixir.Match(pos, left = varId(pos, p), right = cell(pos, varRef(pos, p)))
        }

    /** `TemperCore.Heap.new(:cell, %{v: value})`: a variable a closure shares. */
    private fun cell(pos: Position, value: Elixir.Expr): Elixir.Expr =
        heapCall(
            pos,
            "new",
            listOf(
                Elixir.Atom(pos, CELL_CLASS),
                Elixir.MapLit(pos, listOf(Elixir.MapEntry(pos, Elixir.Atom(pos, CELL), value))),
            ),
        )

    /**
     * `name = fn a, b -> ... end`.
     *
     * One that calls itself cannot say so, because an Elixir `fn` has no name
     * to call. It is written to take itself as a first argument, the way a
     * loop is, and `name` is a wrapper that passes it in.
     */
    private fun localFunction(decl: TmpL.LocalFunctionDeclaration, outer: FunctionContext): List<Elixir.BlockItem> {
        val pos = decl.pos
        val name = decl.name.name
        declare(name)
        val formals = decl.parameters.parameters.map { it.name } + listOfNotNull(decl.parameters.restParameter?.name)
        formals.forEach { declare(it.name) }
        var recursive = false
        decl.body.boundaryDescent { node ->
            if ((node is TmpL.FnReference && nameOf(node.id) == name) ||
                (node is TmpL.Reference && nameOf(node.id) == name)
            ) {
                recursive = true
            }
            !recursive
        }
        val params = formals.map { idOf(it) as Elixir.Pattern }
        val cls = outer.cls?.let { ClassContext(it.module, it.isStruct, it.thisName, isConstructor = false) }
        if (name in boxed) {
            // the cell was made at the top of the block; calls, its own
            // included, read the function out of it
            val boxedPrelude = formals.map { it.name }.filter { it in boxed }.map { p ->
                Elixir.Match(pos, left = varId(pos, p), right = cell(pos, varRef(pos, p)))
            }
            val lambdaBody = functionBody(pos, decl.body.statements, cls, prelude = boxedPrelude)
            val lambda = Elixir.Fn(pos, params = params, body = lambdaBody)
            return listOf(heapCall(pos, "put", listOf(varRef(pos, name), Elixir.Atom(pos, CELL), lambda)))
        }
        val boxedParams = formals.map { it.name }.filter { it in boxed }.map { p ->
            Elixir.Match(pos, left = varId(pos, p), right = cell(pos, varRef(pos, p)))
        }
        if (!recursive) {
            val body = functionBody(pos, decl.body.statements, cls, prelude = boxedParams)
            val lambda = Elixir.Fn(pos, params = params, body = body)
            return listOf(Elixir.Match(pos, left = varId(pos, name), right = lambda))
        }
        val self = names.gensym("rec")
        recursiveLocals[name] = self to formals.size
        val body = try {
            functionBody(pos, decl.body.statements, cls, prelude = boxedParams)
        } finally {
            recursiveLocals.remove(name)
        }
        val wrapperParams = formals.map { varId(pos, it.name) as Elixir.Pattern }
        return listOf(
            Elixir.Match(
                pos,
                left = Elixir.Id(pos, self),
                right = Elixir.Fn(pos, params = listOf<Elixir.Pattern>(Elixir.Id(pos, self)) + params, body = body),
            ),
            Elixir.Match(
                pos,
                left = varId(pos, name),
                right = Elixir.Fn(
                    pos,
                    params = wrapperParams,
                    body = Elixir.Block(
                        pos,
                        listOf(
                            Elixir.AnonCall(
                                pos,
                                fn = Elixir.Id(pos, self),
                                args = listOf(Elixir.Id(pos, self)) + formals.map { varRef(pos, it.name) },
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    // ── Classes ──────────────────────────────────────────────────────────

    /**
     * A class or interface becomes a module, `TemperMain.Name`.
     *
     * A class that never writes a property outside its constructor and has no
     * setter becomes a `defstruct`: its objects are immutable values, and its
     * constructor rebinds `this` as it fills them in. Any other class keeps
     * its fields in `TemperCore.Heap`, so that every alias sees every write.
     *
     * Members are flattened, the way be-blimp flattens them: a class carries
     * every inherited method body it does not override, so a call never has
     * to find a super implementation at run time.
     */
    private fun translateType(decl: TmpL.TypeDeclaration): Elixir.ModuleDef {
        val pos = decl.pos
        val module = typeModule(decl)
        val isClass = decl.kind == TmpL.TypeDeclarationKind.Class
        if (!isClass && decl.kind != TmpL.TypeDeclarationKind.Interface) TODO("${decl.kind} declaration: ${decl.name}")
        val flattened = if (isClass) flattenMembers(decl) else decl.members.filterIsInstance<TmpL.Member>()
        val isStruct = isClass && isStructClass(flattened)
        val fields = flattened.filterIsInstance<TmpL.InstanceProperty>()
            .filter { it.memberShape.abstractness == lang.temper.type.Abstractness.Concrete }
            .map { fieldText(it.name) }
        val items = mutableListOf<Elixir.ModuleItem>()
        if (isStruct) items.add(Elixir.StructDef(pos, fields.map { Elixir.Atom(pos, it) }))
        items.add(
            Elixir.FunDef(
                pos,
                id = Elixir.Id(pos, OutName(SUPERTYPES, null)),
                body = Elixir.Block(
                    pos,
                    listOf(Elixir.ListLit(pos, (listOf(module) + ancestorModules(decl)).map { moduleOf(pos, it) })),
                ),
            ),
        )
        for (member in flattened) {
            when (member) {
                is TmpL.InstanceProperty -> {}
                is TmpL.Constructor -> if (isClass) items.add(constructor(member, module, isStruct, fields))
                is TmpL.NormalMethod -> member.body?.let {
                    items.add(method(member, names.sanitize(member.dotName.dotNameText), module, isStruct))
                }
                is TmpL.Getter -> member.body?.let {
                    items.add(method(member, getterName(member.dotName.dotNameText), module, isStruct))
                }
                is TmpL.Setter -> member.body?.let {
                    items.add(method(member, setterName(member.dotName.dotNameText), module, isStruct))
                }
                is TmpL.StaticMethod -> member.body?.let {
                    items.add(method(member, names.sanitize(member.dotName.dotNameText), module, isStruct))
                }
                is TmpL.StaticProperty -> {
                    val fn = FunctionContext(returnTag = null)
                    mainBody.add(
                        coreGlobal(
                            member.pos,
                            "put",
                            listOf(
                                staticKey(member.pos, module, member.dotName.dotNameText),
                                expression(member.expression, fn),
                            ),
                        ),
                    )
                }
            }
        }
        return Elixir.ModuleDef(pos, name = moduleOf(pos, module), items = items)
    }

    private fun constructor(
        ctor: TmpL.Constructor,
        module: List<String>,
        isStruct: Boolean,
        fields: List<String>,
    ): Elixir.FunDef {
        val pos = ctor.pos
        val thisName = ctor.parameters.thisName?.name ?: TODO("constructor without this: $ctor")
        val formals = ctor.parameters.parameters.filter { nameOf(it.name) != thisName }
        formals.forEach { declare(it.name.name) }
        declare(thisName)
        val blank: Elixir.Expr = if (isStruct) {
            Elixir.StructLit(pos, name = moduleOf(pos, module), fields = listOf())
        } else {
            heapCall(
                pos,
                "new",
                listOf(
                    moduleOf(pos, module),
                    Elixir.MapLit(pos, fields.map { Elixir.MapEntry(pos, Elixir.Atom(pos, it), Elixir.NilLit(pos)) }),
                ),
            )
        }
        val cls = ClassContext(module, isStruct, thisName, isConstructor = true)
        return Elixir.FunDef(
            pos,
            id = Elixir.Id(pos, OutName(CONSTRUCTOR, null)),
            params = formals.map { idOf(it.name) },
            body = functionBody(
                pos,
                ctor.body.statements,
                cls,
                prelude = listOf(Elixir.Match(pos, left = varId(pos, thisName), right = blank)) +
                    boxParams(pos, formals.map { it.name.name }),
            ),
        )
    }

    /** A method, getter, setter or static: `this` first unless static. */
    private fun method(
        member: TmpL.FunctionDeclarationOrMethod,
        name: String,
        module: List<String>,
        isStruct: Boolean,
    ): Elixir.FunDef {
        val pos = member.pos
        if (member.parameters.restParameter != null) TODO("rest parameter: $member")
        val thisName = member.parameters.thisName?.name
        val formals = member.parameters.parameters.filter { nameOf(it.name) != thisName }
        formals.forEach { declare(it.name.name) }
        thisName?.let { declare(it) }
        val params = listOfNotNull(thisName?.let { varId(pos, it) }) + formals.map { idOf(it.name) }
        val cls = ClassContext(module, isStruct, thisName, isConstructor = false)
        // std's @connected members keep a placeholder body that panics; one
        // with no Elixir support code says which, rather than just "panic"
        val unsupported = (member as? TmpL.Member)?.metadata?.let(::connectedKeyOf)
            ?.takeIf { isStdLib && it !in elixirConnected && isPanicPlaceholder(member.body) }
        return Elixir.FunDef(
            pos,
            id = Elixir.Id(pos, OutName(name, null)),
            params = params,
            body = if (unsupported != null) {
                Elixir.Block(
                    pos,
                    listOf(
                        localCall(
                            pos,
                            "raise",
                            listOf(
                                elixirModule(pos, "TemperCore", "Panic"),
                                Elixir.StringLit(pos, "no Elixir support code for $unsupported"),
                            ),
                        ),
                    ),
                )
            } else {
                functionBody(
                    pos,
                    member.body!!.statements,
                    cls,
                    prelude = boxParams(pos, formals.map { it.name.name }),
                )
            },
        )
    }

    /**
     * A body that is only `pureVirtual()`: what the frontend gives a bodiless
     * `@connected` member, one every backend must supply itself.
     */
    private fun isPanicPlaceholder(body: TmpL.BlockStatement?): Boolean {
        val only = body?.statements?.singleOrNull() as? TmpL.ExpressionStatement ?: return false
        val callee = (only.expression as? TmpL.CallExpression)?.fn as? TmpL.InlineSupportCodeWrapper ?: return false
        return callee.supportCode == PureVirtual
    }

    /** The key of an `@connected` declaration, `std/temporal.type Date.today()`. */
    private fun connectedKeyOf(metadata: List<TmpL.DeclarationMetadata>): String? {
        if (metadata.none { it.key.symbol == lang.temper.value.connectedSymbol }) return null
        return metadata.firstNotNullOfOrNull { m ->
            if (m.key.symbol != lang.temper.value.qNameSymbol) return@firstNotNullOfOrNull null
            (m.value as? TmpL.ValueData)?.value?.let { lang.temper.value.TString.unpackOrNull(it) }
        }
    }

    private fun setBacked(statement: TmpL.SetBackedProperty, fn: FunctionContext): Elixir.BlockItem {
        val pos = statement.pos
        val value = expression(statement.right, fn)
        return when (val subject = statement.left.subject) {
            is TmpL.This -> {
                val cls = fn.cls ?: TODO("property write outside a class: $statement")
                val field = fieldText(statement.left.property)
                if (cls.isStruct) {
                    // `this = %{this | field => value}`: the constructor's own copy
                    Elixir.Match(
                        pos,
                        left = varId(pos, subject.id.name),
                        right = Elixir.MapUpdate(
                            pos,
                            base = varRef(pos, subject.id.name),
                            entries = listOf(Elixir.MapEntry(pos, Elixir.Atom(pos, field), value)),
                        ),
                    )
                } else {
                    heapCall(pos, "put", listOf(varRef(pos, subject.id.name), Elixir.Atom(pos, field), value))
                }
            }
            is TmpL.TypeSubject -> TODO("static property write: $statement")
            else -> TODO("backed property write on $subject")
        }
    }

    private fun getBacked(expression: TmpL.GetBackedProperty, fn: FunctionContext): Elixir.Expr {
        val pos = expression.pos
        return when (val subject = expression.subject) {
            is TmpL.TypeSubject -> {
                val typeName = (subject as? TmpL.TypeName)?.let { baseNameOf(it) }
                val builtin = typeName?.let { builtinStatics["$it.${propertyText(expression.property)}"] }
                when {
                    builtin != null -> builtin(pos)
                    else -> coreGlobal(
                        pos,
                        "get",
                        listOf(staticKey(pos, typeSubjectModule(subject), propertyText(expression.property))),
                    )
                }
            }
            is TmpL.This -> {
                val cls = fn.cls ?: TODO("property read outside a class: $expression")
                val field = fieldText(expression.property)
                if (cls.isStruct) {
                    Elixir.Field(pos, obj = varRef(pos, subject.id.name), id = Elixir.Id(pos, OutName(field, null)))
                } else {
                    heapCall(pos, "get", listOf(varRef(pos, subject.id.name), Elixir.Atom(pos, field)))
                }
            }
            is TmpL.Expression -> builtinGet(expression, subject, fn)
                ?: TODO("backed property read on another object: $expression")
        }
    }

    /** `instanceof`: a guard for the builtin types, the supertype list for translated ones. */
    private fun instanceOf(pos: Position, value: Elixir.Expr, type: TmpL.AType): Elixir.Expr {
        val name = typeBaseName(type)
        builtinGuards[name]?.let { guard -> return localCall(pos, guard, listOf(value)) }
        indexChecks[name]?.let { check -> return check(pos, value) }
        val module = types[name]?.let { typeModule(it) } ?: externalTypeModule(type) ?: TODO("instanceof $name")
        return coreCall(pos, "is_a", listOf(value, moduleOf(pos, module)))
    }

    private fun cast(cast: TmpL.CastExpression, fn: FunctionContext): Elixir.Expr {
        val pos = cast.pos
        val value = expression(cast.expr, fn)
        if (!cast.canFail) return value
        val name = typeBaseName(cast.checkedType)
        indexChecks[name]?.let { check ->
            val tmp = names.gensym("index")
            return Elixir.Case(
                pos,
                subject = value,
                clauses = listOf(
                    Elixir.Clause(
                        pos,
                        pattern = Elixir.Id(pos, tmp),
                        body = Elixir.Block(
                            pos,
                            listOf(
                                coreCall(
                                    pos,
                                    "cast_check",
                                    listOf(Elixir.Id(pos, tmp), check(pos, Elixir.Id(pos, tmp))),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        builtinGuards[name]?.let { guard ->
            // bind once: the value is both checked and returned
            val tmp = names.gensym("cast")
            return Elixir.Case(
                pos,
                subject = value,
                clauses = listOf(
                    Elixir.Clause(
                        pos,
                        pattern = Elixir.Id(pos, tmp),
                        body = Elixir.Block(
                            pos,
                            listOf(
                                coreCall(
                                    pos,
                                    "cast_check",
                                    listOf(Elixir.Id(pos, tmp), localCall(pos, guard, listOf(Elixir.Id(pos, tmp)))),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val module = types[name]?.let { typeModule(it) } ?: externalTypeModule(cast.checkedType)
            ?: TODO("cast to $name")
        return coreCall(pos, "cast", listOf(value, moduleOf(pos, module)))
    }

    /**
     * Methods of a library this one depends on, which Temper implements and
     * does not mark connected, so they reach the translator as ordinary calls
     * on a type it has no module for. Each is a temper-core function.
     */
    private val builtinMethods = mapOf(
        "Test.softFailToHard" to ("Test" to "soft_fail_to_hard"),
    )

    /**
     * Property reads on builtin types that never reach the support network.
     * Generator.done is a `@connected` property, but TranslateDotHelper only
     * looks for connected getter methods, and an interface property has none,
     * so no key is ever asked for. ValueResult.value is a plain backed
     * property of a type that is lowered to the tuple `{:value, v}`.
     */
    private val builtinGetters = mapOf(
        "Generator.done" to ("Generator" to "done"),
        "SafeGenerator.done" to ("Generator" to "done"),
        "ValueResult.value" to ("Generator" to "value"),
        // a %TemperCore.Pair{} struct; TemperCore.call used to reach these through class_of
        "Pair.key" to ("Pair" to "get_key"),
        "Pair.value" to ("Pair" to "get_value"),
    )

    /**
     * `subject.prop` through a getter. On a translated class that is a method
     * call; on a builtin type, the class_of in TemperCore.call would name a
     * module that does not exist and fail only at run time, so a builtin
     * getter without support code fails the build here instead.
     */
    private fun getAbstract(expression: TmpL.GetAbstractProperty, fn: FunctionContext): Elixir.Expr {
        val pos = expression.pos
        builtinGet(expression, expression.subject, fn)?.let { return it }
        return coreCall(
            pos,
            "call",
            listOf(
                expression(expression.subject, fn),
                Elixir.Atom(pos, getterName(propertyText(expression.property))),
                Elixir.ListLit(pos, listOf()),
            ),
        )
    }

    /** A read of a builtin type's property, or null when the subject's type is translated here. */
    private fun builtinGet(expression: TmpL.GetProperty, subject: TmpL.Expression, fn: FunctionContext): Elixir.Expr? {
        val property = propertyText(expression.property)
        val definition = (subject.passType as? lang.temper.type2.DefinedType)?.definition
        val owner = (definition?.name as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText
        if (owner == null || owner in types || isExternal(definition?.name)) return null
        val builtin = builtinGetters["$owner.$property"]
            ?: TODO("getter $owner.$property has no Elixir support code: $expression")
        return remoteCall(
            expression.pos,
            elixirModule(expression.pos, "TemperCore", builtin.first),
            builtin.second,
            listOf(expression(subject, fn)),
        )
    }

    /** Statics of Temper's builtin types, which have no module here to hold them. */
    private val builtinStatics = mapOf<String, (Position) -> Elixir.Expr>(
        "String.begin" to { pos -> Elixir.NumberLit(pos, 0) },
        "StringIndex.none" to { pos -> Elixir.NumberLit(pos, -1) },
        "Float64.pi" to { pos -> Elixir.NumberLit(pos, kotlin.math.PI) },
        "Float64.e" to { pos -> Elixir.NumberLit(pos, kotlin.math.E) },
    )

    /**
     * Types told apart by value rather than by a guard or a module. A string
     * index is an integer byte offset and "no index" is -1, so the
     * StringIndexOption types are told apart by sign; generator results by tag.
     */
    private val indexChecks = mapOf<String, (Position, Elixir.Expr) -> Elixir.Expr>(
        "StringIndex" to { pos, v -> infixOp(pos, v, ElixirOperator.GreaterEquals, Elixir.NumberLit(pos, 0)) },
        "NoStringIndex" to { pos, v -> infixOp(pos, v, ElixirOperator.Equals, Elixir.NumberLit(pos, -1)) },
        // generator results are `{:value, v}` and `:done`, not Temper objects
        "ValueResult" to { pos, v ->
            remoteCall(pos, elixirModule(pos, "TemperCore", "Generator"), "value_result?", listOf(v))
        },
        "DoneResult" to { pos, v -> infixOp(pos, v, ElixirOperator.Equals, Elixir.Atom(pos, "done")) },
    )

    private val builtinGuards = mapOf(
        "String" to "is_binary",
        "Int" to "is_integer",
        "Int32" to "is_integer",
        "Int64" to "is_integer",
        "Float64" to "is_float",
        "Boolean" to "is_boolean",
        "List" to "is_list",
        "Listed" to "is_list",
    )

    /** A class carries its supertypes' members that it does not redefine, nearest first. */
    private fun flattenMembers(decl: TmpL.TypeDeclaration): List<TmpL.Member> {
        val byKey = linkedMapOf<String, TmpL.Member>()
        var level = listOf(decl)
        val seen = mutableSetOf<String>()
        while (level.isNotEmpty()) {
            for (type in level) {
                for (member in type.members.filterIsInstance<TmpL.Member>()) {
                    memberKey(member)?.let { byKey.putIfAbsent(it, member) }
                }
            }
            level = level.flatMap { type ->
                type.superTypes.mapNotNull { superType ->
                    val key = baseNameOf(superType.typeName) ?: return@mapNotNull null
                    if (seen.add(key)) types[key] else null
                }
            }
        }
        return byKey.values.toList()
    }

    private fun memberKey(member: TmpL.Member): String? = when (member) {
        is TmpL.InstanceProperty -> "prop:" + member.dotName.dotNameText
        is TmpL.StaticProperty -> "static-prop:" + member.dotName.dotNameText
        is TmpL.Getter -> "get:" + member.dotName.dotNameText
        is TmpL.Setter -> "set:" + member.dotName.dotNameText
        // an abstract method must not hide an inherited body
        is TmpL.NormalMethod -> if (member.body == null) null else "fn:" + member.dotName.dotNameText
        is TmpL.StaticMethod -> "static-fn:" + member.dotName.dotNameText
        is TmpL.Constructor -> "ctor"
    }

    /** No setter, and no property write outside the constructor. */
    private fun isStructClass(members: List<TmpL.Member>): Boolean {
        if (members.any { it is TmpL.Setter }) return false
        return members.none { member ->
            member !is TmpL.Constructor && run {
                var writes = false
                member.boundaryDescent { node ->
                    if (node is TmpL.SetBackedProperty) writes = true
                    !writes
                }
                writes
            }
        }
    }

    private fun ancestorModules(decl: TmpL.TypeDeclaration): List<List<String>> {
        val out = mutableListOf<List<String>>()
        val seen = mutableSetOf<String>()
        var level = listOf(decl)
        while (level.isNotEmpty()) {
            level = level.flatMap { type ->
                type.superTypes.mapNotNull { superType ->
                    val key = baseNameOf(superType.typeName) ?: return@mapNotNull null
                    if (!seen.add(key)) return@mapNotNull null
                    val found = types[key] ?: return@mapNotNull null
                    out.add(typeModule(found))
                    found
                }
            }
        }
        return out
    }

    private fun typeModule(decl: TmpL.TypeDeclaration): List<String> =
        root + names.moduleSegment(baseNameOfId(decl.name))

    private fun typeNameModule(typeName: TmpL.TypeName): List<String> {
        val key = baseNameOf(typeName) ?: TODO("type with no name: $typeName")
        return types[key]?.let { typeModule(it) }
            ?: externalModule(typeName.sourceDefinition?.name)
            ?: TODO("type not declared here: $key")
    }

    /**
     * A type as a value: the module of a translated class, this library's
     * or another's, and the name of a builtin type as an atom (`:Void`),
     * as be-js gives the name as a string. Java gives a class literal.
     */
    private fun typeValue(pos: Position, type: lang.temper.type2.Type2, expression: TmpL.Tree): Elixir.Expr {
        val definition = (type as? lang.temper.type2.DefinedType)?.definition
            ?: TODO("type value with no definition: $expression")
        val base = (definition.name as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText
            ?: TODO("type value with no name: $expression")
        types[base]?.let { return moduleOf(pos, typeModule(it)) }
        externalModule(definition.name)?.let { return moduleOf(pos, it) }
        return Elixir.Atom(pos, base)
    }

    /** The library another translated library's name was declared in, or null for Temper's builtins. */
    private fun libraryOf(name: lang.temper.name.TemperName?): DashedIdentifier? {
        val loc = ((name as? lang.temper.name.ModularName)?.origin?.loc as? lang.temper.name.ModuleName)
            ?: return null
        return libraryRoots[loc.libraryRoot()]
    }

    private fun isExternal(name: lang.temper.name.TemperName?): Boolean = libraryOf(name) != null

    /** `Temper.Std.JsonArray` for std's JsonArray. */
    private fun externalModule(name: lang.temper.name.TemperName?): List<String>? {
        val library = libraryOf(name) ?: return null
        val base = (name as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText ?: return null
        return ElixirBackend.libraryModule(library) + names.moduleSegment(base)
    }

    private fun externalTypeModule(type: TmpL.AType): List<String>? =
        externalModule((type.ot as? TmpL.NominalType)?.typeName?.sourceDefinition?.name)

    private fun typeSubjectModule(subject: TmpL.TypeSubject): List<String> = when (subject) {
        is TmpL.TypeName -> typeNameModule(subject)
        else -> TODO("type subject: $subject")
    }

    private fun baseNameOf(typeName: TmpL.TypeName): String? =
        (typeName.sourceDefinition?.name as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText

    private fun baseNameOfId(id: TmpL.Id): String =
        (nameOf(id) as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText
            ?: names.outName(id.name).outputNameText

    private fun typeBaseName(type: TmpL.AType): String =
        (type.ot as? TmpL.NominalType)?.typeName?.let { baseNameOf(it) } ?: TODO("type test against $type")

    private fun moduleOf(pos: Position, segments: List<String>): Elixir.ModuleName =
        elixirModule(pos, *segments.toTypedArray())

    private fun staticKey(pos: Position, module: List<String>, member: String): Elixir.Expr =
        Elixir.Atom(pos, module.joinToString(".") + "." + member)

    private fun fieldText(id: TmpL.Id): String = names.outName(id.name).outputNameText

    private fun fieldText(property: TmpL.PropertyId): String = when (property) {
        is TmpL.InternalPropertyId -> fieldText(property.name)
        is TmpL.ExternalPropertyId -> names.sanitize(property.name.dotNameText)
    }

    private fun propertyText(property: TmpL.PropertyId): String = when (property) {
        is TmpL.InternalPropertyId -> baseNameOfId(property.name)
        is TmpL.ExternalPropertyId -> property.name.dotNameText
    }

    private fun heapCall(pos: Position, fn: String, args: List<Elixir.Expr>): Elixir.Expr =
        remoteCall(pos, elixirModule(pos, "TemperCore", "Heap"), fn, args)

    private fun getterName(property: String) = names.sanitize("get_$property")

    private fun setterName(property: String) = names.sanitize("set_$property")

    // ── Names ────────────────────────────────────────────────────────────

    private fun nameOf(id: TmpL.Id): ResolvedName? = runCatching { id.name }.getOrNull()

    private fun idOf(id: TmpL.Id): Elixir.Id = Elixir.Id(id.pos, names.outName(id.name))

    private fun varId(pos: Position, name: ResolvedName): Elixir.Id = Elixir.Id(pos, names.outName(name))

    private fun varRef(pos: Position, name: ResolvedName): Elixir.Expr = varId(pos, name)

    private fun functionName(name: ResolvedName): OutName = names.outName(name)

    /** `:"Temper.Std.hexDigits__386"`: keyed by library, as two libraries' globals share one process. */
    private fun globalAtom(pos: Position, name: ResolvedName): Elixir.Atom {
        val module = externals[name]?.module ?: root
        return Elixir.Atom(pos, module.joinToString(".") + "." + names.outName(name).outputNameText)
    }

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
            TFloat64 -> TFloat64.unpack(expression.value).let { f ->
                // the BEAM's floats have no NaN or infinity; TemperCore.Float
                // stands atoms in for them
                when {
                    f.isNaN() -> Elixir.Atom(pos, "nan")
                    f == Double.POSITIVE_INFINITY -> Elixir.Atom(pos, "infinity")
                    f == Double.NEGATIVE_INFINITY -> Elixir.Atom(pos, "neg_infinity")
                    else -> Elixir.NumberLit(pos, f)
                }
            }
            TInt -> Elixir.NumberLit(pos, TInt.unpack(expression.value))
            TInt64 -> Elixir.NumberLit(pos, TInt64.unpack(expression.value))
            is TString -> Elixir.StringLit(pos, TString.unpack(expression.value))
            // RepresentationOfVoid.ReifyVoid: a void value really flows, and nil is it
            TNull, TVoid -> Elixir.NilLit(pos)
            TType -> typeValue(pos, TType.unpack(expression.value).type2, expression)
            is TClass, TClosureRecord, TFunction, TList, TListBuilder, TMap, TMapBuilder,
            TProblem, TStageRange, TSymbol,
            -> TODO("value of type $tag: $expression")
        }
    }

    private companion object {
        const val RETURN = "temper_return"
        const val BREAK = "temper_break"
        const val CONTINUE = "temper_continue"
        const val NEXT = "temper_next"
        const val DONE = "temper_done"
        const val CONSTRUCTOR = "new"
        const val CONNECTED_MODULE = "TemperConnected"
        const val CELL = "v"
        const val CELL_CLASS = "cell"
        const val SUPERTYPES = "__temper_supertypes__"
    }
}
