package lang.temper.lexer

import lang.temper.common.C_BQ
import lang.temper.common.C_COLON
import lang.temper.common.C_COMMA
import lang.temper.common.C_DOL
import lang.temper.common.C_DQ
import lang.temper.common.C_EMOJI_PRESENTATION_SELECTOR
import lang.temper.common.C_GT
import lang.temper.common.C_LEFT_CURLY
import lang.temper.common.C_LT
import lang.temper.common.C_RIGHT_CURLY
import lang.temper.common.C_SLASH
import lang.temper.common.C_SQ
import lang.temper.common.Cons
import lang.temper.common.Log
import lang.temper.common.N_HEX_PER_BYTE
import lang.temper.common.N_HEX_PER_UTF16
import lang.temper.common.asciiLowerCase
import lang.temper.common.backtickTemperEscaper
import lang.temper.common.charCount
import lang.temper.common.compatRemoveFirst
import lang.temper.common.console
import lang.temper.common.decodeUtf16
import lang.temper.common.emptyBooleanArray
import lang.temper.common.isEmpty
import lang.temper.common.logIf
import lang.temper.common.toStringViaBuilder
import lang.temper.log.CodeLocation
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import kotlin.math.min

private const val DEBUG = false
private inline fun debug(message: () -> String) = console.logIf(DEBUG, message = message)

/** If we pause lexing inside a multi-line token, record that fact. */
private enum class OpenTokenType {
    NONE,

    /** `/*...*/` */
    BLOCK_COMMENT,

    /** `//...` */
    LINE_COMMENT,

    /**
     * A string.
     * The kind of string depends on the delimiter stack.
     * - `"` -> a single-line, double-quoted string
     * - `'` -> a single-line, single-quoted string
     * - ``` -> a single-line, back-quoted string
     * - `/" -> a regular-expression style literal like `/^(c|d)*$/`.
     * - `"""`, `$$"""`, `$$$"""`, etc. -> a multi-line string
     */
    STRING,

    /** When we encounter a newline in a multiline string, we need to scan for a margin character. */
    STRING_CONTINUATION,

    SEMI_LIT_COMMENT,
    UNICODE_RUN,
}

class Lexer(
    override val codeLocation: CodeLocation,
    private val logSink: LogSink,
    val sourceText: CharSequence,
    /** The position of the start of [sourceText] in [codeLocation] */
    internal val offset: Int = 0,
    private val lang: LanguageConfig = StandaloneLanguageConfig,
    private val ignoreTrailingSynthetics: Boolean = false,
    private val allowWordSuffixChars: Boolean = false,
) : TokenSource {
    /** The start of the next token in [sourceText] */
    private var start: Int = 0

    /**
     * A stack of openers for [token clusters][TokenCluster.Chunk].
     * This answers questions about the kind of string delimiters and whether a `}` token
     * would be followed by token or character content.
     * Using a Cons list makes copying O(1).
     */
    private var delimiterStack: Cons<Pair<String, Position>> = Cons.Empty

    /**
     * For each `"""` entry on [delimiterStack], the stored brackets that need to be closed by a
     * scriptlet before the multi-quoted string can end.
     */
    private var scriptletStack: Cons<List<Pair<String, Position>>> = Cons.Empty
    private var open: OpenTokenType = OpenTokenType.NONE

    /**
     * Indicates whether a '/' that does not start a comment starts a division operator
     * like '/' or '/=' and not a regular expression like `/reg(ular )?exp(r(ession)?)?/`
     */
    private var lastPreDiv: Boolean = false
    private val pendingTokens = mutableListOf<TemperToken>()

    override fun hasNext(): Boolean = peek() != null
    override fun next(): TemperToken {
        val token = peek() ?: throw NoSuchElementException()
        pendingTokens.compatRemoveFirst()
        return token
    }

    override fun peek(): TemperToken? {
        if (pendingTokens.isEmpty()) {
            if (end < sourceText.length) {
                findMoreTokens()
            } else if (!ignoreTrailingSynthetics) {
                while (!delimiterStack.isEmpty() && pendingTokens.isEmpty()) {
                    updateTokenClusters(end, end)
                }
            }
        }
        return pendingTokens.firstOrNull()
    }

    override fun copy(logSink: LogSink?): Lexer = copy(logSink = logSink, copyPendingTokens = true)

    fun copy(logSink: LogSink? = null, copyPendingTokens: Boolean = true, sourceText: CharSequence? = null): Lexer {
        val copy = Lexer(
            codeLocation = codeLocation,
            logSink = logSink ?: this.logSink,
            lang = lang,
            sourceText = sourceText ?: this.sourceText,
            offset = offset,
            ignoreTrailingSynthetics = ignoreTrailingSynthetics,
            allowWordSuffixChars = allowWordSuffixChars,
        )
        if (sourceText == null) {
            // If we keep the old text, also keep the old positions.
            copy.start = start
            copy.end = end
        }
        copy.currentTokenType = currentTokenType
        copy.error = error
        if (copyPendingTokens) {
            copy.pendingTokens.addAll(pendingTokens)
        }
        copy.delimiterStack = delimiterStack
        copy.scriptletStack = scriptletStack
        copy.open = open
        copy.lastPreDiv = lastPreDiv
        return copy
    }

    // State mutated by helpers while finding new tokens for pendingTokens
    private var end = 0
    private var currentTokenType: TokenType? = null
    private var error: MessageTemplate? = null
    private val currentTokenText
        get() =
            if (start < end) {
                sourceText.substring(start, end)
            } else {
                null
            }

    private fun error(s: Int, e: Int, messageTemplate: MessageTemplate) {
        error = messageTemplate
        logSink.log(
            level = Log.Error,
            template = messageTemplate,
            pos = Position(codeLocation, offset + s, offset + e),
            values = emptyList(),
        )
    }

    private fun updateLastPreDiv() {
        when (currentTokenType) {
            null, TokenType.Comment, TokenType.Space -> {}
            TokenType.LeftDelimiter, TokenType.QuotedString, TokenType.RightDelimiter,
            TokenType.Number, TokenType.Error,
            -> {
                lastPreDiv = true
            }
            TokenType.Word -> {
                val tokenText = currentTokenText
                // Some keywords precede regular expressions:
                //    return /regex/.split(...)
                lastPreDiv = tokenText !in valuePrecedingKeywords
            }
            TokenType.Punctuation -> {
                val tokenText = currentTokenText
                lastPreDiv = tokenText != null && (
                    // ++ and -- are div operator preceders as in
                    //     x++ / divisor
                    tokenText.startsWith("--") || tokenText.startsWith("++") ||
                        // Close brackets are div operator preceders as in
                        //     f() / a[i] / divisor
                        tokenText.endsWith(")") || tokenText.endsWith("]")
                    )
            }
        }
    }

    private fun findMoreTokens() {
        start = end
        currentTokenType = null
        error = null

        val text = sourceText
        val limit = text.length
        if (start == limit) {
            return
        }

        if (offset == 0 && end == 0 && lang.isSemilit) {
            // If the source code is embedded in a documentation language, then start in the comment
            // state.
            open = OpenTokenType.SEMI_LIT_COMMENT
        }

        debug {
            "B start=$start, limit=$limit, open=$open, DS=$delimiterStack, text=${
                escapeWithCaretAt(text, end)
            }"
        }

        var reenter: Boolean
        var openWas: OpenTokenType = OpenTokenType.NONE
        endLoop@
        do {
            reenter = false

            // Tokens can be interrupted by
            //     ```
            // on a line by itself.
            // Multi-line token handling code below has to break to here on seeing a line break.
            if (atStartOfLine(end) && open != OpenTokenType.SEMI_LIT_COMMENT) {
                val delimiterEnd =
                    lookForEmbeddingLanguageDelimiter(lang::matchSemilitCommentEntrance)
                if (delimiterEnd >= 0) {
                    // If we've found some token content, terminate that token.
                    if (start < end) {
                        break
                    }
                    // Else proceed to process a semi-lit content token.

                    end = delimiterEnd
                    if (end == limit || LexicalDefinitions.isLineBreak(text[end])) {
                        open = OpenTokenType.SEMI_LIT_COMMENT
                    } else {
                        // Treat the whole line as an error token.
                        end = skipToLineEnd(end)
                        error(start, end, MessageTemplate.MalformedSemilit)
                        currentTokenType = TokenType.Error
                        break
                    }
                }
            }

            openWas = open
            when (open) {
                OpenTokenType.NONE -> {
                    val cp0 = decodeUtf16(text, end)
                    when (val kind = LexicalDefinitions.classifyTokenBasedOnFirstChar(cp0)) {
                        LexicalDefinitions.CharKind.WordStart -> {
                            currentTokenType = TokenType.Word
                            end += charCount(cp0)
                            findEndOfWord()

                            // Mark as an error token if not properly normalized.
                            for (i in start until end) {
                                if (text[i] > '\u00FF') {
                                    val word = text.substring(start, end)
                                    if (!LexicalDefinitions.isIdentifierNormal(word)) {
                                        error(start, end, MessageTemplate.UnnormalizedIdentifier)
                                        currentTokenType = TokenType.Error
                                    }
                                    break
                                }
                            }

                            // If the token is the literal name`...`, then we convert that to a
                            // single word token.  In the Syntax stage, we will check whether this
                            // should be treated as a template string (based on whether "name") in
                            // scope resolves to the builtin.
                            if (
                                end < limit && text[end] == '`' &&
                                text.regionMatches(
                                    start,
                                    LexicalDefinitions.quotedNamePrefix,
                                    0,
                                    LexicalDefinitions.quotedNamePrefix.length,
                                    false,
                                )
                            ) {
                                // As long as the string is closed and has no ${...} interpolations
                                // then compact it.
                                var quoteEnd = end + 1
                                var useQuoted = false
                                quoteEndLoop@
                                while (quoteEnd < limit) {
                                    when (text[quoteEnd++]) {
                                        '`' -> {
                                            useQuoted = true
                                            break@quoteEndLoop
                                        }
                                        '$' -> {
                                            if (quoteEnd < limit && text[quoteEnd] == '{') {
                                                break@quoteEndLoop
                                            }
                                        }
                                        '\\' -> {
                                            if (quoteEnd == limit) {
                                                break@quoteEndLoop
                                            }
                                            quoteEnd += 1
                                        }
                                    }
                                }
                                if (useQuoted) {
                                    end = quoteEnd
                                }
                            }

                            // TODO: If end is the end of input, do we need to record that the
                            // word is open?
                        }
                        LexicalDefinitions.CharKind.Dot -> {
                            when (if (end + 1 < limit) text[end + 1] else '\u0000') {
                                in '0'..'9' -> {
                                    currentTokenType = TokenType.Number
                                    findEndOfNumber()
                                }
                                else -> {
                                    currentTokenType = TokenType.Punctuation
                                    end += 1
                                    while (end < limit) {
                                        if (
                                            text[end] != '.' ||
                                            (end + 1 < limit && text[end + 1] in '0'..'9')
                                        ) {
                                            break
                                        }
                                        end += 1
                                    }
                                }
                            }
                        }
                        LexicalDefinitions.CharKind.Slash -> {
                            when (if (end + 1 < limit) text[end + 1] else '\u0000') {
                                '*' -> {
                                    end += 2
                                    open = OpenTokenType.BLOCK_COMMENT
                                    reenter = true
                                }
                                '/' -> {
                                    end += 2
                                    open = OpenTokenType.LINE_COMMENT
                                    reenter = true
                                }
                                else -> {
                                    if (lastPreDiv) {
                                        currentTokenType = TokenType.Punctuation
                                        findPunctuationTokenEnd()
                                    } else {
                                        updateTokenClusters(end, end + 1)
                                        currentTokenType = TokenType.QuotedString
                                        reenter = true
                                    }
                                }
                            }
                        }
                        LexicalDefinitions.CharKind.Backslash -> {
                            currentTokenType = TokenType.Punctuation
                            when (if (end + 1 < limit) { text[end + 1] } else { '\u0000' }) {
                                '{' -> updateTokenClusters(end + 1, end + 2)
                                '(' -> end += 2
                                else -> end += 1
                            }
                        }
                        LexicalDefinitions.CharKind.Dollar -> {
                            if (end + 1 < limit && text[end + 1] == '{') {
                                currentTokenType = TokenType.Punctuation
                                updateTokenClusters(end, end + 2)
                            } else {
                                currentTokenType = TokenType.Error
                                error(start, end, MessageTemplate.UnrecognizedToken)
                                end += 1
                            }
                        }
                        LexicalDefinitions.CharKind.Digit -> {
                            currentTokenType = TokenType.Number
                            findEndOfNumber()
                        }
                        LexicalDefinitions.CharKind.Punctuation -> {
                            currentTokenType = TokenType.Punctuation
                            if (cp0 == C_COLON && end + 1 < limit && sourceText[end + 1] == '}') {
                                updateTokenClusters(end, end + 2)
                            } else {
                                end += charCount(cp0)
                                if (end < limit && text[end] == '\uFE0F') {
                                    // Special handling for keycap Emojis.
                                    // Disable this and see comments in unittests that fail for more
                                    // details.
                                    currentTokenType = TokenType.Error
                                    findEndOfWord()
                                    error(start, end, MessageTemplate.BadEmoji)
                                } else {
                                    val continues = when (cp0) {
                                        C_LT -> {
                                            val next =
                                                if (end < limit) {
                                                    sourceText[end]
                                                } else {
                                                    '\u0000'
                                                }
                                            next == '<' || next == '=' || next == '/'
                                        }

                                        C_GT -> !isAngleBracketAt(start) // Split >> as needed.
                                        else -> true
                                    }
                                    if (continues) {
                                        findPunctuationTokenEnd()
                                    }
                                }
                            }
                        }
                        LexicalDefinitions.CharKind.StandalonePunctuation -> {
                            currentTokenType = TokenType.Punctuation
                            when (cp0) {
                                C_LEFT_CURLY -> {
                                    val clusterEnd = end +
                                        if (end + 1 < limit && sourceText[end + 1] == ':') {
                                            2
                                        } else {
                                            1
                                        }
                                    updateTokenClusters(end, clusterEnd)
                                }
                                C_RIGHT_CURLY -> updateTokenClusters(end, end + 1)
                                else -> end += 1
                            }
                        }
                        LexicalDefinitions.CharKind.Error -> {
                            currentTokenType = TokenType.Error
                            findEndOfRun(kind)
                            error(start, end, MessageTemplate.UnrecognizedToken)
                        }
                        LexicalDefinitions.CharKind.Space -> {
                            currentTokenType = TokenType.Space
                            findEndOfRun(kind)
                        }
                        LexicalDefinitions.CharKind.LineBreak -> {
                            currentTokenType = TokenType.Space
                            findEndOfRun(kind)
                        }
                        LexicalDefinitions.CharKind.Semi -> {
                            currentTokenType = TokenType.Punctuation
                            findEndOfRun(kind)
                        }
                        LexicalDefinitions.CharKind.Quote -> {
                            when (cp0) {
                                C_SQ, C_BQ -> {
                                    updateTokenClusters(end, end + 1)
                                }
                                C_DQ -> {
                                    var nQuoteChars = 1
                                    while (end + nQuoteChars < limit && text[end + nQuoteChars] == '"') {
                                        nQuoteChars += 1
                                        if (nQuoteChars == MQ_DELIMITER_LENGTH) {
                                            break
                                        }
                                    }
                                    val delimLength = if (nQuoteChars == MQ_DELIMITER_LENGTH) {
                                        MQ_DELIMITER_LENGTH
                                    } else { // Process "" as two separate single-char clusters
                                        1
                                    }
                                    updateTokenClusters(end, end + delimLength)
                                }
                                else -> error("$cp0")
                            }
                            currentTokenType = if (open == OpenTokenType.STRING) {
                                TokenType.LeftDelimiter
                            } else {
                                TokenType.Error
                            }
                        }
                    }
                }
                OpenTokenType.BLOCK_COMMENT -> {
                    currentTokenType = TokenType.Comment
                    while (end < limit) {
                        val c = text[end]
                        end += 1

                        if (c == '*' && end < limit && text[end] == '/') {
                            end += 1 // Consume '/'
                            open = when (TokenCluster.Chunk.from(delimiterStack.headOrNull?.first)) {
                                // If the comment started inside a multiline string, like the below,
                                // keep looking for a margin character.
                                TokenCluster.Chunk.MultiQuote -> OpenTokenType.STRING_CONTINUATION
                                else -> OpenTokenType.NONE
                            }
                            break
                        }
                        if (LexicalDefinitions.isLineBreak(c)) {
                            // Let the semilit checker above decide whether it owns the line break.
                            reenter = true
                            break
                        }
                    }
                }
                OpenTokenType.LINE_COMMENT -> {
                    currentTokenType = TokenType.Comment
                    while (end < limit) {
                        if (LexicalDefinitions.isLineBreak(text[end])) {
                            open = when (TokenCluster.Chunk.from(delimiterStack.headOrNull?.first)) {
                                // If the comment started inside a multiline string, like the below,
                                // keep looking for a margin character.
                                TokenCluster.Chunk.MultiQuote -> OpenTokenType.STRING_CONTINUATION
                                else -> OpenTokenType.NONE
                            }
                            // If the lexer was just reset, we might have consumed no content in
                            // which case we need to parse a linebreak token.
                            reenter = end == start
                            break
                        }
                        end += 1
                    }
                }
                OpenTokenType.STRING -> {
                    currentTokenType = TokenType.QuotedString
                    val context = StringContext()
                    var inCharClass = false // Handle '/' inside regex char class like /[/]/
                    @Suppress("LoopWithTooManyJumpStatements") // Enables flatter code.
                    while (end < limit) {
                        val c = text[end]

                        if (LexicalDefinitions.isLineBreak(c)) {
                            if (context.isMultiQuote) {
                                // For multi-line, multi-quoted strings, we still need to
                                // let the semilit delimiter checker take a look at each line.
                                // If it finds nothing, control should end up back here because
                                // open has not changed.
                                open = OpenTokenType.STRING_CONTINUATION
                                end += 1
                            } else if (start == end) {
                                updateTokenClusters(end, end + 1)
                                if (open == OpenTokenType.STRING) {
                                    reenter = true
                                } else {
                                    currentTokenType = TokenType.Space
                                }
                            }
                            break
                        }
                        end += 1

                        if ( // Check for special 2-or-3-character sequences: `${`, `{:`, `\u{`
                            context.startingHole(c) ||
                            end + 1 < limit && (c == '\\' && text[end] == 'u' && text[end + 1] == '{')
                        ) {
                            if (end - 1 == start) {
                                currentTokenType = TokenType.Punctuation
                                val step = when {
                                    text[end] == 'u' -> 2
                                    else -> 1
                                }
                                updateTokenClusters(end - 1, end + step)
                                if (open != OpenTokenType.STRING) {
                                    break
                                }
                            } else {
                                // Close the current run of quoted content and
                                // treat the boundary marker as a separate token
                                end -= 1
                                break
                            }
                        } else if (c == '\\') {
                            if (end - 1 == start) {
                                if (
                                    end == limit ||
                                    (!context.isMultiQuote && LexicalDefinitions.isLineBreak(text[end]))
                                ) {
                                    // A non-escaped '\\'
                                    error(end - 1, end, MessageTemplate.UnrecognizedStringEscape)
                                    currentTokenType = TokenType.Error // TODO Or just handle the error later?
                                } else {
                                    val next = text[end]
                                    end += 1
                                    // Consume appropriate following chars. Worry about errors later.
                                    if (next == 'u') {
                                        // Attempt 4 hex digits.
                                        findEndOfHex(N_HEX_PER_UTF16)
                                    } else if (next == 'x') {
                                        // Attempt 2 hex digits.
                                        findEndOfHex(N_HEX_PER_BYTE)
                                    } // else just single-char escape
                                }
                                // Always keep each escape in its own token.
                                // TODO Could technically keep in main string token if known non-error.
                                break
                            } else {
                                // Start a new token for the escape, as for cluster handling above.
                                end -= 1
                                break
                            }
                            // Lower branches check for sequences that are valid when not escaped
                        } else if (!context.isMultiQuote && c == context.delimChar && !inCharClass) {
                            // String end.
                            if (context.isRegexLike) {
                                if (limit >= end + 2) {
                                    // this may be a replacement
                                    val next = text[end]
                                    val afterNext = text[end + 1]
                                    if (next == '-' && afterNext == '>') {
                                        end += 2
                                        continue
                                    }
                                }
                            }
                            if (start != (end - 1) && !context.isRegexLike) {
                                end -= 1
                                // Emit content unambiguously in the string, then re-enter to treat the delimiter
                                // as a separate token.
                                break
                            }
                            val delimiterStart = end - 1
                            val delimiterEnd = end
                            updateTokenClusters(delimiterStart, delimiterEnd)
                            if (open != OpenTokenType.STRING) {
                                if (context.isRegexLike) {
                                    // Consume any regex flags
                                    findEndOfWord()
                                } else {
                                    currentTokenType = TokenType.RightDelimiter
                                }
                            }
                            break
                        } else if (c == '[' && context.isRegexLike) {
                            inCharClass = true
                        } else if (c == ']') {
                            inCharClass = false
                        }
                    }
                }
                OpenTokenType.STRING_CONTINUATION -> {
                    // Consume all space and comments until we see a non-space characters.
                    // Depending on the prefix we transition this way:
                    //     "    -> a margin character. Skip it and transition to the STRING token type.
                    //     //   -> a line comment.  Process it normally.
                    //     /*   -> a block comment.  Process it normally.
                    //     else -> end of string.  Emit a synthetic delimiter.
                    val cp0 = decodeUtf16(text, end)
                    if (cp0 == C_DQ) {
                        open = OpenTokenType.STRING
                        end += 1
                        currentTokenType = TokenType.Space // Margin char is Ignorable
                    } else if (LexicalDefinitions.isLineBreak(cp0)) {
                        currentTokenType = TokenType.Space
                        end += charCount(cp0)
                        // Stay in the STRING_CONTINUATION
                    } else if (LexicalDefinitions.isSpace(cp0)) {
                        end += charCount(cp0)
                        currentTokenType = TokenType.Space
                        while (end < limit) {
                            val cp = decodeUtf16(text, end)
                            if (!LexicalDefinitions.isSpace(cp)) {
                                break
                            }
                            end += charCount(cp)
                        }
                    } else {
                        var isComment = false
                        if (end + 1 < limit && cp0 == C_SLASH) {
                            val next = text[end + 1]
                            isComment = next == '/' || next == '*'
                        }
                        if (isComment) {
                            open = OpenTokenType.NONE
                            reenter = true
                        } else {
                            popDelimiterStack(start, synthesize = true)
                            open = OpenTokenType.NONE
                            reenter = true
                        }
                    }
                }
                OpenTokenType.SEMI_LIT_COMMENT -> {
                    currentTokenType = TokenType.Comment
                    // TODO: does atStartOfLine need to be separate from open?
                    while (end < limit) {
                        if (atStartOfLine(end)) {
                            val commentEnd =
                                lookForEmbeddingLanguageDelimiter(lang::matchSemilitCommentExit)
                            if (commentEnd >= 0) {
                                end = commentEnd
                                open = OpenTokenType.NONE
                                if (end == 0) {
                                    // File start so we actually start with code, not comment.
                                    // This can happen when we start with an indented code block.
                                    reenter = true
                                }
                                break
                            }
                        }
                        end += 1
                    }
                }
                OpenTokenType.UNICODE_RUN -> {
                    // End on right curly or holes. Otherwise, consume either word/number or not as a string.
                    val c = text[end]
                    when (val cp0 = decodeUtf16(text, end)) {
                        C_COMMA -> {
                            currentTokenType = TokenType.Punctuation
                            end += 1
                        }
                        C_RIGHT_CURLY -> { // TODO Pop two levels on close quote? Need StringContext from below?
                            // End the run.
                            currentTokenType = TokenType.Punctuation
                            updateTokenClusters(end, end + 1)
                        }
                        else -> {
                            end += charCount(cp0)
                            when {
                                // Check the stack outside the Unicode run.
                                StringContext(skipFirst = true).startingHole(c) -> {
                                    // Start a hole.
                                    currentTokenType = TokenType.Punctuation
                                    updateTokenClusters(end - 1, end + 1)
                                }
                                else -> {
                                    // Grab a chunk.
                                    currentTokenType = TokenType.QuotedString
                                    when (LexicalDefinitions.classifyTokenBasedOnFirstChar(cp0)) {
                                        LexicalDefinitions.CharKind.Digit, LexicalDefinitions.CharKind.WordStart -> {
                                            findEndOfWord()
                                        }
                                        else -> findNextUnicodeRunSegment()
                                    }
                                }
                            }
                        }
                    }
                }

                // TODO: Do we need to deal with restarts immediately after
                // - a leading surrogate in a word
                // - a '*' in a block comment
                // - a backslash in a quoted string
                // - a '/' that might start a line or block comment.
                // or can we assume that reset resets adjacent to a line terminator.
            }
        } while (reenter)

        require(end > start && currentTokenType != null) {
            "start=$start, end=$end, limit=$limit, cp0=U+${
                decodeUtf16(text, start).toString(@Suppress("MagicNumber") 16)
            }, tokenType=$currentTokenType, open=$open"
        }

        updateLastPreDiv()

        debug {
            ". got ${backtickTemperEscaper.escape(text.substring(start, end))} : tokenType=$currentTokenType${
                "\n"
            }. start=$start, limit=$limit, open=$open, DS=$delimiterStack, end=$end"
        }

        val tokenText = sourceText.substring(start, end)
        val currentMayBracket = when (currentTokenType) {
            TokenType.Punctuation ->
                if (isOpenAngleBracketText(tokenText) || tokenText == ">") {
                    isAngleBracketAt(start)
                } else {
                    tokenText in openBrackets || tokenText in closeBrackets
                }
            TokenType.LeftDelimiter, TokenType.RightDelimiter -> true
            else -> false
        }

        val newToken = TemperToken(
            pos = Position(codeLocation, start + offset, end + offset),
            tokenText = tokenText,
            tokenType = currentTokenType!!,
            mayBracket = currentMayBracket,
            synthetic = false,
            error = error,
        )

        pendingTokens.add(newToken)
        // Explode semilit comment tokens to synthetic tokens for each paragraph.
        // See massageToSemilitSyntheticComment for details.
        if (openWas == OpenTokenType.SEMI_LIT_COMMENT && newToken.tokenType == TokenType.Comment) {
            lang.massageSemilitComment(text, start, end).mapNotNullTo(pendingTokens) { range ->
                massageToSemilitSyntheticComment(text.substring(range))
                    ?.let { massagedTokenText ->
                        TemperToken(
                            pos = Position(
                                codeLocation,
                                range.first + offset, range.endInclusive + offset + 1,
                            ),
                            tokenText = massagedTokenText,
                            tokenType = TokenType.Comment,
                            mayBracket = false,
                            synthetic = true,
                            error = null,
                        )
                    }
            }
        }
    }

    private fun atStartOfLine(pos: Int, allowTabsAndSpaces: Boolean = false): Boolean {
        var sourceTextIndex = pos
        if (allowTabsAndSpaces) {
            while (sourceTextIndex != 0) {
                val c = sourceText[sourceTextIndex - 1]
                if (!LexicalDefinitions.isSpace(c)) { break }
                sourceTextIndex -= 1
            }
        }
        return sourceTextIndex == 0 || LexicalDefinitions.isLineBreak(sourceText[sourceTextIndex - 1])
    }

    /**
     * Updates [delimiterStack] based on the token or source prefix between
     * [before] (inclusive) and [after] (exclusive) in [sourceText].
     */
    private fun updateTokenClusters(
        /** position in [sourceText] before the first character of the cluster */
        before: Int,
        /** Position in [sourceText] after the last character of the cluster */
        after: Int,
    ) {
        console.groupIf(DEBUG, "updateTokenClusters") {
            var tokenOrSourcePrefix = sourceText.substring(before, after)
            while (true) { // Loop so that we can re-process with a different stack when needed.
                val chunk =
                    TokenCluster.Chunk.from(tokenOrSourcePrefix, atEndOfInput = after == sourceText.length)
                val top = TokenCluster.Chunk.from(delimiterStack.headOrNull?.first)
                var changes = TokenCluster.Table[top, chunk]
                debug {
                    ". updateTokenClusters@$before, ${
                        backtickTemperEscaper.escape(tokenOrSourcePrefix)
                    }, top=${top.name}, chunk=${chunk.name}, DS=$delimiterStack, changes=${
                        changes.changeBitString
                    }"
                }

                if (TokenCluster.Change.BadToken in changes) {
                    changes -= TokenCluster.Change.BadToken
                    error(
                        before,
                        before + tokenOrSourcePrefix.length,
                        messageTemplate = TokenCluster.Table.errorMsgs[top to chunk]
                            ?: MessageTemplate.UnrecognizedToken,
                    )
                    currentTokenType = TokenType.Error
                }

                if (TokenCluster.Change.StoreInScriptlet in changes) {
                    changes -= TokenCluster.Change.StoreInScriptlet
                    // We need to close any non-scriptlet containing strings
                    // We can store `{` and `${` elements with the multi-quoted string
                    // because they can be closed by a subsequent scriptlet.
                    // But non-multi-quoted strings and any `${` in a non-multi-quoted
                    // string cannot be.
                    // So scan upwards until we see the multi-quoted string.
                    // Any time we see a string that cannot contain a scriptlet, mark
                    // it for closing.
                    val insideScriptlet = run {
                        var nToPop = 0
                        var stackCursor = delimiterStack
                        var stackCursorDepth = 0
                        while (stackCursor is Cons.NotEmpty) {
                            val opener = TokenCluster.Chunk.from(stackCursor.head.first)
                            if (opener == TokenCluster.Chunk.LeftCurlyColon) { break }

                            stackCursor = stackCursor.tail
                            stackCursorDepth += 1

                            if (opener.tokenType == TokenType.RightDelimiter) {
                                nToPop = stackCursorDepth
                            }
                        }
                        val foundScriptlet = stackCursor !is Cons.Empty

                        if (nToPop != 0 && foundScriptlet) {
                            debug { ". popping $nToPop because unclosed strings in scriptlet" }
                            repeat(nToPop) {
                                popDelimiterStack(before, synthesize = true)
                            }
                        }
                        foundScriptlet
                    }

                    // We need to store everything above the shallowest `{:` token.
                    val toStore = if (insideScriptlet) {
                        val toStore = mutableListOf<Pair<String, Position>>()
                        var stackCursor = delimiterStack
                        while (stackCursor is Cons.NotEmpty) {
                            if (
                                TokenCluster.Chunk.from(stackCursor.head.first) ==
                                TokenCluster.Chunk.LeftCurlyColon
                            ) {
                                break
                            }
                            toStore.add(stackCursor.head)
                            stackCursor = stackCursor.tail
                        }
                        debug { ". storing $toStore" }
                        if (stackCursor is Cons.Empty) {
                            null
                        } else {
                            delimiterStack = stackCursor
                            toStore.toList()
                        }
                    } else {
                        null
                    }

                    if (toStore != null) {
                        scriptletStack = Cons(toStore, scriptletStack.tail)
                    } else {
                        currentTokenType = TokenType.Error
                        changes += TokenCluster.Change.Cons1
                        changes -= TokenCluster.Change.Reproc
                    }
                }

                if (TokenCluster.Change.Cons1 in changes) {
                    changes -= TokenCluster.Change.Cons1
                    tokenOrSourcePrefix = tokenOrSourcePrefix.take(1)
                }

                val synthesize = TokenCluster.Change.Syn in changes
                if (synthesize || TokenCluster.Change.Pop in changes) {
                    changes = changes and
                        (TokenCluster.Change.Pop.mask or TokenCluster.Change.Syn.mask).inv()
                    popDelimiterStack(before, synthesize = synthesize)
                    if (synthesize && currentTokenType != TokenType.Error) {
                        val messageTemplate = TokenCluster.Table.errorMsgs[top to chunk]
                        if (messageTemplate != null) {
                            logSink.log(
                                level = Log.Error,
                                template = messageTemplate,
                                pos = Position(codeLocation, before + offset, before + offset),
                                values = emptyList(),
                            )
                        }
                    }
                }

                if (TokenCluster.Change.Push in changes) {
                    changes -= TokenCluster.Change.Push
                    val prefixPos = Position(
                        codeLocation,
                        before + offset,
                        before + tokenOrSourcePrefix.length + offset,
                    )
                    delimiterStack = Cons(tokenOrSourcePrefix to prefixPos, delimiterStack)
                    if (chunk == TokenCluster.Chunk.MultiQuote) {
                        scriptletStack = Cons(emptyList(), scriptletStack)
                    }
                }

                if (TokenCluster.Change.RestoreFromScriptlet in changes) {
                    changes -= TokenCluster.Change.RestoreFromScriptlet
                    val stored = scriptletStack.head
                    scriptletStack = Cons(emptyList(), scriptletStack.tail)
                    stored.forEach {
                        delimiterStack = Cons(it, delimiterStack)
                    }
                }

                if (TokenCluster.Change.Reproc in changes) {
                    changes -= TokenCluster.Change.Reproc
                    check(changes == 0) { changes.changeBitString }
                    continue
                }

                check(changes == 0) { changes.changeBitString }
                break
            }

            end = before + tokenOrSourcePrefix.length
            open = when (TokenCluster.Chunk.from(delimiterStack.headOrNull?.first)) {
                TokenCluster.Chunk.MultiQuote,
                TokenCluster.Chunk.Quote,
                TokenCluster.Chunk.Backtick,
                -> OpenTokenType.STRING

                TokenCluster.Chunk.UnicodeLeft,
                -> OpenTokenType.UNICODE_RUN

                TokenCluster.Chunk.NoElement,
                TokenCluster.Chunk.DollarLeft,
                TokenCluster.Chunk.LeftCurlyColon,
                TokenCluster.Chunk.LeftCurly,
                -> OpenTokenType.NONE

                // Stack only has openers.
                TokenCluster.Chunk.ColonRightCurly,
                TokenCluster.Chunk.RightCurly,
                TokenCluster.Chunk.LineBreak,
                TokenCluster.Chunk.Other,
                -> error("$delimiterStack")
            }
            debug { ". . after updating token clusters, open=$open, end=$end, DS=$delimiterStack" }
        }
    }

    /** Pop an element from the delimiter stack per the token-clustering algo. */
    private fun popDelimiterStack(pos: Int, synthesize: Boolean = false) {
        val (poppedText) = delimiterStack.headOrNull ?: return
        delimiterStack = delimiterStack.tail

        if (poppedText.endsWith(MQ_DELIMITER)) {
            val stored = scriptletStack.head
            val position = Position(codeLocation, pos + offset, pos + offset)
            if (stored.isNotEmpty()) {
                pendingTokens.add(
                    TemperToken(
                        position,
                        tokenText = TokenCluster.Chunk.LeftCurlyColon.prefixText,
                        tokenType = TokenType.Punctuation,
                        mayBracket = true,
                        synthetic = true,
                    ),
                )
                for ((storedText, storedOpenPos) in stored) {
                    pendingTokens.add(
                        TemperToken(
                            position,
                            tokenText = TokenCluster.closerFor(storedText)!!,
                            tokenType = TokenCluster.Chunk.from(storedText).tokenType!!,
                            mayBracket = true,
                            synthetic = true,
                        ),
                    )
                    logSink.log(
                        Log.Error,
                        MessageTemplate.UnclosedBlock,
                        storedOpenPos,
                        values = listOf(position),
                    )
                }
                pendingTokens.add(
                    TemperToken(
                        position,
                        tokenText = TokenCluster.Chunk.ColonRightCurly.prefixText,
                        tokenType = TokenType.Punctuation,
                        mayBracket = true,
                        synthetic = true,
                    ),
                )
            }
        }

        if (synthesize) {
            val position = Position(codeLocation, pos + offset, pos + offset)
            val closer = TokenCluster.closerFor(poppedText)
            if (closer != null) {
                val tokenType = TokenCluster.Chunk.from(closer).tokenType!!
                pendingTokens.add(
                    TemperToken(
                        position,
                        tokenText = closer,
                        tokenType = tokenType,
                        mayBracket = true,
                        synthetic = true,
                    ),
                )
            }
        }
    }

    private inner class StringContext(skipFirst: Boolean = false) {
        private val delim = when {
            skipFirst -> delimiterStack.tail.head.first
            else -> delimiterStack.head.first
        }
        val delimChar = delim.last()
        val isMultiQuote = delim.endsWith(MQ_DELIMITER)
        private val allowInterpolation = when (delimChar) {
            '"', '`' -> true
            else -> false
        }
        private val allowScriptlets = isMultiQuote // alias for readability
        val isRegexLike = delim == "/"

        fun startingHole(c: Char) = end < sourceText.length && (
            (allowInterpolation && c == '$' && sourceText[end] == '{') ||
                (allowScriptlets && c == '{' && sourceText[end] == ':')
            )
    }

    private fun findPunctuationTokenEnd() {
        val text = sourceText
        val limit = text.length
        while (end < limit) {
            val cp = decodeUtf16(text, end)
            when (LexicalDefinitions.classifyTokenBasedOnFirstChar(cp)) {
                LexicalDefinitions.CharKind.Punctuation -> when (cp) {
                    C_LT -> {
                        // If a '<' is adjacent to a '<' or '=', treat it as part of a
                        // larger shift or comparison operator, or close tag start marker.
                        val before = text[end - 1]
                        val after = if (end + 1 < limit) { text[end - 1] } else { '\u0000' }
                        if (
                            !(before == '=' || before == '<' || after == '=' || after == '<') &&
                            isAngleBracketAt(end)
                        ) {
                            return
                        }
                    }
                    C_GT -> {
                        val stopBeforeGt = isAngleBracketAt(end) ||
                            run {
                                // Do not extend arbitrary punctuation with '>' which would prevent
                                // it from being recognized as an angle bracket above.
                                val before = text[end - 1]
                                when (before) {
                                    '-', '=', '>', '<' -> false
                                    else -> true
                                }
                            }
                        if (stopBeforeGt) {
                            return
                        }
                    }
                    else -> {}
                }

                LexicalDefinitions.CharKind.Dot -> {
                    if (end + 1 < limit && text[end + 1] in '0'..'9') {
                        return
                    }
                }

                LexicalDefinitions.CharKind.Slash -> {
                    if (end + 1 < limit) {
                        val next = text[end + 1]
                        if (next == '*' || next == '/') {
                            return
                        }
                        if (text[start] == '<') {
                            if (end == start + 1) { // Allow </ as a token but not <</
                                end += 1
                            }
                            return
                        }
                    }
                }

                LexicalDefinitions.CharKind.Backslash,
                LexicalDefinitions.CharKind.Digit,
                LexicalDefinitions.CharKind.Dollar,
                LexicalDefinitions.CharKind.LineBreak,
                LexicalDefinitions.CharKind.StandalonePunctuation,
                LexicalDefinitions.CharKind.Quote,
                LexicalDefinitions.CharKind.Semi,
                LexicalDefinitions.CharKind.Space,
                LexicalDefinitions.CharKind.WordStart,
                LexicalDefinitions.CharKind.Error,
                ->
                    return
            }
            end += charCount(cp)
        }
    }

    private fun findNextUnicodeRunSegment() {
        val text = sourceText
        val limit = text.length
        while (end < limit) {
            when (val cp = decodeUtf16(text, end)) {
                // `$` and `{` are for holes, `,` is for separating units, and `}` is for ending the run.
                C_DOL, C_LEFT_CURLY, C_COMMA, C_RIGHT_CURLY -> return
                // Otherwise eat until digit or word start (in case the word is a hex value).
                else -> when (LexicalDefinitions.classifyTokenBasedOnFirstChar(cp)) {
                    LexicalDefinitions.CharKind.Digit, LexicalDefinitions.CharKind.WordStart -> return
                    else -> end += charCount(cp)
                }
            }
        }
    }

    private fun findEndOfNumber() {
        consumeDecimalDigitRun()
        if (
            end + 1 < sourceText.length &&
            // Only a dot followed by a digit is a decimal point.
            // This allows accessing members on numbers via syntax like
            //     1.seconds
            //     123.toJson()
            sourceText[end] == '.' &&
            sourceText[end + 1] in '0'..'9'
        ) {
            end += 1
            consumeDecimalDigitRun()
        }
        if (sourceText.getOrNull(end)?.asciiLowerCase() == 'e') {
            end += 1
            val c = sourceText.getOrNull(end)
            if (c == '+' || c == '-') {
                end += 1
            }
            if (!consumeDecimalDigitRun()) {
                error(start, end, MessageTemplate.MalformedNumber)
                currentTokenType = TokenType.Error
            }
        }
        // Allow letter suffixes like 42L used to distinguish long vs float variants in C++.
        // We use findEndOfWord so that we don't get into a situation where a word suffix
        // character like question mark, prime, or bang is a punctuation character adjacent
        // to a number but not adjacent to a word.
        val beforeSuffix = end
        findEndOfWord()
        if (currentTokenType != TokenType.Error) {
            // Prevent Emojis that start with numbers from being interpreted as whole numbers
            // without any error token.
            // http://unicode.org/reports/tr51/#def_emoji_presentation_selector
            var pos = beforeSuffix
            var badEmoji = false
            while (pos < end) {
                val cp = decodeUtf16(sourceText, pos)
                if (cp !in IdParts.Continue) {
                    break
                }
                if (cp == C_EMOJI_PRESENTATION_SELECTOR) {
                    currentTokenType = TokenType.Error
                    badEmoji = true
                }
                pos += charCount(cp)
            }
            if (badEmoji) {
                error(start, end, MessageTemplate.BadEmoji)
            }
        }
    }

    private fun consumeDecimalDigitRun(): Boolean {
        val text = sourceText
        val limit = text.length
        if (end == limit || text[end] !in '0'..'9') {
            return false
        }
        end += 1
        while (end < limit) {
            val c = text[end]
            if (c == '_') {
                if (end + 1 == limit || text[end + 1] !in '0'..'9') {
                    break
                }
            } else if (c !in '0'..'9') {
                break
            }
            end += 1
        }
        return true
    }

    private fun findEndOfHex(maxDigits: Int) {
        val text = sourceText
        val limit = min(end + maxDigits, text.length)
        while (end < limit) {
            if (decodeHexDigit(text[end]) < 0) {
                break
            }
            end += 1
        }
    }

    private fun findEndOfRun(kind: LexicalDefinitions.CharKind) {
        val text = sourceText
        val limit = text.length
        while (end < limit) {
            val cp = decodeUtf16(text, end)
            if (kind == LexicalDefinitions.classifyTokenBasedOnFirstChar(cp) && !startingSemilitComment()) {
                end += charCount(cp)
            } else {
                break
            }
        }
    }

    private fun startingSemilitComment() =
        atStartOfLine(end) && lang.matchSemilitCommentEntrance(sourceText, end) >= 0

    private fun findEndOfWord() {
        // Assume caller has consumed the start character already.

        val text = sourceText
        val limit = text.length
        var lastWasMedial = false

        val wasError = currentTokenType == TokenType.Error

        loopBackOnTrailingJunk@ // Why becomes apparent at the bottom.
        while (true) {
            while (end < limit) {
                val cp = decodeUtf16(text, end)
                if (
                    cp <= MAX_LATIN && latinWordContinueChars[cp] ||
                    cp in IdParts.Continue
                ) {
                    lastWasMedial = false
                } else if (cp in IdParts.Medial) {
                    // Medial code-points must be followed by a WordContinueChar.
                    if (lastWasMedial) {
                        currentTokenType = TokenType.Error
                    }
                    lastWasMedial = true
                } else {
                    break
                }
                end += charCount(cp)
            }

            if (lastWasMedial) {
                currentTokenType = TokenType.Error
            }

            if (allowWordSuffixChars) {
                // Consume any characters that can attach to the end of a word like primes,
                // question marks, and bangs as used in
                //     o.isNice?()
                //     o.doTheThing!()
                //     let f' = derivativeOf(f)
                while (end < limit) {
                    val cp = decodeUtf16(text, end)
                    if (LexicalDefinitions.isWordSuffixChar(cp)) {
                        end += charCount(cp)
                    } else {
                        break
                    }
                }
            }

            // We don't want adjacent word tokens even if they have a '!' or '?' at the end.
            // If the next character is a medial or continue character, mark it as an error token.
            if (end < limit) {
                val cp = decodeUtf16(text, end)
                if (cp in IdParts.Continue || cp in IdParts.Medial) {
                    currentTokenType = TokenType.Error
                    continue@loopBackOnTrailingJunk
                }
            }
            break
        }

        if (!wasError && currentTokenType == TokenType.Error) {
            error(start, end, MessageTemplate.InvalidIdentifier)
        }
    }

    private fun lookForEmbeddingLanguageDelimiter(matchDelimiter: DelimiterMatcher?): Int {
        if (matchDelimiter == null || !atStartOfLine(end)) {
            return -1
        }

        val text = sourceText
        val limit = text.length

        val afterMatch = matchDelimiter(text, end)
        if (afterMatch >= 0) {
            require(afterMatch in end..limit)
            return afterMatch
        }
        return -1
    }

    private inline fun skipChars(from: Int, p: (c: Char) -> Boolean): Int {
        val text = sourceText
        val limit = text.length
        var pos = from
        while (pos < limit) {
            val c = text[pos]
            if (!p(c)) {
                return pos
            }
            pos += 1
        }
        return limit
    }

    private fun skipToLineEnd(from: Int): Int = skipChars(from) {
        !LexicalDefinitions.isLineBreak(it)
    }

    // We need to treat < and > specially when they are used as angle brackets as in
    //    : TypeName<TypeParameter>
    // but not in compound punctuation operators like
    //    << <<= <= >> >>= >= >>> >>>=
    // or custom punctuation tokens
    //    <=> => ->
    //
    // To do that, we lazily compute a bitset indicating angle brackets
    // by trying to find which '<' have a corresponding '>' without an
    // intervening "angle bracket interrupter".
    private fun isAngleBracketAt(pos: Int): Boolean {
        if (findingAngleBrackets) {
            return false
        }
        if (sourceText[pos] == '<' && pos !in angleBracketMaskLeft until angleBracketMaskRight) {
            // Create a lexer copy that starts lexing at pos using the current state.
            val finder = copy(logSink = LogSink.devNull, copyPendingTokens = false)
            finder.findingAngleBrackets = true
            finder.end = pos
            val (abmLeft: Int, abm: BooleanArray) = console.groupIf(DEBUG, "Classifying angle brackets") {
                classifyAngleBrackets(finder)
            }
            angleBracketMaskLeft = abmLeft
            angleBracketMaskRight = abmLeft + abm.size
            angleBracketMask = abm
        }
        if (pos in angleBracketMaskLeft until angleBracketMaskRight) {
            return angleBracketMask[pos - angleBracketMaskLeft]
        }
        return false
    }

    private var findingAngleBrackets = false
    private var angleBracketMaskLeft = 0
    private var angleBracketMaskRight = 0
    private var angleBracketMask = emptyBooleanArray
}

/**
 * Returns the character after the last character consumed by the match
 * that starts at *pos* in *text*; or -1 if no such match.
 */
private typealias DelimiterMatcher = (text: CharSequence, pos: Int) -> Int

private const val MAX_LATIN = 0xFF

private val latinWordContinueChars = BooleanArray(MAX_LATIN + 1) { it in IdParts.Continue }

private val valuePrecedingKeywords = setOf(
    "return",
    "throw",
    "yield",
)

private val debugEscaper = backtickTemperEscaper.withQuote(null)
private fun escapeWithCaretAt(s: CharSequence, pos: Int): String = toStringViaBuilder { out ->
    out.append('`')
    debugEscaper.escapeTo(s.take(pos), out)
    out.append('\u2038')
    debugEscaper.escapeTo(s.substring(pos), out)
    out.append('`')
}

val Lexer.nextNotSyntheticOrNull: TemperToken?
    get() {
        while (hasNext()) {
            val token = next()
            if (!token.synthetic) { return token }
        }
        return null
    }

fun Lexer.sourceOffsetOf(token: TemperToken) = token.pos.left - offset
fun Lexer.sourceRangeOf(token: TemperToken): IntRange {
    val start = sourceOffsetOf(token)
    val end = start + token.pos.right - token.pos.left
    return start until end
}
