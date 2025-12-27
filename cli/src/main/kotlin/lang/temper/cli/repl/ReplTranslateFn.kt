package lang.temper.cli.repl

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.Dependencies
import lang.temper.be.SourceMap
import lang.temper.be.syncstaging.applyBackendsSynchronously
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.MimeType
import lang.temper.common.WrappedByteArray
import lang.temper.common.abbreviate
import lang.temper.common.buildSetMultimap
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.Cancellable
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.currents.preComputedFuture
import lang.temper.common.ignore
import lang.temper.common.json.JsonValue
import lang.temper.common.temperEscaper
import lang.temper.env.InterpMode
import lang.temper.frontend.Module
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.OutDir
import lang.temper.fs.OutFile
import lang.temper.fs.OutRegularFile
import lang.temper.fs.OutSubDir
import lang.temper.fs.OutputRoot
import lang.temper.fs.temperKeepSegment
import lang.temper.library.DependencyResolver
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.library.outputDirectory
import lang.temper.log.FilePathSegment
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.BackendId
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.stage.Stage
import lang.temper.supportedBackends.defaultSupportedBackendList
import lang.temper.supportedBackends.lookupFactory
import lang.temper.supportedBackends.supportedBackends
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.Helpful
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.TString
import lang.temper.value.void
import java.util.concurrent.ExecutorService
import lang.temper.type.WellKnownTypes as WKT

internal class ReplTranslateFn(
    val repl: Repl,
) : NamedBuiltinFun, CallableValue, Helpful {
    override val sigs: List<Signature2> get() = sigsList
    override val name get() = "translate"
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val console = repl.console

        val (locValue, backendIdValue) = args.unpackPositioned(2, cb)
            ?: return cb.fail(MessageTemplate.ArityMismatch, cb.pos, listOf(2))

        val backendId: String? = TString.unpackOrNull(backendIdValue)

        val locOrFail = repl.commandLineLocationFromValue(
            locValue,
            args.pos(0) ?: cb.pos,
            cb,
        )
        val loc = when (locOrFail) {
            is Either.Left -> locOrFail.item
            is Either.Right -> return locOrFail.item
        }

        val module = repl.getModule(loc)
        val stageCompleted = module?.stageCompleted
        if (stageCompleted == null || stageCompleted < Stage.GenerateCode) {
            console.error("$loc did not reach ${Stage.GenerateCode}!")
            return Fail
        }

        val cancelGroup = NotGoingToCancel(repl.executorService)

        val (backend, otherBackends, outputRoot, mainOutputDir) =
            makeBackendForTranslation(repl, backendId, module, cancelGroup)
                ?: return Fail(
                    LogEntry(
                        Log.Error,
                        MessageTemplate.ExpectedValueOfType,
                        args.pos(1) ?: cb.pos,
                        listOf(
                            "String in (${
                                supportedBackends.joinToString {
                                    temperEscaper.escape(it.uniqueId)
                                }
                            })",
                        ),
                    ),
                )

        runCatching {
            applyBackendsSynchronously(cancelGroup, listOf(backend) + otherBackends)
        }.onFailure { e ->
            // Don't crash the whole repl. Failed translate likely means we're missing output files, though.
            console.error(e)
        }

        // Use console.group to produce an indented form like
        //
        //    path/
        //      to/
        //        file.ext: text/x-foo
        //          Line 1 of generated file
        //          ...
        //        other.json: application/json
        //          {"p": "v"}
        fun dumpText(name: FilePathSegment, mimeType: MimeType?, content: String) {
            val trimmedContent = content.trimEnd()
            console.group("${name.fullName}${mimeType?.let { ": $it" } ?: ""}") {
                console.log(
                    if (name.extension == SourceMap.EXTENSION) {
                        abbreviate(trimmedContent, maxlen = HEX_ENC_ABBREV_LEN)
                    } else {
                        trimmedContent
                    },
                )
            }
        }

        fun dumpBinary(name: FilePathSegment, mimeType: MimeType?, content: WrappedByteArray) {
            // It's nicer to present textual files as text.
            // This is heuristic.  We'll always output empty files as text, for example.
            val textContent = try {
                content.decodeToString(throwOnInvalidSequence = true)
            } catch (ex: CharacterCodingException) {
                // If it doesn't decode to valid UTF-8 then we fall-through to hex encoding
                // below.
                ignore(ex)
                null
            }
            if (textContent != null) {
                dumpText(name, mimeType, textContent)
                return
            }

            console.group("${name.fullName}${mimeType?.let { ": $it" } ?: ""} hex") {
                console.log(
                    abbreviate(
                        content.hexEncode(),
                        maxlen = HEX_ENC_ABBREV_LEN,
                    ),
                )
            }
        }

        fun dump(f: OutFile): Unit = when (f) {
            is OutDir -> {
                console.groupIf(f is OutSubDir, "${f.name}/") {
                    f.files.forEach { dump(it) }
                }
            }
            is OutRegularFile -> dumpBinary(
                f.name,
                f.mimeType,
                outputRoot.byteContentOf(f.path) ?: WrappedByteArray.empty,
            )
        }

        console.group("Translated $backendId for $loc") {
            dump(mainOutputDir)
        }
        return void
    }

    private fun makeBackendForTranslation(
        repl: Repl,
        backendIdText: String?,
        module: Module,
        cancelGroup: CancelGroup,
    ): BackendsForTranslation? {
        if (backendIdText == null) {
            return null
        }
        val backendId = BackendId(backendIdText)

        val externalModules = repl.externalModules
        val libraryConfigurationsByRoot =
            externalModules.libraryConfigurations?.byLibraryRoot?.toMutableMap()
                ?: mutableMapOf()
        val libraryConfiguration =
            ReplChunkIndex.libraryConfiguration.copy(supportedBackendList = listOf(backendId))
        libraryConfigurationsByRoot[libraryConfiguration.libraryRoot] = libraryConfiguration

        val otherModules = buildSetMultimap {
            fun addImportedFrom(sourceModule: Module) {
                for (importRecord in sourceModule.importRecords) {
                    val loc = importRecord.exporterLocation
                    val config = when (loc) {
                        is ModuleName -> libraryConfigurationsByRoot[loc.libraryRoot()] ?: continue
                        is ImplicitsCodeLocation, null -> continue
                    }
                    val replChunkIndex = ReplChunkIndex.from(loc)
                    val imported = if (replChunkIndex != null) {
                        repl.getModule(replChunkIndex)
                    } else {
                        externalModules[loc]
                    } ?: continue
                    val moduleList = this.getOrPut(config.libraryRoot) { mutableSetOf() }
                    if (imported !in moduleList) {
                        moduleList.add(imported)
                        addImportedFrom(imported)
                    }
                }
            }
            addImportedFrom(module)
        }

        val libraryConfigurationsBundle = LibraryConfigurationsBundle.from(libraryConfigurationsByRoot.values)

        val dependencyResolver = object : DependencyResolver {
            override fun resolve(
                loc: ModuleLocation,
                backendId: BackendId,
                logSink: LogSink,
            ): JsonValue? = null
        }

        val outputRoot = OutputRoot(MemoryFileSystem())

        return lookupFactory(backendId)
            ?.let { factory ->
                fun makeBackend(config: LibraryConfiguration, modules: List<Module>): Backend<*> = factory.make(
                    BackendSetup(
                        config.libraryName,
                        Dependencies.Builder(libraryConfigurationsBundle),
                        modules,
                        outputRoot.makeDirs(config.outputDirectory(backendId)).systemAccess(cancelGroup),
                        outputRoot.makeDir(temperKeepSegment).systemReadAccess(cancelGroup),
                        repl.logSink,
                        dependencyResolver,
                        Backend.Config.abbreviated,
                    ),
                )
                val backend = makeBackend(libraryConfiguration, listOf(module))
                val otherBackends = otherModules.entries.mapNotNull { (libraryRoot, modules) ->
                    if (libraryRoot == libraryConfiguration.libraryRoot) {
                        null
                    } else {
                        makeBackend(
                            libraryConfigurationsByRoot.getValue(libraryRoot),
                            modules.toList(),
                        )
                    }
                }
                BackendsForTranslation(
                    backend = backend,
                    otherBackends = otherBackends,
                    outputRoot = outputRoot,
                    mainOutputDir = outputRoot.makeDirs(libraryConfiguration.outputDirectory(backendId)),
                )
            }
    }

    override fun briefHelp(): String = "translates an interpreted object into backend code"

    override fun longHelp(): String = buildString {
        appendLine(
            """
            Translates the interactive result of interpreting a temper statement into backend code.
            Signature:
            """.trimIndent(),
        )
        for (sig in this@ReplTranslateFn.sigs) {
            appendLine("    $sig")
        }
        appendLine(
            """
            `${REPL_LOC_SYMBOL.text}` may be one of:
                A string like "interactive#0" that names a command chunk
                An integer like 123; shorthand for "interactive#123"

            `$TO_ARG` must be a string representing a backend, one of:
            """.trimIndent(),
        )
        for (backendId in defaultSupportedBackendList) {
            appendLine("    $backendId")
        }
    }

    companion object {
        private val sigsList = listOf(WKT.stringType2, WKT.intType2).map { inputType ->
            Signature2(
                returnType2 = WKT.voidType2,
                hasThisFormal = false,
                requiredInputTypes = listOf(inputType, WKT.stringType2),
            )
        }
    }
}

private const val HEX_ENC_ABBREV_LEN = 80
private const val TO_ARG = "to"

private data class BackendsForTranslation(
    val backend: Backend<*>,
    /** Backends for dependencies like "std/" that need information merged with the main backend. */
    val otherBackends: List<Backend<*>>,
    val outputRoot: OutputRoot,
    val mainOutputDir: OutDir,
)

/**
 * The translation is synchronous so we don't need to cancel it to start another.
 */
private class NotGoingToCancel(override val executorService: ExecutorService) : CancelGroup {
    override fun add(c: Cancellable) {
        // Not tracking
    }

    override fun cancelAll(mayInterruptIfRunning: Boolean): SignalRFuture =
        preComputedFuture(Unit)

    override val isCancelled: Boolean get() = false
}
