package lang.temper.be.tmpl

import lang.temper.format.OutputTokenType
import lang.temper.lexer.isIdentifier

/**
 * Keyword operators like "instanceof" should be emitted as word tokens, not
 * punctuation tokens.
 */
fun operatorTokenType(operatorText: String): OutputTokenType =
    if (isIdentifier(operatorText)) { OutputTokenType.Word } else { OutputTokenType.Punctuation }
