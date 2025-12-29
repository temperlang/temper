package lang.temper.log

import lang.temper.common.Log

/**
 * Like [TEE(1)](https://man7.org/linux/man-pages/man1/tee.1.html), publishes log events to each
 * of several log sinks.
 */
class TeeLogSink(private val logSinks: List<LogSink>) : LogSink {
    override val hasFatal: Boolean get() = logSinks.any { it.hasFatal }

    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        logSinks.forEach {
            it.log(level = level, template = template, pos = pos, values = values, fyi = fyi)
        }
    }

    override fun resetUsage() {
        for (logSink in logSinks) {
            logSink.resetUsage()
        }
    }
}
