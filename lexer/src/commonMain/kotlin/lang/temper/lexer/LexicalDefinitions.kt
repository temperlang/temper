package lang.temper.lexer

import lang.temper.common.C_EXCL
import lang.temper.common.C_QUEST
import lang.temper.common.UnicodeNormalForm

private const val UBYTE_MAX = 255

class LexicalDefinitions private constructor() {
    internal enum class CharKind {
        Backslash,

        /** For decimal digits.  Hex digits may be [WordStart] */
        Digit,
        Dollar,
        Dot,
        LineBreak,
        Punctuation,

        /** Like [Punctuation] but for characters that cannot start a larger punctuation token. */
        StandalonePunctuation,
        Quote,
        Semi,
        Slash,
        Space,
        WordStart,
        Error,
    }

    companion object {
        fun isWordSuffixChar(cp: Int): Boolean = cp == C_EXCL || cp == C_QUEST
        fun isLineBreak(cp: Int): Boolean = cp <= UBYTE_MAX && isLineBreak(cp.toChar())
        fun isLineBreak(c: Char): Boolean = c == '\n' || c == '\r'
        fun isSpace(cp: Int): Boolean = cp <= UBYTE_MAX && isSpace(cp.toChar())
        fun isSpace(c: Char): Boolean = c == '\t' || c == ' '

        /** Space characters that can be used to indent a line. */
        fun isIndentingSpace(c: Char): Boolean = c == '\t' || c == ' '

        internal fun classifyTokenBasedOnFirstChar(cp: Int) =
            asciiTokenStartLookupTable.getOrNull(cp) ?: if (cp in IdParts.Start) {
                CharKind.WordStart
            } else {
                CharKind.Error
            }

        /**
         * True if the text is a "normal" identifier.
         *
         * This does not check that it is valid as an unquoted identifier since any non-empty
         * back-quoted run of characters specifies an identifier.
         *
         * It simply checks that the identifier is non-empty and in NFKC.
         *
         * @see <a href="https://unicode.org/reports/tr31/#R6">TR 31</a>
         */
        fun isIdentifierNormal(text: String) =
            text.isNotEmpty() && text == UnicodeNormalForm.NFKC(text)
        // TODO: https://unicode.org/reports/tr31/#NFKC_Modifications

        /**
         * A prefix for tagged-template-string-like tokens that allow quoting names.
         *
         * https://en.wiktionary.org/wiki/-nym is an affix that is
         *
         * > used to form nouns describing types of word or name
         */
        const val quotedNamePrefix = "nym"
    }
}

private val asciiTokenStartLookupTable = ('\u0000'..'\u00FF').map { c ->
    if (c in '0'..'9') {
        LexicalDefinitions.CharKind.Digit
    } else if (c.code in IdParts.Start) {
        LexicalDefinitions.CharKind.WordStart
    } else if (c == '.') {
        LexicalDefinitions.CharKind.Dot
    } else if (c == '/') {
        LexicalDefinitions.CharKind.Slash
    } else if (c == '\\') {
        LexicalDefinitions.CharKind.Backslash
    } else if (c == ';') {
        LexicalDefinitions.CharKind.Semi
    } else if (c == '$') {
        LexicalDefinitions.CharKind.Dollar
    } else if (c == '"' || c == '\'' || c == '`') {
        LexicalDefinitions.CharKind.Quote
    } else if (LexicalDefinitions.isSpace(c)) {
        LexicalDefinitions.CharKind.Space
    } else if (LexicalDefinitions.isLineBreak(c)) {
        LexicalDefinitions.CharKind.LineBreak
    } else if (c in "(){}[],") {
        LexicalDefinitions.CharKind.StandalonePunctuation
    } else if (c in "~!@#%^&*-=+|<>?:") {
        LexicalDefinitions.CharKind.Punctuation
    } else {
        LexicalDefinitions.CharKind.Error
    }
}.toTypedArray()

/*
private val unused = run {
    val sb = StringBuilder()
    for (i in 0..255) {
        if (i != 0) {
            sb.append(if (i % 16 == 0) '\n' else ' ')
        }
        sb.append(jsonEscape(i.toChar().toString()))
        sb.append(':').append(asciiTokenStartLookupTable[i].name[0])
    }
    printErr(sb.toString())
}
*/
