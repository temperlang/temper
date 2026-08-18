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
    override fun toString(): String = debugStringForToken()
}

/** Like [TemperToken] but without the OPP flags. */
internal data class MinimalToken(
    override val pos: Position,
    override val tokenText: String,
    override val tokenType: TokenType,
) : TokenLike {
    override fun toString(): String = debugStringForToken()
}

private fun TokenLike.debugStringForToken(): String = buildString {
    append(tokenType.name.first())
    if (tokenText.isNotEmpty() && '`' !in tokenText && '\\' !in tokenText && '\n' !in tokenText) {
        append('`')
        append(tokenText)
        append('`')
    } else {
        temperEscaper.escapeTo(tokenText, this)
    }
    append(':')
    append("$pos")
}
