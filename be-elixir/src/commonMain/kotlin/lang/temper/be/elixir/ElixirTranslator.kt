package lang.temper.be.elixir

import lang.temper.ast.boundaryDescent
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLOperator
import lang.temper.be.tmpl.dependencyCategory
import lang.temper.be.tmpl.documentation
import lang.temper.be.tmpl.parameterDefaultStatementsInfo
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.name.DashedIdentifier
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.value.DependencyCategory
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
import lang.temper.value.Value

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
    /**
     * Every module-level variable of the library, and every module function
     * an assignment rebinds: that one is in [moduleFunctions] too, which
     * names its `defp`, but everything else reaches it through here.
     */
    private val moduleGlobals: Set<ResolvedName>,
    /** Every class and interface of the library, by its source name. */
    private val types: Map<String, TmpL.TypeDeclaration>,
    /** An imported name, mapped to the exporting module's own name for the same thing. */
    private val imports: Map<ResolvedName, ResolvedName>,
    /** Translating Temper's standard library, whose @connected functions are support code. */
    private val isStdLib: Boolean = false,
    /**
     * Module functions and values only tests use, or that nothing in
     * production reaches. Functions are defined in [testRoot], which is
     * compiled for `mix test` and never shipped with the library.
     */
    private val testOnly: Set<ResolvedName> = setOf(),
    /** Non-exported functions and values nothing reaches, production or tests: not generated. */
    private val unused: Set<ResolvedName> = setOf(),
    /** Non-exported functions only the root module calls: `defp`, called locally. */
    private val private: Set<ResolvedName> = setOf(),
) {
    /** True while translating code that lands in the root module, where a [private] function is in reach. */
    private var inRoot = false

    /** `Temper.MyLib.Tests`: tests and what only they use, in `test/support/`. */
    private val testRoot = root + ElixirBackend.TEST_MODULE

    /** The module a module function is defined in. */
    private fun functionModule(name: ResolvedName) = if (name in testOnly) testRoot else root

    /** The name a value was declared under, seen through any imports. */
    private fun canonical(name: ResolvedName): ResolvedName {
        var current = name
        repeat(imports.size + 1) { current = imports[current] ?: return current }
        return current
    }

    private val functions = mutableListOf<Elixir.ModuleItem>()

    /** `@spec` and `@type` types: a class is its module's `t()`, this library's or another's. */
    private val specs = ElixirTypespecs { shape ->
        localType(shape.name)?.let { typeModule(it) } ?: externalModule(shape.name)
    }

    /** Whether the class being translated is `@actor`: its instances are processes. */
    private var currentClassIsActor = false

    /** Whether the class being translated is exported: Elixir code may construct it directly. */
    private var currentClassIsExported = false
    private val mainBody = mutableListOf<Elixir.BlockItem>()
    private val modules = mutableListOf<Elixir.ModuleDef>()

    data class Translated(
        val functions: List<Elixir.ModuleItem>,
        val mainBody: List<Elixir.BlockItem>,
        val modules: List<Elixir.ModuleDef>,
        /** Each test's function name, which is also the name the JUnit report gives it. */
        val tests: List<String>,
        /** Each test's own sentence, by function name: the name `mix test` shows. */
        val testTitles: Map<String, String>,
        /** Each test's TmpL node, by function name, for the CLI's test registry. */
        val testNodes: Map<String, TmpL.Test>,
        /** What only tests use, and the tests themselves: [testRoot]'s functions. */
        val testFunctions: List<Elixir.ModuleItem>,
        /** Top-level statements that initialize test-only values. */
        val testMainBody: List<Elixir.BlockItem>,
        /** Classes only tests use. */
        val testModules: List<Elixir.ModuleDef>,
    )

    private val testFunctions = mutableListOf<Elixir.ModuleItem>()
    private val testMainBody = mutableListOf<Elixir.BlockItem>()
    private val testModules = mutableListOf<Elixir.ModuleDef>()

    private val tests = mutableListOf<String>()
    private val testTitles = mutableMapOf<String, String>()
    private val testNodes = mutableMapOf<String, TmpL.Test>()

    /**
     * Locals a closure reads that are also assigned somewhere. Elixir closures
     * capture values and Temper's capture variables, so these live in a heap
     * cell that the closure and its enclosing function share.
     */
    private val boxed = mutableSetOf<ResolvedName>()

    /** A local function that calls itself: the self-passing name its body calls through, and its arity. */
    private val recursiveLocals = mutableMapOf<ResolvedName, Pair<OutName, Int>>()

    /**
     * Loops lifted out as `defp`s, with their specs, waiting to join the
     * module of the function they came from; see [loop].
     */
    private val lifted = mutableListOf<Elixir.ModuleItem>()

    /** The function being translated, which names the loops lifted from it. */
    private var currentFunction = INIT_NAME

    /** Loops lifted so far, so each gets a name of its own. */
    private var liftedCount = 0

    /** [lifted], taken: each place that adds a function to a module adds these after it. */
    private fun takeLifted(): List<Elixir.ModuleItem> = lifted.toList().also { lifted.clear() }

    /** [body] with [currentFunction] set to [name]. */
    private fun <T> within(name: String, body: () -> T): T {
        val outer = currentFunction
        currentFunction = name
        try {
            return body()
        } finally {
            currentFunction = outer
        }
    }

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
        // A local assigned in a `do` body and declared outside it lives in a cell
        // too. A `do` is an Elixir `try`, and a binding made inside `try` never
        // reaches `rescue`: `do { x = 1; fail() } orelse ...` saw x as it was
        // before the `do`. A cell is written in place, so the write survives the
        // raise. What a nested function assigns is decided by the rule above.
        for (topLevel in module.topLevels) {
            topLevel.boundaryDescent { node ->
                if (node is TmpL.TryStatement) {
                    val declaredInside = mutableSetOf<ResolvedName>()
                    val assignedInside = mutableSetOf<ResolvedName>()
                    node.tried.boundaryDescent { inner ->
                        when (inner) {
                            is TmpL.LocalFunctionDeclaration -> false
                            is TmpL.LocalDeclaration -> {
                                nameOf(inner.name)?.let(declaredInside::add)
                                true
                            }
                            is TmpL.Assignment -> {
                                nameOf(inner.left)?.let(assignedInside::add)
                                true
                            }
                            else -> true
                        }
                    }
                    boxed.addAll(assignedInside - declaredInside - moduleGlobals)
                }
                true
            }
        }
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
        testTitles.clear()
        testNodes.clear()
        testFunctions.clear()
        testMainBody.clear()
        testModules.clear()
        collectBoxed(module)
        for (topLevel in module.topLevels) {
            processTopLevel(topLevel)
        }
        return Translated(
            functions.toList(),
            mainBody.toList(),
            modules.toList(),
            tests.toList(),
            testTitles.toMap(),
            testNodes.toMap(),
            testFunctions.toList(),
            testMainBody.toList(),
            testModules.toList(),
        )
    }

    // ── Top levels ───────────────────────────────────────────────────────

    /**
     * Translates a top level, and moves what it made to the test side when
     * the frontend says only tests need it: a `test`, or a declaration that
     * nothing but tests reaches.
     */
    private fun processTopLevel(topLevel: TmpL.TopLevel) {
        if (topLevel.declaredName()?.let { it in unused } == true) return
        val marks = Triple(functions.size, mainBody.size, modules.size)
        val testSide = topLevel.dependencyCategory() == DependencyCategory.Test || topLevel.declaredName() in testOnly
        inRoot = !testSide && topLevel !is TmpL.TypeDeclaration
        translateTopLevel(topLevel)
        inRoot = false
        if (testSide) {
            testFunctions.addAll(functions.drainFrom(marks.first))
            testMainBody.addAll(mainBody.drainFrom(marks.second))
            testModules.addAll(modules.drainFrom(marks.third))
        }
    }

    private fun TmpL.TopLevel.declaredName(): ResolvedName? = when (this) {
        is TmpL.ModuleFunctionDeclaration -> name.name
        is TmpL.ModuleLevelDeclaration -> name.name
        else -> null
    }

    private fun <T> MutableList<T>.drainFrom(start: Int): List<T> {
        val tail = subList(start, size)
        return tail.toList().also { tail.clear() }
    }

    private fun translateTopLevel(topLevel: TmpL.TopLevel) {
        when (topLevel) {
            is TmpL.ModuleInitBlock -> {
                val fn = FunctionContext(returnTag = null)
                mainBody.addAll(statements(topLevel.body.statements, End.Discard, fn))
                functions.addAll(takeLifted())
            }
            is TmpL.ModuleLevelDeclaration -> {
                processModuleLevelDeclaration(topLevel)
                functions.addAll(takeLifted())
            }
            is TmpL.ModuleFunctionDeclaration -> {
                val name = topLevel.name.name
                val exported = name is lang.temper.name.ExportedName
                // rebound later, `var f = fn ...; f = g;`, so f is a module-level
                // value and its first value goes where its `var` was
                val rebound = name in moduleGlobals
                if (rebound && exported) {
                    reboundExport(topLevel)
                    return
                }
                val fn = within(functionName(name).outputNameText) {
                    names.withLocals(declaredIn(topLevel)) { translateFunction(topLevel) }
                }
                if (name in private) {
                    fn.isPrivate = true
                } else if (exported) {
                    docAttr(fn.pos, "doc", (topLevel as TmpL.Declaration).documentation)?.let(functions::add)
                } else {
                    // a class's members or the tests call it, so it is public, but not API
                    val doc = Elixir.Id(fn.pos, OutName("doc", null))
                    functions.add(Elixir.ModuleAttr(fn.pos, doc, Elixir.BoolLit(fn.pos, false)))
                }
                val result = specs.of(topLevel.returnType)
                functions.add(spec(fn, topLevel.parameters.parameters, result, fromElixir = exported))
                functions.add(fn)
                functions.addAll(takeLifted())
                if (rebound) mainBody.add(globalPut(topLevel.pos, name, capture(topLevel.pos, name)))
            }
            // each member names its own locals; see translateType
            is TmpL.TypeDeclaration -> modules.add(translateType(topLevel))
            is TmpL.Test -> {
                val fn = within("test") { names.withLocals(declaredIn(topLevel)) { translateTest(topLevel) } }
                // a test's value is whatever its last statement left; nothing reads it
                functions.add(spec(fn, topLevel.parameters.parameters, specs.builtin(topLevel.pos, "term")))
                functions.add(fn)
                functions.addAll(takeLifted())
            }
            is TmpL.GarbageTopLevel -> mainBody.add(garbage(topLevel.pos, topLevel.diagnostic))
            // TypeConnection, PooledValueDeclaration, SupportCodeDeclaration,
            // comments and garbage carry no Elixir output
            else -> {}
        }
    }

    /**
     * An exported function an assignment rebinds. Another library calls
     * `Temper.Lib.f(x)` and Elixir code may too, so `def f` stays, and
     * calls whatever f holds now. Its first value is the declared body,
     * under a name of its own:
     *
     *     defp ex_f_3(x) do x end
     *     def f(x) do (entry) TemperCore.Global.get(:"Temper.Lib.f").(x) end
     *     # in __temper_init__:
     *     TemperCore.Global.put(:"Temper.Lib.f", &ex_f_3/1)
     */
    private fun reboundExport(decl: TmpL.ModuleFunctionDeclaration) {
        val pos = decl.pos
        val name = decl.name.name
        val outName = functionName(name)
        val first = names.gensym(outName.outputNameText)
        val result = specs.of(decl.returnType)
        val (impl, dispatch) = within(outName.outputNameText) {
            names.withLocals(declaredIn(decl)) {
                val impl = translateFunction(decl, id = Elixir.Id(decl.name.pos, first), entryPoint = false)
                impl.isPrivate = true
                val formals = decl.parameters.parameters
                val args = formals.map { varRef(pos, it.name.name) }
                val call = Elixir.AnonCall(pos, fn = globalGet(pos, name), args = args)
                val dispatch = Elixir.FunDef(
                    pos,
                    id = Elixir.Id(decl.name.pos, outName),
                    params = formals.map { idOf(it.name) },
                    body = entry(pos, Elixir.Block(pos, listArgs(pos, formals) + call)),
                )
                impl to dispatch
            }
        }
        functions.add(spec(impl, decl.parameters.parameters, result))
        functions.add(impl)
        functions.addAll(takeLifted())
        docAttr(pos, "doc", (decl as TmpL.Declaration).documentation)?.let(functions::add)
        functions.add(spec(dispatch, decl.parameters.parameters, result, fromElixir = true))
        functions.add(dispatch)
        val arity = Elixir.NumberLit(pos, decl.parameters.parameters.size)
        mainBody.add(globalPut(pos, name, Elixir.Capture(pos, fn = Elixir.Id(pos, first), arity = arity)))
    }

    private fun processModuleLevelDeclaration(decl: TmpL.ModuleLevelDeclaration) {
        if (decl.isConsole()) return
        val fn = FunctionContext(returnTag = null)
        val value = decl.init?.let { expression(it, fn) } ?: Elixir.NilLit(decl.pos)
        mainBody.add(globalPut(decl.pos, decl.name.name, value))
    }

    /** Every local a declaration declares: parameters, `this`, locals and local functions, at any depth. */
    private fun declaredIn(topLevel: TmpL.Tree): List<ResolvedName> {
        val out = mutableListOf<ResolvedName>()
        fun parameters(p: TmpL.Parameters) {
            p.thisName?.let { out.add(it.name) }
            p.parameters.forEach { out.add(it.name.name) }
        }
        topLevel.boundaryDescent { node ->
            when (node) {
                is TmpL.LocalDeclaration -> out.add(node.name.name)
                is TmpL.LocalFunctionDeclaration -> {
                    out.add(node.name.name)
                    parameters(node.parameters)
                }
                is TmpL.FunctionDeclarationOrMethod -> parameters(node.parameters)
                else -> {}
            }
            true
        }
        if (topLevel is TmpL.FunctionDeclarationOrMethod) parameters(topLevel.parameters)
        // a test is a function too: its `test` parameter is one of its locals
        if (topLevel is TmpL.Test) parameters(topLevel.parameters)
        return out
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

        /** Whether anything leaves it, by `break` or by falling off the end of a block. */
        var leaves = false

        /**
         * True for a loop that hands an exit bound past it back to its call
         * site, as the tuple it would otherwise throw, and ends normally with
         * `{:cont, vars}`. Its call site is a `case` over those.
         */
        var escaping = false

        /** The exits an escaping loop handed back: one call-site clause each. */
        val escaped = linkedSetOf<Exit>()

        /**
         * `self(vars)`, a call of the loop's own function. Its free variables
         * are put in front once they are known; see [loop].
         */
        fun selfCall(pos: Position, args: List<Elixir.Expr>): Elixir.Call =
            Elixir.Call(pos, callee = Elixir.Id(pos, self!!), args = args)
    }

    /** Where a `return`, `break` or `continue` goes. */
    private sealed interface Exit {
        object Return : Exit

        /** Equal by [target]'s identity: one clause per target. */
        data class Break(val target: LoopContext) : Exit

        data class Continue(val target: LoopContext) : Exit
    }

    /**
     * [decl] as a `def`, named [id]. An [entryPoint], an exported function,
     * is where Elixir code calls in; see [entry] and [listArgs].
     */
    private fun translateFunction(
        decl: TmpL.ModuleFunctionDeclaration,
        id: Elixir.Id = Elixir.Id(decl.name.pos, functionName(decl.name.name)),
        entryPoint: Boolean = decl.name.name is lang.temper.name.ExportedName,
    ): Elixir.FunDef {
        val pos = decl.pos
        if (decl.parameters.thisName != null) TODO("this parameter: $decl")
        // parameters are locals too: a loop that assigns one must carry it
        val formals = decl.parameters.parameters.map { it.name }
        formals.forEach { declare(it.name) }
        val params = formals.map { idOf(it) as Elixir.Pattern }
        val body = decl.body ?: TODO("function without a body: $decl")
        if (decl.metadata.any { it.key.symbol == lang.temper.value.connectedSymbol } && !isStdLib) {
            return Elixir.FunDef(pos, id = id, params = params, body = connectedBody(decl))
        }
        val prelude = (if (entryPoint) listArgs(pos, decl.parameters.parameters) else listOf()) +
            boxParams(pos, formals.map { it.name })
        val translated = functionBody(pos, body.statements, prelude = prelude)
        return Elixir.FunDef(
            pos,
            id = id,
            params = params,
            body = if (entryPoint) entry(pos, translated) else translated,
        )
    }

    /**
     * `@doc` or `@moduledoc` from a declaration's doc comment, as a heredoc,
     * so `h Temper.Lib.f` in IEx and ExDoc show what the Temper author wrote.
     */
    private fun docAttr(pos: Position, attr: String, doc: lang.temper.value.OccasionallyHelpful?): Elixir.ModuleAttr? {
        val text = doc?.prettyPleaseHelp()?.longHelp()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Elixir.ModuleAttr(pos, Elixir.Id(pos, OutName(attr, null)), Elixir.Heredoc(pos, text))
    }

    /**
     * `@spec` for [fn], whose parameters are [formals] after any leading
     * [self]: what each formal's Temper type is on the BEAM, `nil` added for
     * one a caller may leave out.
     */
    private fun spec(
        fn: Elixir.FunDef,
        formals: List<TmpL.Formal>,
        result: Elixir.TypeExpr,
        self: Elixir.TypeExpr? = null,
        fromElixir: Boolean = false,
    ): Elixir.TypeSpec {
        val pos = fn.pos
        val params = formals.map { formal ->
            specs.of(formal.type).let { if (fromElixir) specs.acceptingPlainLists(it) else it }.let {
                if (formal.optionalState !=
                    lang.temper.common.TriState.FALSE
                ) {
                    specs.orNil(pos, it)
                } else {
                    it
                }
            }
        }
        val id = Elixir.Id(pos, fn.id.outName)
        return Elixir.TypeSpec(pos, id, params = listOfNotNull(self) + params, result = result)
    }

    /**
     * An exported function is where Elixir code calls in, so its body runs
     * through `TemperCore.Heap.entry`: when the outermost such call returns,
     * the objects it made and left unreachable are freed.
     */
    private fun entry(pos: Position, body: Elixir.Block): Elixir.Block =
        Elixir.Block(pos, listOf(selfInit(pos), heapCall(pos, "entry", listOf(Elixir.Fn(pos, body = body)))))

    /**
     * `xs = TemperCore.Vec.of(xs)` for each `List` among [formals], where
     * Elixir code calls in: a caller may pass a plain list, and inside the
     * library a List is a Vec. Without it a plain list went through as it
     * was, and `nonEmpty([1, 2])`, specced to return a Vec, returned
     * `[1, 2]`; Dialyzer said so.
     */
    private fun listArgs(pos: Position, formals: List<TmpL.Formal>): List<Elixir.BlockItem> =
        formals.filter { formal -> formal.type.privOtOrNull?.let(::isList) == true }.map { formal ->
            val name = formal.name.name
            Elixir.Match(
                pos,
                left = varId(pos, name),
                right = remoteCall(pos, elixirModule(pos, "TemperCore", "Vec"), "of", listOf(varRef(pos, name))),
            )
        }

    /** A Temper `List`, or a union with one in it, such as `List<T>?`. */
    private fun isList(type: TmpL.Type): Boolean = when (type) {
        is TmpL.NominalType ->
            (type.typeName as? TmpL.TemperTypeName)?.typeDefinition == WellKnownTypes.listTypeDefinition
        is TmpL.TypeUnion -> type.types.any(::isList)
        else -> false
    }

    /** Whether Elixir code can call [member] directly: a public member of an exported class. */
    private fun fromElixir(member: TmpL.FunctionDeclarationOrMethod) =
        currentClassIsExported && (member as? TmpL.Member)?.visibility?.visibility == TmpL.Visibility.Public

    /**
     * `Temper.Lib.__temper_init__()`: Elixir code calling into the library
     * need not initialize it first. Init runs once per node, so after the first
     * call this is one ETS lookup, and code inside the init itself skips it.
     */
    private fun selfInit(pos: Position): Elixir.Expr =
        remoteCall(pos, mainModule(pos), ElixirBackend.INIT_FUNCTION, listOf())

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
        testTitles[name] = test.rawName
        testNodes[name] = test
        return Elixir.FunDef(
            pos,
            id = Elixir.Id(pos, OutName(name, null)),
            params = formals.map { idOf(it) },
            body = functionBody(pos, test.body.statements, prelude = boxParams(pos, formals.map { it.name })),
        )
    }

    /**
     * A user `@connected` function: Temper's own parameter defaulting, then a
     * call to the library's `_connected.ex`, which defines `Temper.Lib.Connected`.
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
        val connected = elixirModule(pos, root + ElixirBackend.CONNECTED_MODULE)
        return Elixir.Block(pos, items + remoteCall(pos, connected, fnName, args))
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

        /**
         * The list is a labeled block's body, with what follows the block
         * folded in: leaving it is going on with [rest], which ends as [outer]
         * does. Any other exit passes through to [outer].
         */
        class Then(val block: LoopContext, val rest: List<TmpL.Statement>, val outer: End) : End
    }

    private fun statements(list: List<TmpL.Statement>, end: End, fn: FunctionContext): List<Elixir.BlockItem> {
        val flat = madeAfterWhatTheyCapture(flatten(list))
        val out = mutableListOf<Elixir.BlockItem>()
        for (decl in flat.filterIsInstance<TmpL.LocalFunctionDeclaration>()) {
            // a list folded into another's branch was in that list, whose cells it shares
            if (decl.name.name in boxed && scopes.none { decl.name.name in it }) {
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
                statement is TmpL.LabeledStatement && statement.statement !is TmpL.WhileStatement &&
                    rest.all(::copyable) -> {
                    out.addAll(foldedBlock(statement, rest, end, fn))
                    return out
                }
                statement is TmpL.TryStatement && rest.isEmpty() && end is End.Return -> {
                    out.addAll(tryOf(statement, fn, returning = true))
                    return out
                }
                statement is TmpL.TryStatement &&
                    (
                        exitsPast(statement.tried, setOf(), ownLoop = false, fn) +
                            exitsPast(statement.recover, setOf(), ownLoop = false, fn)
                        ).any { takes(end, it) } -> {
                    out.addAll(escapingTry(statement, rest, end, fn))
                    return out
                }
                statement is TmpL.IfStatement && rest.isNotEmpty() && ifEscapes(statement, end, fn) -> {
                    out.addAll(escapingIf(statement, rest, end, fn))
                    return out
                }
                escapes(statement, end, fn) -> {
                    val (loop, label) = loopOf(statement)!!
                    out.addAll(loop(loop, label, fn, Folded(rest, end)))
                    return out
                }
                else -> out.addAll(statement(statement, fn))
            }
            i += 1
        }
        out.addAll(fallThrough(end, flat.lastOrNull()?.pos ?: lang.temper.log.unknownPos, fn))
        return out
    }

    /**
     * [list] with each cell-held local function moved, if need be, to just after
     * the last declaration in [list] of a local it captures.
     *
     * The frontend hoists a local function above the statements before it when
     * something calls it first: `a` calling `b`, declared after `a`, puts `b`
     * above both. An Elixir closure captures values when it is made, so a `b`
     * made up there names locals that are not bound yet, and the module does not
     * compile. Such a `b` is in a cell made at the top of the block, which is
     * what `a` calls through, so only the store into the cell moves. Nothing can
     * call `b` before the move's target: that call would read a `let` before it
     * is initialized, which the frontend and js both reject.
     */
    private fun madeAfterWhatTheyCapture(list: List<TmpL.Statement>): List<TmpL.Statement> {
        val out = list.toMutableList()
        for (decl in list.filterIsInstance<TmpL.LocalFunctionDeclaration>()) {
            if (decl.name.name !in boxed) continue
            val captured = capturedBy(decl)
            val from = out.indexOf(decl)
            val last = out.indices.lastOrNull { j ->
                j > from && (out[j] as? TmpL.LocalDeclaration)?.let { nameOf(it.name) in captured } == true
            } ?: continue
            out.removeAt(from)
            out.add(last, decl) // `last` shifted down by one with the removal
        }
        return out
    }

    /** The locals [decl]'s body reads or assigns that it does not declare itself. */
    private fun capturedBy(decl: TmpL.LocalFunctionDeclaration): Set<ResolvedName> {
        val declaredInside = mutableSetOf<ResolvedName>()
        val usedInside = mutableSetOf<ResolvedName>()
        decl.parameters.parameters.forEach { f -> nameOf(f.name)?.let(declaredInside::add) }
        decl.body.boundaryDescent { inner ->
            when (inner) {
                is TmpL.LocalDeclaration -> nameOf(inner.name)?.let(declaredInside::add)
                is TmpL.LocalFunctionDeclaration -> {
                    nameOf(inner.name)?.let(declaredInside::add)
                    inner.parameters.parameters.forEach { f -> nameOf(f.name)?.let(declaredInside::add) }
                }
                is TmpL.Reference -> nameOf(inner.id)?.let(usedInside::add)
                is TmpL.FnReference -> nameOf(inner.id)?.let(usedInside::add)
                is TmpL.Assignment -> nameOf(inner.left)?.let(usedInside::add)
                else -> {}
            }
            true
        }
        return usedInside - declaredInside
    }

    /** An End whose fall-through is the whole list's value, so an `if` at the end can carry it in both arms. */
    private fun endIsTail(end: End): Boolean = when (end) {
        End.Discard -> false
        is End.Then -> endIsTail(end.outer)
        else -> true
    }

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
        is End.Then -> statements(end.rest, end.outer, fn)
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
        else -> loop.selfCall(pos, loop.carried.map { varRef(pos, it) })
    }

    /** Leaving a loop or block with its variables. */
    private fun leave(pos: Position, loop: LoopContext): Elixir.Expr {
        loop.leaves = true
        return when {
            loop.tryMode -> Elixir.TupleLit(pos, listOf(Elixir.Atom(pos, DONE), packed(pos, loop.carried)))
            else -> continuing(pos, loop, packed(pos, loop.carried))
        }
    }

    /** A loop's variables as it ends normally: `{:cont, vars}` if it is escaping. */
    private fun continuing(pos: Position, loop: LoopContext, vars: Elixir.Expr): Elixir.Expr = when {
        loop.escaping -> Elixir.TupleLit(pos, listOf(Elixir.Atom(pos, CONT), vars))
        else -> vars
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
                exitTo(pos, Exit.Return, value, end, fn)
            }
            is TmpL.BreakStatement -> {
                val target = target(statement.label?.id, isContinue = false, fn, statement)
                exitTo(pos, Exit.Break(target), packed(pos, target.carried), end, fn)
            }
            is TmpL.ContinueStatement -> {
                val target = target(statement.label?.id, isContinue = true, fn, statement)
                exitTo(pos, Exit.Continue(target), packed(pos, target.carried), end, fn)
            }
            else -> error("not an exit: $statement")
        }
    }

    /**
     * Taking [exit], carrying [payload] (the result, or the target's
     * variables), from a list that ends as [end]: by value where [end] can
     * take it, handed back to an escaping loop's call site, or thrown.
     */
    private fun exitTo(
        pos: Position,
        exit: Exit,
        payload: Elixir.Expr,
        end: End,
        fn: FunctionContext,
    ): List<Elixir.BlockItem> =
        when {
            end is End.Then -> when {
                exit is Exit.Break && exit.target === end.block -> statements(end.rest, end.outer, fn)
                else -> exitTo(pos, exit, payload, end.outer, fn)
            }
            exit is Exit.Return && end is End.Return -> listOf(payload)
            exit is Exit.Break && (end is End.Again && end.loop === exit.target) ->
                listOf(leave(pos, exit.target))
            exit is Exit.Break && (end is End.Leave && end.block === exit.target) ->
                listOf(leave(pos, exit.target))
            exit is Exit.Continue && end is End.Again && end.loop === exit.target -> listOf(again(pos, exit.target))
            escapingContext(end) != null -> {
                escapingContext(end)!!.escaped.add(exit)
                listOf(exitTuple(pos, exit, payload, fn))
            }
            else -> {
                when (exit) {
                    Exit.Return -> fn.threwReturn = true
                    is Exit.Break -> exit.target.threw = true
                    is Exit.Continue -> exit.target.threw = true
                }
                listOf(localCall(pos, "throw", listOf(exitTuple(pos, exit, payload, fn))))
            }
        }

    /** `{:temper_return, :tag, value}` and the like: what [exit] throws, or an escaping loop hands back. */
    private fun exitTuple(pos: Position, exit: Exit, payload: Elixir.Expr, fn: FunctionContext): Elixir.Expr =
        taggedTuple(pos, exitKind(exit), exitTag(exit, fn), payload)

    private fun exitKind(exit: Exit) = when (exit) {
        Exit.Return -> RETURN
        is Exit.Break -> BREAK
        is Exit.Continue -> CONTINUE
    }

    private fun exitTag(exit: Exit, fn: FunctionContext) = when (exit) {
        Exit.Return -> fn.returnTag ?: TODO("return outside a function")
        is Exit.Break -> exit.target.tag
        is Exit.Continue -> exit.target.tag
    }

    /** Whether a list ending as [end] takes [exit] without throwing it. */

    /** The escaping loop or `if` a list ending as [end] hands its exits back to, if any. */
    private fun escapingContext(end: End): LoopContext? = when (end) {
        is End.Again -> end.loop.takeIf { it.escaping }
        is End.Leave -> end.block.takeIf { it.escaping }
        else -> null
    }

    private fun takes(end: End, exit: Exit): Boolean = when (end) {
        End.Return -> exit is Exit.Return
        is End.Then -> (exit is Exit.Break && exit.target === end.block) || takes(end.outer, exit)
        is End.Again -> end.loop.escaping || when (exit) {
            is Exit.Break -> exit.target === end.loop
            is Exit.Continue -> exit.target === end.loop
            Exit.Return -> false
        }
        is End.Leave -> end.block.escaping || (exit is Exit.Break && exit.target === end.block)
        End.Discard, is End.Yield -> false
    }

    /** The loop [statement] is, and its label. */
    private fun loopOf(statement: TmpL.Statement): Pair<TmpL.WhileStatement, TmpL.Id?>? = when (statement) {
        is TmpL.WhileStatement -> statement to null
        is TmpL.LabeledStatement -> (statement.statement as? TmpL.WhileStatement)?.let { it to statement.label.id }
        else -> null
    }

    /**
     * Whether [statement] is a loop to translate escaping: one with an exit
     * past it, a `return` or a jump to an enclosing label, that the list it
     * sits in, ending as [end], takes without a throw.
     */
    private fun escapes(statement: TmpL.Statement, end: End, fn: FunctionContext): Boolean {
        val (loop, label) = loopOf(statement) ?: return false
        return exitsPast(loop.body, setOfNotNull(label?.let { nameOf(it) }), ownLoop = true, fn).any { takes(end, it) }
    }

    /**
     * The exits in [body] that leave it: `return`s, and jumps to a label not
     * among [inside] or declared in [body]. With [ownLoop], [body] is a loop's,
     * and an unlabeled `break` or `continue` in it is that loop's own; without,
     * it targets the loop around [body].
     */
    private fun exitsPast(
        body: TmpL.Statement,
        inside: Set<ResolvedName>,
        ownLoop: Boolean,
        fn: FunctionContext,
    ): List<Exit> {
        val exits = mutableListOf<Exit>()
        fun jump(id: TmpL.Id?, inside: Set<ResolvedName>, inLoop: Boolean, isContinue: Boolean) {
            val target = if (id == null) {
                if (inLoop) return // this loop's, or one inside it
                fn.loops.lastOrNull { it.isLoop } ?: return
            } else {
                val name = nameOf(id) ?: return
                if (name in inside) return
                fn.loops.lastOrNull { it.label == name && (it.isLoop || !isContinue) } ?: return
            }
            exits.add(if (isContinue) Exit.Continue(target) else Exit.Break(target))
        }
        fun walk(s: TmpL.Statement, inside: Set<ResolvedName>, inLoop: Boolean) {
            when (s) {
                is TmpL.ReturnStatement -> exits.add(Exit.Return)
                is TmpL.BreakStatement -> jump(s.label?.id, inside, inLoop, isContinue = false)
                is TmpL.ContinueStatement -> jump(s.label?.id, inside, inLoop, isContinue = true)
                is TmpL.BlockStatement -> s.statements.forEach { walk(it, inside, inLoop) }
                is TmpL.IfStatement -> {
                    walk(s.consequent, inside, inLoop)
                    s.alternate?.let { walk(it, inside, inLoop) }
                }
                is TmpL.WhileStatement -> walk(s.body, inside, inLoop = true)
                is TmpL.LabeledStatement -> walk(s.statement, inside + listOfNotNull(nameOf(s.label.id)), inLoop)
                is TmpL.TryStatement -> {
                    walk(s.tried, inside, inLoop)
                    walk(s.recover, inside, inLoop)
                }
                else -> {}
            }
        }
        walk(body, inside, ownLoop)
        return exits
    }

    /**
     * An `if` in the middle of a list with an exit inside it that the list,
     * ending as [end], takes. Its arms hand back `{:cont, vars}` or the exit,
     * and a `case` around it goes on with [rest] or takes the exit:
     *
     * ```
     * ex_step_3 = if c do ... {:cont, digit} else ... {:temper_break, :ex_block_1, return} end
     * case ex_step_3 do
     *   {:cont, digit} -> ...rest...
     *   {:temper_break, :ex_block_1, return} -> return
     * end
     * ```
     *
     * Its arms used to hand back variables only, so an exit inside one had
     * no way out but a throw. std's JSON parser was most of what still threw.
     */
    private fun escapingIf(
        statement: TmpL.IfStatement,
        rest: List<TmpL.Statement>,
        end: End,
        fn: FunctionContext,
    ): List<Elixir.BlockItem> {
        val pos = statement.pos
        val carried = assignedOuter(statement, fn)
        val context = LoopContext(
            label = null,
            isLoop = false,
            tag = names.gensym("if").outputNameText,
            carried = carried,
            self = null,
        ).apply { escaping = true }
        val arms = End.Leave(context)
        val value = ifOf(
            pos,
            expression(statement.test, fn),
            statements(listOf(statement.consequent), arms, fn),
            statements(listOfNotNull(statement.alternate), arms, fn).ifEmpty { listOf(leave(pos, context)) },
        )
        // `case if ... end do` does not parse: the if's value is named first
        val step = names.gensym("step")
        return listOf(
            Elixir.Match(pos, left = Elixir.Id(pos, step), right = value),
            // when every arm exits, a `{:cont, _}` clause is one Elixir says never matches
            foldedCase(pos, Elixir.Id(pos, step), context, Folded(rest, end), fn, endsNormally = context.leaves),
        )
    }

    /** Whether [statement] is an `if` to translate escaping: see [escapingIf]. */
    private fun ifEscapes(statement: TmpL.IfStatement, end: End, fn: FunctionContext): Boolean =
        (listOf(statement.consequent) + listOfNotNull(statement.alternate))
            .flatMap { exitsPast(it, setOf(), ownLoop = false, fn) }
            .any { takes(end, it) }

    /** A statement cheap and simple enough to translate once per way out of a block: see [foldedBlock]. */
    private fun copyable(statement: TmpL.Statement): Boolean = when (statement) {
        is TmpL.ReturnStatement, is TmpL.ExpressionStatement, is TmpL.Assignment -> {
            var size = 0
            statement.boundaryDescent {
                size += 1
                true
            }
            size <= COPYABLE_SIZE
        }
        else -> false
    }

    private fun target(label: TmpL.Id?, isContinue: Boolean, fn: FunctionContext, at: TmpL.Statement): LoopContext {
        val name = label?.let { nameOf(it) }
        return fn.loops.lastOrNull { loop ->
            (name == null || loop.label == name) && (loop.isLoop || (!isContinue && name != null))
        } ?: TODO("jump with no target: $at")
    }

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
    ): Elixir.Expr {
        val thenBlock = Elixir.Block(pos, then.ifEmpty { listOf(Elixir.NilLit(pos)) })
        // `if a ... else if b ... else ... end end` reads as the `cond` it is
        val arms = when (val only = otherwise.singleOrNull()) {
            is Elixir.If -> listOf(
                Elixir.CondArm(pos, only.test.deepCopy(), only.then.deepCopy()),
                Elixir.CondArm(pos, Elixir.BoolLit(pos, true), only.otherwise?.deepCopy() ?: nilBlock(pos)),
            )
            is Elixir.Cond -> only.arms.map { it.deepCopy() }
            else -> null
        }
        if (arms != null) return Elixir.Cond(pos, listOf(Elixir.CondArm(pos, test, thenBlock)) + arms)
        val otherwiseBlock = Elixir.Block(pos, otherwise.ifEmpty { listOf(Elixir.NilLit(pos)) })
        return nilCase(pos, test, thenBlock, otherwiseBlock)
            ?: Elixir.If(pos, test = test, then = thenBlock, otherwise = otherwiseBlock)
    }

    /**
     * `if x === nil`, which is how the frontend writes `x ?? y` and every null
     * check, as `case x do nil -> ...; x -> ... end`. It means the same, reads
     * as the Elixir it is, and the second clause tells Dialyzer that `x` is not
     * nil there: from an `if`, `x` keeps its nil, and a function returning
     * `finish(out) ?? []` looked as if it might return nil.
     */
    private fun nilCase(pos: Position, test: Elixir.Expr, then: Elixir.Block, otherwise: Elixir.Block): Elixir.Expr? {
        val op = test as? Elixir.Operation ?: return null
        val subject = op.left as? Elixir.Id ?: return null
        if (op.right !is Elixir.NilLit) return null
        val (ifNil, ifNot) = when (op.operator.operator) {
            ElixirOperator.StrictEquals -> then to otherwise
            ElixirOperator.StrictNotEquals -> otherwise to then
            else -> return null
        }
        return Elixir.Case(
            pos,
            subject = Elixir.Id(subject.pos, subject.outName),
            clauses = listOf(
                Elixir.Clause(pos, pattern = Elixir.NilLit(pos), body = ifNil),
                Elixir.Clause(pos, pattern = Elixir.Id(subject.pos, subject.outName), body = ifNot),
            ),
        )
    }

    private fun nilBlock(pos: Position) = Elixir.Block(pos, listOf(Elixir.NilLit(pos)))

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
            is TmpL.GarbageStatement -> listOf(garbage(statement.pos, statement.diagnostic))
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
            is TmpL.WhileStatement -> loop(statement, label = null, fn, folded = null)
            is TmpL.LabeledStatement -> when (val inner = statement.statement) {
                is TmpL.WhileStatement -> loop(inner, label = statement.label.id, fn, folded = null)
                else -> labeledBlock(statement, fn)
            }
            is TmpL.TryStatement -> tryOf(statement, fn)
            is TmpL.SetBackedProperty -> listOf(setBacked(statement, fn))
            is TmpL.LocalFunctionDeclaration -> localFunction(statement, fn)
            is TmpL.SetAbstractProperty -> {
                val subject = statement.left.subject as? TmpL.Expression ?: TODO("setter subject: $statement")
                val property = propertyText(statement.left.property)
                if (classLacksAccessor(subject, property, setter = true)) {
                    val message = "write of .$property on a class that does not declare it"
                    return listOf(garbage(pos, TmpL.Diagnostic(pos, message)))
                }
                val value = expression(statement.right, fn)
                listOf(dispatch(pos, subject, setterName(property), listOf(value), fn))
            }
            else -> TODO("statement: $statement")
        }
    }

    // ── Loops ────────────────────────────────────────────────────────────

    /**
     * A loop is a function of its own, `defp`, next to the one it came from:
     * the variables it reads are passed in, the ones it assigns are passed
     * round and handed back.
     *
     * ```
     * defp sum_loop_1(xs, i, total) do
     *   if i < TemperCore.List.length(xs) do
     *     ...body...
     *     sum_loop_1(xs, i, total)
     *   else
     *     {i, total}
     *   end
     * end
     *
     * {i, total} = sum_loop_1(xs, i, total)
     * ```
     *
     * It was a closure passed to itself, `loop.(loop, i, total)`, and
     * Dialyzer types a call through a closure argument `any()`: whatever
     * came out of a loop, and every function returning it, could have any
     * spec at all. A named function it infers like any other.
     *
     * With [folded], the loop is escaping: an exit past it, which would
     * otherwise be thrown, ends it with the same tuple, and the call site
     * takes each such exit and folds in the rest of the list:
     *
     * ```
     * case loop.(loop, a, b) do
     *   {:cont, {a, b}} -> ...rest...
     *   {:temper_return, :ex_return_0, value} -> value
     * end
     * ```
     *
     * Dialyzer cannot see through a throw: a function that returned from
     * inside a loop had the result `any()`, whatever its spec said.
     */
    private fun loop(
        statement: TmpL.WhileStatement,
        label: TmpL.Id?,
        fn: FunctionContext,
        folded: Folded?,
    ): List<Elixir.BlockItem> {
        val pos = statement.pos
        val carried = assignedOuter(statement, fn)
        liftedCount += 1
        val selfName = OutName("${currentFunction}_loop_$liftedCount", null)
        val context = LoopContext(
            label = label?.let { nameOf(it) },
            isLoop = true,
            tag = names.gensym("loop").outputNameText,
            carried = carried,
            self = selfName,
        ).apply { escaping = folded != null }
        fun body(): Elixir.Expr {
            context.leaves = false
            fn.loops.addLast(context)
            scopes.addLast(mutableSetOf())
            try {
                val iteration = statements(listOf(statement.body), End.Again(context), fn)
                val test = expression(statement.test, fn)
                val exitValue = continuing(pos, context, packed(pos, carried))
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
                                            listOf(context.selfCall(pos, carried.map { varRef(pos, it) })),
                                        ),
                                    ),
                                    Elixir.Clause(
                                        pos,
                                        pattern = Elixir.TuplePattern(pos, listOf(Elixir.Atom(pos, DONE), caught())),
                                        body = Elixir.Block(pos, listOf(continuing(pos, context, caught()))),
                                    ),
                                ) + listOfNotNull(passOn(pos).takeIf { context.escaping }),
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
        // `while (true)` is its body alone: no `if true`, and no way to end but an exit
        val forever = ((ifExpr as? Elixir.If)?.test as? Elixir.BoolLit)?.value == true
        val items = if (forever) (ifExpr as Elixir.If).then.exprs.map { it.deepCopy() } else listOf(ifExpr)
        val endsNormally = !forever || context.leaves || context.threw
        fun params() = carried.map { varId(pos, it) }
        val free =
            freeVariables(Elixir.Fn(pos, params = params(), body = Elixir.Block(pos, items.map { it.deepCopy() })))
        fun freeIds() = free.map { Elixir.Id(pos, OutName(it, null)) }
        // found by walking, not kept as they were made: an `if` folded into a
        // `cond` is a copy
        items.flatMap { selfCalls(it, selfName) }.forEach { call ->
            // the setter empties the list it replaces, so keep a copy
            val args = call.args.toList()
            call.args = listOf()
            call.args = freeIds() + args
        }
        val def = Elixir.FunDef(
            pos,
            id = Elixir.Id(pos, selfName),
            params = freeIds() + params(),
            body = Elixir.Block(pos, items),
        ).apply { isPrivate = true }
        // term() throughout: Dialyzer still infers what the loop returns from
        // its body, and checks the function the loop came from against that
        val anything = { specs.builtin(pos, "term") }
        val specParams = def.params.map { anything() }
        lifted.add(Elixir.TypeSpec(pos, Elixir.Id(pos, selfName), params = specParams, result = anything()))
        lifted.add(def)
        val call = Elixir.Call(
            pos, callee = Elixir.Id(pos, selfName),
            args =
            freeIds() + carried.map { varRef(pos, it) },
        )
        if (folded == null) {
            return listOf(
                if (carried.isEmpty() || !endsNormally) {
                    call
                } else {
                    Elixir.Match(pos, left = packedPattern(pos, carried), right = call)
                },
            )
        }
        return listOf(foldedCase(pos, call, context, folded, fn, endsNormally))
    }

    /**
     * `case subject do ... end` where [subject] is an escaping loop's call or
     * an escaping `if`: `{:cont, vars}` goes on with the rest of the list,
     * and each exit [context] handed back is taken where the case stands.
     */
    private fun foldedCase(
        pos: Position,
        subject: Elixir.Expr,
        context: LoopContext,
        folded: Folded,
        fn: FunctionContext,
        endsNormally: Boolean,
    ): Elixir.Expr {
        val carried = context.carried
        // what follows a loop nothing leaves normally is never reached
        val going = if (!endsNormally) {
            null
        } else {
            val rest = statements(folded.rest, folded.end, fn).ifEmpty { listOf(Elixir.NilLit(pos)) }
            Elixir.Clause(
                pos,
                pattern = Elixir.TuplePattern(pos, listOf(Elixir.Atom(pos, CONT), packedPatternOrWild(pos, carried))),
                body = Elixir.Block(pos, rest),
            )
        }
        val exits = context.escaped.map { exit ->
            val valueName = names.gensym("value")
            val (pattern, payload) = when (exit) {
                Exit.Return -> Elixir.Id(pos, valueName) to Elixir.Id(pos, valueName)
                is Exit.Break -> packedPatternOrWild(pos, exit.target.carried) to packed(pos, exit.target.carried)
                is Exit.Continue -> packedPatternOrWild(pos, exit.target.carried) to packed(pos, exit.target.carried)
            }
            Elixir.Clause(
                pos,
                pattern = taggedPattern(pos, exitKind(exit), exitTag(exit, fn), pattern),
                body = Elixir.Block(pos, exitTo(pos, exit, payload, folded.end, fn)),
            )
        }
        val clauses = listOfNotNull(going) + exits
        return if (clauses.isEmpty()) subject else Elixir.Case(pos, subject = subject, clauses = clauses)
    }

    /** The calls of [name] in [tree]. */
    private fun selfCalls(tree: Elixir.Tree, name: OutName): List<Elixir.Call> {
        val out = mutableListOf<Elixir.Call>()
        fun walk(node: Elixir.Tree) {
            if (node is Elixir.Call && node.callee.outName.outputNameText == name.outputNameText) out.add(node)
            for (i in 0 until node.childCount) node.childOrNull(i)?.let(::walk)
        }
        walk(tree)
        return out
    }

    /** The rest of a list, folded into an escaping loop's call site, and how that list ends. */
    private class Folded(val rest: List<TmpL.Statement>, val end: End)

    /** `exit -> exit`: in try mode, an escaping loop's exit past it goes on to the call site. */
    private fun passOn(pos: Position): Elixir.Clause {
        val name = names.gensym("exit")
        val body = Elixir.Block(pos, listOf(Elixir.Id(pos, name)))
        return Elixir.Clause(pos, pattern = Elixir.Id(pos, name), body = body)
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

    /**
     * A labeled block with the [rest] of its list folded in, at each way out
     * of it. The frontend writes a `return` from inside a loop as an
     * assignment and a `break` out of a block around the function's body, with
     * `return v;` after it; folded, those are the function's own results, not
     * throws. [rest] is copied once per way out, so it is held to [copyable]
     * statements.
     */
    private fun foldedBlock(
        statement: TmpL.LabeledStatement,
        rest: List<TmpL.Statement>,
        end: End,
        fn: FunctionContext,
    ): List<Elixir.BlockItem> {
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
            statements(listOf(statement.statement), End.Then(context, rest, end), fn)
        } finally {
            scopes.removeLast()
            fn.loops.removeLast()
        }
        if (!context.threw) return items
        // a break from somewhere the folding could not reach
        return listOf(
            Elixir.Try(
                pos,
                body = Elixir.Block(pos, items),
                catches = listOf(
                    Elixir.Clause(
                        pos,
                        pattern = taggedPattern(pos, BREAK, context.tag, packedPatternOrWild(pos, carried)),
                        body = Elixir.Block(pos, statements(rest, end, fn).ifEmpty { listOf(Elixir.NilLit(pos)) }),
                    ),
                ),
            ),
        )
    }

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

    /**
     * `{a} = try do ... rescue _ in TemperCore.Bubble -> ... end`, or with
     * [returning], the last statement of a function, each arm returning: a
     * `return` in either is the function's result, not a throw.
     *
     * Only a function's end can be carried into a `try` this way. A loop
     * body's end goes round again, and a call made inside a `try` is not a
     * tail call: each iteration would hold a frame, and a bubble in a later
     * one would be rescued by an earlier one.
     */
    private fun tryOf(
        statement: TmpL.TryStatement,
        fn: FunctionContext,
        returning: Boolean = false,
    ): List<Elixir.BlockItem> {
        val pos = statement.pos
        val vars = if (returning) listOf() else assignedOuter(statement, fn)
        val value = tryValue(statement, if (returning) End.Return else End.Yield(vars), fn)
        return listOf(if (vars.isEmpty()) value else Elixir.Match(pos, left = packedPattern(pos, vars), right = value))
    }

    /**
     * A `try` in a list whose end takes an exit inside it, the way
     * [escapingIf] handles an `if`: its arms end `{:cont, vars}` or with the
     * exit, and the `case` that takes either is outside the `try`, so a loop
     * going round again from there is still a tail call.
     */
    private fun escapingTry(
        statement: TmpL.TryStatement,
        rest: List<TmpL.Statement>,
        end: End,
        fn: FunctionContext,
    ): List<Elixir.BlockItem> {
        val pos = statement.pos
        val context = LoopContext(
            label = null,
            isLoop = false,
            tag = names.gensym("try").outputNameText,
            carried = assignedOuter(statement, fn),
            self = null,
        ).apply { escaping = true }
        val step = names.gensym("step")
        return listOf(
            Elixir.Match(pos, left = Elixir.Id(pos, step), right = tryValue(statement, End.Leave(context), fn)),
            foldedCase(pos, Elixir.Id(pos, step), context, Folded(rest, end), fn, endsNormally = context.leaves),
        )
    }

    /** `try do ... rescue _ in TemperCore.Bubble -> ... end`, both arms ending as [end]. */
    private fun tryValue(statement: TmpL.TryStatement, end: End, fn: FunctionContext): Elixir.Expr {
        val pos = statement.pos
        return Elixir.Try(
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
        is TmpL.GarbageExpression -> garbage(expression.pos, expression.diagnostic)
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
                    in moduleGlobals -> globalGet(expression.pos, name)
                    in moduleFunctions -> capture(expression.pos, name)
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

    /** `&Temper.Lib.name/2`, or `&name/2` for a private function from inside the root module */
    private fun capture(pos: Position, name: ResolvedName): Elixir.Expr =
        if (inRoot && name in private) {
            Elixir.Capture(
                pos,
                fn = Elixir.Id(pos, functionName(name)),
                arity = Elixir.NumberLit(pos, moduleFunctions.getValue(name)),
            )
        } else {
            capture(pos, functionModule(name), name, moduleFunctions.getValue(name))
        }

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
        val given = call.parameters.map { expression(it, fn) }
        return when (val callee = call.fn) {
            is TmpL.InlineSupportCodeWrapper ->
                (callee.supportCode as ElixirInlineSupportCode).callFactory(pos, given)
            is TmpL.FnReference -> {
                val name = canonical(callee.id.name)
                val args = arguments(pos, callee.type, given)
                when (name) {
                    // a module function an assignment rebinds: whatever it holds now
                    in moduleGlobals -> Elixir.AnonCall(pos, fn = globalGet(callee.pos, name), args = args)
                    // qualified, so it works from inside a class module too, and
                    // never meets a Kernel import of the same name
                    in moduleFunctions -> if (inRoot && name in private) {
                        Elixir.Call(pos, callee = Elixir.Id(pos, functionName(name)), args = args)
                    } else {
                        remoteCall(pos, moduleOf(pos, functionModule(name)), functionName(name).outputNameText, args)
                    }
                    in externals -> when (val external = externals.getValue(name)) {
                        is ExternalFunction ->
                            remoteCall(pos, moduleOf(pos, external.module), functionName(name).outputNameText, args)
                        is ExternalValue -> Elixir.AnonCall(pos, fn = globalGet(callee.pos, name), args = args)
                    }
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
                arguments(pos, constructorSig(callee) ?: callee.type, given),
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
                        dispatch(pos, subject, method, args, fn)
                    }
                }
            }
            is TmpL.FunInterfaceCallable ->
                Elixir.AnonCall(pos, fn = expression(callee.expr, fn), args = given)
            is TmpL.GarbageCallable -> garbage(pos, callee.diagnostic)
        }
    }

    /**
     * The arguments a call passes, from those it was given: omitted optional
     * ones become nil (the body tests for null itself).
     *
     * A call the frontend could not type-check carries [lang.temper.type2.invalidSig], which
     * declares no inputs at all. Its real arity is unknown, so its arguments
     * go as written.
     */
    private fun arguments(
        pos: Position,
        sig: lang.temper.type2.Signature2,
        given: List<Elixir.Expr>,
    ): List<Elixir.Expr> {
        if (sig == lang.temper.type2.invalidSig) return given
        val fixed = sig.requiredInputTypes.size - (if (sig.hasThisFormal) 1 else 0) + sig.optionalInputTypes.size
        return if (given.size < fixed) given + List(fixed - given.size) { Elixir.NilLit(pos) } else given
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
        val formals = decl.parameters.parameters.map { it.name }
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
     * to find a super implementation at run time. A body from another
     * library's interface is a [forwarder] to that interface's module.
     */
    private fun translateType(decl: TmpL.TypeDeclaration): Elixir.ModuleDef {
        val pos = decl.pos
        val module = typeModule(decl)
        val isClass = decl.kind == TmpL.TypeDeclarationKind.Class
        if (!isClass && decl.kind != TmpL.TypeDeclarationKind.Interface) TODO("${decl.kind} declaration: ${decl.name}")
        val inherited = if (isClass) inheritance(decl) else null
        val flattened = inherited?.members ?: decl.members.filterIsInstance<TmpL.Member>()
        val isStruct = isClass && isImu(decl)
        val isActor = isClass && decl.metadata.any { it.key.symbol == lang.temper.value.actorSymbol }
        if (isActor && isStruct) TODO("class ${decl.name} is both @imu and @actor")
        currentClassIsActor = isActor
        currentClassIsExported = decl.name.name is lang.temper.name.ExportedName
        if (isStruct && !hasNoWritesAfterConstruction(flattened)) {
            TODO("@imu class ${decl.name} writes a property outside its constructor")
        }
        val fields = flattened.filterIsInstance<TmpL.InstanceProperty>()
            .filter { it.memberShape.abstractness == lang.temper.type.Abstractness.Concrete }
            .map { fieldText(it.name) }
        val items = mutableListOf<Elixir.ModuleItem>()
        docAttr(pos, "moduledoc", decl.documentation)?.let(items::add)
        if (isStruct) items.add(Elixir.StructDef(pos, fields.map { Elixir.Atom(pos, it) }))
        items.add(typeT(decl, module, flattened, isStruct, isActor, isClass))
        val supertypes = Elixir.FunDef(
            pos,
            id = Elixir.Id(pos, OutName(SUPERTYPES, null)),
            body = Elixir.Block(
                pos,
                listOf(Elixir.ListLit(pos, (listOf(module) + ancestorModules(decl)).map { moduleOf(pos, it) })),
            ),
        )
        val modules = Elixir.ListType(pos, specs.builtin(pos, "module"))
        items.add(Elixir.TypeSpec(pos, Elixir.Id(pos, OutName(SUPERTYPES, null)), result = modules))
        items.add(supertypes)
        val self = { specs.remote(pos, module, "t") }
        for (member in flattened) {
            when (member) {
                is TmpL.InstanceProperty -> {}
                is TmpL.Constructor -> if (isClass) {
                    val fn = within(CONSTRUCTOR) {
                        names.withLocals(declaredIn(member), actorThis(member.parameters)) {
                            constructor(member, module, isStruct, fields)
                        }
                    }
                    items.add(spec(fn, memberFormals(member), self(), fromElixir = fromElixir(member)))
                    items.add(fn)
                    items.addAll(takeLifted())
                }
                is TmpL.NormalMethod -> member.body?.let {
                    items.addAll(specced(member, names.sanitize(member.dotName.dotNameText), module, isStruct, self()))
                }
                is TmpL.Getter -> member.body?.let {
                    items.addAll(specced(member, getterName(member.dotName.dotNameText), module, isStruct, self()))
                }
                is TmpL.Setter -> member.body?.let {
                    items.addAll(specced(member, setterName(member.dotName.dotNameText), module, isStruct, self()))
                }
                is TmpL.StaticMethod -> member.body?.let {
                    items.addAll(specced(member, names.sanitize(member.dotName.dotNameText), module, isStruct, null))
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
                    // a loop in a function made here runs in the library's init
                    functions.addAll(takeLifted())
                }
            }
        }
        inherited?.forwarded?.forEach { items.addAll(forwarder(pos, it, self())) }
        return Elixir.ModuleDef(pos, name = moduleOf(pos, module), items = items)
    }

    /**
     * A member the class being translated inherits from another library's
     * interface, as a call of the function that interface's module defines:
     *
     *     @spec greet(Temper.App.Direct.t(), String.t(), String.t() | nil) :: String.t()
     *     def greet(this, other, punct) do
     *       Temper.Base.Named.greet(this, other, punct)
     *     end
     *
     * The body there calls back through `TemperCore.call`, as an interface's
     * body does, so it reaches this class's own getters and methods. An
     * actor's runs inside the actor, as a body copied into it would.
     */
    private fun forwarder(pos: Position, inherited: Forwarded, self: Elixir.TypeExpr): List<Elixir.ModuleItem> {
        val method = inherited.method
        val what = "${method.enclosingType.name}.${method.symbol.text}"
        val sig = method.descriptor ?: TODO("inherited $what has no signature")
        if (!sig.hasThisFormal) TODO("inherited $what has no this")
        val name = when (method.methodKind) {
            MethodKind.Getter -> getterName(method.symbol.text)
            MethodKind.Setter -> setterName(method.symbol.text)
            else -> names.sanitize(method.symbol.text)
        }
        val inputs = sig.requiredInputTypes.drop(1) + sig.optionalInputTypes
        val firstOptional = sig.requiredInputTypes.size - 1
        val thisText = if (currentClassIsActor) "server" else "this"
        val taken = mutableSetOf(thisText)
        val hints = method.parameterInfo?.names ?: listOf()
        val paramTexts = inputs.indices.map { i ->
            val hint = hints.getOrNull(i + 1)?.text?.let(names::sanitize) ?: "arg"
            var text = hint
            var n = 1
            while (!taken.add(text)) text = "${hint}_${n++}"
            text
        }
        val id = { text: String -> Elixir.Id(pos, OutName(text, null)) }
        val call = remoteCall(pos, moduleOf(pos, inherited.module), name, (listOf(thisText) + paramTexts).map(id))
        val body = if (currentClassIsActor) {
            actorCall(pos, "run", listOf(id(thisText), Elixir.Fn(pos, body = Elixir.Block(pos, listOf(call)))))
        } else {
            call
        }
        val fn = Elixir.FunDef(
            pos,
            id = id(name),
            params = (listOf(thisText) + paramTexts).map(id),
            body = Elixir.Block(pos, listOf(body)),
        )
        val fromElixir = currentClassIsExported && method.visibility == lang.temper.type.Visibility.Public
        val params = inputs.mapIndexed { i, type ->
            val spec = specs.of(pos, type).let { if (fromElixir) specs.acceptingPlainLists(it) else it }
            if (i >= firstOptional) specs.orNil(pos, spec) else spec
        }
        val result = specs.of(pos, sig.returnType2)
        return listOf(Elixir.TypeSpec(pos, id(name), params = listOf(self) + params, result = result), fn)
    }

    /**
     * `@type t`, what a value of this class is: its struct for an `@imu`
     * class; for an `@actor` class or any other, the actor or heap reference
     * whose `class` is this module. Every such reference carries its class, so
     * one class's type is not another's: a `Query` passed where a `Schema`
     * belongs is a type error to Dialyzer, as it is to Temper. An interface's
     * values may be any Temper object of any class, from any library: a
     * struct, a heap reference or an actor.
     */
    private fun typeT(
        decl: TmpL.TypeDeclaration,
        module: List<String>,
        flattened: List<TmpL.Member>,
        isStruct: Boolean,
        isActor: Boolean,
        isClass: Boolean,
    ): Elixir.TypeDef {
        val pos = decl.pos
        val body = when {
            !isClass -> specs.union(
                pos,
                listOf(
                    Elixir.StructType(pos, name = elixirModule(pos, "TemperCore", "Ref")),
                    Elixir.StructType(pos, name = elixirModule(pos, "TemperCore", "Actor")),
                    specs.builtin(pos, "struct"),
                ),
            )
            isStruct -> Elixir.StructType(
                pos,
                name = moduleOf(pos, module),
                fields = flattened.filterIsInstance<TmpL.InstanceProperty>()
                    .filter { it.memberShape.abstractness == lang.temper.type.Abstractness.Concrete }
                    .map { field ->
                        val key = Elixir.Id(pos, OutName(fieldText(field.name), null))
                        Elixir.TypeField(pos, key, specs.of(field.type))
                    },
            )
            isActor -> specs.reference(pos, "Actor", module)
            else -> specs.reference(pos, "Ref", module)
        }
        return Elixir.TypeDef(pos, Elixir.LocalType(pos, Elixir.Id(pos, OutName("t", null))), body)
    }

    /** A member's declared parameters, without `this`. */
    private fun memberFormals(member: TmpL.FunctionDeclarationOrMethod): List<TmpL.Formal> {
        val thisName = member.parameters.thisName?.name
        return member.parameters.parameters.filter { nameOf(it.name) != thisName }
    }

    /** A method and its `@spec`; [self] is the type of `this`, null for a static. */
    private fun specced(
        member: TmpL.FunctionDeclarationOrMethod,
        name: String,
        module: List<String>,
        isStruct: Boolean,
        self: Elixir.TypeExpr?,
    ): List<Elixir.ModuleItem> {
        val fn = within(name) { memberDef(member, name, module, isStruct) }
        val thisSelf = if (member.parameters.thisName != null) self else null
        // an abstract method's body, or a connected one's, only raises: calls to
        // an implementation, or to support code, never reach it
        val result = if (isPanicPlaceholder(member.body)) {
            specs.builtin(member.pos, "no_return")
        } else {
            specs.of(member.returnType)
        }
        val doc = (member as? TmpL.Member)?.let { docAttr(member.pos, "doc", it.documentation) }
        val typeSpec = spec(fn, memberFormals(member), result, thisSelf, fromElixir = fromElixir(member))
        return listOfNotNull(doc, typeSpec, fn) + takeLifted()
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
        val fieldMap = Elixir.MapLit(pos, fields.map { Elixir.MapEntry(pos, Elixir.Atom(pos, it), Elixir.NilLit(pos)) })
        val blank: Elixir.Expr = when {
            isStruct -> Elixir.StructLit(pos, name = moduleOf(pos, module), fields = listOf())
            // inside the actor's new process: `this` is the actor, its fields kept there
            currentClassIsActor -> actorCall(pos, "init_self", listOf(moduleOf(pos, module), fieldMap))
            // built here, not by Heap.new, so that its class is in its type
            else -> reference(pos, "Ref", module, localCall(pos, "make_ref", listOf()))
        }
        // a ref built in place is given its fields by the heap
        val register = if (isStruct || currentClassIsActor) {
            listOf()
        } else {
            listOf(heapCall(pos, "init", listOf(varRef(pos, thisName), fieldMap.deepCopy())))
        }
        val cls = ClassContext(module, isStruct, thisName, isConstructor = true)
        val init = if (currentClassIsExported) listOf(selfInit(pos)) else listOf()
        val body = functionBody(
            pos,
            ctor.body.statements,
            cls,
            prelude = listOf(Elixir.Match(pos, left = varId(pos, thisName), right = blank)) + register +
                (if (fromElixir(ctor)) listArgs(pos, formals) else listOf()) +
                boxParams(pos, formals.map { it.name.name }),
        )
        return Elixir.FunDef(
            pos,
            id = Elixir.Id(pos, OutName(CONSTRUCTOR, null)),
            params = formals.map { idOf(it.name) },
            body = if (currentClassIsActor) {
                // `new` starts the process and runs the constructor in it
                val id = actorCall(pos, "start_id", listOf(moduleOf(pos, module), Elixir.Fn(pos, body = body)))
                val start = reference(pos, "Actor", module, id)
                Elixir.Block(pos, init + start)
            } else {
                Elixir.Block(pos, init + body.exprs.map { it.deepCopy() })
            },
        )
    }

    /**
     * `%TemperCore.Ref{class: Temper.Lib.C, id: id}`, or an `Actor`: an object
     * whose class Dialyzer can see, as it cannot in one a function returns.
     */
    private fun reference(pos: Position, kind: String, module: List<String>, id: Elixir.Expr): Elixir.Expr =
        Elixir.StructLit(
            pos,
            name = elixirModule(pos, "TemperCore", kind),
            fields = listOf(
                Elixir.KeywordEntry(pos, Elixir.Id(pos, OutName("class", null)), moduleOf(pos, module)),
                Elixir.KeywordEntry(pos, Elixir.Id(pos, OutName("id", null)), id),
            ),
        )

    private fun actorCall(pos: Position, fn: String, args: List<Elixir.Expr>): Elixir.Expr =
        remoteCall(pos, elixirModule(pos, "TemperCore", "Actor"), fn, args)

    /** A method, with its own locals named plainly. */
    private fun memberDef(
        member: TmpL.FunctionDeclarationOrMethod,
        name: String,
        module: List<String>,
        isStruct: Boolean,
    ): Elixir.FunDef = names.withLocals(declaredIn(member), actorThis(member.parameters)) {
        method(member, name, module, isStruct)
    }

    /**
     * An actor's `this` is `server`, as `GenServer.call(server, ...)` names
     * it: an identity that the registry resolves to whichever process runs
     * the actor now, not a pid, which a supervised restart would leave stale.
     * Anything else's `this` is a heap reference in the same process.
     */
    private fun actorThis(parameters: TmpL.Parameters): Map<ResolvedName, String> {
        val thisName = parameters.thisName?.name
        return if (currentClassIsActor && thisName != null) mapOf(thisName to "server") else emptyMap()
    }

    /** A method, getter, setter or static: `this` first unless static. */
    private fun method(
        member: TmpL.FunctionDeclarationOrMethod,
        name: String,
        module: List<String>,
        isStruct: Boolean,
    ): Elixir.FunDef {
        val pos = member.pos
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
                val body = functionBody(
                    pos,
                    member.body!!.statements,
                    cls,
                    prelude = (if (fromElixir(member)) listArgs(pos, formals) else listOf()) +
                        boxParams(pos, formals.map { it.name.name }),
                )
                // an actor's method runs in the actor: here if this is it, else by a call
                if (currentClassIsActor && thisName != null) {
                    val run = actorCall(pos, "run", listOf(varId(pos, thisName), Elixir.Fn(pos, body = body)))
                    Elixir.Block(pos, listOf(run))
                } else {
                    body
                }
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
        builtinGuards[name]?.let { guard -> return guard(pos, value) }
        indexChecks[name]?.let { check -> return check(pos, value) }
        val module = localType(nominalName(type))?.let { typeModule(it) } ?: externalTypeModule(type)
            ?: TODO("instanceof $name")
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
                                    listOf(Elixir.Id(pos, tmp), guard(pos, Elixir.Id(pos, tmp))),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val module = localType(nominalName(cast.checkedType))?.let { typeModule(it) }
            ?: externalTypeModule(cast.checkedType)
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
        val property = propertyText(expression.property)
        if (classLacksAccessor(expression.subject, property, setter = false)) {
            return garbage(pos, TmpL.Diagnostic(pos, "read of .$property on a class that does not declare it"))
        }
        return dispatch(pos, expression.subject, getterName(property), listOf(), fn)
    }

    /**
     * A call of a translated method, getter or setter. Temper cannot extend a
     * concrete class ("Cannot extend concrete type(s) A"), so when the
     * subject's static type is one, the object is of exactly that class and
     * the call goes straight to its module: `Temper.Std.Regex.find(r, ...)`.
     * Only an interface-typed subject needs `TemperCore.call`, which finds
     * the module from the object at run time.
     */
    private fun dispatch(
        pos: Position,
        subject: TmpL.Expression,
        method: String,
        args: List<Elixir.Expr>,
        fn: FunctionContext,
    ): Elixir.Expr {
        val receiver = expression(subject, fn)
        val module = concreteClassModule(subject)
            ?: return coreCall(pos, "call", listOf(receiver, Elixir.Atom(pos, method), Elixir.ListLit(pos, args)))
        return remoteCall(pos, moduleOf(pos, module), method, listOf(receiver) + args)
    }

    /**
     * Whether [subject]'s static type is a class of this library with no getter
     * (or, for [setter], no setter) for [property], counting inherited ones, so
     * that the class's module never defines `get_<property>` / `set_<property>`.
     * Only code the frontend rejected gets here: a class that declares its
     * constructor inputs twice keeps the constructor's `this.x = x` and its
     * reads of `this.x`, but loses the property.
     *
     * The answer is no only when every member could be seen: the class is this
     * library's, and so is everything it inherits from. A class of another
     * library, or one with an ancestor there, cannot be checked from here, so
     * its accessor call stands.
     */
    private fun classLacksAccessor(subject: TmpL.Expression, property: String, setter: Boolean): Boolean {
        val definition = (subject.passType as? lang.temper.type2.DefinedType)?.definition ?: return false
        if (definition.abstractness != lang.temper.type.Abstractness.Concrete) return false
        val decl = localType(definition.name)?.takeIf { it.kind == TmpL.TypeDeclarationKind.Class } ?: return false
        if (hasExternalAncestor(decl)) return false
        return flattenMembers(decl).none {
            val accessor = if (setter) it is TmpL.Setter else it is TmpL.Getter
            accessor && (it as TmpL.GetterOrSetter).body != null && it.dotName.dotNameText == property
        }
    }

    /** The module of [subject]'s static type when that is a concrete class, this library's or another's. */
    private fun concreteClassModule(subject: TmpL.Expression): List<String>? {
        val definition = (subject.passType as? lang.temper.type2.DefinedType)?.definition ?: return null
        if (definition.abstractness != lang.temper.type.Abstractness.Concrete) return null
        localType(definition.name)?.let { decl ->
            return if (decl.kind == TmpL.TypeDeclarationKind.Class) typeModule(decl) else null
        }
        return externalModule(definition.name)
    }

    /**
     * A read of a builtin type's property, or null when the subject's type is translated here.
     * A subject typed *Invalid* comes from code the frontend rejected, such as a value of a
     * class that declares its inputs twice. That is broken code, raised when reached, not a
     * builtin missing its support code.
     */
    private fun builtinGet(expression: TmpL.GetProperty, subject: TmpL.Expression, fn: FunctionContext): Elixir.Expr? {
        val property = propertyText(expression.property)
        val definition = (subject.passType as? lang.temper.type2.DefinedType)?.definition
        val pos = expression.pos
        if (definition == lang.temper.type.WellKnownTypes.invalidTypeDefinition) {
            return garbage(pos, TmpL.Diagnostic(pos, "read of .$property on a value of a type that did not compile"))
        }
        // AnyValue has no properties, so a read of one only survives in code the
        // frontend rejected: a parameter whose declared type does not exist is one
        if (definition == lang.temper.type.WellKnownTypes.anyValueTypeDefinition) {
            return garbage(pos, TmpL.Diagnostic(pos, "read of .$property on a value with no properties"))
        }
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

    /**
     * `x is T` for a builtin `T`. Most are Kernel guards. A Float64 may also be
     * `:infinity`, `:neg_infinity` or `:nan`; a List is a `TemperCore.Vec` or a
     * plain list from Elixir code; a ListBuilder is `Listed` too.
     */
    private val builtinGuards = mapOf<String, (Position, Elixir.Expr) -> Elixir.Expr>(
        "String" to kernelGuard("is_binary"),
        "Int" to kernelGuard("is_integer"),
        "Int32" to kernelGuard("is_integer"),
        "Int64" to kernelGuard("is_integer"),
        "Float64" to coreGuard("Float", "float?"),
        "Boolean" to kernelGuard("is_boolean"),
        "List" to coreGuard("List", "list?"),
        "Listed" to coreGuard("List", "listed?"),
    )

    private fun kernelGuard(fn: String): (Position, Elixir.Expr) -> Elixir.Expr =
        { pos, v -> localCall(pos, fn, listOf(v)) }

    private fun coreGuard(module: String, fn: String): (Position, Elixir.Expr) -> Elixir.Expr =
        { pos, v -> remoteCall(pos, elixirModule(pos, "TemperCore", module), fn, listOf(v)) }

    /** A class carries its supertypes' members that it does not redefine, nearest first. */
    private fun flattenMembers(decl: TmpL.TypeDeclaration): List<TmpL.Member> = inheritance(decl).members

    /**
     * A getter, setter or method with a body that a class inherits from an
     * interface of another library. That library's TmpL is not here to copy,
     * so the class gets a function of the same name that calls the one the
     * interface's own module defines.
     */
    private class Forwarded(val method: MethodShape, val module: List<String>)

    private class Inheritance(val members: List<TmpL.Member>, val forwarded: List<Forwarded>)

    /**
     * What [decl] has, its own members and those of every supertype that it
     * does not redefine, nearest first. A supertype of this library gives
     * its members' TmpL, to be copied; one of another library gives its
     * shape, whose members with bodies are [forwarded][Forwarded]. Builtin
     * types have nothing to give, and their supertypes are not followed.
     */
    private fun inheritance(decl: TmpL.TypeDeclaration): Inheritance {
        val byKey = linkedMapOf<String, TmpL.Member>()
        val forwarded = linkedMapOf<String, Forwarded>()
        val seen = mutableSetOf<TypeShape>(decl.typeShape)
        var level = listOf(decl.typeShape)
        while (level.isNotEmpty()) {
            for (shape in level) {
                val local = if (shape == decl.typeShape) decl else localType(shape.name)
                if (local != null) {
                    for (member in local.members.filterIsInstance<TmpL.Member>()) {
                        memberKey(member)?.takeIf { it !in forwarded }?.let { byKey.putIfAbsent(it, member) }
                    }
                } else {
                    val module = externalModule(shape.name) ?: continue
                    for (method in shape.methods) {
                        val key = methodKey(method)?.takeIf { it !in byKey && it !in forwarded } ?: continue
                        forwarded[key] = Forwarded(method, module)
                    }
                }
            }
            level = level.flatMap { shape ->
                if (shape != decl.typeShape && localType(shape.name) == null && !isExternal(shape.name)) {
                    return@flatMap listOf()
                }
                shape.superTypes.mapNotNull { (it.definition as? TypeShape)?.takeIf(seen::add) }
            }
        }
        return Inheritance(byKey.values.toList(), forwarded.values.toList())
    }

    /** As [memberKey], for another library's member: one with a body, which a class inherits. */
    private fun methodKey(method: MethodShape): String? = when {
        method.isPureVirtual -> null
        else -> when (method.methodKind) {
            MethodKind.Normal -> "fn:"
            MethodKind.Getter -> "get:"
            MethodKind.Setter -> "set:"
            MethodKind.Constructor -> null
        }?.let { it + method.symbol.text }
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

    /**
     * A class is a struct when it says it is immutable, `@imu`, which the
     * frontend enforces. Inferring it from the body would let a later version
     * that adds a setter silently turn a library's struct into a heap ref:
     * a consumer's `%Lib.Point{}` patterns stop matching, and a value that
     * crossed processes freely no longer does.
     */
    private fun isImu(decl: TmpL.TypeDeclaration): Boolean =
        decl.metadata.any { it.key.symbol == lang.temper.value.imuSymbol }

    /** No setter, and no property write outside the constructor: what a struct needs. */
    private fun hasNoWritesAfterConstruction(members: List<TmpL.Member>): Boolean {
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

    /**
     * The module of every translated type [decl] inherits from, this
     * library's or another's, for `__temper_supertypes__/0`: `x is I` and
     * `x as I` look there.
     */
    private fun ancestorModules(decl: TmpL.TypeDeclaration): List<List<String>> {
        val out = mutableListOf<List<String>>()
        val seen = mutableSetOf<TypeShape>(decl.typeShape)
        var level = listOf(decl.typeShape)
        while (level.isNotEmpty()) {
            level = level.flatMap { type ->
                type.superTypes.mapNotNull { superType ->
                    val shape = (superType.definition as? TypeShape)?.takeIf(seen::add) ?: return@mapNotNull null
                    val module = localType(shape.name)?.let { typeModule(it) } ?: externalModule(shape.name)
                    module?.let {
                        out.add(it)
                        shape
                    }
                }
            }
        }
        return out
    }

    private fun typeModule(decl: TmpL.TypeDeclaration): List<String> =
        root + names.moduleSegment(baseNameOfId(decl.name))

    private fun typeNameModule(typeName: TmpL.TypeName): List<String> {
        val key = baseNameOf(typeName) ?: TODO("type with no name: $typeName")
        return localType(typeName.sourceDefinition?.name)?.let { typeModule(it) }
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
        localType(definition.name)?.let { return moduleOf(pos, typeModule(it)) }
        externalModule(definition.name)?.let { return moduleOf(pos, it) }
        return Elixir.Atom(pos, base)
    }

    /**
     * Code the frontend already reported as broken, and translated anyway:
     * it raises when reached, with the frontend's diagnostic, as be-py and
     * be-js do. The one place a failure is deliberately left for run time,
     * because the build was told to go on.
     */
    private fun garbage(pos: Position, diagnostic: TmpL.Diagnostic?): Elixir.Expr =
        localCall(
            pos,
            "raise",
            listOf(
                elixirModule(pos, "TemperCore", "Panic"),
                Elixir.StringLit(pos, "broken code: ${diagnostic?.text ?: "no diagnostic"}"),
            ),
        )

    /** The library another translated library's name was declared in, or null for Temper's builtins. */
    private fun libraryOf(name: lang.temper.name.TemperName?): DashedIdentifier? {
        val loc = ((name as? lang.temper.name.ModularName)?.origin?.loc as? lang.temper.name.ModuleName)
            ?: return null
        return libraryRoots[loc.libraryRoot()]
    }

    private fun isExternal(name: lang.temper.name.TemperName?): Boolean = libraryOf(name) != null

    /**
     * This library's declaration of the type [name] names, or null when it names
     * another library's type or a builtin. [types] is keyed by short name, so
     * looking it up by short name alone would let a class here answer for a
     * dependency's class of the same name.
     */
    private fun localType(name: lang.temper.name.TemperName?): TmpL.TypeDeclaration? {
        if (isExternal(name)) return null
        val base = (name as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText ?: return null
        return types[base]
    }

    /** Whether [decl] inherits from a type declared in another library, whose members cannot be seen here. */
    private fun hasExternalAncestor(decl: TmpL.TypeDeclaration): Boolean {
        val seen = mutableSetOf<TmpL.TypeDeclaration>()
        var level = listOf(decl)
        while (level.isNotEmpty()) {
            level = level.flatMap { type ->
                type.superTypes.mapNotNull { superType ->
                    val name = superType.typeName.sourceDefinition?.name
                    if (isExternal(name)) return true
                    localType(name)?.takeIf(seen::add)
                }
            }
        }
        return false
    }

    private fun nominalName(type: TmpL.AType): lang.temper.name.TemperName? =
        (type.ot as? TmpL.NominalType)?.typeName?.sourceDefinition?.name

    /**
     * The signature of the constructor a call names, when it is this library's and
     * the call's own did not survive type checking ([invalidSig][arguments]): the
     * declaration still knows its arity.
     */
    private fun constructorSig(callee: TmpL.ConstructorReference): lang.temper.type2.Signature2? {
        if (callee.type != lang.temper.type2.invalidSig) return null
        val decl = localType(callee.typeName.sourceDefinition?.name) ?: return null
        val sig = decl.members.filterIsInstance<TmpL.Constructor>().firstOrNull()?.sig ?: return null
        return sig.takeIf { it != lang.temper.type2.invalidSig }
    }

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
        elixirModule(pos, segments)

    private fun staticKey(pos: Position, module: List<String>, member: String): Elixir.Expr =
        Elixir.Atom(pos, module.joinToString(".") + "." + member)

    /**
     * A field's name in its struct or heap map: the property's plain name,
     * `x` for `x__29`. A class cannot have two members of one name, and a
     * field is only ever read or written inside its own class, so the plain
     * name is unique where it is used.
     */
    private fun fieldText(id: TmpL.Id): String =
        (nameOf(id) as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText?.let(names::sanitize)
            ?: names.outName(id.name).outputNameText

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

    private fun translateValueReference(expression: TmpL.ValueReference): Elixir.Expr =
        value(expression.pos, expression.value, expression)

    /**
     * A value the frontend computed, as Elixir. Most are scalars, but a
     * coroutine turned into a state machine starts each local it hoists out
     * of the generator at its type's zero value (ZeroValues), and a List's
     * is `[]`. Anything else is a TODO naming where it came from.
     */
    private fun value(pos: Position, value: Value<*>, at: TmpL.ValueReference): Elixir.Expr =
        when (val tag = value.typeTag) {
            TBoolean -> Elixir.BoolLit(pos, TBoolean.unpack(value))
            TFloat64 -> TFloat64.unpack(value).let { f ->
                // the BEAM's floats have no NaN or infinity; TemperCore.Float
                // stands atoms in for them
                when {
                    f.isNaN() -> Elixir.Atom(pos, "nan")
                    f == Double.POSITIVE_INFINITY -> Elixir.Atom(pos, "infinity")
                    f == Double.NEGATIVE_INFINITY -> Elixir.Atom(pos, "neg_infinity")
                    else -> Elixir.NumberLit(pos, f)
                }
            }
            TInt -> Elixir.NumberLit(pos, TInt.unpack(value))
            TInt64 -> Elixir.NumberLit(pos, TInt64.unpack(value))
            is TString -> Elixir.StringLit(pos, TString.unpack(value))
            // RepresentationOfVoid.ReifyVoid: a void value really flows, and nil is it
            TNull, TVoid -> Elixir.NilLit(pos)
            TType -> typeValue(pos, TType.unpack(value).type2, at)
            // an immutable List is a Vec, as a list literal is
            TList -> vecLiteral(pos, TList.unpack(value).map { this.value(pos, it, at) })
            // TmpL turns a bare Empty into a call of `empty()`, but not one
            // inside a List value; `core.empty()` is `:empty`
            is TClass -> if (tag.typeShape == WellKnownTypes.emptyTypeDefinition) {
                Elixir.Atom(pos, "empty")
            } else {
                TODO("$pos: value of class ${tag.typeShape.name}: $at")
            }
            TClosureRecord, TFunction, TListBuilder, TMap, TMapBuilder, TProblem, TStageRange, TSymbol,
            -> TODO("$pos: value of type $tag: $at")
        }

    private companion object {
        const val RETURN = "temper_return"
        const val BREAK = "temper_break"
        const val CONTINUE = "temper_continue"
        const val NEXT = "temper_next"
        const val DONE = "temper_done"

        /** How an escaping loop that ran to its end says so. */
        const val CONT = "cont"

        /** Nodes in a statement [copyable] lets a block copy. */
        const val COPYABLE_SIZE = 8
        const val CONSTRUCTOR = "new"

        /** What a loop lifted from module init code is named after. */
        const val INIT_NAME = "init"
        const val CELL = "v"
        const val CELL_CLASS = "cell"
        const val SUPERTYPES = "__temper_supertypes__"
    }
}
