package lang.temper.common

import java.lang.StringBuilder

// %[parameter][flags][width][.precision][length]type
val formatMarker = Regex(
    listOf(
        """%(?:(\d+)[$])?""",
        """([\-+ 0'#]*)""",
        """([1-9]\d*|[*])?""",
        """(?:[.](\d+|[*]))?""",
        """(hh|h|l|ll|L|z|j|t)?""",
        @Suppress("SpellCheckingInspection") // Regex character sets are not words
        """([%diufFeEgGxXoscpaA])""",
    ).joinToString(""),
)

private const val PARAMETER_GROUP = 1
private const val FLAG_GROUP = 2
private const val WIDTH_GROUP = 3
private const val PRECISION_GROUP = 4

@Suppress("Unused", "UnusedPrivateMember") // Leaving it as it is a fact about the regex.
private const val LENGTH_GROUP = 5
private const val TYPE_GROUP = 6

// This is not part of any printf specification and just used until someone puts in the effort to
// do something better.
private const val DEFAULT_PRECISION = 4

fun sprintf(formatString: String, values: List<Any>): String = buildString {
    sprintfTo(
        formatString = formatString,
        values = values,
        formatSink = StringBuildingFormatSink(this),
    )
}

@Suppress("TooGenericExceptionCaught") // see comment in catch below
fun sprintfTo(formatString: String, values: List<Any>, formatSink: FormatSink) {
    val nValues = values.size

    var i = -1
    var pos = 0
    formatMarker.findAll(formatString).forEach formatOne@{
        // Emit chunk of template string
        val matchRange = it.range
        formatSink.formatTemplateStringFragment(formatString, pos until matchRange.first)
        pos = matchRange.last + 1

        try {
            val groups = it.groups
            val type = groups[TYPE_GROUP]?.value

            if (type == "%") { // %% collapses to %
                formatSink.formatAsTemplateString("%")
                return@formatOne
            }

            val flags = groups[FLAG_GROUP]?.value ?: ""
            val width: Int? = when (val w = groups[WIDTH_GROUP]?.value) {
                null, "" -> null
                "*" -> {
                    i += 1
                    (values[i] as Number).toInt()
                }
                else -> w.toInt(DECIMAL_RADIX)
            }
            val precision: Int? = when (val p = groups[PRECISION_GROUP]?.value) {
                null, "" -> null
                "*" -> {
                    i += 1
                    (values[i] as Number).toInt()
                }
                else -> p.toInt(DECIMAL_RADIX)
            }
            // Ignore length field
            val parameter = groups[PARAMETER_GROUP]?.value
            val parameterIndex = if (parameter == null) {
                i += 1
                i
            } else {
                val oneIndexed = parameter.toInt(DECIMAL_RADIX)
                require(oneIndexed in 1..nValues) { "$oneIndexed\$ from ${ groups[0] }" }
                oneIndexed - 1
            }

            if (parameterIndex !in values.indices) {
                formatSink.missingValueReplacement()
                return@formatOne
            }
            fun formattable(value: Any?): Formatted {
                if (value == null) { return FormattedNull }
                if (value is Map<*, *>) {
                    return FormatMap(value)
                }
                if (value is Map.Entry<*, *>) {
                    return FormatEntry(value)
                }
                if (value is Iterable<*>) {
                    return FormatIterable(value)
                }

                var isNumeric = false
                var formatted: Formatted = when (type) {
                    "d", "i" -> {
                        isNumeric = true
                        FormattedString((value as Number).toLong().toString(DECIMAL_RADIX))
                    }
                    // TODO: "e" is supposed to prefer scientific form
                    "e", "E", "f", "F" -> {
                        isNumeric = true
                        FormattedString(
                            sameCase(
                                formatDouble(
                                    (value as Number).toDouble(),
                                    precision ?: DEFAULT_PRECISION,
                                ),
                                type,
                            ),
                        )
                    }

                    "x", "X", "o" -> {
                        isNumeric = true
                        val num = (value as Number).toLong()
                        val radix = if (type == "x" || type == "X") HEX_RADIX else OCTAL_RADIX
                        val prefix = when {
                            num == 0L || '#' !in flags -> ""
                            type == "x" -> "0x"
                            type == "X" -> "0X"
                            else -> "0"
                        }
                        FormattedString(
                            sameCase(
                                "$prefix${num.toString(radix)}",
                                type,
                            ),
                        )
                    }

                    "s" -> FormattedValue(value)

                    else -> TODO(type ?: "")
                }

                if (isNumeric && formatted is FormattedString && formatted.text[0] != '-') {
                    if ('+' in flags) {
                        formatted = FormattedString("+${formatted.text}")
                    } else if (' ' in flags) {
                        formatted = FormattedString(" ${formatted.text}")
                    }
                }

                if (precision != null && !isNumeric) {
                    formatted = PrecisionLimit(formatted, precision)
                }

                if (width != null) {
                    formatted = WidthLimit(formatted, width = width, isNumeric = isNumeric, flags = flags)
                }

                return formatted
            }
            fun format(value: Any?) {
                formattable(value).appendReplacement(formatSink, format = ::format)
            }
            format(values[parameterIndex])
        } catch (e: Exception) {
            // If there's an error during printing an error message, it's better to get
            // some diagnostic info than to get nothing.
            try {
                formatSink.formatReplacement(e::class.simpleName ?: "Exception")
            } catch (cannotEvenDumpErrorName: Exception) {
                ignore(cannotEvenDumpErrorName)
            }
        }
    }

    formatSink.formatTemplateStringFragment(formatString, pos until formatString.length)
}

private fun sameCase(str: String, exemplar: String) =
    if (exemplar.getOrNull(0) in 'a'..'z') {
        str.asciiLowerCase()
    } else {
        str.asciiUpperCase()
    }

const val MISSING_VALUE_REPLACEMENT = "\u274E"

/**
 * https://en.wikipedia.org/wiki/Double-precision_floating-point_format
 * > If an IEEE 754 double-precision number is converted to a decimal string with at least 17
 * > significant digits, and then converted back to double-precision representation, the final
 * > result must match the original number.
 */
const val MAX_DECIMAL_DIGITS_FOR_64B_MANTISSA = 17

const val SAFE_DOUBLE_FORMAT_STRING = "%1.${MAX_DECIMAL_DIGITS_FOR_64B_MANTISSA}f"

class StringBuildingFormatSink(
    val stringBuilder: StringBuilder,
) : FormatSink {
    override fun formatTemplateStringFragment(formatString: String, formatStringCharRange: IntRange) {
        stringBuilder.append(formatString, formatStringCharRange.first, formatStringCharRange.last + 1)
    }

    override fun formatAsTemplateString(charSequence: CharSequence) {
        stringBuilder.append(charSequence)
    }

    override fun formatReplacement(charSequence: CharSequence) {
        stringBuilder.append(charSequence)
    }

    override fun formatValue(value: Any?) {
        if (value is Formattable) {
            stringBuilder.append(value.preformat())
        } else {
            stringBuilder.append(value)
        }
    }

    override fun missingValueReplacement() {
        stringBuilder.append(MISSING_VALUE_REPLACEMENT)
    }
}

private sealed class Formatted {
    abstract fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit)
}

private object FormattedNull : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        formatSink.formatReplacement("null")
    }
}

private data class FormatMap(val map: Map<*, *>) : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        formatSink.formatAsTemplateString("{")
        map.entries.forEachIndexed { index, e ->
            if (index != 0) {
                formatSink.formatAsTemplateString(", ")
            }
            format(e)
        }
        formatSink.formatAsTemplateString("}")
    }
}

private data class FormatIterable(val iterable: Iterable<*>) : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        formatSink.formatAsTemplateString("[")
        iterable.forEachIndexed { index, e ->
            if (index != 0) {
                formatSink.formatAsTemplateString(", ")
            }
            format(e)
        }
        formatSink.formatAsTemplateString("]")
    }
}

private data class FormatEntry(val entry: Map.Entry<*, *>) : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        format(entry.key)
        formatSink.formatAsTemplateString(": ")
        format(entry.value)
    }
}

private data class FormattedString(val text: String) : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        formatSink.formatReplacement(text)
    }
}

private data class FormattedValue(val value: Any?) : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        formatSink.formatValue(value)
    }
}

private data class PrecisionLimit(
    val underlying: Formatted,
    val precision: Int,
) : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        val text = buildString {
            underlying.appendReplacement(StringBuildingFormatSink(this), format)
        }
        formatSink.formatReplacement(
            if (text.length > precision) {
                text.substring(0, precision)
            } else {
                text
            },
        )
    }
}

private data class WidthLimit(
    val underlying: Formatted,
    val width: Int,
    val isNumeric: Boolean,
    val flags: String,
) : Formatted() {
    override fun appendReplacement(formatSink: FormatSink, format: (Any?) -> Unit) {
        val text = buildString {
            underlying.appendReplacement(StringBuildingFormatSink(this), format)
        }
        formatSink.formatReplacement(
            if (text.length < width) {
                val leftAlign = ('-' in flags)
                val padding = (
                    if (isNumeric && "0" in flags) "0" else " "
                    ).repeat(width - text.length)
                if (leftAlign) "$text$padding" else "$padding$text"
            } else {
                text
            },
        )
    }
}
