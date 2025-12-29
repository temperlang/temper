package lang.temper.log

import lang.temper.common.Log
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured

/** Receives log messages. */
interface LogSink {
    /**
     * If level >= [Log.Error] then this message indicates that
     * the compiler output is not suitable for a production build.
     * [Log.Fatal] if this message indicates that staging should halt.
     *
     * @param level the log level of the message.
     */
    fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        /**
         * True if this call is advisory as when a child log sink thinks it handled it but is
         * passing it on to a parent as an FYI.
         */
        fyi: Boolean = false,
    )

    /**
     * Logs using the suggested message's level.
     */
    fun log(
        template: LeveledMessageTemplate,
        pos: Position,
        values: List<Any>,
        /**
         * True if this call is advisory as when a child log sink thinks it handled it but is
         * passing it on to a parent as an FYI.
         */
        fyi: Boolean = false,
    ) = log(
        level = template.suggestedLevel,
        template = template,
        pos = pos,
        values = values,
        fyi = fyi,
    )

    /** True iff a [Log.Fatal] message has been logged via this sink. */
    val hasFatal: Boolean

    /** Reset for new round of reporting. */
    fun resetUsage() {}

    companion object {
        /** A log sink that drops messages and never reports fatality. */
        val devNull: LogSink = DevNull
    }
}

/**
 * Bundles inputs to [LogSink.log].
 */
data class LogEntry(
    val level: Log.Level,
    val template: MessageTemplateI,
    val pos: Position,
    val values: List<Any>,
    val fyi: Boolean = false,
) : Structured {
    constructor (
        template: LeveledMessageTemplate,
        pos: Position,
        values: List<Any>,
        fyi: Boolean = false,
    ) : this(
        level = template.suggestedLevel,
        template = template,
        pos = pos,
        values = values,
        fyi = fyi,
    )

    fun logTo(logSink: LogSink) = logSink.log(
        level = level,
        template = template,
        pos = pos,
        values = values,
        fyi = fyi,
    )

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("level", Hints.nu) { value(level) }
        key("template", Hints.n) { value(template.name) }
        key("values", Hints.n) { value(values) }
        key("formatted", Hints.su) { value(messageText) }
        pos.positionPropertiesTo(this, Hints.u)
    }

    val messageText: String get() {
        val formatted = template.format(values)
        return when (level) {
            Log.Fatal -> "$formatted!!"
            Log.Error -> "$formatted!"
            Log.Warn, Log.Fine, Log.Info -> formatted
        }
    }
}

private object DevNull : LogSink {
    override val hasFatal = false
    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        // no-op
    }
}
