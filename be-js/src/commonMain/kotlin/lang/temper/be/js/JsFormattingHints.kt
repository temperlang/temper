package lang.temper.be.js

import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType

internal object JsFormattingHints : FormattingHints {
    fun getInstance() = JsFormattingHints

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean {
        if (isDot(preceding)) {
            val cNext = following.text[0]
            return cNext == '.' || cNext in '0'..'9'
        } else if (isDot(following)) {
            return preceding.type == OutputTokenType.NumericValue && '.' !in preceding.text
        }
        if (preceding.text == "function" && following.text == "*") {
            // Format generator functions like `function* f() { yield 1; }`
            return false
        }
        if (
            (preceding.text == "--" && following.text == ">") ||
            (preceding.text == "!" && following.text == "--")
        ) {
            // Prevent emitting `-->` or `<!--` as discusses in ES262 Annex B.
            return true
        }
        if (preceding.text == "}" && following.text == "[") {
            // Optional jsdoc params.
            return true
        }
        return super.spaceBetween(preceding, following)
    }

    override fun mayBreakLineBetween(preceding: OutputToken, following: OutputToken): Boolean {
        if (preceding.text == ";" || following.text == ";") {
            return true
        }
        if (
            preceding.text in noLineBreaksAfter ||
            following.text in noLineBreaksBefore
        ) {
            return false
        }
        if (
            following.text.startsWith("`") &&
            (preceding.type == OutputTokenType.Word || preceding.type == OutputTokenType.Name)
        ) {
            // TODO: do we need this rule for
            // www.ecma-international.org/ecma-262/11.0/index.html
            // #sec-left-hand-side-expressions-static-semantics-early-errors
            return false
        }
        return super.mayBreakLineBetween(preceding, following)
    }

    override fun continuesStatementAfterBlock(token: OutputToken): Boolean {
        return super.continuesStatementAfterBlock(token) ||
            // Don't insert line break before `from` after `import {...}`
            token.type == OutputTokenType.Word && token.text == "from"
    }

    override fun localLevel(token: OutputToken, plan: Boolean?): Boolean? {
        return when (token.type) {
            // Do not indent subsequent lines of jsdoc comments where '@' is followed
            // by a JSDoc annotation.
            OutputTokenType.Punctuation -> if (token.text == "@") { false } else { plan }
            else -> plan
        }
    }
}

// Derived from
// https://www.ecma-international.org/ecma-262/#sec-no-lineterminator-here-automatic-semicolon-insertion-list
private val noLineBreaksAfter =
    setOf("async", "break", "continue", "return", "throw", "yield")
private val noLineBreaksBefore = setOf("++", "--", "=>", "as", "[", "!")

private fun isDot(t: OutputToken) =
    t.type == OutputTokenType.Punctuation && (t.text == "." || t.text == "?.")
