package lang.temper.be.lua

import lang.temper.common.TriState
import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken

internal class LuaFormattingHints : FormattingHints {
    companion object {
        fun getInstance() = LuaFormattingHints()
    }

    private val noSpace = Regex("^(:|;|\\.|::)$")
    private var expectingParams = false

    override fun tokenProcessed(token: OutputToken) {
        if (expectingParams && token.text == ")") {
            expectingParams = false
        } else if (token.text == "function") {
            expectingParams = true
        }
    }

    override fun spaceBetween(
        preceding: OutputToken,
        following: OutputToken,
    ): Boolean {
        if (following.text == ",") {
            return false
        }
        if (preceding.text == "function" && following.text == "(") {
            return false
        }
        if (noSpace.matches(preceding.text) || noSpace.matches(following.text)) {
            return false
        }
        return super.spaceBetween(preceding, following)
    }

    override fun shouldBreakAfter(
        token: OutputToken,
    ): Boolean {
        if (expectingParams && token.text == ")") {
            // No default args in Lua, so the only close paren after function keyword should be params end.
            expectingParams = false
            return true
        }
        return token.text == "do" || token.text == "then" ||
            token.text == "else" || token.text == ";"
    }

    override fun shouldBreakBefore(token: OutputToken): Boolean {
        return token.text == "local" || token.text == "if"
    }

    override fun shouldBreakBetween(preceding: OutputToken, following: OutputToken): TriState {
        if (preceding.text == "end") {
            return when (following.text) {
                ";", ")" -> TriState.FALSE
                else -> TriState.TRUE
            }
        }
        return super.shouldBreakBetween(preceding, following)
    }

    override fun indents(token: OutputToken): Boolean {
        return when (token.text) {
            "do", "else", "function", "then" -> true
            else -> false
        }
    }

    override fun dedents(token: OutputToken): Boolean {
        return when (token.text) {
            "else", "elseif", "end" -> true
            else -> false
        }
    }

    override val localLevelIndents: Boolean
        get() = false
}
