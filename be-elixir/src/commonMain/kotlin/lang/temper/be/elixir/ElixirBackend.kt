package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.storeDescriptorsForDeclarations
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.be.tmpl.isStdLib
import lang.temper.common.MimeType
import lang.temper.frontend.Module
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.last
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.OutName
import lang.temper.name.ResolvedName

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
    override fun tentativeTmpL(): TmpL.ModuleSet =
        TmpLTranslator.translateModules(
            logSink,
            readyModules,
            ElixirSupportNetwork,
            libraryConfigurations,
            dependencyResolver,
            ::tentativeOutputPathFor,
        ).also {
            storeDescriptorsForDeclarations(it, Factory)
        }

    /**
     * One Mix project: `mix.exs`, and `lib/temper_main.ex` whose `main/0`
     * runs every module's top-level statements in order.
     *
     * Not done: every library is `TemperMain` with app `:temper_main`, so two
     * libraries built together would collide. One library at a time is all
     * the functional suite asks for so far.
     */
    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val pos = finished.pos
        fun id(text: String) = Elixir.Id(pos, OutName(text, null))
        val names = ElixirNames()
        // a pre-pass over every module, so a call knows a module function
        // (and its arity) from a local holding a function value
        val moduleFunctions = mutableMapOf<ResolvedName, Int>()
        val moduleGlobals = mutableSetOf<ResolvedName>()
        for (module in finished.modules) {
            for (topLevel in module.topLevels) {
                when (topLevel) {
                    is TmpL.ModuleFunctionDeclaration ->
                        moduleFunctions[topLevel.name.name] = topLevel.parameters.parameters.size +
                            (if (topLevel.parameters.restParameter != null) 1 else 0)
                    is TmpL.ModuleLevelDeclaration -> if (!topLevel.isConsole()) moduleGlobals.add(topLevel.name.name)
                    else -> {}
                }
            }
        }
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
        // a function or module-level value is known by the name its own module declared
        val canonicalFunctions = moduleFunctions.toMap()
        val isStdLib = finished.modules.all { it.isStdLib }
        val translator = ElixirTranslator(names, canonicalFunctions, moduleGlobals, types, imports, isStdLib)
        val translated = finished.modules.map { translator.translateModule(it) }
        val mainBody = translated.flatMap { it.mainBody } +
            remoteCall(pos, elixirModule(pos, "TemperCore", "Async"), "drain", listOf())
        val functions =
            translated.flatMap { it.functions } + listOfNotNull(testRunner(pos, translated.flatMap { it.tests }))
        val classModules = translated.flatMap { it.modules }
        // a user library's Elixir for its @connected functions, copied as is
        val connected = rawBackendFiles.filter { it.key.last().fullName == CONNECTED_FILE }.values.map { source ->
            MetadataFileSpecification(path = filePath("lib", CONNECTED_FILE), mimeType = mimeType, content = source)
        }
        return connected + listOf(
            MetadataFileSpecification(
                path = filePath(MIX_FILE),
                mimeType = mimeType,
                content = mixProject(MAIN_MODULE, "temper_main"),
            ),
            TranslatedFileSpecification(
                path = filePath("lib", "temper_main$FILE_EXTENSION"),
                content = Elixir.SourceFile(
                    pos,
                    items = classModules + listOf(
                        Elixir.ModuleDef(
                            pos,
                            name = Elixir.ModuleName(pos, listOf(id(MAIN_MODULE))),
                            items = functions + listOf(
                                Elixir.FunDef(pos, id = id(MAIN_FUNCTION), body = Elixir.Block(pos, mainBody)),
                            ),
                        ),
                    ),
                ),
                mimeType = mimeType,
            ),
        )
    }

    /**
     * `__temper_tests__/0`: runs every `@test` and answers the JUnit XML.
     *
     * std/testing would do this itself, but it is a library of its own and
     * not in this translation, so `TemperCore.Test` is a port of it.
     */
    private fun testRunner(pos: lang.temper.log.Position, tests: List<String>): Elixir.FunDef? {
        if (tests.isEmpty()) return null
        fun id(text: String) = Elixir.Id(pos, OutName(text, null))
        val main = Elixir.ModuleName(pos, listOf(id(MAIN_MODULE)))
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

    override val supportNetwork = ElixirSupportNetwork

    private fun tentativeOutputPathFor(module: Module): FilePath =
        allocateTextFile(module, FILE_EXTENSION, defaultName = "module")

    companion object {
        const val FILE_EXTENSION = ".ex"
        const val MIX_FILE = "mix.exs"

        /** Elixir for a library's own @connected functions, beside its Temper source. */
        const val CONNECTED_FILE = "_connected.ex"

        /** Where temper-core lands, relative to the backend's output root. */
        const val CORE_DIR = "temper-core"

        /** The module whose [MAIN_FUNCTION] `mix run` calls. */
        const val MAIN_MODULE = "TemperMain"
        const val MAIN_FUNCTION = "main"

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
        internal fun mixProject(moduleName: String, appName: String): String = """
            |defmodule $moduleName.MixProject do
            |  use Mix.Project
            |
            |  def project do
            |    [app: :$appName, version: "0.1.0", elixir: "~> 1.15", deps: deps()]
            |  end
            |
            |  defp deps do
            |    [{:temper_core, path: "../$CORE_DIR"}]
            |  end
            |end
            |
        """.trimMargin()
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
                filePath("test", "test_helper.exs"),
                filePath("test", "temper_core_test.exs"),
                filePath("test", "temper_core_float_test.exs"),
                filePath("test", "temper_core_list_test.exs"),
                filePath("test", "temper_core_string_test.exs"),
                filePath("test", "temper_core_map_test.exs"),
                filePath("test", "temper_core_test_test.exs"),
                filePath("test", "temper_core_generator_test.exs"),
                filePath("test", "temper_core_promise_test.exs"),
                filePath("test", "temper_core_net_test.exs"),
            )

        override fun make(setup: BackendSetup<ElixirBackend>) = ElixirBackend(setup)
    }
}
