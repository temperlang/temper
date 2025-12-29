package lang.temper.format

import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI

/** A log sink that filters out some log messages. */
interface FilteringLogSink : LogSink {
    /** Returns true if the */
    val messageFilter: (MessageTemplateI) -> Boolean
}
