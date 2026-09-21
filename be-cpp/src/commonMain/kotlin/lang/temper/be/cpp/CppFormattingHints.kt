package lang.temper.be.cpp

import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType

internal class CppFormattingHints : FormattingHints {
    companion object {
        fun getInstance() = CppFormattingHints()
    }

    override fun spaceBetween(preceding: OutputToken, following: OutputToken): Boolean {
        // "::" joins words closely like "."
        // TODO: maybe roll this into the parent class.
        if (preceding == CppToks.colons && !following.text.endsWith(":")) {
            return false
        }
        if (following == CppToks.colons && !preceding.text.startsWith(":")) {
            return false
        }

        // `->` is tight binding unless it precedes a return type.
        if (preceding == CppToks.returnTypeArrow) {
            // returnTypeArrow is the same as `OutToks.rArrow` but is a distinct
            // JVM object reference, so we use `===` to check for the sentinel value.
            return preceding === CppToks.returnTypeArrow
        }
        if (following == CppToks.returnTypeArrow) {
            // returnTypeArrow is the same as `OutToks.rArrow` but is a distinct
            // JVM object reference, so we use `===` to check for the sentinel value.
            return following === CppToks.returnTypeArrow ||
                // `-` `->` should not lex as `--` `>`
                preceding.text.endsWith("-")
        }

        if (preceding.type == OutputTokenType.Word && following == CppToks.leftAngle) {
            // #include <
            return true
        }
        return super.spaceBetween(preceding, following)
    }

    override val localLevelIndents: Boolean
        get() = false
}
