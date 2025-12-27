package lang.temper.langserver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.log.FilePosition
import lang.temper.log.FilePositions
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.LogEntry
import lang.temper.log.Position
import lang.temper.tooling.DeclInfo
import lang.temper.tooling.DeclKind
import lang.temper.tooling.ModuleDataUpdate
import lang.temper.tooling.ModuleDiagnosticsUpdate
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range

@DelicateCoroutinesApi
internal class UpdatePublisher(val languageServer: TemperLanguageServerImpl) {
    val channel = Channel<LanguageServerUpdate>(Channel.UNLIMITED)

    /** Channel all arbitrarily timed updates from server to client through here. */
    fun publishUpdates() {
        runBlocking {
            val buildListeners = mutableMapOf<ServerBuildManager, Job>()
            try {
                runCatching {
                    while (true) {
                        when (val update = channel.receive()) {
                            LanguageServerClose -> break
                            else -> processUpdate(buildListeners = buildListeners, update = update)
                        }
                    }
                }.onFailure { ex ->
                    when (ex) {
                        is CancellationException -> throw ex
                        else -> console.error("Error in publish updates", ex)
                    }
                }
            } finally {
                // All done. Cancel local coroutines so we can exit.
                for (listener in buildListeners.values) {
                    listener.cancel()
                }
                buildListeners.clear()
            }
        }
    }

    private fun CoroutineScope.processUpdate(
        buildListeners: MutableMap<ServerBuildManager, Job>,
        update: LanguageServerUpdate,
    ) {
        when (update) {
            is LanguageServerClose -> error("Should be handled externally")
            is ModuleDataLanguageServerUpdate -> when (val dataUpdate = update.update) {
                is ModuleDiagnosticsUpdate -> {
                    makePublishDiagnosticParams(
                        buildManager = update.buildManager,
                        update = dataUpdate,
                    ).forEach { languageServer.client?.publishDiagnostics(it) }
                }
            }
            is ModuleDataStoreCloseUpdate -> {
                buildListeners.remove(update.buildManager)?.cancel()
            }
            is ModuleDataStoreOpenUpdate -> {
                // Map channel, and track it.
                val buildManager = update.buildManager
                buildListeners[buildManager] = launch {
                    buildManager.moduleDataUpdateChannel.consumeEach {
                        channel.send(
                            ModuleDataLanguageServerUpdate(buildManager = buildManager, update = it),
                        )
                    }
                }
            }
        }
    }

    private fun makePublishDiagnosticParams(
        buildManager: ServerBuildManager,
        update: ModuleDiagnosticsUpdate,
    ): List<PublishDiagnosticsParams> {
        val grouped = update.logEntries.groupBy { (it.pos.loc as FileRelatedCodeLocation).sourceFile }
        return update.filePositionsMap.entries.mapNotNull files@{ (relPath, filePositions) ->
            val uri = (buildManager.resolve(relPath) ?: return@files null).toUri().toString()
            val logEntries = grouped.getOrDefault(relPath, listOf())
            PublishDiagnosticsParams().apply {
                this.uri = uri
                diagnostics = logEntries.mapNotNull { it.toDiagnostic(filePositions = filePositions) }
            }
        }
    }
}

internal sealed interface LanguageServerUpdate

internal object LanguageServerClose : LanguageServerUpdate

@DelicateCoroutinesApi
internal class ModuleDataLanguageServerUpdate(
    val buildManager: ServerBuildManager,
    val update: ModuleDataUpdate,
) : LanguageServerUpdate

@DelicateCoroutinesApi
internal class ModuleDataStoreOpenUpdate(val buildManager: ServerBuildManager) : LanguageServerUpdate

@DelicateCoroutinesApi
internal class ModuleDataStoreCloseUpdate(val buildManager: ServerBuildManager) : LanguageServerUpdate

fun LogEntry.toDiagnostic(filePositions: FilePositions): Diagnostic? {
    level.ordinal >= Log.Warn.ordinal || return null
    val entry = this
    return Diagnostic().apply {
        // Avoid entry.messageText because bangs that are handled here explicitly.
        message = entry.template.format(entry.values)
        range = entry.pos.toRange(filePositions = filePositions)
        severity = when (entry.level) {
            Log.Fine -> DiagnosticSeverity.Hint
            Log.Info -> DiagnosticSeverity.Information
            Log.Warn -> DiagnosticSeverity.Warning
            Log.Error, Log.Fatal -> DiagnosticSeverity.Error
        }
    }
}

fun Position.toRange(filePositions: FilePositions) = Range(
    filePositions.filePositionAtOffset(left).toLsp(),
    filePositions.filePositionAtOffset(right).toLsp(),
)

/** Translates from 1-based lines to 0-based. */
fun FilePosition.toLsp() = org.eclipse.lsp4j.Position(line - 1, charInLine)

/** Translates from 0-based lines to 1-based. */
fun org.eclipse.lsp4j.Position.toTemper() = FilePosition(line = line + 1, charInLine = character)

fun DeclInfo?.toHover(): Hover = when (this) {
    null -> Hover(emptyList())
    else -> {
        val box = when (box) {
            null -> ""
            else -> "$box."
        }
        val type = when (kind) {
            DeclKind.Class -> ""
            else -> ": ${type ?: "?"}"
        }
        Hover(
            // Hijack TypeScript highlighting for now.
            // TODO(tjp, tooling): Styling that ideally matches other temper syntax highlighting.
            // TODO(tjp, tooling): Could names include triple backtick or anything else crazy???
            MarkupContent().also { markup ->
                markup.kind = "markdown"
                // We exclude punctuation here because it's already included above where applicable.
                // A common example using all parts might be "(property) Person.age: Int".
                markup.value =
                    """
                    ```temper
                    ${kind.toText()} $box$name$type
                    ```
                    """.trimIndent()
            },
        )
    }
}

fun DeclKind.toText() = when (this) {
    DeclKind.Class, DeclKind.Let, DeclKind.Var -> toString().lowercase()
    // For vscode TypeScript and Python, non-keywords get parenthesized, so copy that style.
    else -> "(${toString().lowercase()})"
}
