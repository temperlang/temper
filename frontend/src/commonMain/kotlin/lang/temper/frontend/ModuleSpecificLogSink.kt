package lang.temper.frontend

import lang.temper.common.CustomValueFormatter
import lang.temper.format.ConsoleBackedContextualLogSink
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI

class ModuleSpecificLogSink(
    projectLogSink: LogSink,
    val module: Module,
    customValueFormatter: CustomValueFormatter,
    messageFilter: (MessageTemplateI) -> Boolean,
    allowDuplicateLogPositions: Boolean = false,
) : ConsoleBackedContextualLogSink(
    module.console,
    sharedLocationContext = module.sharedLocationContext,
    parent = projectLogSink,
    customValueFormatter = customValueFormatter,
    messageFilter = messageFilter,
    simplifying = (projectLogSink as? ConsoleBackedContextualLogSink)?.simplifying == true,
    allowDuplicateLogPositions = allowDuplicateLogPositions,
) {
    val projectLogSink: LogSink get() = this.parent!!

    override fun metadataSuffix(template: MessageTemplateI): String? =
        if (template == MessageTemplate.StartingStage) {
            null
        } else {
            module.currentStage?.let { "$it" }
        }
}
