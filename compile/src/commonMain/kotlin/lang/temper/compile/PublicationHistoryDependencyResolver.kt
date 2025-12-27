package lang.temper.compile

import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.RSuccess
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.currents.join
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonValue
import lang.temper.fs.FileClassification
import lang.temper.fs.FileSystem
import lang.temper.library.DependencyResolver
import lang.temper.library.PublicationHistoryFile
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.resolveFile
import lang.temper.name.BackendId
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import java.util.Collections

fun makePublicationHistoryDependencyResolver(
    cancelGroup: CancelGroup,
    fileSystem: FileSystem,
    gatheredLibraries: List<GatheredLibrary>,
    console: Console,
    logSink: LogSink,
): RFuture<DependencyResolver, Nothing> {
    val libraryRoots = mutableSetOf<FilePath>()
    val publishHistoryPerLibrary =
        Collections.synchronizedMap(mutableMapOf<FilePath, JsonValue>())

    // A function that loads JSON for one library.  Since this step is file-system blocking, we use
    // jobs to parallelize file reads.
    fun loadJsonForLibrary(gatheredLibrary: GatheredLibrary): SignalRFuture {
        val libraryConfiguration =
            gatheredLibrary.libraryConfigurations.currentLibraryConfiguration
        val libraryRoot = libraryConfiguration.libraryRoot
        val name = libraryConfiguration.libraryName

        for (module in gatheredLibrary.modules) {
            val loc = module.loc
            if (loc is ModuleName) {
                libraryRoots.add(loc.libraryRoot())
            }
        }

        val historyFilePath = libraryRoot.resolveFile(PublicationHistoryFile.fileName)

        return fileSystem.readBinaryFileContent(historyFilePath)
            .then("Read publication history JSON from $historyFilePath") { r ->
                var err = r.throwable ?: r.failure
                val jsonText = try {
                    r.result?.decodeToString(throwOnInvalidSequence = true)
                } catch (e: CharacterCodingException) {
                    err = e
                    null
                }
                val jsonResult = jsonText?.let { JsonValue.parse(it) }
                if (
                    // Maybe there's a file, but it's invalid JSON.
                    (jsonText != null && jsonResult !is RSuccess) ||
                    // Maybe there's a file but an I/O error prevented us from reading its content.
                    (jsonText == null && fileSystem.classify(historyFilePath) == FileClassification.File)
                ) {
                    logSink.log(
                        level = Log.Fatal,
                        template = MessageTemplate.BadPublicationHistory,
                        pos = Position(historyFilePath, 0, 0),
                        values = listOf(name),
                    )
                    if (err != null) {
                        console.error(err)
                    }
                }
                if (jsonResult is RSuccess) {
                    val json = jsonResult.result
                    publishHistoryPerLibrary[libraryRoot] = json
                }
                RSuccess(Unit)
            }
    }
    val fileReadJobs = gatheredLibraries.map { gatheredLibrary ->
        loadJsonForLibrary(gatheredLibrary)
    }
    @Suppress("SpreadOperator") // API requires it.
    return cancelGroup.join(fileReadJobs)
        .then("Create publication history resolver from parsed JSON") {
            RSuccess(
                PublicationHistoryDependencyResolver(
                    publishHistoryPerLibrary.toMap(),
                    libraryRoots.toSet(),
                ),
            )
        }
}

private class PublicationHistoryDependencyResolver(
    private val libraryRootToJson: Map<FilePath, JsonValue>,
    private val libraryRoots: Set<FilePath>,
) : DependencyResolver {
    override fun libraryRootFor(loc: CodeLocation): FilePath? {
        var f = (loc as? FileRelatedCodeLocation)?.sourceFile
            ?: return null
        while (true) {
            if (f in libraryRoots) { return f }
            if (f.segments.isEmpty()) { return null }
            f = f.dirName()
        }
    }

    override fun resolve(
        loc: ModuleLocation,
        backendId: BackendId,
        logSink: LogSink,
    ): JsonValue? = when (loc) {
        is ModuleName -> {
            val json = libraryRootToJson[loc.libraryRoot()]
            // Look up `.history[backendId]` and find an array.
            val historyJson = (json as? JsonObject)?.get(PublicationHistoryFile.KEY_HISTORY)
            val publicationRecordEntries =
                (historyJson as? JsonObject)?.get(backendId.uniqueId) as? JsonArray
            if (!publicationRecordEntries.isNullOrEmpty()) {
                // TODO: Use the requesting library's version preference to pick a version.
                // That requires taking storing dependency->version maps in LibraryConfigurations
                // and passing them through the dependency resolver.
                val publicationRecordEntry = publicationRecordEntries[0]

                // Finally, look up the backend specific part.
                (publicationRecordEntry as? JsonObject)
                    ?.get(PublicationHistoryFile.KEY_TARGET_IDENTIFIER)
            } else {
                null
            }
        }
        is ImplicitsCodeLocation -> null
    }
}
