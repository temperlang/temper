@file:Suppress("unused")

package lang.temper.be.js

import lang.temper.common.LeftOrRight
import lang.temper.format.OperatorDefinition
import kotlin.math.sign

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence#Table
@Suppress("MagicNumber") // Precedences
enum class JsOperatorDefinition(
    val precedence: Int,
    val associativity: LeftOrRight,
    val formatString: String,
    private val inBrackets: IntRange = IntRange.EMPTY,
) : OperatorDefinition {
    Comma(1, LeftOrRight.Left, "{{0}}, {{1}}"),

    Yield(2, LeftOrRight.Right, "yield {{0}}"),
    YieldStar(2, LeftOrRight.Right, "yield* {{0}}"),

    // SpreadElement contains an AssignmentExpression
    DotDotDot(2, LeftOrRight.Right, "...{{0}}"),

    Eq(3, LeftOrRight.Right, "{{0}} = {{1}}"),
    PlusEq(3, LeftOrRight.Right, "{{0}} += {{1}}"),
    MinusEq(3, LeftOrRight.Right, "{{0}} -= {{1}}"),
    StarStarEq(3, LeftOrRight.Right, "{{0}} **= {{1}}"),
    StarEq(3, LeftOrRight.Right, "{{0}} *= {{1}}"),
    DivEq(3, LeftOrRight.Right, "{{0}} /= {{1}}"),
    PctEq(3, LeftOrRight.Right, "{{0}} %= {{1}}"),
    LtLtEq(3, LeftOrRight.Right, "{{0}} <<= {{1}}"),
    GtGtEq(3, LeftOrRight.Right, "{{0}} >>= {{1}}"),
    GtGtGtEq(3, LeftOrRight.Right, "{{0}} >>>= {{1}}"),
    AmpEq(3, LeftOrRight.Right, "{{0}} &= {{1}}"),
    CaretEq(3, LeftOrRight.Right, "{{0}} ^= {{1}}"),
    BarEq(3, LeftOrRight.Right, "{{0}} |= {{1}}"),
    ArrowFunction(3, LeftOrRight.Right, "{{0}} => {{1}}"),
    Conditional(3, LeftOrRight.Right, "{{0}} ? {{1}} : {{2}}"),

    NullishCoalescing(5, LeftOrRight.Left, "{{0}} ?? {{1}}"),

    LogicalOr(6, LeftOrRight.Left, "{{0}} || {{1}}"),

    LogicalAnd(7, LeftOrRight.Left, "{{0}} && {{1}}"),

    BitwiseOr(8, LeftOrRight.Left, "{{0}} | {{1}}"),

    BitwiseXor(9, LeftOrRight.Left, "{{0}} ^ {{1}}"),

    BitwiseAnd(10, LeftOrRight.Left, "{{0}} & {{1}}"),

    EqEq(11, LeftOrRight.Left, "{{0}} == {{1}}"),
    BangEq(11, LeftOrRight.Left, "{{0}} != {{1}}"),
    EqEqEq(11, LeftOrRight.Left, "{{0}} === {{1}}"),
    BangEqEq(11, LeftOrRight.Left, "{{0}} !== {{1}}"),

    Instanceof(12, LeftOrRight.Left, "{{0}} instanceof {{1}}"),
    In(12, LeftOrRight.Left, "{{0}} in {{1}}"),
    Gte(12, LeftOrRight.Left, "{{0}} >= {{1}}"),
    Gt(12, LeftOrRight.Left, "{{0}} > {{1}}"),
    Lte(12, LeftOrRight.Left, "{{0}} <= {{1}}"),
    Lt(12, LeftOrRight.Left, "{{0}} < {{1}}"),

    LtLt(13, LeftOrRight.Left, "{{0}} << {{1}}"),
    GtGt(13, LeftOrRight.Left, "{{0}} >> {{1}}"),
    GtGtGt(13, LeftOrRight.Left, "{{0}} >>> {{1}}"),

    Plus(14, LeftOrRight.Left, "{{0}} + {{1}}"),
    Minus(14, LeftOrRight.Left, "{{0}} - {{1}}"),

    Star(15, LeftOrRight.Left, "{{0}} * {{1}}"),
    Div(15, LeftOrRight.Left, "{{0}} / {{1}}"),
    Rem(15, LeftOrRight.Left, "{{0}} % {{1}}"),

    Exp(16, LeftOrRight.Right, "{{0}} ** {{1}}"),

    Await(17, LeftOrRight.Right, "await {{0}}"),
    Delete(17, LeftOrRight.Right, "delete {{0}}"),
    Void(17, LeftOrRight.Right, "void {{0}}"),
    Typeof(17, LeftOrRight.Right, "typeof {{0}}"),
    PreIncr(17, LeftOrRight.Right, "++{{0}}"),
    PreDecr(17, LeftOrRight.Right, "--{{0}}"),
    UnaryPlus(17, LeftOrRight.Right, "+{{0}}"),
    UnaryMinus(17, LeftOrRight.Right, "-{{0}}"),
    BitwiseNot(17, LeftOrRight.Right, "~{{0}}"),
    LogicalNot(17, LeftOrRight.Right, "!{{0}}"),

    PostIncr(18, LeftOrRight.Right, "{{0}}++"),
    PostDecr(18, LeftOrRight.Right, "{{0}}--"),

    NewNoArgs(19, LeftOrRight.Right, "new {{0}}"),

    OptionalChaining(20, LeftOrRight.Left, "{{0}}?.{{1}}"),
    GenericRef(20, LeftOrRight.Left, "{{0}}<{{*:, }}>", 1 until Int.MAX_VALUE),
    FunctionCall(20, LeftOrRight.Left, "{{0}}({{*:, }})", 1 until Int.MAX_VALUE),
    NewWithArgs(20, LeftOrRight.Right, "new {{0}}({{*:, }})", 1 until Int.MAX_VALUE),
    ComputedMemberAccess(20, LeftOrRight.Left, "{{0}}[{{1}}]", 1 until Int.MAX_VALUE),
    ComputedOptionalChaining(20, LeftOrRight.Left, "{{0}}?.[{{1}}]"),
    MemberAccess(20, LeftOrRight.Left, "{{0}}.{{1}}"),

    Grouping(21, LeftOrRight.Left, "({{*:, }})", 0..Int.MAX_VALUE),
    ;

    override val isCommaOperator: Boolean get() = this == Comma

    override fun canNest(inner: OperatorDefinition, childIndex: Int): Boolean =
        childIndex in inBrackets || (
            inner is JsOperatorDefinition &&
                when (this.precedence.compareTo(inner.precedence).sign) {
                    -1 -> true
                    1 -> false
                    0 -> (childIndex == 0) == (this.associativity != LeftOrRight.Right)
                    else -> error("sign is not in [-1,1]")
                }
            )
}
