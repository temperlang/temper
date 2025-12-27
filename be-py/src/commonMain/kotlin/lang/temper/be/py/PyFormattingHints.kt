package lang.temper.be.py

import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType.Punctuation
import lang.temper.format.OutputTokenType.Word
import lang.temper.format.SpecialTokens
import lang.temper.format.TokenAssociation

internal object PyFormattingHints : FormattingHints {
    fun getInstance() = PyFormattingHints

    override val allowSingleLine: Boolean = false
    override val localLevelIndents: Boolean = false
    override val standardIndent get() = "    "

    override fun indents(token: OutputToken): Boolean = token == SpecialTokens.indent

    override fun dedents(token: OutputToken): Boolean = token == SpecialTokens.dedent

    override fun shouldBreakAfter(token: OutputToken): Boolean {
        return indents(token) || dedents(token)
    }

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean = when {
        isStarPrefix(preceding) -> false
        isFrom(preceding) && isDots(following) -> true
        isDots(preceding) && isDots(following) -> false
        else -> super.spaceBetween(preceding, following)
    }
}

private fun isStarPrefix(token: OutputToken) =
    token.type == Punctuation && (token.text == "*" || token.text == "**") &&
        token.association == TokenAssociation.Prefix
private fun isFrom(token: OutputToken) = token.type == Word && token.text == "from"
private fun isDots(token: OutputToken) = token.type == Punctuation && token.text == "."

/**
 * https://docs.python.org/3/reference/lexical_analysis.html#physical-lines says
 *
 * > A physical line is a sequence of characters terminated by an end-of-line sequence.
 * > In source files and strings, any of the standard platform line termination sequences
 * > can be used - the Unix form using ASCII LF (linefeed), the Windows form using the
 * > ASCII sequence CR LF (return followed by linefeed), or the old Macintosh form using
 * > the ASCII CR (return) character.
 */
internal val pyLineTerminator = Regex("""[\n\r]""")
