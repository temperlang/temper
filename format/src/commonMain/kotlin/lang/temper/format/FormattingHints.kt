package lang.temper.format

import lang.temper.common.TriState
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.LogSink

/**
 * Formatting hints are attached to an out grammar and customize the behavior of [FormattingTokenSink] and
 * [CodeFormatter].
 */
interface FormattingHints {
    /**
     * Creates a new [FormattingTokenSink] whose [out][FormattingTokenSink.out] is [nonFormattingTokenSink]
     * and whose [formattingHints][FormattingTokenSink.formattingHints] is this.
     */
    fun makeFormattingTokenSink(
        nonFormattingTokenSink: TokenSink,
        filePositions: FilePositions = FilePositions.nil,
        singleLine: Boolean = allowSingleLine && filePositions == FilePositions.nil,
        /**
         * Optional log sink that may be used if it is impossible to format code properly.
         * This may be the case for languages that have hard restrictions like column limits.
         * It's better to know about errors as they arise.
         */
        logSink: LogSink? = null,
        /**
         * The path used for positions in errors reported to [logSink] about
         * problems producing conforming output.
         */
        outPath: FilePath? = null,
    ): FormattingTokenSink = CStyleFormattingTokenSink(
        out = nonFormattingTokenSink,
        filePositions = filePositions,
        formattingHints = this,
        singleLine = singleLine,
    )

    /** Formatters may check this to require multi-line output. */
    val allowSingleLine: Boolean
        get() = true
    val localLevelIndents: Boolean
        get() = true

    /** The standard whitespace to repeat in order to indent one level. */
    val standardIndent: String get() = "  "

    /** Examines the token to determine whether the next line should be indented. */
    fun indents(token: OutputToken) = when {
        token == SpecialTokens.indent -> true
        token.type == OutputTokenType.Punctuation -> when (token.text) {
            "(", "{", "[", "\\{", "\\(", "\\[" -> true
            "<" -> token.association == TokenAssociation.Bracket
            else -> false
        }

        else -> false
    }

    /** Examines the token to indicate whether the current line should be dedented. */
    fun dedents(token: OutputToken) = when {
        token == SpecialTokens.dedent -> true
        token.type == OutputTokenType.Punctuation -> when (token.text) {
            ")", "}", "]" -> true
            ">" -> token.association == TokenAssociation.Bracket
            else -> false
        }
        else -> false
    }

    /** Examines the two tokens to require space between them. */
    fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean {
        if (preceding.type == OutputTokenType.NotEmitted || following.type == OutputTokenType.NotEmitted) {
            // Never put spaces around unemitted tokens.
            return false
        }
        if (preceding.type == OutputTokenType.Comment) {
            val commentText = preceding.text
            if (commentText.startsWith("//") && commentText.endsWith("\n")) {
                // Already have newline
                return false
            }
        }

        if (dotExclusion(preceding, following)) {
            return true
        }

        if (preceding.association == TokenAssociation.Infix ||
            following.association == TokenAssociation.Infix
        ) {
            return true
        }

        if ((preceding.association == TokenAssociation.Bracket && indents(preceding)) ||
            (following.association == TokenAssociation.Bracket && dedents(following)) ||
            (preceding.type == OutputTokenType.Punctuation && preceding.association == TokenAssociation.Prefix) ||
            (following.type == OutputTokenType.Punctuation && following.association == TokenAssociation.Postfix)
        ) {
            return false
        }

        if (following.association == TokenAssociation.Bracket && following.text == "<") {
            return false
        }

        val ptext = preceding.text
        val ptype = preceding.type
        val ftext = following.text
        val ftype = following.type

        val b =
            spaceBetween[ptext to ftext]
                ?: spaceBetween[ptext to ftype]
                ?: spaceBetween[ptype to ftext]
                ?: spaceBetween[ptype to ftype]
        if (b != null) {
            return b
        }

        val before = spaceAfter[ptext] ?: spaceAfter[ptype]
        val after = spaceBefore[ftext] ?: spaceBefore[ftype]
        val freq = if (before != null) {
            if (after == null) {
                before
            } else {
                if (after.confidence > before.confidence) { after } else { before }
            }
        } else {
            after
        }
        return freq?.valence ?: true
    }

    /** Restricts insertion of line breaks recommended by the shouldBreak* methods. */
    fun mayBreakLineBetween(preceding: OutputToken, following: OutputToken): Boolean =
        true

    /** Recommends line breaks before a token. */
    fun shouldBreakBefore(token: OutputToken) =
        token.type == OutputTokenType.Punctuation && token.text == "}"

    /** Recommends line breaks after a token. */
    fun shouldBreakAfter(token: OutputToken) =
        token.type == OutputTokenType.Punctuation && when (token.text) {
            "{", ";" -> true
            ":" -> token.association == TokenAssociation.Postfix
            else -> false
        }

    /** Recommends line breaks between tokens. */
    fun shouldBreakBetween(preceding: OutputToken, following: OutputToken): TriState {
        if (preceding.type == OutputTokenType.Punctuation) {
            when (preceding.text) {
                "}" -> return TriState.of(!continuesStatementAfterBlock(following))
                "{" -> {
                    if (following.type == OutputTokenType.Punctuation && following.text == "}") {
                        return TriState.FALSE
                    }
                }
            }
        }
        return TriState.OTHER
    }

    /** Indicates token that continues a compound statement after a closing brace, e.g. `catch` after `}` */
    fun continuesStatementAfterBlock(token: OutputToken) = token in blockContinuers

    /**
     * Called when [FormattableTree]s [operator definitions][FormattableTree.operatorDefinition]
     * indicates that parentheses are needed.
     *
     * This default implementation returns two callbacks that respectively amit an open
     * and close parenthesis.
     *
     * @return two callbacks, the first of which appends any tokens that should precede the inner
     *     child, the second of which appends any tokens that should follow.
     */
    fun parenthesize(
        outer: FormattableTree,
        inner: FormattableTree,
    ): Pair<(TokenSink) -> Unit, (TokenSink) -> Unit> = parenthesizerPair

    fun localLevel(token: OutputToken, plan: Boolean?): Boolean? =
        if (
            plan == true && token.type == OutputTokenType.Punctuation &&
            token.text == ":" && token.association == TokenAssociation.Postfix
        ) {
            // The `:` after `case` and `default` statements shouldn't only
            // indent the next statement.
            // We don't want:
            //
            //    default:
            //      first;
            //    second;
            false
        } else {
            plan
        }

    /** For easy observation of a token after being processed. */
    fun tokenProcessed(token: OutputToken) {}

    object Default : FormattingHints
}

private enum class Freq(val valence: Boolean, val confidence: Int) {
    Never(false, 1),
    Rarely(false, 0),
    Often(true, 0),
    Always(true, 1),
}

private val spaceBefore = mapOf<Any, Freq>(
    ")" to Freq.Never,
    "}" to Freq.Never,
    "]" to Freq.Never,
    "(" to Freq.Rarely,
    "[" to Freq.Rarely,
    "{" to Freq.Rarely,
    "\\(" to Freq.Rarely,
    "\\[" to Freq.Rarely,
    "\\{" to Freq.Rarely,
    "," to Freq.Never,
    ";" to Freq.Never,
    ":" to Freq.Never, // except ternary
    "." to Freq.Rarely,
    ".." to Freq.Rarely,
    "=" to Freq.Always,
)

private val spaceAfter = mapOf<Any, Freq>(
    "=" to Freq.Always,
    "." to Freq.Rarely,
    ".." to Freq.Rarely,
    "," to Freq.Always,
    ":" to Freq.Always,
    "(" to Freq.Never,
    "[" to Freq.Never,
    "{" to Freq.Never,
    "=>" to Freq.Often,
)

private val spaceBetween = mapOf<Pair<Any, Any>, Boolean>(
    (")" to "{") to true, // if (...) {
    (">" to "{") to true, // Type<TYPE_PARAM> {
    ("," to ")") to false, // f(a, b, c,)
    ("," to "]") to false, // [a, b, c,]
    ("{" to "}") to false, // {}
    ("\\{" to "}") to false, // \{}
    ("this" to "(") to false, // this(...)
    ("@" to OutputTokenType.Word) to false, // @annotation
    ("@" to OutputTokenType.Name) to false, // @annotation
    ("..." to OutputTokenType.Word) to false, // ...Int
    ("..." to OutputTokenType.Name) to false, // ...Int
    (OutputTokenType.Word to "(") to true, // if (
    (OutputTokenType.Name to "{") to true, // f {
    (OutputTokenType.Word to "{") to true, // do {
)

private fun dotExclusion(preceding: OutputToken, following: OutputToken): Boolean =
    (isAllDots(preceding) && cannotBeDotAdjacent(following)) ||
        (isAllDots(following) && cannotBeDotAdjacent(preceding))

private fun cannotBeDotAdjacent(token: OutputToken) = token.type == OutputTokenType.NumericValue ||
    (token.type == OutputTokenType.Punctuation && '.' in token.text)

private fun isAllDots(token: OutputToken) =
    token.type == OutputTokenType.Punctuation && token.text.all { it == '.' }

private val blockContinuers = setOf(
    OutToks.catchWord,
    OutToks.elseWord,
    OutToks.orElseWord,
    OutToks.finallyWord,
    OutToks.comma,
    OutToks.eq,
    OutToks.colon,
    OutputToken(
        OutToks.eq.text,
        OutToks.eq.type, // but no association
    ),
    OutToks.rightParen,
    OutToks.rightSquare,
    OutToks.semi,
    OutToks.whileWord,
)

internal val parenthesizerPair = Pair(
    { ts: TokenSink -> ts.emit(OutToks.leftParen) },
    { ts: TokenSink -> ts.emit(OutToks.rightParen) },
)
