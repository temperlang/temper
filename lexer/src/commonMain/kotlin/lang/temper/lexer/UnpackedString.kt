package lang.temper.lexer

import lang.temper.common.BITS_PER_HEX_DIGIT
import lang.temper.common.C_LOWER_A
import lang.temper.common.C_MAX_CODEPOINT
import lang.temper.common.C_MAX_SURROGATE
import lang.temper.common.C_MIN_SURROGATE
import lang.temper.common.C_UPPER_A
import lang.temper.common.C_ZERO
import lang.temper.common.N_HEX_PER_BYTE
import lang.temper.common.N_HEX_PER_UTF16
import lang.temper.common.VALUE_HEX_NUMERAL_A
import lang.temper.common.encodeUtf16
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder

data class UnpackedString(
    val decoded: String,
    val isOk: Boolean,
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("decoded", Hints.s) { value(decoded) }
        key("isOk", isDefault = isOk) { value(isOk) }
    }
}

fun unpackQuotedString(tokenText: String, skipDelimiter: Boolean = true): UnpackedString {
    var i = if (skipDelimiter) { 1 } else { 0 }
    var isOk = true
    val limit = tokenText.length - if (skipDelimiter) { 1 } else { 0 }
    if (limit < i) {
        isOk = false
    }

    val str = toStringViaBuilder { sb ->
        if (!isOk) {
            return@toStringViaBuilder
        }
        isOk = false // Until we reach the end without exiting the loop
        while (true) {
            if (i == limit) {
                // Only processing all input chars gets us out with isOk == true
                isOk = true
                break
            }
            val c = tokenText[i]
            i += 1
            if (c != '\\') {
                sb.append(c)
                continue
            }
            if (i == limit) {
                return@toStringViaBuilder
            }
            val next = tokenText[i]
            i += 1
            /**
             * <!-- snippet: syntax/EscapeSequences -->
             * # Escape Sequences
             * Escape sequences in Temper are roughly like those in C, Java, JavaScript, JSON, etc.
             *
             * ![snippet/syntax/EscapeSequence.svg]
             *
             * The following escape sequences are recognized in strings:
             *
             * | Escape                      | Meaning                                             |
             * | --------------------------- | --------------------------------------------------- |
             * | `\0`                        | Codepoint 0, NUL                                    |
             * | `\\`                        | A single backslash                                  |
             * | `\/`                        | A forward-slash                                     |
             * | `\"`                        | A double-quote                                      |
             * | `\'`                        | A single-quote                                      |
             * | `` \` ``                    | A back-quote                                        |
             * | `\{`                        | A left curly-bracket                                |
             * | `\}`                        | A right curly-bracket                               |
             * | `\$`                        | A dollar sign                                       |
             * | `\b`                        | A backspace                                         |
             * | `\t`                        | A tab                                               |
             * | `\n`                        | A line-feed a.k.a. new-line                         |
             * | `\v`                        | A vertical tab                                      |
             * | `\f`                        | A form-feed                                         |
             * | `\r`                        | A carriage-return                                   |
             * | `\u` *xxxx*                 | A four-digit hex escape sequence                    |
             * | `\u{` *xxxxx* `,` *xxx* `}` | One or more comma-separated hex code-points         |
             * | `\`                         | Broken escape                                       |
             *
             */
            fun unescapeHex(count: Int): Unit? {
                var codeUnit: Int? = null
                if (i + count <= limit) {
                    codeUnit = decodeHex(tokenText, i, i + count)
                }
                if (codeUnit == null || codeUnit in C_MIN_SURROGATE..C_MAX_SURROGATE) {
                    return null
                }
                sb.append(codeUnit.toChar())
                i += count
                return Unit
            }
            when (next) {
                '\\', '"', '\'', '`', '$', '{', '}', '/' -> sb.append(next)
                '0' -> sb.append('\u0000')
                'b' -> sb.append('\b')
                't' -> sb.append('\t')
                'n' -> sb.append('\n')
                'v' -> sb.append('\u000b')
                'f' -> sb.append('\u000c')
                'r' -> sb.append('\r')
                'u' -> {
                    if (i < limit && tokenText[i] == '{') {
                        // \u{xx,xxx,xxxx,...}
                        // allows multiple hex codepoints separated by commas.
                        val end = decodeHexCommaRun(tokenText, i + 1, limit, sb)
                        if (end in 0 until limit && tokenText[end] == '}') {
                            i = end + 1
                        } else {
                            return@toStringViaBuilder
                        }
                    } else {
                        // Allows expression of basic plane or surrogate pairs.
                        unescapeHex(N_HEX_PER_UTF16) ?: return@toStringViaBuilder
                    }
                }
                'x' -> {
                    // Allows expression through latin-1, not general utf8.
                    unescapeHex(N_HEX_PER_BYTE) ?: return@toStringViaBuilder
                }
                else -> {
                    return@toStringViaBuilder
                }
            }
        }
    }

    return UnpackedString(
        str,
        isOk = isOk,
    )
}

fun decodeHex(s: String, offset: Int, limit: Int): Int? {
    var n = 0
    for (i in offset until limit) {
        val digitValue = when (val x = decodeHexDigit(s[i])) {
            -1 -> return null
            else -> x
        }
        n = (n shl BITS_PER_HEX_DIGIT) or digitValue
    }
    return n
}

/** @return hex digit value or -1 for bad digit. */
internal fun decodeHexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c.code - C_ZERO
    in 'A'..'F' -> c.code + (VALUE_HEX_NUMERAL_A - C_UPPER_A)
    in 'a'..'f' -> c.code + (VALUE_HEX_NUMERAL_A - C_LOWER_A)
    else -> -1
}

private fun decodeHexCommaRun(s: String, offset: Int, limit: Int, sb: StringBuilder): Int {
    var i = offset
    var cp = 0
    var sawDigit = false

    loop@
    while (i < limit) {
        val c = s[i]
        i += 1
        val digitValue = when (c) {
            ',' -> {
                if (!sawDigit) {
                    return -1
                }
                encodeUtf16(cp, sb)
                cp = 0
                sawDigit = false
                continue@loop
            }

            in '0'..'9' -> c.code - C_ZERO
            in 'A'..'F' -> c.code + (VALUE_HEX_NUMERAL_A - C_UPPER_A)
            in 'a'..'f' -> c.code + (VALUE_HEX_NUMERAL_A - C_LOWER_A)

            else -> {
                if (sawDigit) {
                    encodeUtf16(cp, sb)
                }
                // If not, zero codepoints and trailing commas are ok
                return i - 1
            }
        }
        cp = (cp shl BITS_PER_HEX_DIGIT) or digitValue
        sawDigit = true
        if (cp > C_MAX_CODEPOINT || cp in C_MIN_SURROGATE..C_MAX_SURROGATE) {
            return -1
        }
    }
    return i
}
