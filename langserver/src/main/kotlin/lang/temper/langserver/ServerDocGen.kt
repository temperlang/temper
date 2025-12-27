package lang.temper.langserver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.common.invoke
import lang.temper.common.orThrow
import lang.temper.docgen.prepDocGen
import lang.temper.fs.FileChange
import lang.temper.fs.FileSystem
import lang.temper.fs.FileWatchService
import lang.temper.fs.fromJavaPathThrowing
import lang.temper.fs.toFilePath
import lang.temper.log.FilePath
import lang.temper.log.LogSink
import lang.temper.name.BackendId
import lang.temper.supportedBackends.defaultSupportedBackendList
import java.io.File
import java.io.StringReader
import java.nio.file.Path

@DelicateCoroutinesApi
internal class ServerDocGen(
    private val fileSystem: FileSystem,
    private val languageServer: TemperLanguageServerImpl,
    private val cancelGroup: CancelGroup,
    logSink: LogSink,
    workRoot: Path,
) {
    private val docGen =
        defaultSupportedBackendList.associateWith {
            prepDocGen(
                libraryRoot = fromJavaPathThrowing(workRoot),
                logSink = logSink,
                backends = listOf(it),
                cancelGroup = cancelGroup,
            )
        }
    private val subscriptions = mutableMapOf<FilePath, MutableSet<DocGenKey>>()

    @Synchronized
    private fun syncGet(path: FilePath) = subscriptions[path]

    @Synchronized
    private fun syncHas(path: FilePath) = path in subscriptions

    suspend fun run() {
        fileSystem.createWatchService(
            FilePath(emptyList(), isDir = true),
        ).orThrow().use { watch ->
            while (true) {
                RResult.of(rethrow = listOf(CancellationException::class)) {
                    // Filter and coalesce changes.
                    // Technically our list of subscriptions can change during these loops, but eh.
                    val changes = mutableMapOf<FilePath, FileChange>()
                    val allChanges = fileChanges(watch, millis = TIMEOUT_MS)
                    allChanges.asSequence().filter { syncHas(it.filePath) }.forEach { change ->
                        changes[change.filePath] = change
                    }
                    changes.values.forEach { change ->
                        syncGet(change.filePath)?.forEach { key ->
                            deliver(change, key)
                        }
                    }
                }.invoke { (_, failure) ->
                    if (failure != null) {
                        console.error(failure)
                    }
                }
            }
        }
    }

    @Synchronized
    fun subscribe(key: DocGenKey, path: Path): Boolean {
        val result = subscriptions.getOrPut(toFilePath(path)) { mutableSetOf() }.add(key)
        deliver(change = FileChange(filePath = toFilePath(path), fileChangeKind = FileChange.Kind.Edited), key = key)
        return result
    }

    @Synchronized
    fun unsubscribe(key: DocGenKey, path: Path) = subscriptions[toFilePath(path)]?.remove(key)

    private fun deliver(change: FileChange, key: DocGenKey) {
        val content = RResult.of {
            val gen = docGen[BackendId(key.backend)]
                ?: throw IllegalArgumentException("No such backend '${key.backend}'")
            // Even if the change is officially a delete, try to read the content anyway. Trust the current state.
            val source = (fileSystem.textualFileContent(change.filePath).result ?: "").toString()
            // The file object here is bogus, but it's used only for its extension.
            val file = File(change.filePath.toString())
            // Process, logging failures, and providing basic error content to the client in such cases.
            gen.processFile(file) { StringReader(source) }
                ?: throw IllegalArgumentException("No parser found")
        }.let {
            when (it) {
                is RSuccess -> buildString { it.result.writeTo(this) }
                is RFailure -> "# ERROR\n\n```\n${it.failure.stackTraceToString()}\n```\n"
            }
        }
        languageServer.client?.docGen(DocGenUpdate(key = key, content = content))
    }
}

data class DocGenKey(val uri: String, val backend: String)
data class DocGenUpdate(val key: DocGenKey, val content: String)

private const val TIMEOUT_MS = 100L

suspend fun fileChanges(watch: FileWatchService, millis: Long): MutableList<FileChange> {
    // Once we have some changes, wait around for any other immediate-ish changes.
    val allChanges = watch.changes.receive().toMutableList()
    withTimeoutOrNull(millis) {
        while (true) {
            watch.changes.receiveCatching().getOrNull()?.let {
                allChanges.addAll(it)
            }
        }
    }
    return allChanges
}
