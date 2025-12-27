package lang.temper.be.csharp

import lang.temper.common.TriState
import lang.temper.format.FormattingHints
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType

object CSharpFormattingHints : FormattingHints {
    fun getInstance() = CSharpFormattingHints

    override fun localLevel(token: OutputToken, plan: Boolean?): Boolean? {
        return when (token.text) {
            "{" -> false
            else -> plan
        }
    }

    override fun shouldBreakBefore(token: OutputToken): Boolean {
        return when (token.text) {
            "{" -> true
            else -> super.shouldBreakBefore(token)
        }
    }

    override fun shouldBreakBetween(preceding: OutputToken, following: OutputToken): TriState {
        if (preceding.type == OutputTokenType.Punctuation) {
            if (preceding.text == "}") {
                // C# typically breaks before these, but not before `while` of do/while.
                when (following) {
                    OutToks.catchWord, OutToks.elseWord, OutToks.finallyWord -> return TriState.TRUE
                    else -> {}
                }
            }
        }
        return super.shouldBreakBetween(preceding, following)
    }

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean {
        return when {
            preceding.isNamespacer() || following.isNamespacer() -> false
            else -> super.spaceBetween(preceding, following)
        }
    }

    override val standardIndent get() = "    "
}

private fun OutputToken.isNamespacer() = type == OutputTokenType.Punctuation && text == "::"
