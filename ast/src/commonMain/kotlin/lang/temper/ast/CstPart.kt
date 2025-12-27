package lang.temper.ast

import lang.temper.common.jsonEscaper
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.Operator
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.Position
import lang.temper.log.Positioned

sealed class CstPart : Structured, Positioned

class CstToken(
    val token: TemperToken,
) : CstPart() {
    override val pos: Position get() = token.pos
    val tokenType: TokenType get() = token.tokenType
    val tokenText: String get() = token.tokenText
    val synthetic: Boolean get() = token.synthetic

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("tokenType", Hints.n) {
            value(tokenType.name)
        }
        key("tokenText", Hints.ns) {
            value(tokenText)
        }
        pos.positionPropertiesTo(this, Hints.u)
        key("pseudoTokenType", Hints.u) { value("CstToken") }
        key("synthetic", isDefault = !synthetic) { value(synthetic) }
    }

    override fun toString() = toStringViaBuilder {
        jsonEscaper.escapeTo(tokenText, it)
        it.append(": ")
        it.append(tokenType.name)
        it.append(" @ ")
        it.append(pos)
        if (synthetic) {
            it.append(" /*synthetic*/")
        }
    }

    operator fun component1() = tokenText
    operator fun component2() = tokenType

    override fun hashCode(): Int = token.hashCode()

    override fun equals(other: Any?): Boolean =
        other is CstToken && this.token == other.token
}

class LeftParenthesis(
    val operator: Operator,
    override val pos: Position,
) : CstPart(), Positioned {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("abbrev", Hints.su) {
            value(this@LeftParenthesis.toString())
        }
        key("pseudoTokenType") { value("LeftParenthesis") }
        pos.positionPropertiesTo(this, Hints.u)
    }

    override fun toString() = "(${ operator.name }"
}

class RightParenthesis(
    val operator: Operator,
    override val pos: Position,
) : CstPart(), Positioned {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("abbrev", Hints.su) {
            value(this@RightParenthesis.toString())
        }
        key("operator") { value(operator as Structured) }
        key("pseudoTokenType") { value("RightParenthesis") }
        pos.positionPropertiesTo(this, Hints.u)
    }

    override fun toString() = "${ operator.name })"
}
