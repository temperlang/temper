package lang.temper.be.java

import lang.temper.common.LeftOrRight
import lang.temper.common.LeftOrRight.Left
import lang.temper.common.LeftOrRight.Right
import lang.temper.format.OperatorDefinition

enum class JavaOperatorDefinition(
    private val associativity: LeftOrRight = Left,
) : OperatorDefinition {
    Lambda,
    Assignment(Right),
    Conditional(Right),
    ConditionalOr,
    ConditionalAnd,
    InclusiveOr,
    ExclusiveOr,
    And,
    Equality,
    Relational,
    Shift,
    Additive,
    Multiplicative,
    Prefix,
    Unary,
    Postfix,
    Atom,

    ;

    override fun canNest(inner: OperatorDefinition, childIndex: Int): Boolean =
        if (inner !is JavaOperatorDefinition) {
            false
        } else if (this.ordinal < inner.ordinal) {
            true
        } else if (this.ordinal > inner.ordinal) {
            false
        } else {
            (childIndex == 0) == (this.associativity != Right)
        }
}
