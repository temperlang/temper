package lang.temper.ast

import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.TemperName
import lang.temper.value.InnerTreeType
import lang.temper.value.TreeType
import lang.temper.value.Value

sealed class AstPart : Positioned

sealed class LeafAstPart : AstPart()

data class StartTree(override val pos: Position) : AstPart(), Positioned {
    override fun toString() = "\u00AB"
}
data class FinishTree(
    override val pos: Position,
    val type: FinishedType,
) : AstPart(), Positioned {
    constructor(pos: Position, treeType: TreeType) : this(pos, FinishedTreeType(treeType))

    override fun toString() = "${ type.abbrev }\u00BB"
}
data class TokenLeaf(val cstToken: CstToken) : LeafAstPart(), Positioned by cstToken {
    override fun toString() = "`${ cstToken.tokenText }`"
}
data class ValuePart(
    val value: Value<*>,
    override val pos: Position,
) : LeafAstPart(), Positioned {
    override fun toString() = "$value"
}
data class NamePart(
    val name: TemperName,
    override val pos: Position,
) : LeafAstPart(), Positioned {
    override fun toString() = "$name"
}

/**
 * Helps process infix operators so `1 + 2` becomes (Call + 1 2) by shifting `+` left of 1.
 */
data class ShiftLeft(override val pos: Position) : AstPart() {
    override fun toString() = "shiftLeft"
}

sealed class ErrorEvent(
    val parts: List<CstPart>,
) : AstPart() {
    override fun toString() = "(error x ${ parts.size })"
    override val pos: Position get() {
        val first = parts.first().pos
        val last = parts.last().pos
        return Position(first.loc, first.left, last.right)
    }
}

class ProductionFailedEvent(
    val messageTemplate: MessageTemplateI,
    val messageValues: List<Any>,
    parts: List<CstPart>,
) : ErrorEvent(parts)

class KnownProblemEvent(
    val messageTemplate: MessageTemplate,
    parts: List<CstPart>,
) : ErrorEvent(parts)

/**
 * Explains how to create a tree from a [FinishTree], its corresponding [StartTree], and children
 * created from the events in between.
 */
sealed class FinishedType {
    abstract val treeType: TreeType
    abstract val abbrev: String
}

data class FinishedTreeType(override val treeType: TreeType) : FinishedType() {
    override val abbrev get() = "${treeType.name[0]}"
}

/**
 * Like [InnerTreeType.Block] but flattens to its child if there is exactly one.
 */
object SoftBlock : FinishedType() {
    override fun toString() = "SoftBlock"
    override val abbrev = "SB"
    override val treeType = InnerTreeType.Block
}

/**
 * Like [InnerTreeType.Call] with an implied call to [`,`], but flattens to its child if there is exactly one.
 */
object SoftComma : FinishedType() {
    override fun toString() = "SoftComma"
    override val abbrev = "SC"
    override val treeType = InnerTreeType.Call
}
