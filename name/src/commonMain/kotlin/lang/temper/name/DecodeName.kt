package lang.temper.name

import lang.temper.lexer.LexicalDefinitions
import lang.temper.lexer.unpackQuotedString

fun decodeName(text: String): ParsedName? =
    if (isQuotedName(text)) {
        val (decoded, isOk) =
            unpackQuotedString(text.substring(LexicalDefinitions.quotedNamePrefix.length))
        if (isOk && LexicalDefinitions.isIdentifierNormal(decoded)) {
            ParsedName(decoded)
        } else {
            null
        }
    } else {
        ParsedName(text)
    }

private fun isQuotedName(nameText: String): Boolean =
    nameText.length > LexicalDefinitions.quotedNamePrefix.length &&
        nameText.startsWith(LexicalDefinitions.quotedNamePrefix) &&
        nameText[LexicalDefinitions.quotedNamePrefix.length] == '`'
