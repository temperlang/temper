package lang.temper.be.js

import lang.temper.common.jsonEscaper

internal fun stringTokenText(value: String): String {
    var lineBreakCount = 0
    var hasTemplateStringSpecial = false
    loop@
    for (c in value) {
        if (c.isJsLineTerminatorChar) {
            lineBreakCount += 1 // modulo CRLF
        } else if (c == '\\' || c == '$' || c == '`') {
            hasTemplateStringSpecial = true
            break@loop
        }
    }
    return if (lineBreakCount >= BACKQUOTED_LF_COUNT_THRESHOLD && !hasTemplateStringSpecial) {
        "`$value`"
    } else {
        jsonEscaper.escape(value)
    }
}

internal const val BACKQUOTED_LF_COUNT_THRESHOLD = 5

internal val Char.isJsLineTerminatorChar get() = when (this) {
    '\n', '\r', '\u2028', '\u2029' -> true
    else -> false
}
