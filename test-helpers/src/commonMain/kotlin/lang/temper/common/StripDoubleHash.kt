package lang.temper.common

/**
 * Removes lines starting with whitespace followed by `##`.
 * This allows for commentary inside test golden strings.
 */
fun String.stripDoubleHashCommentLinesToPutCommentsInlineBelow() =
    this
        .split("\n")
        .filter {
            !it.trimStart().startsWith("##")
        }
        .joinToString("\n")
