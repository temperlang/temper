package lang.temper.langserver

import lang.temper.common.Console
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.MessageTemplate
import lang.temper.tooling.initConfigContent
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.services.LanguageClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name
import kotlin.io.path.notExists

internal fun suggestLibraryConfig(
    client: LanguageClient,
    console: Console,
    forFile: FilePath,
    workspaceFolder: Path,
) {
    // No library config for this path, so suggest one.
    client.showMessageRequest(
        ShowMessageRequestParams().apply {
            type = MessageType.Warning
            message = MessageTemplate.CreateLibraryConfig.format(listOf(forFile, workspaceFolder.name))
            actions = listOf(
                // Another option is to suggest all parent dirs and let them choose, but they might need to dig around
                // to get an informed opinion, anyway, if the top isn't an obvious choice.
                // And without extra attached values to action choices, repeated dir names would be unclear, unless we
                // give full paths, which would require more ui control than lsp itself gives.
                MessageTemplate.CreateLibraryConfigYes.formatString,
                // Technically, we could leave this off, as the user can just close the notice, but this might make
                // the option clearer.
                MessageTemplate.CreateLibraryConfigNo.formatString,
            ).map {
                // If I subclass MessageActionItem, I can send values in addition to title, and vscode says it
                // has "additionalPropertiesSupport", but I haven't managed to get those values back.
                // See also: https://github.com/eclipse/lsp4j/issues/614
                // So just send the raw text and match on it for the reply.
                MessageActionItem(it)
            }
        },
    ).handle reply@{ item, err ->
        when {
            item != null -> if (item.title == MessageTemplate.CreateLibraryConfigYes.formatString) {
                val configPath = workspaceFolder.resolve(LibraryConfiguration.fileName.fullName)
                if (configPath.notExists()) {
                    runCatching {
                        Files.writeString(configPath, DefaultLibraryConfigContent, StandardOpenOption.CREATE_NEW)
                    }.onFailure {
                        client.showMessage(
                            MessageParams().apply {
                                type = MessageType.Error
                                message = MessageTemplate.CreateLibraryConfigFailed.format(listOf(configPath))
                            },
                        )
                        console.error("Failed to create $configPath", it)
                        return@reply
                    }
                    // Made the config. Show it to the user.
                    client.showDocument(ShowDocumentParams("${configPath.toUri()}"))
                }
            }
            err != null -> console.error("Library suggestion error", err)
            else -> console.info("Library suggestion empty response")
        }
    }
}

val DefaultLibraryConfigContent = initConfigContent(title = "Library Name")

enum class SuggestionDelay {
    None,
    Immediate,
    Delayed,
}

/** Throttle user annoyance in hopes of actually being helpful. */
fun shouldSuggest(suggestionDelay: SuggestionDelay, lastSuggestionTimeMillis: Long): Boolean {
    val suggestionTime = lastSuggestionTimeMillis + when (suggestionDelay) {
        SuggestionDelay.None -> return false
        SuggestionDelay.Immediate -> 0
        SuggestionDelay.Delayed -> SUGGESTION_DELAY_TIME_MILLIS
    }
    return System.currentTimeMillis() > suggestionTime
}

/** Arbitrarily selected delay that might depend on the thing being timed. */
private const val SUGGESTION_DELAY_TIME_MILLIS = 30_000L
