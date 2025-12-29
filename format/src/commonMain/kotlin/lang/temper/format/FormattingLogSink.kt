package lang.temper.format

import lang.temper.common.CustomValueFormatter
import lang.temper.log.LogSink

/**
 * A log sink that uses [sprintfTo][lang.temper.common.sprintfTo] format strings
 * to format messages.
 *
 * This allows for customizing how values are presented by tweaking
 * [customValueFormatter].
 */
interface FormattingLogSink : LogSink {
    val customValueFormatter: CustomValueFormatter
}
