package lang.temper.format

import lang.temper.common.CustomValueFormatter
import lang.temper.common.FormatSink
import lang.temper.common.Log
import lang.temper.common.MISSING_VALUE_REPLACEMENT
import lang.temper.common.sprintfTo
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext

/**
 * Wraps a log sink to make sure its [parent] receives simplified
 * [values][lang.temper.log.LogEntry.values].
 *
 * Simplification happens in several ways:
 *
 * - instances of [lang.temper.log.SimplifiesInLogMessage] can substitute a value
 * - [TokenSerializable]s are reduced to a string from their token sequence
 *
 * See [LogSinkValueFormatter] for all the simplifications.
 */
class ValueSimplifyingLogSink(
    private val parent: LogSink,
    private val nameSimplifying: Boolean,
    override val customValueFormatter: CustomValueFormatter = CustomValueFormatter.Nope,
    private val sharedLocationContext: SharedLocationContext? = null,
) : FormattingLogSink {
    override val hasFatal: Boolean
        get() = parent.hasFatal

    override fun log(level: Log.Level, template: MessageTemplateI, pos: Position, values: List<Any>, fyi: Boolean) {
        val adjustedValues = values.map {
            val converter = Converter(
                CustomValueFormatter.chain(
                    customValueFormatter,
                    LogSinkValueFormatter(
                        simplifying = nameSimplifying,
                        sharedLocationContext = sharedLocationContext,
                        textOutput = null,
                        formattedPositions = null,
                    ),
                ),
            )
            sprintfTo("%s", listOf(it), converter)
            converter.getValue() ?: "null"
        }
        parent.log(level, template, pos, adjustedValues, fyi)
    }

    private class Converter(
        val customValueFormatter: CustomValueFormatter,
    ) : FormatSink {
        private sealed class Content
        private object NothingYet : Content()
        private class BufferedString : Content() {
            val buffer = StringBuilder()

            fun append(value: Any?) {
                buffer.append(value)
            }
        }
        private class ConvertedValue(val value: Any?) : Content()

        private var content: Content = NothingYet

        override fun formatTemplateStringFragment(formatString: String, formatStringCharRange: IntRange) {
            formatAsTemplateString(formatString.substring(formatStringCharRange))
        }

        override fun formatAsTemplateString(charSequence: CharSequence) {
            if (charSequence.isNotEmpty()) {
                requireBufferedString().buffer.append(charSequence)
            }
        }

        override fun formatReplacement(charSequence: CharSequence) {
            if (charSequence.isNotEmpty()) {
                requireBufferedString().buffer.append(charSequence)
            }
        }

        override fun formatValue(value: Any?) {
            if (customValueFormatter.maybeFormat(value, this)) {
                // recursive call will get the converted value
                return
            }
            when (content) {
                NothingYet -> content = ConvertedValue(value)
                else -> requireBufferedString().append("$value")
            }
        }

        private fun requireBufferedString(): BufferedString {
            when (val before = content) {
                is BufferedString -> {}
                is ConvertedValue -> {
                    val after = BufferedString()
                    after.append(before.value)
                    content = after
                }
                NothingYet -> content = BufferedString()
            }
            return content as BufferedString
        }

        override fun missingValueReplacement() {
            formatReplacement(MISSING_VALUE_REPLACEMENT)
        }

        fun getValue() = when (val content = this.content) {
            is BufferedString -> content.buffer.toString()
            is ConvertedValue -> content.value
            NothingYet -> null
        }
    }
}
