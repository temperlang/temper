package lang.temper.be.lua

import lang.temper.common.LeftOrRight
import lang.temper.format.OperatorDefinition
import kotlin.math.sign

@Suppress("MagicNumber")
enum class LuaOperatorDefinition(
    val precedence: Int,
    private val associativity: LeftOrRight = LeftOrRight.Left,
) : OperatorDefinition {
    Dot(-1),
    Pow(0),
    Not(1),
    Unm(1),
    Mul(2),
    Div(2),
    Mod(2),
    Add(3),
    Sub(3),
    Concat(4),
    Eq(5),
    Ne(5),
    Lt(5),
    Le(5),
    Gt(5),
    Ge(5),
    Or(6),
    And(7),
    ;
    override fun canNest(inner: OperatorDefinition, childIndex: Int): Boolean =
        inner is LuaOperatorDefinition &&
            when (this.precedence.compareTo(inner.precedence).sign) {
                -1 -> true
                1 -> false
                0 -> (childIndex == 0) == (this.associativity != LeftOrRight.Right)
                else -> error("sign is not in [-1,1]")
            }

    companion object {
        fun lookup(name: String?): LuaOperatorDefinition? {
            return name?.let { it ->
                when (val result = byName[it]) {
                    null -> throw IllegalArgumentException("Can't find PyOpDef for $name")
                    else -> result
                }
            }
        }

        private val byName = entries.associateBy { it.name }
    }
}
