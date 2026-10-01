package lang.temper.be.elixir

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.storeDescriptorsForDeclarations
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TmpLTranslator
import lang.temper.common.MimeType
import lang.temper.frontend.Module
import lang.temper.fs.ResourceDescriptor
import lang.temper.log.FilePath
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
         * A `mix.exs` with no dependencies.
         *
         * The backend is developed against Elixir 1.19.5 on OTP 28.
         * `"~> 1.15"` is a floor that has not been tested below 1.19.
         */
        internal fun mixProject(moduleName: String, appName: String): String = """
            |defmodule $moduleName.MixProject do
            |  use Mix.Project
            |
            |  def project do
            |    [app: :$appName, version: "0.1.0", elixir: "~> 1.15", deps: []]
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

        // TODO A temper-core written in Elixir: the heap for mutable objects,
        //  UTF-16 string indices over UTF-8 binaries, and Int32 wraparound.
        override val coreLibraryResources: List<ResourceDescriptor> = listOf()

        override fun make(setup: BackendSetup<ElixirBackend>) = ElixirBackend(setup)
    }
}
