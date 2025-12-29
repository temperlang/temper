@file:Suppress("MagicNumber")

package lang.temper.be.lua

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

private fun doEncodeUtf8(c: Int): String {
    val parts = when (c) {
        in 0x0..0x7F -> listOf(c)
        in 0x80..0x07FF -> listOf(
            0xC0 or (c shr 6),
            0x80 or (c and 0x3F),
        )
        in 0x800..0xFFFF -> listOf(
            0xE0 or (c shr 12),
            0x80 or ((c shr 6) and 0x3F),
            0x80 or (c and 0x3F),
        )
        in 0x10000..0x10FFFF -> listOf(
            0xF0 or (c shr 18),
            0x80 or ((c shr 12) and 0x3F),
            0x80 or ((c shr 6) and 0x3F),
            0x80 or (c and 0x3F),
        )
        else -> throw IllegalArgumentException("Decoding produced an invalid codepoint")
    }
    return parts.joinToString("") {
        "\\x${it.toHexPadded(2)}"
    }
}

private fun escapeString(str: String, escapes: Map<Int, String>): String = toStringViaBuilder(str.length) { sb ->
    for (c: Int in decodeUtf16Iter(str)) {
        sb.append(
            when (c) {
                in 0..0x1f -> escapes[c] ?: "\\x${c.toHexPadded(2)}"
                in 0x20..0x7f -> escapes[c] ?: c.toChar()
                else -> doEncodeUtf8(c)
            },
        )
    }
}

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
