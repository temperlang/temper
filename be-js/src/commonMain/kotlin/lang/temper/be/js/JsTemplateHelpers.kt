package lang.temper.be.js

import lang.temper.be.BaseOutTree
import lang.temper.common.LeftOrRight
import lang.temper.common.unquotedJsonEscaper
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.log.Position

internal object JsTemplateHelpers {
    fun renderTemplateTo(
        tokenSink: TokenSink,
        pos: Position,
        quasis: List<Js.TemplateElement>,
        holeExpressions: List<Js.Expression>,
    ) {
        val lastQuasiIndex = quasis.lastIndex
        require(holeExpressions.size == lastQuasiIndex)

        tokenSink.position(pos.leftEdge, LeftOrRight.Left)
        var i = 0
        while (i <= lastQuasiIndex) {
            val quasi = quasis[i]
            // The first quasi starts with a back-tick, and the last ends with one.
            // Otherwise, they start with `}` / end with `${`
            val prefix = if (i == 0) "`" else "}"
            val suffix = if (i == lastQuasiIndex) "`" else "\${"
            val holeExpression = holeExpressions.getOrNull(i)
            i += 1

            tokenSink.position(quasi.pos.leftEdge, LeftOrRight.Left)
            tokenSink.emit(
                OutputToken(
                    "$prefix${quasi.raw}$suffix",
                    OutputTokenType.QuotedValue,
                ),
            )
            tokenSink.position(quasi.pos.rightEdge, LeftOrRight.Right)
            if (holeExpression != null) {
                (holeExpression as BaseOutTree<*>).formatTo(tokenSink)
            }
        }
        tokenSink.position(pos.rightEdge, LeftOrRight.Right)
    }

    /**
     * Given some raw template chunk text,
     * returns a chunk that passes [checkAllowedTemplateElementText]
     * with the same semantics in an untagged template literal.
     *
     * This assumes the template string is un-tagged, so we may use escape
     * sequences to prevent disallowed sequences.
     */
    fun untaggedTemplateText(quasiText: String) = unquotedJsonEscaper.escape(quasiText)

    /**
     * This rejects an input with sequences like
     *
     * - <even number of backslash> `${` which starts a template hole
     * - <even number of backslash> <back-tick> which ends the template string
     * - <odd number of backslash> <end of chunk> which would escape the character that closes the chunk
     * - non-standard line terminators which might be interpreted differently when the containing file
     *   is converted between LF and CRLF newlines.
     *
     * @return null if ok
     */
    fun checkAllowedTemplateElementText(
        rawQuasiText: String,
    ): String? {
        var i = 0
        val n = rawQuasiText.length
        var nSlashes = 0
        while (i < n) {
            val c = rawQuasiText[i]
            i += 1
            if (c == '\\') {
                nSlashes += 1
            } else {
                if (c == '\u2028' || c == '\u2029' || c == '\r' && rawQuasiText.getOrNull(i) != '\n') {
                    return "non-standard line-terminator"
                }
                if ((nSlashes and 1) == 0) {
                    if (c == '$' && rawQuasiText.getOrNull(i) == '{') {
                        return "un-escaped `\${`"
                    } else if (c == '`') {
                        return "un-escaped back-tick"
                    }
                }
                nSlashes = 0
            }
        }
        return if ((nSlashes and 1) == 0) {
            null
        } else {
            "escape at end of chunk"
        }
    }
}
