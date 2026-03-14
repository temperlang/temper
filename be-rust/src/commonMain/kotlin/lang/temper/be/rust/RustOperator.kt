package lang.temper.be.rust

import lang.temper.format.TokenSink

enum class RustOperator(
    private val operatorName: String,
    val operatorDefinition: RustOperatorDefinition,
) {
    Assign("=", RustOperatorDefinition.Assignment),
    And("&", RustOperatorDefinition.And),
    Or("|", RustOperatorDefinition.InclusiveOr),
    Xor("^", RustOperatorDefinition.ExclusiveOr),
    As("as", RustOperatorDefinition.As),
    Equals("==", RustOperatorDefinition.Relational),
    GreaterEquals(">=", RustOperatorDefinition.Relational),
    GreaterThan(">", RustOperatorDefinition.Relational),
    LessEquals("<=", RustOperatorDefinition.Relational),
    LessThan("<", RustOperatorDefinition.Relational),
    NotEquals("!=", RustOperatorDefinition.Relational),
    Division("/", RustOperatorDefinition.Multiplicative),
    Multiplication("*", RustOperatorDefinition.Multiplicative),
    Remainder("%", RustOperatorDefinition.Multiplicative),
    Addition("+", RustOperatorDefinition.Additive),
    Subtraction("-", RustOperatorDefinition.Additive),
    LeftShift("<<", RustOperatorDefinition.Shift),
    RightShift(">>", RustOperatorDefinition.Shift),
    BoolComplement("!", RustOperatorDefinition.Prefix),
    BitComplement("!", RustOperatorDefinition.Prefix),
    Minus("-", RustOperatorDefinition.Prefix),
    Deref("*", RustOperatorDefinition.Prefix),
    Ref("&", RustOperatorDefinition.Prefix),
    Propagation("?", RustOperatorDefinition.Propagation),
    Member(".", RustOperatorDefinition.Member),
    MemberNotMethod(".", RustOperatorDefinition.Member),
    ;

    fun emit(sink: TokenSink) = when (operatorDefinition) {
//        RustOperatorDefinition.Prefix -> sink.prefixOp(operatorName)
//        RustOperatorDefinition.Postfix -> sink.postfixOp(operatorName)
        else -> sink.infixOp(operatorName)
    }
}
