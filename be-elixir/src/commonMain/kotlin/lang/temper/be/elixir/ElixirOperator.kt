package lang.temper.be.elixir

import lang.temper.format.TokenSink

/**
 * The operators this backend emits.
 *
 * Integer division and remainder are absent on purpose: Elixir's `/` always
 * returns a float (`7 / 2` is 3.5), so Temper's `Int` division is the `div`
 * function, and remainder is `rem`. Both truncate toward zero, as Temper does.
 */
enum class ElixirOperator(
    private val operatorName: String,
    val operatorDefinition: ElixirOperatorDefinition,
) {
    /** Strict boolean or: raises unless the left side is a boolean. */
    Or("or", ElixirOperatorDefinition.Or),
    And("and", ElixirOperatorDefinition.And),
    Equals("==", ElixirOperatorDefinition.Equality),
    NotEquals("!=", ElixirOperatorDefinition.Equality),

    /** `1 == 1.0` is true; `1 === 1.0` is not. */
    StrictEquals("===", ElixirOperatorDefinition.Equality),
    StrictNotEquals("!==", ElixirOperatorDefinition.Equality),
    LessThan("<", ElixirOperatorDefinition.Comparison),
    LessEquals("<=", ElixirOperatorDefinition.Comparison),
    GreaterThan(">", ElixirOperatorDefinition.Comparison),
    GreaterEquals(">=", ElixirOperatorDefinition.Comparison),
    Pipe("|>", ElixirOperatorDefinition.Pipe),
    In("in", ElixirOperatorDefinition.In),

    /** Binary (string) concatenation. */
    StringConcat("<>", ElixirOperatorDefinition.Concat),
    ListConcat("++", ElixirOperatorDefinition.Concat),
    ListSubtract("--", ElixirOperatorDefinition.Concat),
    Addition("+", ElixirOperatorDefinition.Additive),
    Subtraction("-", ElixirOperatorDefinition.Additive),
    Multiplication("*", ElixirOperatorDefinition.Multiplicative),

    /** Float division only. */
    FloatDivision("/", ElixirOperatorDefinition.Multiplicative),
    Power("**", ElixirOperatorDefinition.Power),
    Negate("-", ElixirOperatorDefinition.Prefix),
    Not("not", ElixirOperatorDefinition.Prefix),
    ;

    fun emit(sink: TokenSink) = when (operatorDefinition) {
        ElixirOperatorDefinition.Prefix -> sink.prefixOp(operatorName)
        else -> sink.infixOp(operatorName)
    }
}
