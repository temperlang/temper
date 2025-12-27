package lang.temper.be.tmpl

/**
 * Escapes select characters to prevent CR, LF, and "<code>*\/</code>" sequences from prematurely
 * ending comments.
 *
 * @param wholeCommentText the whole comment text.  This function looks back so applying it to
 *     adjacent substrings separately may produce a different result than applying to the whole.
 */
internal fun commentSafe(wholeCommentText: String): String {
    val n = wholeCommentText.length
    var sb: StringBuilder? = null
    var appended = 0
    for (i in wholeCommentText.indices) {
        val replacement = when (wholeCommentText[i]) {
            '\n' -> "%0A"
            '\r' -> "%0D"
            '*' -> if (i + 1 < n && wholeCommentText[i + 1] == '/') { "%2A" } else { continue }
            else -> continue
        }
        if (sb == null) {
            sb = StringBuilder()
            sb.ensureCapacity(n)
        }
        sb.append(wholeCommentText, appended, i).append(replacement)
        appended = i + 1
    }
    return sb?.append(wholeCommentText, appended, n)?.toString() ?: wholeCommentText
}
