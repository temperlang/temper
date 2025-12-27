package lang.temper.lexer

/** The type of tokens recognized by Temper. */
enum class TokenType(
    /**
     * True if a token of this type could be replaced with a single space character without
     * affecting semantics.
     */
    val ignorable: Boolean,
    /**
     * True if tokens of this type can be operators or brackets.
     */
    val grammatical: Boolean,
) {
    /** Comment tokens. */
    Comment(ignorable = true, grammatical = false),

    /** Numeric tokens. */
    Number(ignorable = false, grammatical = false),

    /** Punctuation tokens. */
    Punctuation(ignorable = false, grammatical = true),

    /** Open quote token */
    LeftDelimiter(ignorable = false, grammatical = true),

    /** Close quote token */
    RightDelimiter(ignorable = false, grammatical = true),

    /** Quoted string content tokens. */
    QuotedString(ignorable = false, grammatical = false),

    /** Space tokens. */
    Space(ignorable = true, grammatical = false),

    /** Word tokens. */
    Word(ignorable = false, grammatical = true),

    /** Malformed token. */
    Error(ignorable = false, grammatical = false),
}
