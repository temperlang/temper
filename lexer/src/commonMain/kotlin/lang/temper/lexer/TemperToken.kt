package lang.temper.lexer

import lang.temper.common.jsonEscaper
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.Positioned

data class TemperToken(
    override val pos: Position,
    val tokenText: String,
    val tokenType: TokenType,
    val mayBracket: Boolean,
    val synthetic: Boolean = false,
    /** If [tokenType] is [TokenType.Error], the kind of error. */
    val error: MessageTemplateI? = null,
) : Positioned {
    override fun toString(): String =
        "${
            if (tokenText.isNotEmpty() && '`' !in tokenText && '\\' !in tokenText) {
                "`$tokenText`"
            } else {
                jsonEscaper.escape(tokenText)
            }
        }:$pos"
}
