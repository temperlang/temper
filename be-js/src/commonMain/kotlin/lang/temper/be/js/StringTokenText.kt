package lang.temper.be.js

import lang.temper.common.C_BQ
import lang.temper.common.C_BS
import lang.temper.common.C_CR
import lang.temper.common.C_DOL
import lang.temper.common.C_DQ
import lang.temper.common.C_LF
import lang.temper.common.C_SLASH
import lang.temper.common.C_SQ
import lang.temper.common.C_TAB

private const val CRLF_MASK = (1L shl C_CR) or (1L shl C_LF)

@Suppress("MagicNumber") // Lots of bit twiddling
internal fun stringTokenText(value: String): String {
    val n = value.length
    var lowUsedMask = 0L
    var highUsedMask = 0L
    for (c in value) {
        when (val code = c.code) {
            in 0..63 -> lowUsedMask = lowUsedMask or (1L shl code)
            in 64..127 -> highUsedMask = highUsedMask or (1L shl (code - 64))
        }
    }

    val hasSingleQuote = (lowUsedMask and (1L shl C_SQ)) != 0L
    val hasDoubleQuote = (lowUsedMask and (1L shl C_DQ)) != 0L
    val hasBackSlash = (lowUsedMask and (1L shl C_BS)) != 0L
    val controls = (lowUsedMask and (0xFFFF_FFFFL))
    val hasBacktick = (highUsedMask and (1L shl (C_BQ - 64))) != 0L
    val hasDollar = (lowUsedMask and (1L shl C_DOL)) != 0L
    val hasTemplateStringSpecial = hasBacktick or hasDollar or hasBackSlash

    if (!hasTemplateStringSpecial && (controls and CRLF_MASK.inv()) == 0L) {
        var lineBreakCount = 0
        var i = 0
        while (i < n) {
            val c = value[i]
            i += 1
            if (c.isJsLineTerminatorChar) {
                lineBreakCount++
                if (i < n && c == '\r' && value[i] == '\n') {
                    i += 1 // Count CR LF as one terminator
                }
            }
        }
        if (lineBreakCount >= BACKQUOTED_LF_COUNT_THRESHOLD) {
            return "`$value`"
        }
    }

    val delimiter = when {
        hasSingleQuote == hasDoubleQuote -> '"'
        hasDoubleQuote -> '\''
        else -> '"'
    }

    return buildString {
        append(delimiter)
        for (i in value.indices) {
            val c = value[i]
            when (val code = c.code) {
                C_TAB -> append("\\t")
                C_LF -> append("\\n")
                C_CR -> append("\\r")
                in 0..0x1F, 0x7F -> {
                    append("\\x")
                    append(HEX[code shr 4])
                    append(HEX[code and 0b1111])
                }
                C_BS -> append("\\\\")
                // Generated JS is just better if it avoids any </script> tag risk
                C_SLASH if (i != 0 && value[i - 1] == '<') -> append("\\/")
                delimiter.code -> {
                    append("\\")
                    append(delimiter)
                }
                else -> append(c)
            }
        }
        append(delimiter)
    }
}

internal const val BACKQUOTED_LF_COUNT_THRESHOLD = 5

internal val Char.isJsLineTerminatorChar get() = when (this) {
    '\n', '\r', '\u2028', '\u2029' -> true
    else -> false
}

private const val HEX = "0123456789abcdef"
