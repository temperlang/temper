package lang.temper.be.py

import lang.temper.common.LeftOrRight
import lang.temper.format.OperatorDefinition
import kotlin.math.sign

/**
 * Python operator definitions.
 *
 * [Precedence in docs](https://docs.python.org/3/reference/expressions.html#operator-precedence).
 * [Precedence in unparser](https://github.com/python/cpython/blob/3.10/Python/ast_unparse.c#L98).
 *
 * Notes:
 *  * the walrus operator is a "named expression" and allowed by the syntax only
 *    in specific places. In particular, `a := b := X` is not allowed.
 */
@Suppress("MagicNumber") // Precedences
enum class PyOperatorDefinition(
    val precedence: Int,
    private val associativity: LeftOrRight = LeftOrRight.Left,
) : OperatorDefinition {
    Tuple(0),
    Yield(1),
    YieldFrom(1),
    Lambda(2),
    Test(2, LeftOrRight.Right), // sic
    BoolOr(3),
    BoolAnd(4),
    BoolNot(5),
    Lt(6),
    Gt(6),
    Eq(6),
    GtEq(6),
    LtEq(6),
    NotEq(6),
    In(6),
    NotIn(6),
    Is(6),
    IsNot(6),
    StarExpr(7),
    BitwiseOr(8),
    BitwiseXor(9),
    BitwiseAnd(10),
    ShiftLeft(11),
    ShiftRight(11),
    Add(12),
    Sub(12),
    Mult(13),
    MatMult(13),
    Div(13),
    Mod(13),
    FloorDiv(13),
    UnaryAdd(14),
    UnarySub(14),
    UnaryInvert(14),
    Pow(15, LeftOrRight.Right),
    Await(16),
    Attribute(17),
    Subscript(17),
    Call(17),
    Grouping(18),
    Atom(19),
    ;

    override fun canNest(inner: OperatorDefinition, childIndex: Int): Boolean =
        inner is PyOperatorDefinition &&
            when (this.precedence.compareTo(inner.precedence).sign) {
                -1 -> true
                1 -> false
                0 -> (childIndex == 0) == (this.associativity != LeftOrRight.Right)
                else -> error("sign is not in [-1,1]")
            }

    companion object {
        fun lookup(name: String?): PyOperatorDefinition? {
            return name?.let { it ->
                when (val result = byName[it]) {
                    null -> throw IllegalArgumentException("Can't find PyOpDef for $name")
                    else -> result
                }
            }
        }

        private val byName = values().associateBy { it.name }
    }
}
