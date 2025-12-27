package lang.temper.be.rust

import lang.temper.common.LeftOrRight
import lang.temper.common.LeftOrRight.Left
import lang.temper.format.OperatorDefinition

enum class RustOperatorDefinition(
    private val associativity: LeftOrRight = Left,
) : OperatorDefinition {
    // See: https://doc.rust-lang.org/reference/expressions.html#expression-precedence
    Assignment(LeftOrRight.Right),
    Range,
    LogicalOr,
    LogicalAnd,
    Relational,
    InclusiveOr,
    ExclusiveOr,
    And,
    Shift,
    Additive,
    Multiplicative,
    As,
    Prefix,
    Propagation,
    Member,
    ;

    // Copied from CSharpOperatorDefinition.
    override fun canNest(inner: OperatorDefinition, childIndex: Int) = when {
        inner !is RustOperatorDefinition -> false
        ordinal < inner.ordinal -> true
        ordinal > inner.ordinal -> false
        // TODO Does this allow `==` to nest?
        else -> (childIndex == 0) == (associativity != LeftOrRight.Right)
    }
}
