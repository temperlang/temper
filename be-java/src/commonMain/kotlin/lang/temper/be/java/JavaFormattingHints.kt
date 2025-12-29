package lang.temper.be.java

import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType

object JavaFormattingHints : FormattingHints {
    fun getInstance() = JavaFormattingHints

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean =
        if ((preceding.isPunc("->") || preceding.isPunc(">")) &&
            following.isPunc("{")
        ) {
            true
        } else if (preceding.type == OutputTokenType.NumericValue &&
            (following.isWord("L") || following.isWord("D"))
        ) {
            false
        } else {
            super.spaceBetween(preceding, following)
        }

    override val standardIndent: String
        get() = "    "
}

private fun OutputToken.isPunc(what: String) = type == OutputTokenType.Punctuation && text == what

private fun OutputToken.isWord(what: String) = type == OutputTokenType.Word && text == what
