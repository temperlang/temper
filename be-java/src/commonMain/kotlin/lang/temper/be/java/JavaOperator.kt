package lang.temper.be.java

import lang.temper.format.TokenSink
import lang.temper.be.java.JavaOperatorDefinition as Jod

enum class JavaOperator(
    private val operatorName: String,
    val operatorDefinition: Jod,
    val makesStatement: Boolean = false, // true iff this makes an expression statement. 14.8
) {
    Remainder("%", Jod.Multiplicative), // 15.17
    Division("/", Jod.Multiplicative), // 15.17
    Multiplication("*", Jod.Multiplicative), // 15.17
    Subtraction("-", Jod.Additive), // 15.18
    Addition("+", Jod.Additive), // 15.18
    LogicalRightShift(">>>", Jod.Shift), // 15.19
    RightShift(">>", Jod.Shift), // 15.19
    LeftShift("<<", Jod.Shift), // 15.19
    GreaterEquals(">=", Jod.Relational), // 15.20
    LessEquals("<=", Jod.Relational), // 15.20
    GreaterThan(">", Jod.Relational), // 15.20
    LessThan("<", Jod.Relational), // 15.20
    NotEquals("!=", Jod.Equality), // 15.21
    Equals("==", Jod.Equality), // 15.21
    And("&", Jod.And), // 15.22
    ExclusiveOr("^", Jod.ExclusiveOr), // 15.22
    InclusiveOr("|", Jod.InclusiveOr), // 15.22
    ConditionalAnd("&&", Jod.ConditionalAnd), // 15.23
    ConditionalOr("||", Jod.ConditionalOr), // 15.24
    Conditional("?", Jod.Conditional), // 15.25
    Assign("=", Jod.Assignment), // 15.26.1
    TimesAssign("*=", Jod.Assignment), // 15.26.2
    DivideAssign("/=", Jod.Assignment), // 15.26.2
    RemainderAssign("%=", Jod.Assignment), // 15.26.2
    PlusAssign("+=", Jod.Assignment), // 15.26.2
    MinusAssign("-=", Jod.Assignment), // 15.26.2
    LeftShiftAssign("<<=", Jod.Assignment), // 15.26.2
    RightShiftAssign(">>=", Jod.Assignment), // 15.26.2
    LogicalRightShiftAssign(">>>=", Jod.Assignment), // 15.26.2
    AndAssign("&=", Jod.Assignment), // 15.26.2
    ExclusiveOrAssign("^=", Jod.Assignment), // 15.26.2
    InclusiveOrAssign("|=", Jod.Assignment), // 15.26.2
    PostIncrement("++", Jod.Postfix, makesStatement = true), // 15.14.2
    PostDecrement("--", Jod.Postfix, makesStatement = true), // 15.14.3
    PreIncrement("++", Jod.Prefix, makesStatement = true), // 15.15.1
    PreDecrement("--", Jod.Prefix, makesStatement = true), // 15.15.2
    Plus("+", Jod.Unary), // 15.15.3
    Minus("-", Jod.Unary), // 15.15.4
    BitwiseComplement("~", Jod.Unary), // 15.15.5
    BoolComplement("!", Jod.Unary), // 15.15.6
    ;

    fun isInfix() = when (operatorDefinition) {
        Jod.ConditionalOr,
        Jod.ConditionalAnd,
        Jod.InclusiveOr,
        Jod.ExclusiveOr,
        Jod.And,
        Jod.Equality,
        Jod.Relational,
        Jod.Shift,
        Jod.Additive,
        Jod.Multiplicative,
        -> true
        else -> false
    }

    fun isAssignment() = operatorDefinition == Jod.Assignment
    fun isPrefix() = operatorDefinition == Jod.Prefix || operatorDefinition == Jod.Unary
    fun isPostfix() = operatorDefinition == Jod.Postfix

    fun emit(sink: TokenSink) = when (operatorDefinition) {
        Jod.Prefix -> sink.prefixOp(operatorName)
        Jod.Unary -> sink.prefixOp(operatorName)
        Jod.Postfix -> sink.postfixOp(operatorName)
        else -> sink.infixOp(operatorName)
    }
}
