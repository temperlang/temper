package lang.temper.kcodegen.outgrammar

import lang.temper.log.Position
import lang.temper.log.Positioned

internal sealed class SyntaxDeclaration : Positioned {
    /** Best effort to find a condition required for the syntax to succeed. */
    abstract fun toImplicitCondition(): Condition?
}

internal data class LiteralText(
    override val pos: Position,
    val text: String,
) : SyntaxDeclaration() {
    override fun toImplicitCondition(): Condition? = null
}

internal data class SpecialTokenExpr(
    override val pos: Position,
    val code: KotlinCode,
) : SyntaxDeclaration() {
    override fun toImplicitCondition(): Condition? = null
}

internal data class PropertyUse(
    override val pos: Position,
    val propertyName: Id,
) : SyntaxDeclaration() {
    override fun toImplicitCondition() = Condition(pos, propertyName, Truthy)
}

internal data class Repetition(
    override val pos: Position,
    val repeated: PropertyUse,
    val joiner: String,
    val mayBeEmpty: Boolean,
) : SyntaxDeclaration() {
    override fun toImplicitCondition(): Condition? = null
}

internal data class Concatenation(
    override val pos: Position,
    val left: SyntaxDeclaration,
    val right: SyntaxDeclaration,
) : SyntaxDeclaration() {
    override fun toImplicitCondition(): Condition? =
        // We can't currently represent conjunction of conditions so we just pick one.
        // This is best effort though.
        left.toImplicitCondition() ?: right.toImplicitCondition()
}

internal data class Alternation(
    override val pos: Position,
    val left: SyntaxDeclaration,
    val right: SyntaxDeclaration,
) : SyntaxDeclaration() {
    override fun toImplicitCondition(): Condition? = null
}

internal data class Conditional(
    override val pos: Position,
    val condition: Condition,
    val consequent: SyntaxDeclaration,
) : SyntaxDeclaration() {
    override fun toImplicitCondition(): Condition? = null
}

internal data class Epsilon(override val pos: Position) : SyntaxDeclaration() {
    override fun toImplicitCondition(): Condition? = null
}
