package lang.temper.lexer

import lang.temper.common.commonPrefixBy
import lang.temper.common.compatRemoveFirst
import lang.temper.common.compatRemoveLast

/**
 * Strips non-comment context from slash-star comments.
 *
 * This recognizes several comment styles
 *
 * ```
 * /** Single line **/
 * /* Single line */
 * /********* Single line asterisked to occupy width *********/
 *
 * /**
 *  * Asterisks lined up at edge.
 *  * Second line
 *  *
 *  *     Indented
 *  *     Indented
 *  *
 *  * Lorem ipsum
 *  */
 *
 * /** Like the last but
 *  ** with heavier star borders
 *  **/
 *
 * /**************************
 *  ** Some people like to  **
 *  ** put content in boxes **
 *  ** made of stars.       **
 *  ** OMG Reverse 2001.    **
 *  **************************/
 *
 * /*
 *    Lines other than first indented
 *    by minimal common space prefix.
 *      Indented
 *    Unindented
 *  */
 *
 * /************************/
 * // Tha line above is a horizontal rule with no content.
 * ```
 */
fun blockCommentContent(text: String): String = unwrapCommentContent(
    text,
    firstLineDelimiter = slashStarFirstLineDelimiter,
    lastLineDelimiter = slashStarLastLineDelimiter,
    allowedCommonLinePrefix = slashStarAllowedCommonLinePrefix,
    allowedCommonLineSuffix = slashStarAllowedCommonLineSuffix,
)

internal fun unwrapCommentContent(
    text: String,
    firstLineDelimiter: Regex,
    lastLineDelimiter: Regex,
    allowedCommonLinePrefix: Regex,
    allowedCommonLineSuffix: Regex,
): String {
    val lines = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split("\n")
        .toMutableList()
    // Remove /*
    lines[0] = firstLineDelimiter.replace(lines[0], "")
    lines[lines.lastIndex] = lastLineDelimiter.replace(lines[lines.lastIndex], "")
    return if (lines.size == 1) {
        // No common prefix to remove.
        // Special case this to avoid confusion between first line being same as last line
        lines[0].trimEnd(::ignorableSpace)
    } else {
        // Strip common prefix of spaces and stars
        val commonPrefix = commonPrefixBy(
            lines.subList(1, lines.lastIndex).filter {
                // Space indented blocks may have completely blank lines
                it.any { c -> !ignorableSpace(c) }
            },
        ) {
            it.asIterable()
        }.joinToString("")

        // Limit the common prefix to just spaces and some stars and strip it
        val prefixToRemove = allowedCommonLinePrefix.find(commonPrefix)!!.value
        if (prefixToRemove.isNotEmpty()) {
            for (i in 1 until lines.size) {
                lines[i] = lines[i].removePrefix(prefixToRemove)
            }
        }
        if (prefixToRemove.endsWith(' ')) {
            if (lines[0].startsWith(' ')) {
                lines[0] = lines[0].substring(1)
            }
        } else if (
            // If all lines are either blank or start with a space, strip that space.
            (1 until lines.size).all { i ->
                val line = lines[i]
                line.isBlank() || line.startsWith(' ')
            }
        ) {
            lines.forEachIndexed { i, line ->
                lines[i] = line.removePrefix(" ")
            }
        }

        // Drop invisible white-space from end of lines.
        lines.forEachIndexed { index, s ->
            lines[index] = s.trimEnd(::ignorableSpace)
        }

        // If there's a common star suffix, drop it.
        // See the reverse-2001 box in the comment above.
        val mayHaveSuffix = 1 until lines.lastIndex
        val commonSuffix = commonSuffix(mayHaveSuffix.map { lines[it] })
        val commonSuffixToRemove = allowedCommonLineSuffix.find(commonSuffix)!!.value
        if (commonSuffixToRemove.isNotEmpty()) {
            // We find the common suffix by looking at lines 1 until second-to-last
            // but strip it from the zero-th as well so that we don't limit the suffix
            // based on first-line delimiter stripping.
            for (i in 0 until lines.lastIndex) {
                lines[i] = lines[i].removeSuffix(commonSuffixToRemove)
            }

            // Drop invisible white-space from end of lines again now that we've
            // removed any star runs.
            lines.forEachIndexed { index, s ->
                lines[index] = s.trimEnd(::ignorableSpace)
            }
        }

        // Drop leading blank lines and trailing blank lines.
        while (lines.firstOrNull()?.isEmpty() == true) {
            lines.compatRemoveFirst()
        }
        while (lines.lastOrNull()?.isEmpty() == true) {
            lines.compatRemoveLast()
        }

        // Rejoin lines into a content string
        lines.joinToString("\n")
    }
}

// IF EDITS ARE NEEDED TO THESE, PLEASE ALSO SEE THE CORRESPONDING PATTERNS
// IN `lineCommentContent.kt`.
private val slashStarFirstLineDelimiter = Regex("""^/[*]+ ?""")

// We match [*]* here because if the first line delimiter merges into the last
// line delimiter we might be left with only a '/'
private val slashStarLastLineDelimiter = Regex("""[*]*/$""")
private val slashStarAllowedCommonLinePrefix = Regex("""^[ \t]*(?:[*]+ ?)?""")
private val slashStarAllowedCommonLineSuffix = Regex("""[*]*$""")

private fun commonSuffix(strings: Iterable<String>): String {
    val stringIterator = strings.iterator()
    if (!stringIterator.hasNext()) { return "" }
    var prefix: String = stringIterator.next()
    while (stringIterator.hasNext() && prefix.isNotEmpty()) {
        val str = stringIterator.next()
        var i = prefix.lastIndex
        var j = str.lastIndex
        while (i >= 0) {
            if (j < 0) {
                prefix = prefix.substring(i + 1)
                break
            }
            if (prefix[i] != str[j]) {
                prefix = prefix.substring(i + 1)
                break
            }
            i -= 1
            j -= 1
        }
    }
    if (prefix.isNotEmpty() && prefix.first() in '\uDC00'..'\uDFFF') {
        prefix = prefix.substring(1)
    }
    return prefix
}

private fun ignorableSpace(c: Char) = c == ' ' || c == '\t'
