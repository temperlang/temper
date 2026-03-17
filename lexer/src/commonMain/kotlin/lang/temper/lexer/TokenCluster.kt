package lang.temper.lexer

import lang.temper.log.MessageTemplate

internal typealias TokenClusterStackChangeBits = Int
internal typealias TokenClusterStackChangeBitsArray = IntArray

/**
 * Token clusters are sequences of adjacent tokens that start and end with a bracket or string boundaries.
 * Token clusters may nest, so in `{ { ; } }` the token cluster `{ ; }` nests inside a larger token cluster.
 *
 * The lexer keeps a stack so that it may identify token cluster boundaries.
 * For example, in
 *
 *     {
 *       s = "chars ${
 *                     f {
 *                          g("c")
 *                     }
 *                   } more chars";
 *     }
 *
 * At the character 'c', the stack looks like
 *
 * - *Top*
 * - `"` from the nested string,
 * - `{` from the block lambda started by `f {`,
 * - `${` from the string expression interpolation after `"chars ${`,
 * - `"` from the outer string started at `s = "`,
 * - `{` from the enclosing block started on the first line.
 * - *Bottom*
 *
 * Note that after `}` characters, we can end up in different contexts.
 *
 * - After the `f { ... }`, the lexer re-enters a token context.
 *   If the next character were `;`, for example, it would end the statement.
 * - After the `"chars ${ ... }`, the lexer re-enters a character context.
 *   If the next character were `;`, that would contribute a literal semicolon
 *   to the string.
 * - After the outermost `{ s = ... }`, the lexer re-enters a token context.
 *
 * So whether we're parsing tokens or character data, depends on the stack.
 *
 * One source of complexity is scriptlets.
 * For example, in
 *
 *     markdown"""
 *     # Title
 *
 *     ## Table of contents
 *
 *     {:  for (let el in elements) {  :}
 *     - [${ el.text }](${ el.url })
 *     {:  }  :}
 *     """
 *
 * We see a tagged, *multi-quoted* template for Markdown content.
 * The `{: ... :}` sequence identify sequences of statement-level content called *scriptlets*.
 * Taken together, a multi-quoted template's scriptlets must nest, but individually, may not.
 * For example, the block started at the end of `for (...) {` does not end until the next
 * scriptlet.
 *
 * Another source of complexity is the need to do something semi-sensible with missing or
 * extra brackets.
 * The parser needs to group together the character parts of a string template.
 * The IDE needs to style Temper tokens to make it clear to the user, who may be half-way
 * through inserting a sensible sequence of characters, what the parser receives as token
 * content, and what it receives as character content.
 * Some mis-nested constructs have clear intent:
 *
 *     markdown"""
 *     "{:  for (let el in elements) {  :}
 *     "-
 *
 * Obviously, there's a missing `}` before the implicit end of string.
 *
 * Since quoted-strings with fewer than three quote characters may not contain embedded
 * newlines in character content, the string below clearly needs a close quote and any
 * next line would not be treated as character content.
 *
 *     s = "foo
 *
 * [The design doc](https://hackmd.io/@temper/H1JiV%55SA9) explains in detail how
 * the token clustering drops synthesized tokens.
 *
 * But the main property that this table ensures is that the transitions lead to
 * token clusters that:
 *
 * - nest cleanly; no cluster starts inside one cluster but ends in another and
 * - are complete; no cluster is missing a close delimiter, and no token has
 *   close delimiter token text and a close delimiter token type and does not
 *   close a cluster
 *
 * These properties allow parse stage passes to group character data chunks,
 * interpolated expression token sequences, and scriptlets for string templates
 * together properly with the delimiters for those templates.
 */
object TokenCluster {

    /** A token text or source text prefix that is significant to clustering. */
    enum class Chunk(
        val prefixText: String,
        /** The token type for any corresponding closer */
        val tokenType: TokenType?,
    ) {

        // Openers and closers
        Quote("\"", TokenType.RightDelimiter),
        Backtick("`", TokenType.RightDelimiter),

        // Openers
        MultiQuote("\"\"\"", TokenType.RightDelimiter),
        DollarLeft($$"${", TokenType.Punctuation),
        UnicodeLeft("\\u{", TokenType.Punctuation),
        LeftCurly("{", TokenType.Punctuation),

        // Closers
        RightCurly("}", TokenType.Punctuation),

        // Margin Chars
        MarginStmtFragment(":", TokenType.Margin),
        MarginChars("\"", TokenType.Margin), // or "~"

        // Other
        LineBreak("\n", TokenType.Space),
        NoElement("", null),
        Other("?", null),

        ;

        override fun toString(): String = prefixText

        companion object {
            fun from(
                tokenLike: TokenLike?,
                /** True if no other tokens could follow [tokenLike]. */
                atEndOfInput: Boolean = false,
            ): Chunk {
                if (tokenLike == null) {
                    return NoElement
                }
                val tokenText = tokenLike.tokenText
                if (tokenLike.tokenType == TokenType.Margin) {
                    when (tokenText) {
                        ":" -> return MarginStmtFragment
                        "\"", "~" -> return MarginChars
                    }
                }
                if (tokenText.length == 1) {
                    return when (tokenText[0]) {
                        '{' -> LeftCurly
                        '}' -> RightCurly
                        '`' -> Backtick
                        '/', '\'', '\"' -> Quote
                        '\n', '\r' -> LineBreak
                        else -> Other
                    }
                }
                return when (tokenText) {
                    $$"${" -> DollarLeft
                    "\\u{" -> UnicodeLeft
                    "\r\n" -> LineBreak
                    "" -> if (atEndOfInput) {
                        NoElement
                    } else {
                        Other
                    }

                    else -> {
                        var isMq = false
                        // Examples of MultiQuote openers:
                        //   """
                        //   $"""
                        //   $$"""
                        if (tokenText.endsWith(MQ_DELIMITER)) {
                            val nBefore = tokenText.length - MQ_DELIMITER_LENGTH
                            isMq = true
                            for (i in 0 until nBefore) {
                                if (tokenText[i] != '$') {
                                    isMq = false
                                    break
                                }
                            }
                        }
                        if (isMq) {
                            MultiQuote
                        } else {
                            Other
                        }
                    }
                }
            }
        }
    }

    /**
     * Changes to make when we see a clustering-relevant character sequence based on the top-most
     * stack element.
     */
    internal object Table {
        /** Given the top stack element and the prefix, any changes that need to be performed to the stack. */
        operator fun get(opener: Chunk, prefix: Chunk): TokenClusterStackChangeBits =
            transitions[opener.ordinal][prefix.ordinal]

        private val transitions: Array<TokenClusterStackChangeBitsArray> =
            Chunk.entries.map { IntArray(size = Chunk.entries.size) }
                .toTypedArray()

        /**
         * Message template used when marking a token as a [Change.BadToken]
         * or synthesizing a missing token.
         */
        val errorMsgs: Map<Pair<Chunk, Chunk>, MessageTemplate>

        init {
            operator fun Array<TokenClusterStackChangeBitsArray>.set(
                opener: Chunk,
                prefix: Chunk,
                bits: TokenClusterStackChangeBits,
            ) {
                this[opener.ordinal][prefix.ordinal] = bits
            }

            infix fun TokenClusterStackChangeBits.or(c: Change): TokenClusterStackChangeBits = this or c.mask

            val errorMsgs = mutableMapOf<Pair<Chunk, Chunk>, MessageTemplate>()

            val noOpener = Chunk.NoElement
            transitions[Chunk.Quote, Chunk.Quote] = 0 or Change.Pop
            transitions[Chunk.Quote, Chunk.MultiQuote] = 0 or Change.BadToken
            transitions[Chunk.Quote, Chunk.DollarLeft] = 0 or Change.Push
            transitions[Chunk.Quote, Chunk.UnicodeLeft] = 0 or Change.Push
            transitions[Chunk.Quote, Chunk.LineBreak] = 0 or Change.Syn or Change.Reproc
            transitions[Chunk.Quote, Chunk.MarginChars] = 0 or Change.Syn or Change.Reproc
            transitions[Chunk.Quote, Chunk.MarginStmtFragment] = 0 or Change.Syn or Change.Reproc
            transitions[Chunk.Quote, Chunk.NoElement] = 0 or Change.Syn or Change.Reproc
            errorMsgs[Chunk.Quote to Chunk.LineBreak] = MessageTemplate.UnclosedQuotation
            errorMsgs[Chunk.Quote to Chunk.MarginChars] = MessageTemplate.UnclosedQuotation
            errorMsgs[Chunk.Quote to Chunk.MarginStmtFragment] = MessageTemplate.UnclosedQuotation
            errorMsgs[Chunk.Quote to Chunk.NoElement] = MessageTemplate.UnclosedQuotation
            transitions[Chunk.Backtick, Chunk.Backtick] = 0 or Change.Pop
            transitions[Chunk.Backtick, Chunk.DollarLeft] = 0 or Change.Push
            transitions[Chunk.Backtick, Chunk.UnicodeLeft] = 0 or Change.Push
            transitions[Chunk.Backtick, Chunk.NoElement] = 0 or Change.Syn or Change.Reproc
            errorMsgs[Chunk.Backtick to Chunk.NoElement] = MessageTemplate.UnclosedQuotation
            transitions[Chunk.MultiQuote, Chunk.MultiQuote] = 0 or Change.Push or Change.UnsetCL
            transitions[Chunk.MultiQuote, Chunk.DollarLeft] = 0 or Change.Push or Change.UnsetCL
            transitions[Chunk.MultiQuote, Chunk.LeftCurly] = 0 or Change.Push
            transitions[Chunk.MultiQuote, Chunk.RightCurly] = 0 or Change.BadToken
            errorMsgs[Chunk.MultiQuote to Chunk.RightCurly] = MessageTemplate.UnmatchedBracket
            transitions[Chunk.MultiQuote, Chunk.UnicodeLeft] = 0 or Change.Push
            transitions[Chunk.MultiQuote, Chunk.Quote] = 0 or Change.Push
            transitions[Chunk.MultiQuote, Chunk.MarginStmtFragment] = 0 or Change.RestoreFromScriptlet
            transitions[Chunk.MultiQuote, Chunk.MarginChars] = 0 or Change.StoreInScriptlet
            transitions[Chunk.MultiQuote, Chunk.NoElement] = 0 or Change.Syn or Change.Reproc // Not an error
            transitions[Chunk.DollarLeft, Chunk.Quote] = 0 or Change.Push
            transitions[Chunk.DollarLeft, Chunk.Backtick] = 0 or Change.Push
            transitions[Chunk.DollarLeft, Chunk.MultiQuote] = 0 or Change.Push
            transitions[Chunk.DollarLeft, Chunk.DollarLeft] = 0 or Change.Cons1 or Change.BadToken
            transitions[Chunk.DollarLeft, Chunk.MarginStmtFragment] = 0 or Change.BadToken
            transitions[Chunk.DollarLeft, Chunk.MarginChars] = 0 or Change.BadToken
            transitions[Chunk.DollarLeft, Chunk.LeftCurly] = 0 or Change.Push
            transitions[Chunk.DollarLeft, Chunk.RightCurly] = 0 or Change.Pop or Change.ResetCL
            transitions[Chunk.DollarLeft, Chunk.NoElement] = 0 or Change.Syn or Change.Reproc
            transitions[Chunk.UnicodeLeft, Chunk.DollarLeft] = 0 or Change.Push
            transitions[Chunk.UnicodeLeft, Chunk.MarginStmtFragment] = 0 or Change.Pop or Change.Reproc
            transitions[Chunk.UnicodeLeft, Chunk.RightCurly] = 0 or Change.Pop
            transitions[Chunk.UnicodeLeft, Chunk.NoElement] = 0 or Change.Syn or Change.Reproc
            transitions[Chunk.LeftCurly, Chunk.Quote] = 0 or Change.Push
            transitions[Chunk.LeftCurly, Chunk.MultiQuote] = 0 or Change.Push
            transitions[Chunk.LeftCurly, Chunk.Backtick] = 0 or Change.Push
            transitions[Chunk.LeftCurly, Chunk.DollarLeft] = 0 or Change.Push
            transitions[Chunk.LeftCurly, Chunk.LeftCurly] = 0 or Change.Push
            transitions[Chunk.LeftCurly, Chunk.RightCurly] = 0 or Change.Pop
            transitions[Chunk.LeftCurly, Chunk.MarginChars] = 0 or Change.StoreInScriptlet
            transitions[Chunk.LeftCurly, Chunk.NoElement] = 0 or Change.Syn or Change.Reproc
            errorMsgs[Chunk.LeftCurly to Chunk.NoElement] = MessageTemplate.UnmatchedBracket
            transitions[noOpener, Chunk.Quote] = 0 or Change.Push
            transitions[noOpener, Chunk.MultiQuote] = 0 or Change.Push
            transitions[noOpener, Chunk.Backtick] = 0 or Change.Push
            transitions[noOpener, Chunk.DollarLeft] = 0 or Change.Push
            transitions[noOpener, Chunk.LeftCurly] = 0 or Change.Push
            transitions[noOpener, Chunk.RightCurly] = 0 or Change.BadToken
            errorMsgs[noOpener to Chunk.RightCurly] = MessageTemplate.UnmatchedBracket
            transitions[noOpener, Chunk.NoElement] = 0 // Done

            this.errorMsgs = errorMsgs.toMap()
        }
    }

    internal enum class ContentLineKind {
        Chars,
        StmtFragment,
    }

    /** The primitive operations that may be applied to a token cluster stack. */
    internal enum class Change {
        /**
         * Mark the token or character sequence as an error sequence, so that it is not treated as
         * starting or ending a token cluster.
         */
        BadToken,

        /** Treat the first character in the sequence as content separate from the suffix. */
        Cons1,

        /** Remove the top-most element from the stack. */
        Pop,

        /** Push the token or character sequence on as a cluster opener. */
        Push,

        /** After performing the other operations, re-process in the context of the changed stack. */
        Reproc,

        /** Restore any blocks that started in a closed scriptlet. */
        RestoreFromScriptlet,

        /**
         * Store any unclosed blocks inside the current scriptlet so that they may be restored when
         * we see another scriptlet for the same string template.
         */
        StoreInScriptlet,

        /** Synthesize a closer token. */
        Syn,

        /** Unset the internal [ContentLineKind] */
        UnsetCL,

        /** Reset the internal [ContentLineKind] */
        ResetCL,
    }

    internal fun closerFor(opener: MinimalToken): MinimalToken? {
        val (pos, openerText, openerType) = opener
        if (openerType == TokenType.Margin) {
            return MinimalToken(pos, "\n", TokenType.Space)
        }
        if (openerText.endsWith('"')) {
            // " closes ", """ closes """, """" closes """"
            return if (openerText.length >= MQ_DELIMITER_LENGTH) {
                MinimalToken(pos, MQ_DELIMITER, TokenType.RightDelimiter) // Close $""" with """
            } else {
                MinimalToken(pos, "\"", TokenType.RightDelimiter)
            }
        }
        return when (openerText) {
            $$"${", "\\u{", "{" -> MinimalToken(pos, "}", TokenType.Punctuation)
            "`", "'", "/" -> if (opener.tokenType == TokenType.LeftDelimiter) {
                MinimalToken(pos, openerText, TokenType.RightDelimiter)
            } else {
                null
            }
            else -> error(opener)
        }
    }
}

internal val TokenCluster.Change.mask get() = 1 shl ordinal

internal operator fun TokenClusterStackChangeBits.contains(c: TokenCluster.Change) =
    0 != (this and c.mask)

internal operator fun TokenClusterStackChangeBits.minus(c: TokenCluster.Change) =
    this and c.mask.inv()

internal operator fun TokenClusterStackChangeBits.plus(c: TokenCluster.Change) =
    this or c.mask

internal val TokenClusterStackChangeBits.changeBitString: String
    get() =
        if (this == 0) {
            "0"
        } else {
            TokenCluster.Change.entries
                .filter { it in this }
                .joinToString("|") { it.name }
        }

/**
 * A multi-quoted string delimiter has three quote characters (`"`).
 * Just 2 quote characters, `""`, denote the empty string.
 */
const val MQ_DELIMITER_LENGTH = 3

/**
 * <!-- snippet: syntax/multi-quoted-strings -->
 * # Multi-quoted strings
 *
 * Multi-quoted strings start with 3 `"`'s.
 * Each content line must start with a `"`, called a *margin-quote*,
 * which is not part of the content.
 *
 * ```temper
 * "3 quotes" ==
 *   """
 *   "3 quotes
 * ```
 *
 * When a non-blank line doesn't start with a margin-quote, the
 * multi-quoted string ends.
 *
 * Quotes can be embedded inside multi-quoted strings.
 *
 * ```temper
 * (
 *   "Alice said\n\"Hello, World!\"" ==
 *     """
 *     "Alice said
 *     ""Hello, World!"
 * )
 * ```
 *
 * Multi-quoted strings may contain interpolations.
 *
 * ```temper
 * let whom = """
 *     "World
 * ;
 * "Hello, World!" == """
 *   "Hello, ${whom}!
 * ```
 */
const val MQ_DELIMITER = "\"\"\""
