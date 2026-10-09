package lang.temper.be.elixir

import lang.temper.ast.boundaryDescent
import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.storeDescriptorsForDeclarations
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.dependencyCategory
import lang.temper.be.tmpl.isStdLib
import lang.temper.common.Either
import lang.temper.common.MimeType
import lang.temper.frontend.Module
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.log.FilePath
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.value.DependencyCategory

/**
 * <!-- snippet: backend/elixir -->
 * # Elixir Backend
 *
 * ⎀ backend/elixir/id
 *
 * Translates Temper to [Elixir], producing a Mix project per library that
 * runs on the BEAM.
 *
 * A Temper class with no mutable state becomes a `defstruct`; one with
 * mutable state becomes a reference into a per-process heap, so that aliases
 * see each other's writes. Temper's module paths become Elixir module names.
 *
 * ## Pre-requisites
 *
 * Elixir 1.15 or later, with `mix` on the path.
 *
 * [Elixir]: https://elixir-lang.org/
 */
class ElixirBackend(setup: BackendSetup<ElixirBackend>) : Backend<ElixirBackend>(Factory.backendId, setup) {
    /**
     * False once [ElixirActorChecker] has reported an `@actor` it rejects. The
     * build has already failed with that located message, so [translate]
     * writes nothing: no Elixir for a program the backend said it cannot run,
     * and no translator TODO for a shape the checker has already named, such
     * as a class that is both `@actor` and `@imu`.
     */
    private var actorsPass = true

    override fun tentativeTmpL(): TmpL.ModuleSet {
        val checker = ElixirActorChecker(logSink)
        for (module in readyModules) {
            val passes = module.generatedCode?.let(checker::check) ?: true
            actorsPass = actorsPass && passes
        }
        return TmpLTranslator.translateModules(
            logSink,
            readyModules,
            ElixirSupportNetwork,
            libraryConfigurations,
            dependencyResolver,
            ::tentativeOutputPathFor,
        ).also {
            storeDescriptorsForDeclarations(it, Factory)
        }
    }

    /**
     * One Mix project per library: `mix.exs`, which depends on temper-core
     * and on every library this one imports from, and `lib/temper_main.ex`.
     *
     * Everything lives under the library's root module, `Temper.Std` for
     * std. Its `__temper_init__/0` runs the libraries it depends on, then
     * every module's top-level statements in order, once per process;
     * `__temper_main__/0` runs that and drains the async queue.
     */
    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        if (!actorsPass) return emptyList()
        val pos = finished.pos
        fun id(text: String) = Elixir.Id(pos, OutName(text, null))
        val names = ElixirNames()
        val root = libraryModule(libraryName)
        // what other libraries export to this one, and where their roots are,
        // so a name can be traced to the library that declared it
        val externals = mutableMapOf<ResolvedName, External>()
        val libraryRoots = mutableMapOf<FilePath, lang.temper.name.DashedIdentifier>()
        for (module in finished.modules) {
            for (import in module.imports) {
                val path = import.path as? TmpL.CrossLibraryPath ?: continue
                libraryRoots[path.to.libraryRoot()] = path.libraryName
                val external = runCatching { import.externalName.name }.getOrNull() ?: continue
                val module = libraryModule(path.libraryName)
                when (val sig = import.sig) {
                    is TmpL.ImportedFunction -> externals[external] = ExternalFunction(
                        module,
                        sig.type.requiredInputTypes.size + sig.type.optionalInputTypes.size,
                    )
                    is TmpL.ImportedValue -> externals[external] = ExternalValue(module)
                    // a type is found from its definition's library, wherever it is
                    // named; a connected function arrives as support code
                    is TmpL.ImportedType, is TmpL.ImportedConnection, null -> {}
                }
            }
        }
        // std's modules list no deps, so the libraries imports cross into count too
        val declared = finished.modules.flatMap { module -> module.deps.map { it.libraryName } }
        val dependencies = (declared + libraryRoots.values).filter { it != libraryName }.distinct()
        // a pre-pass over every module, so a call knows a module function
        // (and its arity) from a local holding a function value
        val moduleFunctions = mutableMapOf<ResolvedName, Int>()
        val moduleGlobals = mutableSetOf<ResolvedName>()
        // what only tests reach goes to test/support/, out of the library
        val testOnly = mutableSetOf<ResolvedName>()
        for (module in finished.modules) {
            for (topLevel in module.topLevels) {
                when (topLevel) {
                    is TmpL.ModuleFunctionDeclaration -> {
                        moduleFunctions[topLevel.name.name] = topLevel.parameters.parameters.size
                        if (topLevel.dependencyCategory() == DependencyCategory.Test) testOnly.add(topLevel.name.name)
                    }
                    is TmpL.ModuleLevelDeclaration -> if (!topLevel.isConsole()) moduleGlobals.add(topLevel.name.name)
                    else -> {}
                }
            }
        }
        names.nameModuleLevel(
            finished.modules.flatMap { module ->
                module.topLevels.mapNotNull { topLevel ->
                    when (topLevel) {
                        is TmpL.ModuleFunctionDeclaration -> topLevel.name.name
                        is TmpL.ModuleLevelDeclaration -> topLevel.name.name
                        is TmpL.Test -> topLevel.name.name
                        else -> null
                    }
                }
            },
        )
        val types = mutableMapOf<String, TmpL.TypeDeclaration>()
        for (module in finished.modules) {
            for (topLevel in module.topLevels) {
                if (topLevel is TmpL.TypeDeclaration) {
                    val key = (topLevel.name.name as? lang.temper.name.ResolvedParsedName)?.baseName?.nameText
                    if (key != null) types[key] = topLevel
                }
            }
        }
        val imports = mutableMapOf<ResolvedName, ResolvedName>()
        for (module in finished.modules) {
            for (import in module.imports) {
                val local = import.localName?.let { runCatching { it.name }.getOrNull() } ?: continue
                val external = runCatching { import.externalName.name }.getOrNull() ?: continue
                if (local != external) imports[local] = external
            }
        }
        // `var f = fn ...; f = g;` arrives as a module function named f and an
        // assignment to it. JS and Python rebind a function's name; a `def`
        // cannot be rebound, so f is a module-level value instead, its first
        // value a capture of the `defp` its declaration became
        for (module in finished.modules) {
            for (topLevel in module.topLevels) {
                topLevel.boundaryDescent { node ->
                    if (node is TmpL.Assignment) {
                        var name = node.left.name
                        repeat(imports.size) { name = imports[name] ?: name }
                        if (name in moduleFunctions) moduleGlobals.add(name)
                    }
                    true
                }
            }
        }
        val placed = placement(finished, imports)
        testOnly.addAll(placed.testOnly)
        // a function or module-level value is known by the name its own module declared
        val canonicalFunctions = moduleFunctions.toMap()
        val isStdLib = finished.modules.all { it.isStdLib }
        val translator = ElixirTranslator(
            names, root, externals, libraryRoots, canonicalFunctions, moduleGlobals, types, imports, isStdLib,
            testOnly,
            placed.unused,
            placed.private,
        )
        val translated = finished.modules.map { translator.translateModule(it) }
        // The CLI counts tests from this registry, not from the report: a run that
        // dies before writing test-results.xml then says how many never ran, as
        // "0 of 30 (30 not run)" rather than "0 of 0". The registered name is the
        // function name because that is what the JUnit report carries.
        for (module in translated) {
            for ((name, test) in module.testNodes) {
                dependenciesBuilder.addTest(libraryName, test, backendName = name)
            }
        }
        val rootModule = elixirModule(pos, root)
        val testRoot = root + TEST_MODULE
        val mainBody = listOf(
            remoteCall(pos, rootModule, INIT_FUNCTION, listOf()),
            remoteCall(pos, elixirModule(pos, "TemperCore", "Async"), "drain", listOf()),
        )
        val classModules = translated.flatMap { it.modules }
        val prodBody = translated.flatMap { it.mainBody }
        val functions = translated.flatMap { it.functions }
        // A dependency the library's own code never names is one only its tests
        // need, such as std for std/testing: a dependency's init sets only that
        // dependency's values, so leaving it out cannot change what the library does.
        val prodDeps = dependencies.filter { dep ->
            val prefix = libraryModule(dep).joinToString(".")
            (classModules + functions + prodBody).any { refersTo(it, prefix) }
        }
        val testDeps = dependencies - prodDeps.toSet()
        val main = Elixir.FunDef(pos, id = id(MAIN_FUNCTION), body = Elixir.Block(pos, mainBody))
        val rootItems = listOf(requireHeap(pos)) + functions +
            initFunction(pos, root, listOf(), prodDeps, prodBody) + specced(main)
        val rootDef = Elixir.ModuleDef(pos, name = rootModule, items = rootItems)
        val libraryFile = Elixir.SourceFile(pos, items = classModules + listOf(rootDef)).also(::tidy)
        // after tidy, which ends each block at its first raise: a call after one is gone
        rootDef.items = publishUncalled(rootDef.items.map { it.deepCopy() })
        // tests, and the functions, classes and values only they use
        val allTests = translated.flatMap { it.tests }
        val testFunctions = translated.flatMap { it.testFunctions }
        val testModules = translated.flatMap { it.testModules }
        val testBody = translated.flatMap { it.testMainBody }
        val hasTests = (allTests + testFunctions + testModules + testBody).isNotEmpty() || testDeps.isNotEmpty()
        val testFiles = if (!hasTests) {
            listOf()
        } else {
            val titles = translated.fold(mapOf<String, String>()) { acc, t -> acc + t.testTitles }
            val nodes = translated.fold(mapOf<String, TmpL.Test>()) { acc, t -> acc + t.testNodes }
            val testModule = Elixir.ModuleDef(
                pos,
                name = elixirModule(pos, testRoot),
                items = listOf(requireHeap(pos)) + testFunctions +
                    initFunction(pos, testRoot, listOf(root), testDeps, testBody) +
                    (testRunner(pos, testRoot, allTests)?.let { specced(it, "term") } ?: listOf()),
            )
            listOf(
                TranslatedFileSpecification(
                    path = filePath("test", "support", "temper_tests$FILE_EXTENSION"),
                    content = Elixir.SourceFile(pos, items = testModules + testModule).also(::tidy),
                    mimeType = mimeType,
                ),
                MetadataFileSpecification(
                    path = filePath("test", "test_helper.exs"),
                    mimeType = mimeType,
                    content = "ExUnit.start()\n",
                ),
            ) + exUnitFiles(testRoot.joinToString("."), allTests, titles) { test -> nodes[test]?.let(::sourceLine) }
        }
        // a user library's Elixir for its @connected functions, copied as is
        val connectedModule = (root + CONNECTED_MODULE).joinToString(".")
        val connected = rawBackendFiles.filter { it.key.last().fullName == CONNECTED_FILE }.values.map { source ->
            checkConnectedModule(source, connectedModule)
            MetadataFileSpecification(path = filePath("lib", CONNECTED_FILE), mimeType = mimeType, content = source)
        }
        return connected + testFiles + listOf(
            MetadataFileSpecification(
                path = filePath(MIX_FILE),
                mimeType = mimeType,
                content = mixProject(root.joinToString("."), libraryApp(libraryName), prodDeps, testDeps, hasTests),
            ),
            TranslatedFileSpecification(
                path = filePath("lib", "temper_main$FILE_EXTENSION"),
                content = libraryFile,
                mimeType = mimeType,
            ),
        )
    }

    /**
     * `__temper_init__/0`: `TemperCore.init_once(:"Temper.Lib", fn -> ... end)`,
     * which runs [first] (the library, for its tests), then [deps], then
     * [body], the top-level statements, once per node.
     */
    private fun initFunction(
        pos: lang.temper.log.Position,
        module: List<String>,
        first: List<List<String>>,
        deps: List<lang.temper.name.DashedIdentifier>,
        body: List<Elixir.BlockItem>,
    ): List<Elixir.ModuleItem> {
        val calls = (first + deps.map(::libraryModule)).map { m ->
            remoteCall(pos, elixirModule(pos, m), INIT_FUNCTION, listOf())
        }
        return specced(
            Elixir.FunDef(
                pos,
                id = Elixir.Id(pos, OutName(INIT_FUNCTION, null)),
                body = Elixir.Block(
                    pos,
                    listOf(
                        remoteCall(
                            pos,
                            elixirModule(pos, "TemperCore"),
                            "init_once",
                            listOf(
                                Elixir.Atom(pos, module.joinToString(".")),
                                Elixir.Fn(pos, body = Elixir.Block(pos, calls + body + Elixir.NilLit(pos))),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    /**
     * A private function nothing in the root module calls is made public
     * again, `@doc false`. The frontend's tree reached it, but every call was
     * in code it rejected, or after a raise, which the tidy pass ends the
     * block at. As `defp` it would be an "unused function" warning.
     */
    private fun publishUncalled(functions: List<Elixir.ModuleItem>): List<Elixir.ModuleItem> {
        fun nameOf(fn: Elixir.FunDef) = fn.id.outName.outputNameText
        val private = functions.filterIsInstance<Elixir.FunDef>().filter { it.isPrivate }.associateBy(::nameOf)
        val called = mutableSetOf<String>()
        val pending = ArrayDeque<Elixir.Tree>()
        pending.addAll(functions.filter { it !is Elixir.FunDef || !it.isPrivate })
        // from what is public, through every private function a call reaches
        while (pending.isNotEmpty()) {
            val tree = pending.removeFirst()
            val name = when (tree) {
                is Elixir.Call -> tree.callee.outName.outputNameText
                is Elixir.Capture -> (tree.fn as? Elixir.Id)?.outName?.outputNameText
                else -> null
            }
            if (name != null && called.add(name)) private[name]?.let(pending::add)
            for (i in 0 until tree.childCount) tree.childOrNull(i)?.let(pending::add)
        }
        val uncalled = private.keys - called
        return functions.flatMap { item ->
            when {
                item is Elixir.FunDef && nameOf(item) in uncalled -> listOf(item.also { it.isPrivate = false })
                item is Elixir.TypeSpec && item.id.outName.outputNameText in uncalled -> {
                    val doc = Elixir.Id(item.pos, OutName("doc", null))
                    listOf(Elixir.ModuleAttr(item.pos, doc, Elixir.BoolLit(item.pos, false)), item)
                }
                else -> listOf(item)
            }
        }
    }

    /** `TemperCore.Heap.entry/1`, which every exported function runs through, is a macro. */
    private fun requireHeap(
        pos: lang.temper.log.Position,
    ): Elixir.ModuleItem = Elixir.Require(pos, elixirModule(pos, "TemperCore", "Heap"))

    /**
     * `@spec` for a function the backend writes itself, of no arguments:
     * init and the entry point return `nil`.
     */
    private fun specced(fn: Elixir.FunDef, result: String? = null): List<Elixir.ModuleItem> {
        val pos = fn.pos
        val type: Elixir.TypeExpr =
            result?.let { Elixir.LocalType(pos, Elixir.Id(pos, OutName(it, null))) } ?: Elixir.NilLit(pos)
        return listOf(Elixir.TypeSpec(pos, Elixir.Id(pos, fn.id.outName), result = type), fn)
    }

    /**
     * Where each non-exported function and module-level value belongs. The
     * frontend marks what only tests reach, but a call it evaluated while
     * compiling is gone from the tree, so a helper only tests used, or a
     * constant only they read, looks unused instead and would ship.
     *
     * Production's roots are what Elixir can reach: exported functions and
     * values, classes, top-level statements, and what `@keep` keeps for
     * connected code, which is public too. The tests' roots are the
     * tests and what the frontend marked as theirs. What production does not
     * reach goes to the test side if tests reach it, and is not generated at
     * all if nothing does. A value's initializer comes with it, so leaving out
     * an unread value changes nothing the library does.
     */
    private class Placement(
        val testOnly: Set<ResolvedName>,
        val unused: Set<ResolvedName>,
        /**
         * Non-exported production functions nothing outside the root module
         * calls: no class's members and nothing on the test side. They are
         * `defp`, so an Elixir caller reaches the library only through what it
         * exports, and only after its init.
         */
        val private: Set<ResolvedName>,
    )

    private fun placement(finished: TmpL.ModuleSet, imports: Map<ResolvedName, ResolvedName>): Placement {
        val declarations = mutableMapOf<ResolvedName, TmpL.TopLevel>()
        val productionRoots = mutableListOf<TmpL.Tree>()
        val kept = mutableSetOf<ResolvedName>()
        val testRoots = mutableListOf<TmpL.Tree>()
        for (module in finished.modules) {
            for (topLevel in module.topLevels) {
                if (topLevel.dependencyCategory() == DependencyCategory.Test) {
                    testRoots.add(topLevel)
                    continue
                }
                val name = when (topLevel) {
                    is TmpL.ModuleFunctionDeclaration -> topLevel.name.name
                    is TmpL.ModuleLevelDeclaration -> topLevel.name.name
                    else -> null
                }
                if (name == null || name is lang.temper.name.ExportedName) {
                    productionRoots.add(topLevel)
                } else if (topLevel.isKept()) {
                    // `@keep`: connected code calls it, which nothing here can see
                    productionRoots.add(topLevel)
                    kept.add(name)
                } else {
                    declarations[name] = topLevel
                }
            }
        }
        fun reached(roots: MutableList<TmpL.Tree>): Set<ResolvedName> {
            val reached = mutableSetOf<ResolvedName>()
            while (roots.isNotEmpty()) {
                roots.removeLast().boundaryDescent { node ->
                    val id = (node as? TmpL.Id)?.nameContent as? Either.Left
                    var name = id?.item
                    repeat(imports.size) { name = name?.let { imports[it] ?: it } }
                    name?.let { if (it in declarations && reached.add(it)) roots.add(declarations.getValue(it)) }
                    true
                }
            }
            return reached
        }
        val notProduction = declarations.keys - reached(productionRoots)
        val byTests = reached(testRoots)
        val testOnly = notProduction intersect byTests
        // what a class module, or the test side, names directly must stay callable from there
        val outside = kept.toMutableSet()
        val outsideRoots = finished.modules.flatMap { it.topLevels }.filter {
            it is TmpL.TypeDeclaration || it.dependencyCategory() == DependencyCategory.Test
        } + testOnly.mapNotNull { declarations[it] }
        for (root in outsideRoots) {
            root.boundaryDescent { node ->
                val id = (node as? TmpL.Id)?.nameContent as? Either.Left
                var name = id?.item
                repeat(imports.size) { name = name?.let { imports[it] ?: it } }
                name?.let(outside::add)
                true
            }
        }
        val private = declarations.filterValues { it is TmpL.ModuleFunctionDeclaration }.keys - notProduction - outside
        return Placement(testOnly = testOnly, unused = notProduction - byTests, private = private)
    }

    private fun TmpL.TopLevel.isKept(): Boolean =
        (this as? TmpL.Declaration)?.metadata?.any { it.key.symbol == lang.temper.value.keepSymbol } == true

    /** Whether [tree] names the module [prefix] or one under it, or a value it keeps (`:"Temper.Std.x"`). */
    private fun refersTo(tree: Elixir.Tree, prefix: String): Boolean = when (tree) {
        is Elixir.ModuleName -> tree.segments.joinToString(".") { it.outName.outputNameText }.let {
            it == prefix || it.startsWith("$prefix.")
        }
        is Elixir.Atom -> tree.text.startsWith("$prefix.")
        else -> (0 until tree.childCount).any { i -> tree.childOrNull(i)?.let { refersTo(it, prefix) } ?: false }
    }

    /** `src/diff.temper.md:42`: where a test is, in the Temper source. */
    private fun sourceLine(test: TmpL.Test): String? {
        val loc = test.pos.loc as? FileRelatedCodeLocation ?: return null
        val positions = readyModules.firstNotNullOfOrNull { it.filePositions[loc.sourceFile] } ?: return null
        val line = positions.filePositionAtOffset(test.pos.left).line
        val file = loc.sourceFile.segments.dropWhile { it.fullName != "src" }.ifEmpty { loc.sourceFile.segments }
        return file.joinToString("/") { it.fullName } + ":" + line
    }

    /**
     * `__temper_tests__/0`: runs every `@test` and answers the JUnit XML.
     *
     * std/testing would do this itself, but it is a library of its own and
     * not in this translation, so `TemperCore.Test` is a port of it.
     */
    private fun testRunner(pos: lang.temper.log.Position, root: List<String>, tests: List<String>): Elixir.FunDef? {
        if (tests.isEmpty()) return null
        fun id(text: String) = Elixir.Id(pos, OutName(text, null))
        val main = elixirModule(pos, root)
        val cases = tests.map { test ->
            Elixir.RemoteCall(
                pos,
                module = Elixir.ModuleName(pos, listOf(id("TemperCore"), id("Pair"))),
                fn = id("new"),
                args = listOf(
                    Elixir.StringLit(pos, test),
                    Elixir.Capture(
                        pos,
                        fn = Elixir.Field(pos, obj = main, id = id(test)),
                        arity = Elixir.NumberLit(pos, 1),
                    ),
                ),
            )
        }
        return Elixir.FunDef(
            pos,
            id = id(TESTS_FUNCTION),
            body = Elixir.Block(
                pos,
                listOf(
                    Elixir.RemoteCall(pos, module = main, fn = id(INIT_FUNCTION), args = listOf()),
                    Elixir.RemoteCall(
                        pos,
                        module = Elixir.ModuleName(pos, listOf(id("TemperCore"), id("Test"))),
                        fn = id("run_cases"),
                        args = listOf(Elixir.ListLit(pos, cases)),
                    ),
                ),
            ),
        )
    }

    /**
     * One ExUnit file per Temper source file with tests, `test/diff_test.exs`
     * for `src/diff_test.temper.md`, so `mix test` runs them with ExUnit's
     * reporting, filtering and seeds. A failure names the Temper line the test
     * is on, since that is the line to fix, not the line of this file. The
     * library's top level and the tests' own run first, once, as tests read
     * the values they set. Text, not a tree: `use` and the `test "..." do`
     * macro are not in the output grammar.
     *
     * ExUnit refuses two tests with one name in a module, and one source file
     * may have two `test("same")`, so a repeated title is numbered:
     * "same", then "same (2)".
     */
    private fun exUnitFiles(
        testRoot: String,
        tests: List<String>,
        titles: Map<String, String>,
        lineOf: (String) -> String?,
    ): List<MetadataFileSpecification> =
        tests.groupBy { test -> lineOf(test)?.substringBeforeLast(":")?.let(::testFileStem) ?: "temper" }
            .map { (stem, group) ->
                val module = testRoot.removeSuffix(".$TEST_MODULE") + "." + pascalStem(stem) + "Test"
                val content = buildString {
                    append("defmodule $module do\n")
                    append("  use ExUnit.Case\n\n")
                    append("  setup_all do\n    $testRoot.$INIT_FUNCTION()\n    :ok\n  end\n")
                    val used = mutableSetOf<String>()
                    for (test in group) {
                        val title = titles[test] ?: test
                        var unique = title
                        var n = 1
                        while (!used.add(unique)) unique = "$title (${++n})"
                        val where = lineOf(test)?.let { ", ${elixirStringText(it)}" } ?: ""
                        append("\n  test ${elixirStringText(unique)} do\n")
                        append("    TemperCore.Test.check(&$testRoot.$test/1$where)\n  end\n")
                    }
                    append("end\n")
                }
                MetadataFileSpecification(
                    path = filePath("test", "${stem}_test.exs"),
                    mimeType = mimeType,
                    content = content,
                )
            }

    /**
     * A library's `_connected.ex` must define the module its `@connected` functions call. A module
     * name is the BEAM's only namespace, so each library owns its own; when every library defined
     * `TemperConnected`, the second one loaded replaced the first and its calls were undefined.
     */
    private fun checkConnectedModule(source: String, expected: String) {
        val defined = Regex("""defmodule\s+([A-Za-z0-9_.]+)\s+do""").findAll(source).map { it.groupValues[1] }.toList()
        if (expected !in defined) {
            error(
                "$CONNECTED_FILE must define `$expected`, the module this library's @connected functions call; " +
                    "it defines ${defined.joinToString { "`$it`" }.ifEmpty { "no module" }}",
            )
        }
    }

    /** `diff_test` and `diff` both test into `diff_test.exs`: the stem, without a `_test` of its own. */
    private fun testFileStem(source: String): String =
        source.substringAfterLast("/").substringBefore(".temper").lowercase()
            .replace(Regex("[^a-z0-9]+"), "_").trim('_').removeSuffix("_test").ifEmpty { "temper" }

    private fun pascalStem(stem: String) = stem.split("_").joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

    override val supportNetwork = ElixirSupportNetwork

    private fun tentativeOutputPathFor(module: Module): FilePath =
        allocateTextFile(module, FILE_EXTENSION, defaultName = "module")

    companion object {
        const val FILE_EXTENSION = ".ex"
        const val MIX_FILE = "mix.exs"

        /** Elixir for a library's own @connected functions, beside its Temper source. */
        const val CONNECTED_FILE = "_connected.ex"

        /**
         * The module that file defines, under the library's root: `Temper.Lib.Connected`.
         * One name shared by every library let the second one loaded replace the first.
         */
        const val CONNECTED_MODULE = "Connected"

        /** Where temper-core lands, relative to the backend's output root. */
        const val CORE_DIR = "temper-core"

        /**
         * The entry point `mix run` calls: init, then drain the async queue. Not
         * `main`, which a library may export itself; in Elixir the first of two
         * `def main()` wins, so the library's would run in its place.
         */
        const val MAIN_FUNCTION = "__temper_main__"

        /** `Temper.MyLib.Tests`, under the library's root: tests and what only they use. */
        const val TEST_MODULE = "Tests"

        /** Runs a library's dependencies, then its top levels, once per process. */
        const val INIT_FUNCTION = "__temper_init__"

        /**
         * `Temper.Std` for std, `Temper.MyLib` for my-lib: the module whose
         * [MAIN_FUNCTION] `mix run` calls, and the parent of its classes.
         * Under `Temper.`, so no library can be named into Elixir's own
         * `String` or `Enum`.
         */
        fun libraryModule(library: lang.temper.name.DashedIdentifier): List<String> =
            listOf("Temper", pascal(library.text))

        /** `:temper_std`, `:temper_my_lib`. */
        fun libraryApp(library: lang.temper.name.DashedIdentifier): String =
            "temper_" + library.text.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

        private fun pascal(text: String): String {
            val words = text.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            val joined = words.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
            return if (joined.firstOrNull()?.isLetter() == true) joined else "L$joined"
        }

        /** Runs the `@test`s and answers JUnit XML. */
        const val TESTS_FUNCTION = "__temper_tests__"

        /** Where a test run writes that XML, for the harness to read. */
        const val TEST_RESULTS_FILE = "test-results.xml"

        val mimeType = MimeType("text", "x-elixir")

        /**
         * <!-- snippet: backend/elixir/id -->
         * BackendID: `elixir`
         */
        internal const val BACKEND_ID = "elixir"

        /**
         * A `mix.exs` that depends on temper-core by path: the core library
         * is laid down at `temper.out/elixir/temper-core`, beside every
         * library's own directory.
         *
         * The backend is developed against Elixir 1.19.5 on OTP 28.
         * `"~> 1.15"` is a floor that has not been tested below 1.19.
         */
        internal fun mixProject(
            moduleName: String,
            appName: String,
            dependencies: List<lang.temper.name.DashedIdentifier> = listOf(),
            testDependencies: List<lang.temper.name.DashedIdentifier> = listOf(),
            hasTests: Boolean = false,
        ): String {
            // each library's project sits beside the others, in a directory named for it
            val deps = listOf("{:temper_core, path: \"../$CORE_DIR\"}") +
                dependencies.map { "{:${libraryApp(it)}, path: \"../${it.text}\"}" } +
                testDependencies.map { "{:${libraryApp(it)}, path: \"../${it.text}\", only: :test}" }
            // test/support/ holds the tests' own code, compiled for `mix test` only
            val paths = if (hasTests) ", elixirc_paths: elixirc_paths(Mix.env())" else ""
            val pathsFun = if (hasTests) {
                "\n\n  defp elixirc_paths(:test), do: [\"lib\", \"test/support\"]\n  defp elixirc_paths(_), do: [\"lib\"]"
            } else {
                ""
            }
            return """
            |defmodule $moduleName.MixProject do
            |  use Mix.Project
            |
            |  def project do
            |    [app: :$appName, version: "0.1.0", elixir: "~> 1.15", deps: deps()$paths]
            |  end
            |
            |  defp deps do
            |    [${deps.joinToString(", ")}]
            |  end$pathsFun
            |end
            |
            """.trimMargin()
        }
    }

    @PluginBackendId(BACKEND_ID)
    @BackendSupportLevel(isSupported = true, isDefaultSupported = false, isTested = false)
    object Factory : Backend.Factory<ElixirBackend> {
        override val backendId = BackendId(BACKEND_ID)

        override val specifics = ElixirSpecifics

        override val backendMeta: BackendMeta
            get() = BackendMeta(
                backendId = backendId,
                languageLabel = LanguageLabel(backendId.uniqueId),
                fileExtensionMap = mapOf(
                    FileType.Module to FILE_EXTENSION,
                    FileType.Script to ".exs",
                ),
                mimeTypeMap = mapOf(
                    FileType.Module to mimeType,
                    FileType.Script to mimeType,
                ),
            )

        /**
         * temper-core, its own Mix project, laid down beside the libraries
         * that depend on it by path. A copy inside each library would define
         * `TemperCore` once per library, and two libraries that depend on
         * each other would not compile together.
         */
        override val coreLibraryResources: List<ResourceDescriptor> =
            declareResources(
                base = dirPath("lang", "temper", "be", "elixir", "temper-core"),
                filePath("mix.exs"),
                filePath("lib", "temper_core.ex"),
                filePath("lib", "temper_core_float.ex"),
                filePath("lib", "temper_core_list.ex"),
                filePath("lib", "temper_core_string.ex"),
                filePath("lib", "temper_core_map.ex"),
                filePath("lib", "temper_core_test.ex"),
                filePath("lib", "temper_core_generator.ex"),
                filePath("lib", "temper_core_promise.ex"),
                filePath("lib", "temper_core_net.ex"),
                filePath("lib", "temper_core_regex.ex"),
                filePath("lib", "temper_core_actor.ex"),
                filePath("lib", "temper_core_application.ex"),
                filePath("test", "test_helper.exs"),
                filePath("test", "temper_core_test.exs"),
                filePath("test", "temper_core_float_test.exs"),
                filePath("test", "temper_core_ieee_test.exs"),
                filePath("test", "temper_core_list_test.exs"),
                filePath("test", "temper_core_string_test.exs"),
                filePath("test", "temper_core_map_test.exs"),
                filePath("test", "temper_core_test_test.exs"),
                filePath("test", "temper_core_generator_test.exs"),
                filePath("test", "temper_core_promise_test.exs"),
                filePath("test", "temper_core_net_test.exs"),
                filePath("test", "temper_core_regex_test.exs"),
                filePath("test", "temper_core_heap_test.exs"),
                filePath("test", "temper_core_actor_test.exs"),
            )

        override fun make(setup: BackendSetup<ElixirBackend>) = ElixirBackend(setup)
    }
}
