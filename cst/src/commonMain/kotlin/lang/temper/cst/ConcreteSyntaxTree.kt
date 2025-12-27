package lang.temper.cst

import lang.temper.common.LeftOrRight
import lang.temper.common.jsonEscaper
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.lexer.CommentType
import lang.temper.lexer.Operator
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.lexer.blockCommentContent
import lang.temper.lexer.lineCommentContent
import lang.temper.lexer.unMassagedSemilitParagraphContent
import lang.temper.log.Position
import lang.temper.log.Positioned

sealed class ConcreteSyntaxTree : OperatorStackElement, Structured, Positioned, TokenSerializable {
    abstract val operands: List<ConcreteSyntaxTree>

    override val childCount: Int get() = operands.size
    override fun child(i: Int) = operands[i]
}

data class CstInner(
    override val pos: Position,
    override val operator: Operator,
    override val operands: List<ConcreteSyntaxTree>,
) : ConcreteSyntaxTree(), InnerOperatorStackElement {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("operator") {
            value(operator as Structured)
        }
        pos.positionPropertiesTo(this, Hints.u)
        key("operands", Hints.s) {
            arr {
                operands.forEach { value(it) }
            }
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.position(pos, LeftOrRight.Left)
        if (operator == Operator.Leaf) {
            tokenSink.emit(OutToks.leftSquare)
            operands.forEachIndexed { index, operand ->
                if (index != 0) {
                    tokenSink.emit(OutToks.comma)
                }
                operand.renderTo(tokenSink)
            }
            tokenSink.emit(OutToks.rightSquare)
        } else {
            tokenSink.emit(OutToks.leftCurly)
            tokenSink.emit(OutputToken("/*${operator.name}*/", OutputTokenType.Comment))
            tokenSink.endLine()
            operands.forEachIndexed { index, operand ->
                if (index != 0) {
                    tokenSink.emit(OutToks.semi)
                    tokenSink.endLine()
                }
                operand.renderTo(tokenSink)
            }
            tokenSink.emit(OutToks.rightCurly)
        }
        tokenSink.position(pos, LeftOrRight.Right)
    }
}

data class CstLeaf(
    val temperToken: TemperToken,
) : ConcreteSyntaxTree() {
    override val operands get() = emptyList<ConcreteSyntaxTree>()
    override val operator get() = Operator.Leaf

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("text", Hints.s) { value(tokenText) }
        key("type", Hints.u) { value(temperToken.tokenType.name) }

        // Position metadata is non-normative.
        pos.positionPropertiesTo(this, Hints.u)
        key("synthetic", isDefault = !temperToken.synthetic) { value(temperToken.synthetic) }
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.position(pos, LeftOrRight.Left)
        tokenSink.emit(
            OutputToken(
                jsonEscaper.escape(temperToken.tokenText),
                OutputTokenType.QuotedValue,
            ),
        )
        tokenSink.position(pos, LeftOrRight.Right)
    }

    override val tokenText: String get() = temperToken.tokenText
    override val tokenType: TokenType get() = temperToken.tokenType
    override val pos get() = temperToken.pos
}

// For now, we don't need text here, but we might add it in the future.
data class CstComment(
    override val pos: Position,
    val type: CommentType,
    val text: String,
) : Positioned {
    /** The text but with any delimiters and ignorable prefixes removed. */
    val commentContent: String?
        get() {
            val content = when (type) {
                CommentType.Block -> blockCommentContent(text)
                CommentType.Line -> lineCommentContent(text)
                CommentType.SemilitParagraph -> unMassagedSemilitParagraphContent(text)
                CommentType.Semilit -> null
            }
            return if (content?.isEmpty() == false) {
                content
            } else {
                null
            }
        }
}
