package lang.temper.common

/**
 * Modifies [buffer] in place to ensure that there are no
 * lines that end in [range] that also have trailing spaces that
 * are also in [range].
 */
fun removeTrailingSpaceFromLinesIn(
    buffer: StringBuilder,
    range: IntRange,
    isLineTerminatorChar: (Char) -> Boolean = { it == '\r' || it == '\n' },
    isSpaceChar: (Char) -> Boolean = { it == '\t' || it == ' ' },
) {
    var i = range.first
    var limit = range.last + 1
    while (i < limit) {
        if (isLineTerminatorChar(buffer[i])) {
            val deleteLimit = i
            while (i > range.first) {
                if (isSpaceChar(buffer[i - 1])) {
                    i -= 1
                    limit -= 1
                } else {
                    break
                }
            }
            buffer.deleteRange(i, deleteLimit)
        }
        i += 1
    }
}
