package lang.temper.format

import lang.temper.common.C_DASH
import lang.temper.common.DECIMAL_RADIX
import lang.temper.common.FixedEscape
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.charCount
import lang.temper.common.indexOfNext
import lang.temper.common.unquotedTemperEscaper
import lang.temper.lexer.IdParts
import lang.temper.lexer.unpackQuotedString

/**
 * A formatting template that has placeholders for which the textual content of each
 * format element can be substituted.
 *
 * Usually, leaves will format themselves by implementing [TokenSerializable] in which case
 * [CodeFormatter] will not request a format string.
 * This is especially important for leaves which encapsulate [names][lang.temper.name.Name].
 * Subtypes that extend [TokenSerializable] may override [FormattableTree.codeFormattingTemplate]'s
 * to `throw UnsupportedOperatorException` as the former will be used instead of the latter.
 */
sealed interface CodeFormattingTemplate {
    fun toFormatStringApproximate(): String

    data class Concatenation(
        val elements: List<CodeFormattingTemplate>,
    ) : CodeFormattingTemplate {
        override fun toFormatStringApproximate(): String = elements.joinToString(" ") {
            it.toFormatStringApproximate()
        }
    }

    sealed interface Substitution : CodeFormattingTemplate {
        val relativeIndex: Int
        val elementSeparator: CodeFormattingTemplate
    }

    data class OneSubstitution(
        override val relativeIndex: Int,
    ) : Substitution {
        override val elementSeparator get() = empty
        override fun toFormatStringApproximate(): String = "{{$relativeIndex}}"
    }

    data class GroupSubstitution(
        override val relativeIndex: Int,
        override val elementSeparator: CodeFormattingTemplate,
    ) : Substitution {
        override fun toFormatStringApproximate(): String =
            "{{$relativeIndex*${elementSeparator.toFormatStringApproximate()}}}"
    }

    sealed interface CodeFormattingTemplateAtom : CodeFormattingTemplate {
        val token: OutputToken
    }

    data class LiteralToken(override val token: OutputToken) : CodeFormattingTemplateAtom {
        constructor(
            tokenText: String,
            tokenType: OutputTokenType,
            tokenAssociation: TokenAssociation = TokenAssociation.Unknown,
        ) : this(OutputToken(tokenText, tokenType, tokenAssociation))
        override fun toFormatStringApproximate(): String = formatStringEscaper.escape(token.text)
    }

    sealed interface FormattingSpace : CodeFormattingTemplateAtom {
        val space: String get() = token.text
    }

    data object Space : FormattingSpace {
        override val token: OutputToken = OutToks.oneSpace
        override fun toFormatStringApproximate(): String = "\\ "
    }

    data object NewLine : FormattingSpace {
        override val token: OutputToken = OutputToken("\n", OutputTokenType.Space)
        override fun toFormatStringApproximate(): String = "\\n"
    }

    companion object {
        /**
         * ## String form of format templates
         *
         * In the string form, format element 0 may be substituted `{{0}}`
         * and similarly for other non-negative integers.
         *
         * The last format element may be substituted with `{{-1}}`, the second last with `{{-2}}`, etc.
         *
         * If a format element is a [FormattableTreeGroup] you can use wildcard patterns.
         *
         * `{{1*:, }}` means substitute the contents of element #1 separated by ", ".
         *
         *     "function {{0}}({{1*, }}) {\n{{-1}}\n}"
         *     // Imagine format element 0 is a function name, the last its body, and format element 1
         *     // groups its arguments.
         *
         * the wildcard will format all elements of group 1.
         *
         * When both the tree and the child filling a placeholder have a non-null [OperatorDefinition],
         * then parentheses are inserted around the child's tokens as appropriate.
         * Also, if the child [is a comma operation][OperatorDefinition.isCommaOperator]
         * and the child is formatted as part of a wildcard that uses commas to separate, then the
         * child will be parenthesized.
         */
        fun fromFormatString(formatString: String): CodeFormattingTemplate {
            val parser = FormatStringParser(formatString)
            return parser.parse().also {
                parser.requireDone()
            }
        }

        val empty = Concatenation(emptyList())

        fun guessFromTokenText(tokenText: String): CodeFormattingTemplate =
            when (tokenText) {
                "" -> empty
                "\n" -> NewLine
                " " -> Space
                else -> when (val tokenResult = guessOutputToken(tokenText)) {
                    is RSuccess -> LiteralToken(tokenResult.result)
                    is RFailure -> error(tokenResult.failure)
                }
            }

        fun guessOutputToken(tokenTextUnescaped: String): RResult<OutputToken, String> {
            var tokenText = tokenTextUnescaped
            if (tokenText.isEmpty()) {
                return RFailure("Expected token but was empty")
            }
            var tokenAssoc = TokenAssociation.Unknown
            val c = tokenText[0]
            val tokenType = when {
                looksLikeNumber(tokenText) -> OutputTokenType.NumericValue
                looksLikeWordPart(tokenText) -> OutputTokenType.Word
                c in brackets -> {
                    tokenAssoc = TokenAssociation.Bracket
                    OutputTokenType.Punctuation
                }
                looksLikeString(tokenText) -> OutputTokenType.QuotedValue
                looksLikeComment(tokenText) -> OutputTokenType.Comment
                else -> OutputTokenType.Punctuation
            }
            if (tokenType == OutputTokenType.Punctuation) {
                val tokenUnpacked = unpackQuotedString(tokenText, skipDelimiter = false)
                if (!tokenUnpacked.isOk) {
                    return RFailure("Error unpacking escape sequences in `$tokenText`")
                }
                tokenText = tokenUnpacked.decoded
            }
            val token = OutputToken(text = tokenText, type = tokenType, association = tokenAssoc)
            return RSuccess(token)
        }

        private fun OutputToken.withAssociation(association: TokenAssociation): OutputToken =
            OutputToken(this.text, this.type, association)
        private val uncommittedLeftAngle = OutToks.leftAngle.withAssociation(TokenAssociation.Unknown)
        private val uncommittedRightAngle = OutToks.rightAngle.withAssociation(TokenAssociation.Unknown)

        /** Pair up `<` and `>` with unknown association as brackets */
        fun heuristicAdjustParts(items: MutableList<CodeFormattingTemplate>) {
            for ((index, item) in items.withIndex()) {
                if (item is LiteralToken && item.token == uncommittedLeftAngle) {
                    val rightIndex = items.indexOfNext(index + 1) {
                        (it as? LiteralToken)?.token == uncommittedRightAngle
                    }
                    if (rightIndex >= 0) {
                        items[index] = LiteralToken(OutToks.leftAngle)
                        items[rightIndex] = LiteralToken(OutToks.rightAngle)
                    }
                }
            }
        }
    }
}

private val formatStringEscaper = unquotedTemperEscaper.withExtraEscapes(
    mapOf(
        '{' to FixedEscape("\\{"),
        '}' to FixedEscape("\\}"),
    ),
)

private class FormatStringParser(val formatString: String) {
    private var offset = 0

    fun parse(): CodeFormattingTemplate {
        val items = buildList {
            while (offset < formatString.length) {
                val item = when (val c = formatString[offset]) {
                    '{' -> if (formatString.getOrNull(offset + 1) == '{') {
                        parseSubstitution()
                    } else {
                        offset += 1
                        CodeFormattingTemplate.LiteralToken(OutToks.leftCurly)
                    }
                    '}' -> {
                        var n = 1
                        while (formatString.getOrNull(offset + n) == '}') { n += 1 }
                        // If there is a `}}`, assume it's the end of a substitution.
                        // In a wildcard substitution we recurse to parse the separator.
                        // If there are more than 2 right curlies, we assume all but the last
                        // two are single curly bracket tokens in the separator.
                        // This approach lets us avoid making the parser O(n**2).
                        when {
                            n == 1 -> {
                                offset += 1
                                CodeFormattingTemplate.LiteralToken(OutToks.rightCurly)
                            }
                            else -> {
                                repeat(n - 2) {
                                    offset += 1
                                    add(CodeFormattingTemplate.LiteralToken(OutToks.rightCurly))
                                }
                                break
                            }
                        }
                    }
                    '\n', '\r' -> {
                        offset += 1
                        if (c == '\r' && formatString.getOrNull(offset) == '\n') {
                            offset += 1 // CRLF
                        }
                        CodeFormattingTemplate.NewLine
                    }
                    ' ', '\t' -> {
                        offset += 1
                        null
                    }
                    '\\' -> {
                        if (offset + 1 == formatString.length) {
                            parseError("Expected character after \\")
                        }
                        when (formatString[offset + 1]) {
                            '\n' -> {
                                offset += 2
                                CodeFormattingTemplate.NewLine
                            }
                            ' ' -> {
                                offset += 2
                                CodeFormattingTemplate.Space
                            }
                            else -> parseToken()
                        }
                    }
                    in formatStringAtoms -> {
                        literalFrom(formatString.substring(offset, offset + 1)).also {
                            offset += 1
                        }
                    }
                    else -> parseToken()
                }
                if (item != null) {
                    add(item)
                }
            }
            CodeFormattingTemplate.heuristicAdjustParts(this)
        }
        return if (items.size == 1) {
            items[0]
        } else {
            CodeFormattingTemplate.Concatenation(items)
        }
    }

    private fun parseSubstitution(): CodeFormattingTemplate.Substitution {
        expect('{')
        expect('{')
        val beforeIndex = offset
        maybe('-')
        while (offset < formatString.length && formatString[offset] in '0'..'9') {
            offset += 1
        }
        val index = formatString.substring(beforeIndex, offset).toInt(DECIMAL_RADIX)
        val wildcard = maybe('*')
        val seperator = if (wildcard) {
            expect(':')
            parse()
        } else {
            null
        }
        expect('}')
        expect('}')
        return if (wildcard) {
            CodeFormattingTemplate.GroupSubstitution(index, seperator!!)
        } else {
            CodeFormattingTemplate.OneSubstitution(index)
        }
    }

    private fun parseToken(): CodeFormattingTemplate.LiteralToken {
        val beforeToken = offset
        while (offset < formatString.length) {
            val c = formatString[offset]
            when (c) {
                in '\u0000'..'\u0020' -> break
                '\\' -> offset += 1
                in formatStringAtoms -> break
                else -> {}
            }
            offset += 1
        }
        return literalFrom(formatString.substring(beforeToken, offset))
    }

    private fun maybe(c: Char): Boolean {
        val found = formatString.getOrNull(offset) == c
        if (found) {
            offset += 1
        }
        return found
    }

    private fun expect(c: Char) {
        val got = formatString.getOrNull(offset)
        if (got != c) {
            parseError(
                if (got == null) {
                    "Wanted `$c` but got end of input"
                } else {
                    "Wanted `$c` but got `$got`"
                },
            )
        }
        offset += 1
    }

    private fun parseError(msg: String): Nothing {
        throw IllegalArgumentException("In `$formatString` at offset $offset: $msg")
    }

    fun requireDone() {
        require(offset == formatString.length)
    }

    private fun literalFrom(literalText: String): CodeFormattingTemplate.LiteralToken =
        when (val r = CodeFormattingTemplate.guessOutputToken(literalText)) {
            is RSuccess -> CodeFormattingTemplate.LiteralToken(r.result)
            is RFailure -> parseError(r.failure)
        }
}

private fun looksLikeNumber(tokenText: String): Boolean {
    if (tokenText.isEmpty()) {
        return false
    }
    var i = 0
    val c0 = tokenText[0]
    if (c0 == '+' || c0 == '-') {
        i += 1
    }
    if (tokenText.getOrNull(i) == '.') {
        i += 1
    }
    val c = tokenText.getOrNull(i) ?: return false
    return c in '0'..'9'
}

private fun looksLikeWordPart(tokenText: String): Boolean {
    // We want to recognize words and word affixes.

    // We skip over identifier medial characters and '-' which is used
    // as a segment separator in some languages.
    // Then, if the next is an identifier continuation character,
    // it looks like a word part.
    // This is NOT EXCLUSIVE with looksLikeNumber because digits
    // are identifier continuers, and `-` is a valid sign.
    // Code that needs to distinguish between the two should be careful
    // to check looksLikeNumber first.
    var i = 0
    val n = tokenText.length
    while (i < n) {
        val cp = tokenText.codePointAt(i)
        if (cp !in IdParts.Medial || cp == C_DASH) {
            break
        }
        i += charCount(cp)
    }
    if (i >= n) {
        return false
    }
    val cp = tokenText.codePointAt(i)
    return cp in IdParts.Continue
}

private fun looksLikeComment(tokenText: String): Boolean = when {
    tokenText.startsWith("/*") -> true
    tokenText.startsWith("//") -> true
    tokenText.startsWith("#") -> " " in tokenText
    else -> false
}

private fun looksLikeString(tokenText: String): Boolean = when {
    tokenText.length <= 1 -> false
    tokenText[0] in probableQuotes -> tokenText[tokenText.lastIndex] == tokenText[0]
    else -> false
}

private val probableQuotes = setOf('"', '\'', '`')

private val formatStringAtoms = setOf(
    '(',
    ')',
    '{',
    '}',
    '[',
    ']',
)

private val brackets = setOf('(', ')', '[', ']')
