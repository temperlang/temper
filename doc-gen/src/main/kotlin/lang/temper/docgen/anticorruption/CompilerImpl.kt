package lang.temper.docgen.anticorruption

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.Dependencies
import lang.temper.be.SourceMap
import lang.temper.be.syncstaging.applyBackendsSynchronously
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.common.json.JsonValue
import lang.temper.docgen.SimpleCodeFragment
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.NullSystemAccess
import lang.temper.fs.OutRegularFile
import lang.temper.fs.OutputRoot
import lang.temper.fs.TEMPER_KEEP_NAME
import lang.temper.fs.leaves
import lang.temper.lexer.Genre
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.library.DependencyResolver
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurations
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.resolveFile
import lang.temper.log.unknownPos
import lang.temper.name.BackendId
import lang.temper.name.LanguageLabel
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.supportedBackends.lookupFactory
import lang.temper.supportedBackends.lookupLanguage

/**
 * This is a work in progress/proof of concept as of today it only supports one fixed backend, the
 * JS backend.
 * Large portions of this were pulled out of JsBackendTest
 */
class CompilerImpl(
    private val libraryConfiguration: LibraryConfiguration,
    private val dependencyResolver: DependencyResolver = StubDependencyResolver,
    private val libraryConfigurations: LibraryConfigurations =
        LibraryConfigurationsBundle.from(listOf(libraryConfiguration))
            .withCurrentLibrary(libraryConfiguration),
    private val cancelGroup: CancelGroup,
) : Compiler {

    override fun <T : SimpleCodeFragment> compile(fragments: List<T>): CompilationResult<T> {
        val content = fragments.joinToString("\n") { it.sourceText }
        val logSink = DocGenLogSink()
        var returnedFragments = true
        val envelope = mutableMapOf<LanguageLabel, Envelope>()
        val replacements = mutableMapOf<T, MutableMap<LanguageLabel, String>>()

        doBackend@for (backendId in libraryConfiguration.supportedBackendList) {
            val outputFileSystem = MemoryFileSystem()
            val outputRoot = OutputRoot(outputFileSystem)
            val backendLanguage = backendId.lookupLanguage()
            if (backendLanguage == null) {
                logSink.log(
                    Log.Error,
                    MessageTemplate.BadBackend,
                    unknownPos,
                    listOf(backendId),
                )
                continue@doBackend
            }
            try {
                // TODO: plumb sourceFile in from elsewhere
                val sourceFile = libraryConfiguration.libraryRoot.resolveFile("unknown.temper")
                val moduleName = ModuleName(
                    sourceFile = libraryConfiguration.libraryRoot,
                    libraryRootSegmentCount = libraryConfiguration.libraryRoot.segments.size,
                    isPreface = false,
                )
                val moduleAdvancer = ModuleAdvancer(logSink)
                val module = moduleAdvancer.createModule(moduleName, console, genre = Genre.Documentation)
                module.deliverContent(
                    ModuleSource(
                        filePath = sourceFile,
                        fetchedContent = content,
                        languageConfig = StandaloneLanguageConfig,
                    ),
                )
                moduleAdvancer.advanceModules()
                if (!module.ok) {
                    console.warn { "${libraryConfiguration.libraryName}.$backendId: module failed" }
                    continue@doBackend
                }

                val backend = lookupFactory(backendId)!!.make(
                    BackendSetup(
                        libraryName = libraryConfiguration.libraryName,
                        dependenciesBuilder = Dependencies.Builder(libraryConfigurations.toBundle()),
                        modules = listOf(module),
                        buildFileCreator = outputRoot.systemAccess(cancelGroup),
                        keepFileUpdater = NullSystemAccess(dirPath(TEMPER_KEEP_NAME), cancelGroup),
                        logSink = logSink,
                        dependencyResolver = dependencyResolver,
                        config = Backend.Config.abbreviated,
                    ),
                )

                applyBackendsSynchronously(cancelGroup, listOf(backend))

                // TODO need to figure out how to remove the shared definitions, or maybe hoist them?

                val resultContent = outputRoot.leaves()
                    .filterNot { it.name.extension == SourceMap.EXTENSION }
                    .mapNotNull {
                        val textContent = (it as? OutRegularFile)?.textContent()
                        if (textContent.isNullOrBlank()) {
                            null
                        } else {
                            textContent
                        }
                    }
                    .joinToString("\n")

                val preamble = outputRoot.files
                    .filter { it.name.baseName == "__share-definitions__" }
                    .joinToString("\n")
                // TODO need to use the mappings (which seem to be gibberish right now) to match everything
                // back to the right fragment
                returnedFragments = false
                envelope[backendLanguage] = Envelope(preamble, "")
                for (frag in fragments) {
                    replacements.getOrPut(frag) { mutableMapOf() }[backendLanguage] = resultContent
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                logSink.log(
                    Log.Fatal,
                    MessageTemplate.UnexpectedException,
                    unknownPos,
                    listOf(e.stackTraceToString()),
                )
            }
        }
        return CompilationResult(
            returnedFragments = if (returnedFragments) { fragments } else { listOf() },
            resultingCode = ResultingCode(envelope, replacements),
            errors = logSink.messages,
        )
    }

    private class DocGenLogSink : LogSink {
        val messages = mutableListOf<String>()
        override fun log(
            level: Log.Level,
            template: MessageTemplateI,
            pos: Position,
            values: List<Any>,
            fyi: Boolean,
        ) {
            if (level >= Log.Fatal) {
                hasFatal = true
            }
            if (level >= Log.Warn) {
                messages.add(template.format(values))
            }
        }

        override var hasFatal: Boolean = false
            private set
    }
}

private object StubDependencyResolver : DependencyResolver {
    override fun resolve(
        loc: ModuleLocation,
        backendId: BackendId,
        logSink: LogSink,
    ): JsonValue? = null
}
