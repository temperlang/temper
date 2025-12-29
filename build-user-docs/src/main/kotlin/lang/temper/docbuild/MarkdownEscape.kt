package lang.temper.docbuild

/**
 * Escape strings to Markdown substrings in various ways.
 */
object MarkdownEscape {
    /**
     * General purpose plain text string to Markdown text conversion.
     *
     * - `foo` -> `foo`
     * - `*` -> `\*`
     */
    fun escape(text: String): String {
        var str = text
        if ('\r' in str) {
            str = str.replace(crLfOrLfPattern, "\n")
        }
        return mapChars(str) { c ->
            when (c) {
                // https://spec.commonmark.org/0.30/#backslash-escapes
                // > Any ASCII punctuation character may be backslash-escaped
                // >
                // > Escaped characters are treated as regular characters and do not have their
                // > usual Markdown meanings
                '!' -> "\\!"
                '"' -> "\\\""
                '#' -> "\\#"
                '$' -> "\\$"
                '%' -> "\\%"
                '&' -> "&amp;"
                '\'' -> "\\'"
                '(' -> "\\("
                ')' -> "\\)"
                '*' -> "\\*"
                '+' -> "\\+"
                ',' -> "\\,"
                '-' -> "\\-"
                '.' -> "\\."
                '/' -> "\\/"
                ':' -> "\\:"
                ';' -> "\\;"
                '<' -> "&lt;" // mkdocs doesn't like \<
                '=' -> "\\="
                '>' -> "&gt;"
                '?' -> "\\?"
                '@' -> "\\@"
                '[' -> "\\["
                '\\' -> "\\\\"
                ']' -> "\\]"
                '^' -> "\\^"
                '_' -> "\\_"
                '`' -> "\\`"
                '{' -> "\\{"
                '|' -> "\\|"
                '}' -> "\\}"
                '~' -> "\\~"
                // > A backslash at the end of the line is a hard line break
                '\n' -> "\\\n"
                else -> null
            }
        }
    }

    /**
     * Some markdown that ends up as HTML attribute context needs to be escaped carefully.
     * Markdown meta-characters should not be treated as formatting, but escapes like back-slash
     * don't always translate well.
     *
     * https://spec.commonmark.org/0.30/#entity-and-numeric-character-references
     * > Valid HTML entity references and numeric character references can be used in place of the
     * > corresponding Unicode character, with the following exceptions:
     * >
     * > Entity and character references are not recognized in code blocks and code spans.
     * >
     * > Entity and character references cannot stand in place of special characters that define
     * > structural elements in CommonMark. For example, although `&#42;` can be used in place of a
     * > literal `*` character, `&#42;` cannot replace `*` in emphasis delimiters, bullet list
     * > markers, or thematic breaks.
     */
    fun htmlCompatibleEscape(s: String): String =
        mapChars(s) { c ->
            when (c) {
                // https://spec.commonmark.org/0.30/#backslash-escapes
                // > Any ASCII punctuation character may be backslash-escaped
                // >
                // > Escaped characters are treated as regular characters and do not have their
                // > usual Markdown meanings
                '!' -> "&#33;"
                '"' -> "&#34;"
                '#' -> "&#35;"
                '$' -> "&#36;"
                '%' -> "&#37;"
                '&' -> "&amp;"
                '\'' -> "&#92;"
                '(' -> "&#40;"
                ')' -> "&#41;"
                '*' -> "&#42;"
                '+' -> "&#43;"
                ',' -> "&#44;"
                '-' -> "&#45;"
                '.' -> "&#46;"
                '/' -> "&#47;"
                ':' -> "&#58;"
                ';' -> "&#59;"
                '<' -> "&lt;"
                '=' -> "&#61;"
                '>' -> "&gt;"
                '?' -> "&#63;"
                '@' -> "&#64;"
                '[' -> "&#91;"
                '\\' -> "&#92;"
                ']' -> "&#93;"
                '^' -> "&#94;"
                '_' -> "&#95;"
                '`' -> "&#96;"
                '{' -> "&#123;"
                '|' -> "&#124;"
                '}' -> "&#125;"
                '~' -> "&#126;"
                else -> null
            }
        }

    /**
     * Surrounds in backticks to represent a chunk of code.
     * https://spec.commonmark.org/0.30/#code-span
     *
     * This does not preserve newlines in [text] because
     * > First, line endings are converted to spaces.
     */
    fun codeSpan(text: String): String {
        if ('`' !in text) {
            if (text.isEmpty()) { return "" }
            val surroundingSpace = if (
                (text.startsWith(' ') && text.endsWith(' ')) &&
                text.any { it != ' ' }
            ) {
                // > if the resulting string both begins and ends with a space character, but does not
                // > consist entirely of space characters, a single space character is removed from
                // > the front and back.
                " "
            } else {
                ""
            }
            return "`$surroundingSpace$text$surroundingSpace`"
        }
        // > A backtick string is a string of one or more backtick characters (`) that is neither
        // > preceded nor followed by a backtick.
        // >
        // > A code span begins with a backtick string and ends with a backtick string of equal
        // > length. The contents of the code span are the characters between these two backtick
        // > strings
        // Find a run of backticks large enough to contain the largest run of backticks in text.
        // Then rely on surrounding space elision above to let us separate the backtick string at
        // the front from any backticks at the beginning or end of text.
        val backtickRunLength = backticks.findAll(text).map {
            it.value.length
        }.maxOrNull() ?: 0
        val backtickString = "`".repeat(backtickRunLength + 1)
        return "$backtickString $text $backtickString"
    }
}

private val backticks = Regex("[`]+")

private inline fun mapChars(
    str: String,
    charMapper: (Char) -> String?,
): String {
    var sb: StringBuilder? = null
    var emitted = 0
    val n = str.length
    for (i in 0 until n) {
        val replacement = charMapper(str[i])
        if (replacement != null) {
            if (sb == null) { sb = StringBuilder() }
            sb
                .append(str, emitted, i)
                .append(replacement)
            emitted = i + 1
        }
    }
    return if (sb == null) {
        str
    } else {
        sb.append(str, emitted, n)
        "$sb"
    }
}
