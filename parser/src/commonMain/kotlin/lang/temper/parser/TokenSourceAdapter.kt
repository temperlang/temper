package lang.temper.parser

import lang.temper.common.C_CR
import lang.temper.common.Producer
import lang.temper.common.charCount
import lang.temper.common.compatRemoveFirst
import lang.temper.common.decodeUtf16
import lang.temper.cst.CstComment
import lang.temper.lexer.CommentType
import lang.temper.lexer.LexicalDefinitions
import lang.temper.lexer.MASSAGED_SEMILIT_COMMENT_END
import lang.temper.lexer.MASSAGED_SEMILIT_COMMENT_START
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenSource
import lang.temper.lexer.TokenType
import lang.temper.lexer.closeBrackets
import lang.temper.lexer.openBrackets
import lang.temper.log.spanningPosition

/**
 * Allows customizing the behaviour of the token source adapter.
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
            QuoteTagger(
                AutomaticSemicolonInserter(
                    CommentGrouper(tokenSource, comments),
                ),
            ),
        ),
        modifyingKeywords = modifyingKeywords,
    )

    override fun get(): TokenStackElement? = producer.get()
}

/**
 * Wraps string templates in parentheses do so that
 * string templates form an atomic expression and so that
 * tag expressions can associate with a template to form
 * a tagged template call.
 *
 *     " character-content "
 *
 * becomes
 *     ( " character-content " )
 *
 * and
 *
 *     tag " character-content "
 *
 * becomes
 *
 *     tag ( " character-content " )
 */
private class QuoteTagger(
    val tokens: Producer<TokenStackElement?>,
) : Producer<TokenStackElement?> {
    private val pending = mutableListOf<TokenStackElement>()

    override fun get(): TokenStackElement? {
        if (pending.isNotEmpty()) {
            return pending.compatRemoveFirst()
        }
        val token = tokens.get() ?: return null
        return when (token.tokenType) {
            TokenType.LeftDelimiter -> {
                // Precede token with an open parenthesis
                pending.add(token)
                TokenStackElement(
                    TemperToken(
                        pos = token.pos.leftEdge,
                        tokenText = "(",
                        tokenType = TokenType.Punctuation,
                        mayBracket = true,
                        synthetic = true,
                    ),
                    mayInfix = true,
                )
            }
            TokenType.RightDelimiter -> {
                // Follow it with a close parenthesis
                pending.add(
                    TokenStackElement(
                        TemperToken(
                            pos = token.pos.rightEdge,
                            tokenText = ")",
                            tokenType = TokenType.Punctuation,
                            mayBracket = true,
                            synthetic = true,
                        ),
                        mayInfix = false,
                    ),
                )
                token
            }
            else -> token
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
    val tokens: Producer<TemperToken?>,
) : Producer<TokenStackElement?> {
    private var lastUnignorable: TokenStackElement? = null
    private var newlineSinceLastUnignorable = false
    private var pushback: TemperToken? = null

    override fun get(): TokenStackElement? {
        while (true) {
            val token = pushback?.let {
                pushback = null
                it
            }
                ?: tokens.get()
                ?: break
            val (_, text, type) = token

            if (type.ignorable) {
                if (!newlineSinceLastUnignorable) {
                    newlineSinceLastUnignorable = hasLineBreak(text)
                }
                continue
            }

            pushback = token // Unset if we consume it below.

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
                            )
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
            val result = TokenStackElement(token)
            lastUnignorable = result
            return result
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
        val token = pushback ?: tokens.get() ?: return null
        pushback = null
        val oldLastWasCloseCurly = lastWasCloseCurly
        lastWasCloseCurly = false
        val tokenType = token.tokenType
        val tokenText = token.tokenText

        if (
            oldLastWasCloseCurly && tokenType == TokenType.Word &&
            Operator.matching(tokenText, tokenType, OperatorType.Infix).isEmpty()
        ) {
            pushback = token
            return TokenStackElement(
                TemperToken(
                    pos = token.pos.leftEdge,
                    tokenText = Operator.CallJoin.text!!,
                    tokenType = TokenType.Word,
                    synthetic = true,
                    mayBracket = false,
                ),
                mayInfix = true,
            )
        }

        lastWasCloseCurly = tokenType == TokenType.Punctuation && tokenText == "}"
        return token
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
        if (LexicalDefinitions.Companion.isLineBreak(cp)) {
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
