package lang.temper.be.cpp

import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType

internal class CppFormattingHints : FormattingHints {
    companion object {
        fun getInstance() = CppFormattingHints()
    }

    val names = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
    // TODO private val noSpace = Regex("^(:|;|\\.|::)$")

    override fun spaceBetween(
        preceding: OutputToken,
        following: OutputToken,
    ): Boolean {
        if (following.text == "{") {
            return true
        }
        if (preceding.type == OutputTokenType.Word || preceding.text == ">") {
            if (following.type == OutputTokenType.Word || following.type == OutputTokenType.QuotedValue) {
                return true
            }
        }
        if (preceding.type == OutputTokenType.QuotedValue && following.type == OutputTokenType.Word) {
            // Maybe use custom tokens for `R"..."` and `"..."_s` style strings.
            // We currently also generate number literals as quoted values.
            return true
        }
        if (preceding.text == "#") {
            return false
        }
        if (preceding.text == "include" && following.text == "<") {
            return true
        }
        if (isOperator(preceding) || (isOperator(following) && following.text != ",")) {
            return true
        }
        return false
    }

    override fun shouldBreakAfter(
        token: OutputToken,
    ): Boolean {
        return token.text == "{" || token.text == "}" || token.text == ";"
    }

    override fun shouldBreakBefore(token: OutputToken): Boolean {
        return token.text == "}"
    }

    override fun indents(token: OutputToken): Boolean {
        return when (token.text) {
            "{" -> true
            else -> false
        }
    }

    override fun dedents(token: OutputToken): Boolean {
        return when (token.text) {
            "}" -> true
            else -> false
        }
    }

    override val localLevelIndents: Boolean
        get() = false
}

internal val wrapping = setOf("(", ")", "[", "]", "{", "}", "<", ">")
internal val separators = setOf("::", ".", "->")

internal fun isOperator(token: OutputToken): Boolean {
    return token.type == OutputTokenType.Punctuation &&
        !wrapping.contains(token.text) &&
        !separators.contains(token.text) &&
        token.text != ";"
}
