package lang.temper.format

import lang.temper.common.Style

/** This is not language specific, unlike the token type used by Temper's lexer. */
sealed interface OutputTokenType {
    val style: Style

    sealed class StandardOutputTokenType(
        override val style: Style,
        val name: String,
    ) : OutputTokenType {
        companion object {
            fun valueOf(name: String): StandardOutputTokenType? = nameToValue[name]
            val entries get() = standardEntries
        }
    }

    /** A keyword token like `if` or `null`. */
    data object Word : StandardOutputTokenType(Style.KeyWordToken, "Word")

    /** A language identifier like a variable or label name. */
    data object Name : StandardOutputTokenType(Style.IdentifierToken, "Name")

    /** A quoted string like a string or character literal. */
    data object QuotedValue : StandardOutputTokenType(Style.QuotedStringToken, "QuotedValue")

    /** A number literal. */
    data object NumericValue : StandardOutputTokenType(Style.NumberToken, "NumericValue")

    /** A literal value. */
    data object OtherValue : StandardOutputTokenType(Style.ValueToken, "OtherValue")

    /** A punctuation or operator token. */
    data object Punctuation : StandardOutputTokenType(Style.PunctuationToken, "Punctuation")

    /** An ignorable comment. */
    data object Comment : StandardOutputTokenType(Style.CommentToken, "Comment")

    /** Ignorable space. */
    data object Space : StandardOutputTokenType(Style.NormalOutput, "Space")

    /** A token that is not emitted at all. */
    data object NotEmitted : StandardOutputTokenType(Style.NormalOutput, "NotEmitted")

    /**
     * A target language specific token type that makes sense for use with
     * custom [FormattingTokenSink][FormattingHints.makeFormattingTokenSink]s.
     *
     * May be treated as equivalent to [OtherValue] by code that doesn't
     * understand its specifics.
     */
    interface Custom : OutputTokenType

    companion object {
        private val standardEntries = listOf(
            Word, Name, QuotedValue, NumericValue,
            OtherValue, Punctuation, Comment, Space,
            NotEmitted,
        )
        private val nameToValue = standardEntries.associateBy { it.name }
    }
}
