package lang.temper.be.cpp

import lang.temper.common.LeftOrRight
import lang.temper.format.OperatorDefinition
import kotlin.math.sign

// cppref: https://en.cppreference.com/w/cpp/language/operator_precedence
@Suppress("MagicNumber")
enum class CppOperatorDefinition(
    val precedence: Int,
    private val associativity: LeftOrRight = LeftOrRight.Left,
) : OperatorDefinition {
    ScopeResolve(1),
    Dot(2),
    Arrow(2),
    PreInc(3),
    PreDec(3),
    Plus(3),
    Minus(3),
    Not(3),
    BitNot(3),
    CCast(3),
    Deref(3),
    AddrOf(3),
    SizeOf(3),
    CoAwait(3),
    New(3),
    Delete(3),
    DotDeref(4),
    ArrowDeref(4),
    Mul(5),
    Div(5),
    Mod(5),
    Add(6),
    Sub(6),
    Shl(7),
    Shr(7),
    Compare(8),
    Lt(9),
    Gt(9),
    Le(9),
    Ge(9),
    Eq(10),
    Ne(10),
    BitAnd(11),
    BitXor(12),
    BitOr(13),
    And(14),
    Or(15),
    Ternary(16),
    Throw(16),
    CoYield(16),
    Assign(16),
    AddAssign(16),
    SubAssign(16),
    MulAssign(16),
    DivAssign(16),
    ModAssign(16),
    ShlAssign(16),
    ShrAssign(16),
    AndAssign(16),
    XorAssign(16),
    OrAssign(16),
    Comma(17),
    ;
    override fun canNest(inner: OperatorDefinition, childIndex: Int): Boolean =
        inner is CppOperatorDefinition &&
            when (this.precedence.compareTo(inner.precedence).sign) {
                -1 -> false
                1 -> true
                0 -> (childIndex == 0) == (this.associativity != LeftOrRight.Right)
                else -> error("sign is not in [-1,1]")
            }

    companion object {
        fun lookup(name: String?): CppOperatorDefinition? {
            return name?.let { it ->
                when (val result = byName[it]) {
                    null -> throw IllegalArgumentException("Can't find CppOpDef for $name")
                    else -> result
                }
            }
        }

        private val byName = entries.associateBy { it.name }
    }
}
