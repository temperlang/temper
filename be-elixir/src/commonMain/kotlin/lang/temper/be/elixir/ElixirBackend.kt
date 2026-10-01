package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.storeDescriptorsForDeclarations
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.common.MimeType
import lang.temper.frontend.Module
import lang.temper.fs.ResourceDescriptor
import lang.temper.fs.declareResources
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.name.BackendId
import lang.temper.name.BackendMeta
import lang.temper.name.FileType
import lang.temper.name.LanguageLabel
import lang.temper.name.OutName

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
     * Placeholder translation.
     *
     * Per the backend guide, the first step is a bogus translator that emits a
     * working program in the target language whatever the input. It proves
     * the file plumbing and the run path, `mix compile` then `mix run`, before
     * any real tree walking. A real translator replaces this.
     */
    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
        val pos = finished.pos
        fun id(text: String) = Elixir.Id(pos, OutName(text, null))
        return listOf(
            MetadataFileSpecification(
                path = filePath(MIX_FILE),
                mimeType = mimeType,
                content = mixProject(MAIN_MODULE, "temper_main"),
            ),
            TranslatedFileSpecification(
                path = filePath("lib", "temper_main$FILE_EXTENSION"),
                content = Elixir.SourceFile(
                    pos,
                    items = listOf(
                        Elixir.ModuleDef(
                            pos,
                            name = Elixir.ModuleName(pos, listOf(id(MAIN_MODULE))),
                            items = listOf(
                                Elixir.FunDef(
                                    pos,
                                    id = id(MAIN_FUNCTION),
                                    body = Elixir.Block(
                                        pos,
                                        listOf(
                                            Elixir.RemoteCall(
                                                pos,
                                                module = Elixir.ModuleName(pos, listOf(id("IO"))),
                                                fn = id("puts"),
                                                args = listOf(Elixir.StringLit(pos, "Hello, World!")),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                mimeType = mimeType,
            ),
        )
    }

    override val supportNetwork = ElixirSupportNetwork

    private fun tentativeOutputPathFor(module: Module): FilePath =
        allocateTextFile(module, FILE_EXTENSION, defaultName = "module")

    companion object {
        const val FILE_EXTENSION = ".ex"
        const val MIX_FILE = "mix.exs"

        /** Where temper-core lands, relative to the backend's output root. */
        const val CORE_DIR = "temper-core"

        /** The module whose [MAIN_FUNCTION] `mix run` calls. */
        const val MAIN_MODULE = "TemperMain"
        const val MAIN_FUNCTION = "main"

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
                filePath("test", "test_helper.exs"),
                filePath("test", "temper_core_test.exs"),
            )

        override fun make(setup: BackendSetup<ElixirBackend>) = ElixirBackend(setup)
    }
}
