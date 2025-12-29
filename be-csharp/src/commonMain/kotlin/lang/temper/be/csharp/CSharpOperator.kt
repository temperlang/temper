package lang.temper.be.csharp

import lang.temper.format.TokenSink

enum class CSharpOperator(
    private val operatorName: String,
    val operatorDefinition: CSharpOperatorDefinition,
    val makesStatement: Boolean = false, // if this makes an expression statement
) {
    Assign("=", CSharpOperatorDefinition.Assignment, makesStatement = true),
    PlusAssign("+=", CSharpOperatorDefinition.Assignment, makesStatement = true),
    MinusAssign("-=", CSharpOperatorDefinition.Assignment, makesStatement = true),
    NullCoalescing("??", CSharpOperatorDefinition.NullCoalescing),
    LogicalOr("||", CSharpOperatorDefinition.LogicalOr),
    LogicalAnd("&&", CSharpOperatorDefinition.LogicalAnd),
    InclusiveOr("|", CSharpOperatorDefinition.InclusiveOr),
    And("&", CSharpOperatorDefinition.And),
    Equals("==", CSharpOperatorDefinition.Equality),
    GreaterEquals(">=", CSharpOperatorDefinition.Relational),
    GreaterThan(">", CSharpOperatorDefinition.Relational),
    LessEquals("<=", CSharpOperatorDefinition.Relational),
    LessThan("<", CSharpOperatorDefinition.Relational),
    NotEquals("!=", CSharpOperatorDefinition.Equality),
    Addition("+", CSharpOperatorDefinition.Additive),
    Subtraction("-", CSharpOperatorDefinition.Additive),
    Division("/", CSharpOperatorDefinition.Multiplicative),
    Multiplication("*", CSharpOperatorDefinition.Multiplicative),
    Remainder("%", CSharpOperatorDefinition.Multiplicative),
    Is("is", CSharpOperatorDefinition.Cast),
    As("as", CSharpOperatorDefinition.Cast),
    Minus("-", CSharpOperatorDefinition.Prefix),
    BoolComplement("!", CSharpOperatorDefinition.Prefix),
    NullForgiving("!", CSharpOperatorDefinition.Postfix),
    ;

    fun emit(sink: TokenSink) = when (operatorDefinition) {
        CSharpOperatorDefinition.Prefix -> sink.prefixOp(operatorName)
        CSharpOperatorDefinition.Postfix -> sink.postfixOp(operatorName)
        else -> sink.infixOp(operatorName)
    }
}
