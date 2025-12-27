package lang.temper.docbuild

internal data class CountOfBlankLines(
    val offset: Int,
    val count: Int,
)

/**
 * Examines Markdown space at the end of [cs] and returns the
 * offset before the trailing spaces and the count of line terminator
 * sequences.
 *
 * @return Pair(offsetBeforeTrailingBlank, lineTerminatorCount)
 */
internal fun countBlankLinesAtEnd(cs: CharSequence): CountOfBlankLines {
    var lineTerminatorCount = 0
    var offset = cs.length
    while (offset > 0) {
        val c = cs[offset - 1]
        if (!isMarkdownSpaceChar(c)) { break }
        offset -= 1
        if (c == '\n') {
            lineTerminatorCount += 1
            if (offset > 0 && cs[offset - 1] == '\r') {
                // \r\n counts as one line terminator
                offset -= 1
            }
        } else if (c == '\r') {
            lineTerminatorCount += 1
        }
    }
    return CountOfBlankLines(offset = offset, count = lineTerminatorCount)
}

internal fun countBlankLinesAtStart(cs: CharSequence): CountOfBlankLines {
    var count = 0
    var i = 0
    val n = cs.length
    var offset = 0
    while (i < n) {
        val c = cs[i]
        i += 1
        when (c) {
            '\n' -> {
                count += 1
                offset = i
            }
            '\r' -> {
                if (i < n && cs[i] == '\n') {
                    i += 1
                }
                offset = i
                count += 1
            }
            ' ', '\t' -> Unit
            else -> break
        }
    }
    return CountOfBlankLines(offset = offset, count = count)
}
