package lang.temper.common

private class JsonEscaper(
    quote: Char?,
    asciiEscapes: Array<Escape>,
) : Utf16Escaper<Unit>(
    quote = quote,
    asciiEscapes = asciiEscapes,
) {
    override fun createState() = Unit

    @Suppress("MagicNumber") // Code-points
    override fun nonAsciiEscape(codePoint: Int): Escape = when (codePoint) {
        // JS newlines
        0x2028, 0x2029 -> uPlus4Escape
        in C_MIN_SURROGATE..C_MAX_SURROGATE -> uPlus4Escape
        else -> IdentityEscape
    }
    override fun withExtraEscapes(extras: Map<Char, Escape>, quote: Char?): AbstractEscaper<Unit> {
        val extrasWithQuote = when {
            quote == null -> extras
            quote in extras -> extras
            asciiEscapes.getOrNull(quote.code) !is IdentityEscape? -> extras
            else -> extras + mapOf(quote to uPlus4Escape)
        }
        return JsonEscaper(quote, mergeExtraEscapes(asciiEscapes, extrasWithQuote))
    }
}

val uPlus4Escape = HexEscape(
    prefix = "\\u",
    minDigits = 4,
    maxDigits = 4,
    suffix = "",
)

val unquotedJsonEscaper: Escaper = JsonEscaper(
    quote = null,
    asciiEscapes = mergeExtraEscapes(
        emptyArray(),
        // Escape all control characters
        extras = (0..'\u001f'.code).associate { it.toChar() to uPlus4Escape } +
            mapOf(
                // and some JSON-special chars
                '\t' to FixedEscape("\\t"),
                '\n' to FixedEscape("\\n"),
                '\r' to FixedEscape("\\r"),
                '\\' to FixedEscape("\\\\"),
                // and some HTML meta-characters since JSON often embeds in that.
                '<' to uPlus4Escape,
                '>' to uPlus4Escape,
                '&' to uPlus4Escape,
                '$' to uPlus4Escape,
            ),
    ),
)

val minimalUnquotedJsonEscaper: Escaper = JsonEscaper(
    quote = null,
    asciiEscapes = mergeExtraEscapes(
        emptyArray(),
        // Escape all control characters
        extras = (0..'\u001f'.code).associate { it.toChar() to uPlus4Escape } +
            mapOf(
                // and some JSON-special chars
                '\t' to FixedEscape("\\t"),
                '\n' to FixedEscape("\\n"),
                '\r' to FixedEscape("\\r"),
                '\\' to FixedEscape("\\\\"),
                '"' to FixedEscape("\\\""),
                '\'' to FixedEscape("\\'"),
                // and some HTML meta-characters since JSON often embeds in that.
                '/' to FixedEscape("\\/"),
            ),
    ),
)

/**
 * An escaper that uses JS/JSON/Java/Kotlin style escaping conventions.
 */
val jsonEscaper: Escaper = unquotedJsonEscaper.withQuote('"')

val backtickEscaper = unquotedJsonEscaper.withQuote('`')
val singleQuoteEscaper = unquotedJsonEscaper.withQuote('\'')
