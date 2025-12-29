package lang.temper.be.rust

import lang.temper.common.charCategory
import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import java.util.Locale

object RustFormattingHints : FormattingHints {
    fun getInstance() = RustFormattingHints

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean {
        return when {
            preceding.type == OutputTokenType.Punctuation && preceding.text == "'" -> false
            preceding.text == "." || following.text == "." -> false
            preceding.text == "::" || following.text == "::" -> false
            else -> super.spaceBetween(preceding, following)
        }
    }

    override val standardIndent get() = "    "
}

internal fun stringTokenText(value: String) = buildString {
    append('"')
    codes@ for (code in value.codePoints()) {
        @Suppress("MagicNumber") // ascii code ranges
        when {
            code < 0x80 -> when (code) {
                '\\'.code, '"'.code -> append("""\${code.toChar()}""")
                in 0x20..0x7E -> appendCodePoint(code)
                else -> append("""\x${String.format(Locale.US, "%02x", code)}""")
            }

            else -> when (code.charCategory) {
                // TODO Are these safe? See more and/or potentially combine with `temperEscape`.
                CharCategory.LOWERCASE_LETTER, CharCategory.MODIFIER_LETTER, CharCategory.UPPERCASE_LETTER,
                CharCategory.TITLECASE_LETTER, CharCategory.OTHER_LETTER, CharCategory.DECIMAL_DIGIT_NUMBER,
                ->
                    appendCodePoint(code)

                else -> append("""\u{${code.toString(16)}}""")
            }
        }
    }
    append('"')
}
