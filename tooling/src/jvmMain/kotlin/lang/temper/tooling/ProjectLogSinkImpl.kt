package lang.temper.tooling

import lang.temper.common.Console
import lang.temper.common.CustomValueFormatter
import lang.temper.format.ConsoleBackedContextualLogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.SharedLocationContext

internal class ProjectLogSinkImpl(
    cliConsole: Console,
    sharedLocationContext: SharedLocationContext?,
    allowDuplicateLogPositions: Boolean = false,
) : ConsoleBackedContextualLogSink(
    localConsole = cliConsole,
    sharedLocationContext = sharedLocationContext,
    parent = null,
    customValueFormatter = CustomValueFormatter.Nope,
    allowDuplicateLogPositions = allowDuplicateLogPositions,
) {
    override fun metadataSuffix(template: MessageTemplateI): String? = null
}
