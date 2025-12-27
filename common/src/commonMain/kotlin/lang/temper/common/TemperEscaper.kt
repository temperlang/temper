package lang.temper.common

private class EscState {
    var inBracketEscapeRun = false
}

private class TemperEscaper(
    quote: Char?,
    asciiEscapes: Array<Escape>,
) : Utf32Escaper<EscState>(
    quote = quote,
    asciiEscapes = asciiEscapes,
) {
    override fun createState() = EscState()

    override fun withExtraEscapes(extras: Map<Char, Escape>, quote: Char?): AbstractEscaper<EscState> {
        val extrasWithQuote = when {
            quote == null -> extras
            quote in extras -> extras
            asciiEscapes.getOrNull(quote.code) !is IdentityEscape? -> extras
            else -> extras + mapOf(quote to FixedEscape("\\$quote"))
        }
        return TemperEscaper(
            quote = quote,
            asciiEscapes = mergeExtraEscapes(asciiEscapes, extrasWithQuote),
        )
    }

    override fun nonAsciiEscape(codePoint: Int): Escape = temperEscape(codePoint, quote)

    override fun applyEscape(
        codePoint: Int,
        escape: Escape,
        pos: Int,
        state: EscState,
        out: StringBuilder,
    ) {
        if (escape == unicodeRunEscape) {
            if (state.inBracketEscapeRun) {
                out.append(',')
            } else {
                out.append("\\u{")
                state.inBracketEscapeRun = true
            }
        } else if (state.inBracketEscapeRun) {
            out.append('}')
            state.inBracketEscapeRun = false
        }
        super.applyEscape(codePoint, escape, pos, state, out)
    }

    override fun finish(state: EscState, out: StringBuilder) {
        if (state.inBracketEscapeRun) {
            out.append('}')
            state.inBracketEscapeRun = false
        }
    }

    override fun emitChunk(
        cs: CharSequence,
        start: Int,
        end: Int,
        state: EscState,
        out: StringBuilder,
    ) {
        if (start != end) {
            if (state.inBracketEscapeRun) {
                out.append('}')
                state.inBracketEscapeRun = false
            }
            super.emitChunk(cs, start, end, state, out)
        }
    }
}

private val unicodeRunEscape = HexEscape(
    prefix = "",
    minDigits = 1,
    maxDigits = 8,
    suffix = "",
)

private fun temperEscape(codePoint: Int, quote: Char?) = when (codePoint.charCategory) {
    // General categories L, M, N, Z, P, and S are broadly graphical.
    CharCategory.UPPERCASE_LETTER,
    CharCategory.LOWERCASE_LETTER,
    CharCategory.TITLECASE_LETTER,
    CharCategory.MODIFIER_LETTER,
    CharCategory.OTHER_LETTER,
    CharCategory.NON_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.DECIMAL_DIGIT_NUMBER,
    CharCategory.LETTER_NUMBER,
    CharCategory.OTHER_NUMBER,
    CharCategory.SPACE_SEPARATOR,
    CharCategory.LINE_SEPARATOR,
    CharCategory.PARAGRAPH_SEPARATOR,
    CharCategory.DASH_PUNCTUATION,
    CharCategory.START_PUNCTUATION,
    CharCategory.END_PUNCTUATION,
    CharCategory.CONNECTOR_PUNCTUATION,
    CharCategory.OTHER_PUNCTUATION,
    CharCategory.MATH_SYMBOL,
    CharCategory.CURRENCY_SYMBOL,
    CharCategory.MODIFIER_SYMBOL,
    CharCategory.OTHER_SYMBOL,
    CharCategory.INITIAL_QUOTE_PUNCTUATION,
    CharCategory.FINAL_QUOTE_PUNCTUATION,
    -> {
        if (codePoint == quote?.code) {
            unicodeRunEscape
        } else {
            IdentityEscape
        }
    } // Graphical

    // These other categories are not, so call them out by escaping them.
    CharCategory.UNASSIGNED,
    CharCategory.CONTROL,
    CharCategory.FORMAT,
    CharCategory.PRIVATE_USE,
    CharCategory.SURROGATE,
    -> unicodeRunEscape
}

private val unquotedTemperEscaperInternal = TemperEscaper(
    quote = null,
    asciiEscapes = (0..MAX_ASCII).map { cp ->
        temperEscape(cp, null)
    }.toTypedArray(),
).withExtraEscapes(
    mapOf(
        '\\' to FixedEscape("\\\\"),
        '\r' to FixedEscape("\\r"),
        '\n' to FixedEscape("\\n"),
        '\t' to FixedEscape("\\t"),
        '\u00A0' to unicodeRunEscape,
        '$' to unicodeRunEscape,
    ),
)

/**
 * An escaper that uses Temper-style escaping conventions for strings but
 * the caller is responsible for wrapping with quotes.
 */
val unquotedTemperEscaper: Escaper = unquotedTemperEscaperInternal

/**
 * An escaper that uses Temper-style escaping conventions for strings.
 *
 * This escapes certain code-points, using `\u{xxxxx}` style escapes
 * instead of using `\uxxxx` style escapes popular in UTF-16 oriented languages.
 *
 * Specifically, it escapes:
 * - all Temper string meta-characters
 *   - [Escaper.quote]
 *   - back-slash `\`
 *   - dollar-sign `$`
 *   - line-breaks CR & LF
 * - non-graphical unicode code-points
 *   - code-points in General Category C
 *   - unassigned code-points
 */
val temperEscaper: Escaper = unquotedTemperEscaperInternal.withExtraEscapes(
    mapOf('\"' to FixedEscape("\\\"")),
    quote = '"',
)

val backtickTemperEscaper: Escaper = unquotedTemperEscaperInternal.withExtraEscapes(
    mapOf('`' to FixedEscape("\\`")),
    quote = '`',
)

val singleQuoteTemperEscaper: Escaper = unquotedTemperEscaperInternal.withExtraEscapes(
    mapOf('\'' to FixedEscape("\\'")),
    quote = '\'',
)
