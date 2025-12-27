package lang.temper.cli.repl

import lang.temper.common.CustomValueFormatter
import lang.temper.common.FormatSink
import lang.temper.format.ConsoleBackedContextualLogSink
import lang.temper.log.CodeLocation
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.SharedLocationContext

internal class ReplLogSink(
    val repl: Repl,
    sharedLocationContext: SharedLocationContext,
) : ConsoleBackedContextualLogSink(
    repl.console,
    sharedLocationContext,
    null,
    ReplCustomValueFormatter(),
    messageFilter = ::filterReplLogMessages,
) {
    override fun metadataSuffix(template: MessageTemplateI): String? = null
}

/**
 * Formats [module names][lang.temper.name.ModuleName] for REPL chunks nicely.
 *
 * It's nicer to see the former below instead of the latter.
 *
 *     interactive#123:3+2: ...             // NICE
 *
 *     `-repl//i0123/chunk.temper`:3+2: ... // NOT
 */
private class ReplCustomValueFormatter : CustomValueFormatter {
    override fun maybeFormat(value: Any?, sink: FormatSink): Boolean {
        if (value is CodeLocation) {
            val index = ReplChunkIndex.from(value)
            if (index != null) {
                sink.formatReplacement("$index")
                return true
            }
        }
        return false
    }
}

private fun filterReplLogMessages(template: MessageTemplateI) =
    // This is a useful message for non-REPL cases, but in the REPL
    // we end up showing the module result anyway.
    template != MessageTemplate.NonValueResultFromRunStage &&
        template != MessageTemplate.Interpreting
