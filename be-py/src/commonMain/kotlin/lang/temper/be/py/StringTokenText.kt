@file:Suppress("MagicNumber")

package lang.temper.be.py

import lang.temper.common.decodeUtf16Iter
import lang.temper.common.toHexPadded
import lang.temper.common.toStringViaBuilder

/**
 * Escapes a some text, allowing a quote character.
 */
internal fun stringTokenText(value: String): String = when {
    '\'' in value -> "\"" + escapeString(value, escapeDoubleQuotes) + "\""
    else -> "'" + escapeString(value, escapeSingleQuotes) + "'"
}

private fun escapeString(str: String, escapes: Map<Int, String>): String = toStringViaBuilder(str.length) { sb ->
    for (c: Int in decodeUtf16Iter(str)) {
        sb.append(
            when (c) {
                in 0..0x1f -> escapes[c] ?: "\\x${c.toHexPadded(2)}"
                in 0x20..0x7f -> escapes[c] ?: c.toChar()
                in 0x80..0xff -> "\\x${c.toHexPadded(2)}"
                in 0x100..0xffff -> "\\u${c.toHexPadded(4)}"
                in 0x10000..0x10ffff -> "\\U${c.toHexPadded(8)}"
                else -> throw IllegalArgumentException("Decoding produced an invalid codepoint")
            },
        )
    }
}

// Spec: https://docs.python.org/3/reference/lexical_analysis.html?highlight=escapes#string-and-bytes-literals
val generalEscapes: Map<Int, String> = mapOf(
    '\\'.code to "\\\\",
    7 to "\\a",
    8 to "\\b",
    12 to "\\f",
    '\n'.code to "\\n",
    '\r'.code to "\\r",
    '\t'.code to "\\t",
    11 to "\\v",
)

val escapeDoubleQuotes: Map<Int, String> = mapOf('"'.code to "\\\"") + generalEscapes

val escapeSingleQuotes: Map<Int, String> = mapOf('\''.code to "\\'") + generalEscapes
