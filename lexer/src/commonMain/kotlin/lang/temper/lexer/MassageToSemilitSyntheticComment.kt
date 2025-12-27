package lang.temper.lexer

import lang.temper.common.commonPrefixLength
import lang.temper.common.min

const val MASSAGED_SEMILIT_COMMENT_START = "/**?"
const val MASSAGED_SEMILIT_COMMENT_END = "?*/"
const val DOC_COMMENT_BLANK_LINE_PREFIX = " *"
const val DOC_COMMENT_LINE_PREFIX = " * "

internal fun massageToSemilitSyntheticComment(paragraphText: String): String? {
    val lines = paragraphText.lines()
    val nonBlankLines = lines.filter { it.isNotBlank() }
    if (nonBlankLines.isEmpty()) { return null }
    // Strip off leading space
    val firstNonBlank = nonBlankLines.first()
    var commonSpacePrefixLength = firstNonBlank.indexOfFirst { it !in " \t" }
    if (commonSpacePrefixLength <= 0) {
        commonSpacePrefixLength = 0
    } else {
        for (i in 1..nonBlankLines.lastIndex) {
            commonSpacePrefixLength = min(commonSpacePrefixLength, commonPrefixLength(firstNonBlank, nonBlankLines[i]))
        }
    }
    // Skip any blank lines at the beginning or end
    var start = 0
    var end = lines.size
    while (start < end && lines[start].isBlank()) {
        start += 1
    }
    while (end > start && lines[end - 1].isBlank()) {
        end -= 1
    }
    // Strip prefix off lines to find final line representation.
    val adjustedLines = (start until end).map { i ->
        var adjustedLine = lines[i].trimEnd()
        if (adjustedLine.length >= commonSpacePrefixLength) {
            adjustedLine = adjustedLine.substring(commonSpacePrefixLength)
        }
        adjustedLine
    }
    return buildString {
        append("$MASSAGED_SEMILIT_COMMENT_START\n")
        for (line in adjustedLines) {
            if (line.isEmpty()) {
                append(DOC_COMMENT_BLANK_LINE_PREFIX)
            } else {
                append(DOC_COMMENT_LINE_PREFIX)
                append(line)
            }
            append("\n")
        }
        append(MASSAGED_SEMILIT_COMMENT_END)
    }
}

/** Reverses [massageToSemilitSyntheticComment] */
fun unMassagedSemilitParagraphContent(token: TemperToken): String? =
    if (token.synthetic && token.tokenType == TokenType.Comment) {
        unMassagedSemilitParagraphContent(token.tokenText)
    } else {
        null
    }

fun unMassagedSemilitParagraphContent(tokenText: String): String? {
    val tokenTextLines = tokenText.lines()
    if (tokenTextLines.size > 2 &&
        tokenTextLines.first() == MASSAGED_SEMILIT_COMMENT_START &&
        tokenTextLines.last() == MASSAGED_SEMILIT_COMMENT_END
    ) {
        return tokenTextLines.subList(1, tokenTextLines.lastIndex).joinToString("\n") {
            when {
                it == DOC_COMMENT_BLANK_LINE_PREFIX -> ""
                it.startsWith(DOC_COMMENT_LINE_PREFIX) -> it.substring(DOC_COMMENT_LINE_PREFIX.length)
                else -> it
            }
        }
    }
    return null
}
