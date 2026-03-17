package lang.temper.parser

import lang.temper.common.C_CR
import lang.temper.common.KBitSet
import lang.temper.common.Producer
import lang.temper.common.charCount
import lang.temper.common.compatRemoveFirst
import lang.temper.common.decodeUtf16
import lang.temper.cst.CstComment
import lang.temper.lexer.CommentType
import lang.temper.lexer.LexicalDefinitions
import lang.temper.lexer.MASSAGED_SEMILIT_COMMENT_END
import lang.temper.lexer.MASSAGED_SEMILIT_COMMENT_START
import lang.temper.lexer.MQ_DELIMITER
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenCluster
import lang.temper.lexer.TokenSource
import lang.temper.lexer.TokenType
import lang.temper.lexer.closeBrackets
import lang.temper.lexer.openBrackets
import lang.temper.log.Positioned
import lang.temper.log.spanningPosition
import kotlin.math.max

/**
 * Allows customizing the behavior of the token source adapter.
 * For example, the out-grammar DSL uses the same parse infrastructure
 * but with some different keywords.
 */
class TokenSourceAdapterBuilder {
    var modifyingKeywords = temperModifyingKeywords

    internal fun build(
        tokenSource: TokenSource,
        comments: MutableList<CstComment>?,
    ) = TokenSourceAdapter(
        tokenSource = tokenSource,
        comments = comments,
        modifyingKeywords = modifyingKeywords,
    )
}

/**
 * Responsible for adjusting the token stream to preserve the integrity of recursive lexical
 * structures.
 *
 * This looks for the following kinds of recursive lexical structures:
 * - strings with interpolations: ` "chars${ expr }chars" `
 *   into which it inserts synthetic parentheses.
 */
internal class TokenSourceAdapter(
    tokenSource: TokenSource,
    val comments: MutableList<CstComment>?,
    modifyingKeywords: Set<String> = temperModifyingKeywords,
) : Producer<TokenStackElement?> {
    private val producer = WordPairer(
        InsertCallJoins(
            AutomaticSemicolonInserter(
                StringFixer(
                    CommentGrouper(tokenSource, comments),
                ),
            ),
        ),
        modifyingKeywords = modifyingKeywords,
    )

    override fun get(): TokenStackElement? = producer.get()
}

/**
 * We want simple string expressions to inline easily during staging.
 * But for tagged expressions with embedded statements fragments, we need to invert the
 * nesting so that the parser can focus on consuming whole statements.
 *
 * This pass wraps simple string templates in parentheses do so that
 * string templates form an atomic expression and so that
 * tag expressions can associate with a template to form
 * a tagged template call.
 *
 *     " character-content "
 *
 * That becomes:
 *
 *     ( " character-content " )
 *
 * And throwing an interpolation in there changes nothing.
 *
 * But if a multi-quoted string contains statement fragments, we take a slightly different tack.
 * In the below, where the right column establishes the token type, the `{:...:}` are inlined.
 *
 *     """                TokenType.LeftDelimiter
 *     foo                TokenType.QuotedString
 *     {:                 TokenType.Punctuation, mayBracket
 *       statement        TokenType.Word
 *     :}                 TokenType.Punctuation, mayBracket
 *     ${ bar }           TokenType.QuotedString
 *     """                TokenType.RightDelimiter
 *
 * Because of the presence of the statement phrase, that becomes:
 *
 *     {
 *     """
 *     +++ foo     ;
 *     statement   ;
 *     ${ bar }    ;
 *     """
 *     }
 *
 * The `+++` is a synthetic operator that indicates an emit of a chunk of
 * literal character data to the accumulator.
 */
private class StringFixer(
    val tokens: Producer<TemperToken?>,
) : Producer<TokenStackElement?> {
    private val pending = mutableListOf<TokenStackElement>()

    private fun processNonMq(token: TemperToken) {
        when (token.tokenType) {
            TokenType.LeftDelimiter -> {
                // Precede token with an open parenthesis
                pending.add(TokenStackElement(syntheticLeftBracketBefore(token, "(")))
                pending.add(TokenStackElement(token))
            }
            TokenType.RightDelimiter -> {
                // Follow it with a close parenthesis
                pending.add(TokenStackElement(token))
                pending.add(
                    TokenStackElement(
                        syntheticRightBracketAfter(token, ")"),
                        mayInfix = false,
                    ),
                )
            }
            else -> pending.add(TokenStackElement(token))
        }
    }

    override fun get(): TokenStackElement? {
        if (pending.isEmpty()) {
            val token = tokens.get() ?: return null
            if (token.tokenType == TokenType.LeftDelimiter && token.tokenText == MQ_DELIMITER) {
                // Collect the tokens between the left """ and the (synthesized) close """
                // Then scan through them to choose between the `(` ... `)` wrapping and the
                // one that will require the full block form.
                var nestDepth = 1
                var maxNestDepth = nestDepth
                pending.add(TokenStackElement(token))
                while (true) {
                    val followingToken = tokens.get() ?: break
                    if (followingToken.tokenText == MQ_DELIMITER) {
                        pending.add(TokenStackElement(followingToken))
                        when (followingToken.tokenType) {
                            TokenType.LeftDelimiter -> {
                                nestDepth += 1
                                maxNestDepth = max(maxNestDepth, nestDepth)
                            }

                            TokenType.RightDelimiter -> if (--nestDepth == 0) {
                                break
                            }

                            else -> {}
                        }
                    } else {
                        // Add any parentheses around simple string expressions
                        processNonMq(followingToken)
                    }
                }

                // Pick between `{`...`}` and `(`...`)` wrapping explained in the class comment above.
                // If the maxNestDepth > 1 then we might need to recursively process nested MQ strings.
                fixupCollectedMq()
            } else {
                processNonMq(token)
            }
        }
        return pending.compatRemoveFirst()
    }

    private fun fixupCollectedMq() {
        // Let's have consistent before view, and then replay it onto pending.
        val collected = pending.toList()
        pending.clear()

        // Scan back over the collected tokens making sure that:
        // 1. every MQ string expression, including nested ones, either have parentheses or curlies around.
        // 2. inside each curly-bracketed MQ string expression, every statement fragment is unpacked.
        // 3. inside each curly-bracketed MQ string expression, every string chunk has `+++` before it.

        // Scan to find close brackets, and classifications for each mq string.
        val mqStrings = mutableMapOf<Int, MqString>()
        forEachMqString(
            collected,
            onFragment = { mqStart, range ->
                mqStrings.getOrPut(mqStart) { MqString(mqStart) }
                    .stmtFragments.add(range)
            },
            onInterpolation = { mqStart, range ->
                mqStrings.getOrPut(mqStart) { MqString(mqStart) }
                    .interps.add(range)
            },
            onTextChunk = { mqStart, index ->
                mqStrings.getOrPut(mqStart) { MqString(mqStart) }
                    .charDataChunk.add(index)
            },
            onMqString = { range ->
                val mqStart = range.first
                val mqEnd = range.last
                mqStrings.getOrPut(mqStart) { MqString(mqStart) }.endIndex = mqEnd
            },
        )

        // Now, we build a list of changes to the token list.  This will allow us to edit it without
        // colliding edits.  We can sort the edits and replay them in order to compute the changed
        // list.

        val edits = mutableMapOf<Int, Edit>()
        fun editFor(i: Int) = edits.getOrPut(i) { Edit(collected[i]) }
        fun tokFor(i: Int): TemperToken? {
            val edit = edits[i]
            if (edit != null) {
                return edit.substitution?.temperToken
            }
            return collected[i].temperToken
        }

        for (mqString in mqStrings.values) {
            val start = mqString.startIndex
            val end = mqString.endIndex

            trimIncidentalWhitespace(mqString, ::tokFor, ::editFor)

            if (mqString.stmtFragments.isEmpty()) {
                // 1. every MQ string expression, including nested ones,
                //    either have parentheses or curlies around.
                editFor(start).before = TokenStackElement(
                    syntheticLeftBracketBefore(collected[start], "("),
                )
                editFor(end).after = TokenStackElement(
                    syntheticRightBracketAfter(collected[end], ")"),
                    mayInfix = false,
                )
            } else {
                editFor(start).before = TokenStackElement(
                    syntheticLeftBracketBefore(collected[start], "{"),
                )
                editFor(end).after = TokenStackElement(
                    syntheticRightBracketAfter(collected[end], "}"),
                    mayInfix = false,
                )
                // 2. inside each curly-bracketed MQ string expression,
                //    every statement fragment is unpacked.
                for (stmtFragment in mqString.stmtFragments) {
                    editFor(stmtFragment.first).drop()
                    editFor(stmtFragment.last).drop()
                }
                // 3. inside each curly-bracketed MQ string expression,
                //    every string chunk has `+++` before it.
                for (charDataChunk in mqString.charDataChunk) {
                    // Some data chunks may have been trimmed down to space tokens.
                    if (tokFor(charDataChunk)?.tokenType == TokenType.QuotedString) {
                        editFor(charDataChunk).before = TokenStackElement(
                            TemperToken(
                                collected[charDataChunk].pos.leftEdge,
                                "+++",
                                TokenType.Punctuation,
                                synthetic = true,
                                mayBracket = false,
                            ),
                            mayPrefix = true,
                            mayInfix = false,
                        )
                        editFor(charDataChunk).after = syntheticSemicolon(collected[charDataChunk])
                    }
                }
                for (interpRange in mqString.interps) {
                    val edit = editFor(interpRange.last)
                    val sub = edit.substitution
                    if (sub != null) {
                        edit.after = syntheticSemicolon(sub)
                    }
                }
            }
        }

        val editsInOrder = edits.entries.toMutableList()
        editsInOrder.sortBy { it.key }

        var rebuiltUpTo = 0
        var editIndex = 0
        while (true) {
            val next = editsInOrder.getOrNull(editIndex++)
            val nextIndex = next?.key ?: collected.size
            pending.addAll(collected.subList(rebuiltUpTo, nextIndex))

            val edit = (next ?: break).value

            edit.before?.let { pending.add(it) }
            edit.substitution?.let { pending.add(it) }
            edit.after?.let { pending.add(it) }

            rebuiltUpTo = nextIndex + 1
        }
    }

    companion object {
        private fun syntheticLeftBracketBefore(p: Positioned, tokenText: String) =
            TemperToken(
                pos = p.pos.leftEdge,
                tokenText = tokenText,
                tokenType = TokenType.Punctuation,
                mayBracket = true,
                synthetic = true,
            )

        private fun syntheticRightBracketAfter(p: Positioned, tokenText: String) =
            TemperToken(
                pos = p.pos.rightEdge,
                tokenText = tokenText,
                tokenType = TokenType.Punctuation,
                mayBracket = true,
                synthetic = true,
            )

        private fun syntheticSemicolon(p: Positioned) = TokenStackElement(
            TemperToken(
                p.pos.rightEdge,
                ";",
                TokenType.Punctuation,
                synthetic = true,
                mayBracket = false,
            ),
            mayPrefix = false,
            mayInfix = true,
        )

        private class MqString(val startIndex: Int) {
            var endIndex: Int = -1
            val stmtFragments = mutableListOf<IntRange>()
            val charDataChunk = mutableListOf<Int>()
            val interps = mutableListOf<IntRange>()
        }

        private class Edit(
            var substitution: TokenStackElement?,
        ) {
            var before: TokenStackElement? = null
            var after: TokenStackElement? = null

            /** Replace with a space token */
            fun drop() {
                val substitution = this.substitution ?: return
                this.substitution = substitution.copy(
                    temperToken = substitution.temperToken.let {
                        it.copy(
                            tokenText = buildString {
                                append(it.tokenText)
                                for (i in indices) {
                                    if (!LexicalDefinitions.isLineBreak(this[i])) {
                                        this[i] = ' '
                                    }
                                }
                            },
                            tokenType = TokenType.Space,
                        )
                    },
                )
            }
        }

        /**
         * Mutates a part list in place to remove incidental spaces from strings.
         *
         * <!-- snippet: syntax/string/incidental-space-removal -->
         * # Incidental spaces in multi-line strings
         *
         * When a string spans multiple lines, some space is *significant*;
         * it contributes to the content of the resulting string value.
         * Spaces that do not contribute to the content are called *incidental spaces*.
         * Incidental spaces include:
         *
         * - those used for code indentation, and
         * - those that appear at the end of a line so are invisible to readers, and
         *   often automatically stripped by editors, and
         * - carriage returns which may be inserted or removed depending on
         *   whether a file is edited on Windows or UNIX.
         *
         * Normalizing incidental space steps include:
         *
         * 1. Removing leading space on each line that match the indentation of the close quote.
         * 2. Removing the newline after the open quote, and before the close quote.
         * 3. Removing space at the end of each line.
         * 4. Normalizing line break sequences CRLF, CR, and LF to LF.
         *
         * For the purposes of identifying incidental space, we imagine that any
         * interpolation `${...}`, scriptlet `{:...:}`, or hole `${}` contributes
         * 1 or more non-space, non-line-break characters.
         *
         * Indentation matching the close quote is incidental, hence removed.
         *
         * ```temper
         * """
         *     "Line 1
         *     "Line 2
         * == "Line 1\nLine 2"
         * ```
         *
         * Each content line is stripped up to and including the margin character.
         * It's good style to line up the margin characters, but not necessary.
         *
         * ```temper
         * """
         *     " Line 1
         *    "  Line 2
         *     "   Line 3
         * == " Line 1\n  Line 2\n   Line 3"
         * ```
         *
         * It's an error if a line is not un-indented from the close quote.
         *
         * ```temper FAIL
         * """
         *     "Line 1
         *      Line 2 missing margin character
         *     "Line 3
         * ```
         *
         * Spaces are removed from the end of a line, but not if there is an
         * interpolation or hole:
         *
         * ```temper
         * """
         *     "Line 1  ${"interpolation"}
         *     "Line 2  ${/*hole*/}
         *     "Line 3
         *     == "Line 1  interpolation\nLine 2  \nLine 3"
         * ```
         *
         * For the purpose of this, space includes:
         *
         * - Space character: U+20 ' '
         * - Tab character: U+9 '\t'
         *
         * A line consists of any maximal sequence of characters other than
         * CR (U+A '\n') and LF (U+D '\r').
         *
         * A line break is any of the following sequences:
         *
         * - LF
         * - CR
         * - CR LF
         *
         * Regardless of whether a source file is authored or compiled on a
         * Windows machine (prefers CR LF) or another machine (tend to prefer LF)
         * the meaning of a string is the same.  This means that all of those sequences,
         * where not trimmed, are simplified to LF.  Use `${}` if you really need to
         * embed a `\r` in a file.
         */
        private fun trimIncidentalWhitespace(
            mqString: MqString,
            token: (Int) -> TemperToken?,
            edit: (Int) -> Edit,
        ) {
            // 1. Identify line ends
            val lines = buildList {
                var line = mutableListOf<Int>()
                for (chunkIndex in mqString.charDataChunk) {
                    val token = token(chunkIndex)
                    if (token?.tokenType != TokenType.QuotedString) { continue } // If elided elsewhere
                    line.add(chunkIndex)
                    if (LexicalDefinitions.isLineBreak(token.tokenText.last())) {
                        add(line.toList())
                        line = mutableListOf()
                    }
                }
                if (line.isNotEmpty()) { add(line.toList()) }
            }
            // 2. Identify lines which are just whitespace and one {:...:} region.
            val isJustStatement = lines.map { line ->
                // Three cases.
                // " " {: :} "\n"
                // {: :} "\n"
                // " " {: :}
                when (line.size) {
                    2 -> {
                        val first = line.first()
                        val last = line.last()
                        val fragmentRange = mqString.stmtFragments.firstOrNull { it.first == first + 1 }
                        fragmentRange?.last == last - 1 && isSpaceyCharData(token(first)) &&
                            isSpaceyCharData(token(last))
                    }
                    1 -> {
                        val index = line[0]
                        mqString.stmtFragments.any { it.first == index + 1 || it.last + 1 == index } &&
                            isSpaceyCharData(token(index))
                    }
                    else -> false
                }
            }
            // 3. Eliminate or normalize line breaks.
            //    Eliminate from after opening quotes, from last line.
            // Walk backwards so that we can keep track of whether there is content following.
            var followedByContent = false
            for (lineIndex in lines.indices.reversed()) {
                val line = lines[lineIndex]
                if (isJustStatement[lineIndex]) {
                    for (i in line) { edit(i).drop() }
                    continue
                }

                val lastIndex = line.last()
                val last = token(lastIndex)!!

                val needsNewline = followedByContent &&
                    // Do not keep spacey content following the open quote.
                    !(line.size == 1 && lastIndex == mqString.startIndex + 1 && isSpaceyCharData(last))

                val lastTextChunk = buildString {
                    append(last.tokenText)
                    var beforeLb = this.length
                    while (beforeLb > 0 && LexicalDefinitions.isLineBreak(this[beforeLb - 1])) {
                        beforeLb -= 1
                    }
                    val hadNewline = beforeLb != length

                    // Has no newline. Trim incidental space from the end.
                    var afterSpace = beforeLb
                    while (afterSpace > 0 && LexicalDefinitions.isSpace(this[afterSpace - 1])) {
                        afterSpace -= 1
                    }
                    this.setLength(afterSpace)

                    if (hadNewline && needsNewline) { // Normalize or drop
                        append('\n')
                    }
                }
                if (lastTextChunk != last.tokenText) {
                    val edit = edit(lastIndex)
                    if (lastTextChunk.isEmpty()) {
                        edit.drop()
                    } else {
                        edit.substitution = edit.substitution!!.copy(
                            temperToken = last.copy(tokenText = lastTextChunk),
                        )
                    }
                }
                followedByContent = true
            }
        }

        private fun isSpaceyCharData(token: TemperToken?): Boolean =
            token?.tokenType == TokenType.QuotedString &&
                token.tokenText.all {
                    LexicalDefinitions.isSpace(it) || LexicalDefinitions.isLineBreak(it)
                }

        private inline fun forEachMqString(
            stackElements: List<TokenStackElement>,
            onFragment: (Int, IntRange) -> Unit,
            onInterpolation: (Int, IntRange) -> Unit,
            onTextChunk: (Int, Int) -> Unit,
            onMqString: (IntRange) -> Unit,
        ) {
            val stack = mutableListOf<Int?>()
            val fragments = mutableListOf<Int>()
            val curlies = mutableListOf<Int>()
            for (i in stackElements.indices) {
                val (tok) = stackElements[i]
                when (tok.tokenType) {
                    TokenType.Margin -> {
                        if (fragments.isNotEmpty()) {
                            val start = fragments.removeLast()
                            val top = stack.last()!!
                            onFragment(top, start..<i)
                        }
                        if (tok.tokenText == marginStmtFragmentText) {
                            fragments.add(i)
                        }
                    }
                    TokenType.Space -> {}
                    TokenType.Punctuation -> {
                        when (tok.tokenText) {
                            $$"${", "{" -> curlies.add(i)
                            "}" -> curlies.removeLastOrNull()?.let { start ->
                                val top = stack.lastOrNull()
                                if (top != null && stackElements[start].tokenText == $$"${") {
                                    onInterpolation(top, start..i)
                                }
                            }
                        }
                    }
                    TokenType.LeftDelimiter -> stack.add(
                        if (tok.tokenText == MQ_DELIMITER) {
                            i
                        } else {
                            null
                        },
                    )
                    TokenType.RightDelimiter -> {
                        if (fragments.isNotEmpty()) {
                            val start = fragments.removeLast()
                            val top = stack.last()!!
                            onFragment(top, start..<i)
                        }
                        val start = stack.removeLast()
                        if (start != null) {
                            onMqString(start..i)
                        }
                    }
                    TokenType.QuotedString -> {
                        val top = stack.lastOrNull()
                        if (top != null) {
                            onTextChunk(top, i)
                        }
                    }
                    else -> {}
                }
            }
            check(fragments.isEmpty())
        }
    }
}

private class CommentGrouper(
    val tokenSource: TokenSource,
    val comments: MutableList<CstComment>?,
) : Producer<TemperToken?> {
    val pending = ArrayDeque<TemperToken>()

    override fun get(): TemperToken? {
        if (pending.isNotEmpty()) { return pending.removeFirst() }
        if (!tokenSource.hasNext()) { return null }
        val token = tokenSource.next()
        if (token.tokenType != TokenType.Comment) { return token }

        val text = token.tokenText
        val commentType = classify(text, isSynthetic = token.synthetic)
        if (commentType != CommentType.Line || comments == null) {
            comments?.add(CstComment(pos = token.pos, type = commentType, text = text))
            return token
        }
        // Group together adjacent line comments.
        // By adjacent, we mean, only separated by space tokens with at most one line break.
        var lastWasLineBreak = false
        // Gather tokens stopping if we see a blank line or a non-space/line-comment token.
        // After we gather all the adjacent line comments
        pending.add(token)
        add_adjacent_comment_lines@
        while (tokenSource.hasNext()) {
            val followingToken = tokenSource.peek() ?: break
            val (_, followingText, followingType) = followingToken
            when (followingType) {
                TokenType.Comment ->
                    if (classify(followingText, isSynthetic = followingToken.synthetic) != CommentType.Line) {
                        break@add_adjacent_comment_lines
                    }
                TokenType.Space -> Unit
                else -> break@add_adjacent_comment_lines
            }

            val found = countOfLineBreaksUpTo(followingText, upperBound = 2)
            if (found != 0 && lastWasLineBreak || found > 1) {
                break@add_adjacent_comment_lines
            }
            lastWasLineBreak = found != 0

            pending.add(followingToken)
            tokenSource.next()
        }

        comments.add(
            CstComment(
                pos = pending.spanningPosition(token.pos),
                text = pending.joinToString("") { it.tokenText },
                type = CommentType.Line,
            ),
        )

        // Since any other line comment parts are on pending, they won't be re-processed
        // as a CstComment.
        return pending.removeFirst()
    }

    companion object {
        private fun classify(text: String, isSynthetic: Boolean) = when {
            isSynthetic && text.startsWith(MASSAGED_SEMILIT_COMMENT_START) &&
                text.endsWith(MASSAGED_SEMILIT_COMMENT_END) -> CommentType.SemilitParagraph
            text.startsWith("/*") && text.endsWith("*/") -> CommentType.Block
            text.startsWith("//") -> CommentType.Line
            else -> CommentType.Semilit
        }
    }
}

/**
 * Uses ignorable tokens to decide when to insert semicolons, and strips those out of the token
 * stream, producing a stream of significant Token objects.
 *
 * <!-- snippet: semicolon-insertion -->
 * # Automatic Semicolon Insertion
 *
 * Semicolons are inserted in the following places:
 *
 * - After  `}` that end a line except before a close bracket or an operator token that is not prefix.
 * - Before `{` that starts a line except after an open bracket or an operator token that is not postfix.
 *
 * Except that, semicolons are never inserted after the close curly bracket in a `${` and `}` pair.
 *
 * This is more conservative than semicolon insertion in JavaScript,
 * but still simplifies several things.
 *
 * ## All adjacent statements are separated by semicolons
 *
 * There's no need to have a set of special statements like `if (...) stmt0 else stmt1` that do not
 * need to be followed by a semicolon.
 * Productions for a series of statements and declarations can simply assume that semicolons appear
 * before them.
 *
 * ## No limited set of statement continuers
 *
 * We don't need a special set of statement continuers like `else` so that we know that
 * the token sequence `} else {` is part of one statement.
 * This lets us use common cues to allow new continuers like
 *
 * ```temper inert
 * foo(x) {
 *   // Ruby-style block
 * } bar(y) {
 *   // ruby-style block
 * }
 * ```
 *
 * which de-sugars to a single statement
 *
 * ```temper inert
 * foo(x, fn { ... }, bar = fn (f) { f(y, fn { ... }) });
 * ```
 *
 * vs something without a continuer
 *
 * ```temper inert
 * foo(x) {
 *   // Ruby-style block
 * }                         // <-- Semicolon inserted here
 * bar(y) {
 *   // Ruby-style-block
 * }
 * ```
 *
 * which de-sugars to two statements
 *
 * ```temper inert
 * foo(x, fn { ... });
 * bar(y, fn { ... });
 * ```
 *
 * ## Motivation
 * Developers of C-like languages are used to not following `}`s that end a statement with a
 * semicolon.
 *
 * The exception is `class` definitions in C++ which, unlike Java and more recent C-like languages
 * do need to be followed by semicolons.
 *
 * That that trips me up everytime I go back to C++ seems evidence that requiring semicolons after
 * statements that end with something block-like would be a burden to developers.
 *
 * <!-- /snippet -->
 *
 * See also a `./asi.md` for a summary of the conditions under which semicolons are inserted.
 */
private class AutomaticSemicolonInserter(
    val tokens: Producer<TokenStackElement?>,
) : Producer<TokenStackElement?> {
    private var lastUnignorable: TokenStackElement? = null
    private var newlineSinceLastUnignorable = false
    private var pushback: TokenStackElement? = null
    private var depth = 0

    /**
     * Whether an unclosed `${` or `{` is open at a depth corresponding to the index.
     * Useful for when a `}` is seen and [depth] is incremented.
     */
    private val isInterp = KBitSet()

    override fun get(): TokenStackElement? {
        while (true) {
            val tokenStackElement = pushback?.let {
                pushback = null
                it
            }
                ?: tokens.get()
                ?: break
            val (_, text, type) = tokenStackElement.temperToken

            if (type.ignorable) {
                if (!newlineSinceLastUnignorable) {
                    newlineSinceLastUnignorable = hasLineBreak(text)
                }
                continue
            }

            if (type == TokenType.Punctuation) {
                when (text) {
                    "{", $$"${" -> {
                        isInterp[depth] = text != "{"
                        depth += 1
                    }
                    "}" -> if (depth != 0) {
                        depth -= 1
                    }
                }
            }

            pushback = tokenStackElement // Unset if we consume it below.

            val last = lastUnignorable
            var insertSemicolon = false
            if (last != null && newlineSinceLastUnignorable) {
                val ltx = last.tokenText
                val lty = last.tokenType
                // Maybe insert a semicolon before a '{' at the start of the line.
                if (type == TokenType.Punctuation && text == "{") {
                    val isOpenBracket = ltx in openBrackets
                    val (
                        allowedInPrefixPosition,
                        allowedInInfixPosition,
                        allowedInPostfixPosition,
                    ) = allowedPositions(ltx, lty)
                    if (
                        !isOpenBracket && (
                            allowedInPostfixPosition ||
                                !(allowedInPrefixPosition || allowedInInfixPosition)
                            )
                    ) {
                        insertSemicolon = true
                    }
                }
                // Maybe insert a semicolon after a '}' at the start of the line.
                if (!insertSemicolon && lty == TokenType.Punctuation && ltx == "}") {
                    val isCloseBracket = text in closeBrackets
                    val (
                        allowedInPrefixPosition,
                        allowedInInfixPosition,
                        allowedInPostfixPosition,
                    ) = allowedPositions(text, type)
                    if (
                        !isCloseBracket && (
                            allowedInPrefixPosition ||
                                !(allowedInInfixPosition || allowedInPostfixPosition)
                            ) &&
                        !isInterp[depth]
                    ) {
                        insertSemicolon = true
                    }
                }
            }

            newlineSinceLastUnignorable = false

            if (insertSemicolon) {
                val semicolon = TokenStackElement(
                    TemperToken(
                        pos = last!!.pos.rightEdge,
                        tokenText = ";",
                        tokenType = TokenType.Punctuation,
                        synthetic = true,
                        mayBracket = false,
                    ),
                )
                lastUnignorable = semicolon
                return semicolon
            }

            pushback = null
            lastUnignorable = tokenStackElement
            return tokenStackElement
        }
        return null
    }
}

/**
 * Between any `}` token and a word token that does not correspond to an operator, we insert
 * a synthetic token for [lang.temper.lexer.Operator.CallJoin].
 *
 * This lets us handle constructs like
 *
 *     foo(x) {
 *         ...
 *     } bar(y) {
 *         ...
 *     }
 *
 * where `bar(y) { ... }` is a full call construct that continues `foo(x) { ... }` by treating
 * the whole as (\callJoin (foo x ...) (bar y ...)).
 */
private class InsertCallJoins(val tokens: Producer<TokenStackElement?>) : Producer<TokenStackElement?> {
    private var lastWasCloseCurly = false
    private var pushback: TokenStackElement? = null

    override fun get(): TokenStackElement? {
        val tokenStackElement = pushback ?: tokens.get() ?: return null
        pushback = null
        val oldLastWasCloseCurly = lastWasCloseCurly
        lastWasCloseCurly = false
        val tokenType = tokenStackElement.tokenType
        val tokenText = tokenStackElement.tokenText

        if (
            oldLastWasCloseCurly && tokenType == TokenType.Word &&
            Operator.matching(tokenText, tokenType, OperatorType.Infix).isEmpty()
        ) {
            pushback = tokenStackElement
            return TokenStackElement(
                TemperToken(
                    pos = tokenStackElement.pos.leftEdge,
                    tokenText = Operator.CallJoin.text!!,
                    tokenType = TokenType.Word,
                    synthetic = true,
                    mayBracket = false,
                ),
                mayInfix = true,
            )
        }

        lastWasCloseCurly = tokenType == TokenType.Punctuation && tokenText == "}"
        return tokenStackElement
    }
}

/**
 * There are a few cases where we see adjacent words together in JS/TS/Python like languages.
 *
 * 1. Modifying keywords: `public class`, `async function`.
 *    These are annotative.  We could as easily say `@public class` or `@async function`.
 *    This will group and normalize `@public @async function` and `public async function` to the same result.
 * 2. Compound connectors: `else if`, `is not`.
 *    These can be treated as one identifier where the words are joined by spaces.
 * 3. Definitional constructs: `let name`, `class Name`, `function name`.
 *
 * This pass converts (1) a modifying keyword followed by a word, into an annotation.
 * It leaves combining (2) to the Grammar, so that the Grammar has enough context to distinguish
 * (2) (in infix position) from (3).
 *
 * <!-- snippet: legacy-decorator -->
 * # Legacy decorators
 * To make Temper more readable for people familiar with other languages, some decorators don't need
 * an `@` character before them.
 *
 * The following *modifying words* are converted to decorations when followed by an identifier or
 * keyword token:
 *
 * ⎀ modifying-words-list
 *
 * Additionally, some decorators imply the word `let`:
 * `var` and `const`, when not followed by `let` imply `let`.
 *
 * ```temper
 * do {
 *   @var let i = 1;
 *   i += 10;
 *   console.log(i.toString()); //!outputs "11"
 * }
 * do {
 *   var i = 1;
 *   i += 10;
 *   console.log(i.toString()); //!outputs "11"
 * }
 * ```
 */
private class WordPairer(
    tokens: Producer<TokenStackElement?>,
    val modifyingKeywords: Set<String>,
) : Producer<TokenStackElement?> {
    val tokens = LookaheadProducer(tokens)
    val pushback = ArrayDeque<TokenStackElement>()
    var lastWasAt = false
    var lastWasDot = false

    override fun get(): TokenStackElement? {
        val token = pushback.removeFirstOrNull() ?: tokens.get() ?: return null

        val oldLastWasAt = lastWasAt
        val oldLastWasDot = lastWasDot
        lastWasAt = false
        lastWasDot = false

        if (oldLastWasDot && token.tokenType == TokenType.Word) {
            // a word after a dot is always a member name, never an operator
            return token.copy(mayPrefix = false, mayInfix = false)
        }

        if (token.tokenText in modifyingKeywords && !oldLastWasAt) {
            val approximateWordCount = tokens.lookahead {
                var count = ApproximateCount.Zero
                while (true) {
                    val next = it.get() ?: break
                    val nextTokenType = next.tokenType
                    when {
                        nextTokenType.ignorable -> Unit
                        nextTokenType == TokenType.Word ||
                            // chain annotations
                            (nextTokenType == TokenType.Punctuation && next.tokenText == "@") -> {
                            val oldCount = count
                            count = count.next
                            if (count == oldCount) { break }
                        }
                        else -> break
                    }
                }
                count
            }

            if (approximateWordCount > ApproximateCount.Zero) {
                pushback.add(token)
                lastWasAt = true
                if (
                    isDeclarationWord(token.tokenText) &&
                    approximateWordCount == ApproximateCount.One
                ) {
                    // Convert `const x` into `@ const let x` but not `const foo f() {}`.
                    // See BuildTreeTest for the variants of this theme.
                    pushback.add(
                        TokenStackElement(
                            TemperToken(
                                pos = token.pos.rightEdge,
                                tokenText = "let",
                                tokenType = TokenType.Word,
                                synthetic = true,
                                mayBracket = false,
                            ),
                        ),
                    )
                }

                return TokenStackElement(
                    TemperToken(
                        pos = token.pos.leftEdge,
                        tokenText = "@",
                        tokenType = TokenType.Punctuation,
                        synthetic = true,
                        mayBracket = false,
                    ),
                )
            }
        }

        lastWasAt = token.tokenType == TokenType.Punctuation && token.tokenText == "@"
        lastWasDot = token.tokenType == TokenType.Punctuation && token.tokenText == "."
        return token
    }
}

/**
 * <!-- snippet: modifying-words-list -->
 *
 * - `abstract`
 * - [`const`][snippet/builtin/@const]
 * - [`export`][snippet/builtin/@export]
 * - `final`
 * - `native`
 * - [`private`][snippet/builtin/@private]
 * - [`protected`][snippet/builtin/@protected]
 * - [`public`][snippet/builtin/@public]
 * = [`sealed`][snippet/builtin/@sealed]
 * - [`static`][snippet/builtin/@static]
 * - [`var`][snippet/builtin/@var]
 * - `volatile`
 */
private val temperModifyingKeywords = setOf(
    "abstract",
    "const", // Plus some extra sparkles
    "export",
    "final",
    "native",
    "private",
    "protected",
    "public",
    "sealed",
    "static",
    "var", // Also gets extra sparkles
    "volatile",
)

private fun isDeclarationWord(word: String) = word == "const" || word == "var"

private fun hasLineBreak(tokenText: String) = countOfLineBreaksUpTo(tokenText, 1) != 0

private fun countOfLineBreaksUpTo(tokenText: String, upperBound: Int): Int {
    var found = 0
    var i = 0
    val n = tokenText.length
    while (i < n) {
        val cp = decodeUtf16(tokenText, i)
        i += charCount(cp)
        if (LexicalDefinitions.isLineBreak(cp)) {
            found += 1
            if (found >= upperBound) { break }
            if (cp == C_CR && i < n && tokenText[i] == '\n') {
                // CRLF
                i += 1
            }
        }
    }
    return found
}

/** Simplified from https://discworld.fandom.com/wiki/Troll#Literacy_and_Numeracy */
private enum class ApproximateCount {
    Zero,
    One,
    Lots,
    ;

    val next get() = when (this) {
        Zero -> One
        One, Lots -> Lots
    }
}

private fun allowedPositions(tokenText: String, tokenType: TokenType) = Triple(
    Operator.matching(tokenText, tokenType, OperatorType.Prefix).isNotEmpty(),
    Operator.matching(tokenText, tokenType, OperatorType.Infix).isNotEmpty() ||
        Operator.matching(tokenText, tokenType, OperatorType.Separator).isNotEmpty(),
    Operator.matching(tokenText, tokenType, OperatorType.Postfix).isNotEmpty(),
)

private class LookaheadProducer<T>(val underlying: Producer<T>) : Producer<T> {
    private val pending = ArrayDeque<T>()
    private var activeLookaheadCount = 0

    override fun get(): T {
        // Check that gets do not interleave with lookaheads
        // since lookaheads assume no one is eating from pending.
        check(activeLookaheadCount == 0)
        if (pending.isEmpty()) {
            pending.add(underlying.get())
        }
        return pending.removeFirst()
    }

    fun <O> lookahead(f: (Producer<T>) -> O): O {
        class Lookahead : Producer<T> {
            // this may add to pending, but if
            private var active = false
            fun start() {
                check(!active)
                active = true
            }

            fun stop() {
                check(active)
                active = false
            }

            private var i = 0
            override fun get(): T {
                check(active)
                if (i == pending.size) {
                    pending.add(underlying.get())
                }
                return pending[i++]
            }
        }

        val la = Lookahead()
        activeLookaheadCount += 1
        la.start()
        return try {
            val result = f(la)
            result
        } finally {
            la.stop()
            activeLookaheadCount -= 1
        }
    }
}

private val marginStmtFragmentText = TokenCluster.Chunk.MarginStmtFragment.prefixText
