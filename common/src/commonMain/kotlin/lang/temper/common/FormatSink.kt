package lang.temper.common

/**
 * Invoked by [sprintfTo] to do the work of constructing an output from interleaved
 * chunks of template string content and values.
 *
 * So to format
 *
 *     "foo %s bar"
 *
 * with the values `[123]` will be invoked as
 *
 * - [formatTemplateStringFragment] with the whole format string and the range of characters
 *   corresponding to "foo ",
 * - [formatValue] with `123`
 * - [formatTemplateStringFragment] with the range of characters for " bar".
 *
 * [CustomValueFormatter]s may call back into this to aid in formatting a value.
 */
interface FormatSink {
    fun formatTemplateStringFragment(formatString: String, formatStringCharRange: IntRange)

    /**
     * May be called instead of [formatTemplateStringFragment] for any non-leaf values.
     * Also called when replacing the format string sequence `%%` with `%`.
     */
    fun formatAsTemplateString(charSequence: CharSequence)

    fun formatReplacement(charSequence: CharSequence)

    fun formatValue(value: Any?)

    /**
     * Called when there is no value for a replacement.
     * @see MISSING_VALUE_REPLACEMENT
     */
    fun missingValueReplacement()
}
