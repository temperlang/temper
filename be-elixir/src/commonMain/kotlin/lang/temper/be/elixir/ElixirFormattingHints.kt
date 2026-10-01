package lang.temper.be.elixir

import lang.temper.common.TriState
import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType

/**
 * Elixir blocks are `do` ... `end`, like Blimp's and Lua's, so these hints are
 * shaped like [lang.temper.be.lua.LuaFormattingHints]: `do` opens a block and
 * indents, `end` closes one and dedents.
 *
 * Indentation is for readers only; Elixir's parser ignores it. Newlines are
 * what matter, and the one this backend depends on is after `->`: a `case`
 * clause's body runs from its arrow to the next `pattern ->` or `end`. Clause
 * bodies are not indented past their pattern, because no token marks where a
 * clause ends for the formatter to dedent on.
 */
object ElixirFormattingHints : FormattingHints {
    fun getInstance() = ElixirFormattingHints

    private val closers = setOf(")", "]", "}", ",")
    private val openers = setOf("(", "[", "{", "%{")
    private val breakBefore = setOf("def", "defp", "defmodule", "defstruct", "end", "else", "rescue", "catch")
    private val breakAfter = setOf("do", "->", "else", "rescue", "catch")

    /** Tokens after which a `(` opens an argument list rather than a grouping. */
    private val callableTypes = setOf(OutputTokenType.Name, OutputTokenType.OtherValue)

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean = when {
        // `Temper.Core.puts`, `f.(x)`
        preceding.text == "." || following.text == "." -> false
        // `not (a == b)` reads as the operator it is, not as a call to `not`
        preceding.text == "not" -> true
        // `defstruct[:x]` would be Access syntax on a call to `defstruct`
        preceding.text == "defstruct" -> true
        preceding.text == "|" || following.text == "|" -> true
        // `puts(x)`, `:erlang.abs(x)`, but not `x = (a + b) * c`
        following.text == "(" && (preceding.type in callableTypes || preceding.text == ")") -> false
        following.text == "," -> false
        preceding.text in openers -> false
        following.text in setOf(")", "]", "}") -> false
        // `x: 1` in a struct hugs the colon on the left
        following.text == ":" -> false
        // `%Point{`, `@moduledoc`, `^x`
        preceding.text in setOf("%", "@", "^") -> false
        following.text == "{" && preceding.type == OutputTokenType.Name &&
            preceding.text.firstOrNull()?.isUpperCase() == true -> false
        else -> super.spaceBetween(preceding, following)
    }

    override fun shouldBreakAfter(token: OutputToken): Boolean = token.text in breakAfter

    override fun shouldBreakBefore(token: OutputToken): Boolean = token.text in breakBefore

    override fun shouldBreakBetween(preceding: OutputToken, following: OutputToken): TriState = when {
        // An `end` closing an inline `fn ... end` argument must not push the
        // `)` or `,` after it onto a line of its own.
        preceding.text == "end" && following.text in closers -> TriState.FALSE
        preceding.text == "end" -> TriState.TRUE
        // `}` closes a tuple or a map here, never a block, so the default's
        // break after it would split `case {a, b} do` and `{1, _} ->`, and
        // Elixir does not accept `do` or `->` at the start of a line
        else -> TriState.OTHER
    }

    /** `else`, `rescue` and `catch` close one block and open the next. */
    private val between = setOf("else", "rescue", "catch")

    override fun indents(token: OutputToken): Boolean = token.text == "do" || token.text in between

    override fun dedents(token: OutputToken): Boolean = token.text == "end" || token.text in between

    override val localLevelIndents: Boolean get() = false

    override val standardIndent get() = "  "
}
