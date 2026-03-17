package lang.temper.lexer

import lang.temper.common.temperEscaper
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.Positioned

sealed interface TokenLike : Positioned {
    val tokenText: String
    val tokenType: TokenType
}

data class TemperToken(
    override val pos: Position,
    override val tokenText: String,
    override val tokenType: TokenType,
    val mayBracket: Boolean,
    val synthetic: Boolean = false,
    /** If [tokenType] is [TokenType.Error], the kind of error. */
    val error: MessageTemplateI? = null,
) : TokenLike {
    override fun toString(): String = debugStringForToken(pos, tokenText)
}

/** Like [TemperToken] but without the OPP flags. */
internal data class MinimalToken(
    override val pos: Position,
    override val tokenText: String,
    override val tokenType: TokenType,
) : TokenLike {
    override fun toString(): String = debugStringForToken(pos, tokenText)
}

private fun debugStringForToken(pos: Position, tokenText: String): String =
    "${
        if (tokenText.isNotEmpty() && '`' !in tokenText && '\\' !in tokenText) {
            "`$tokenText`"
        } else {
            temperEscaper.escape(tokenText)
        }
    }:$pos"
