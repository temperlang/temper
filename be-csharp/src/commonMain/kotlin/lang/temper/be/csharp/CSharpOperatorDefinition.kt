package lang.temper.be.csharp

import lang.temper.common.LeftOrRight
import lang.temper.common.LeftOrRight.Left
import lang.temper.common.LeftOrRight.Right
import lang.temper.format.OperatorDefinition

@Suppress("UnusedPrivateMember")
enum class CSharpOperatorDefinition(
    private val associativity: LeftOrRight = Left,
) : OperatorDefinition {
    // See:
    // https://learn.microsoft.com/en-us/cpp/c-language/precedence-and-order-of-evaluation
    // https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/operators/#operator-precedence
    // https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/language-specification/grammar
    // https://github.com/antlr/grammars-v4/blob/master/csharp/CSharpParser.g4
    // These also are mostly copied from JavaOperatorDefinition, as C# is at least very similar.

    Lambda,
    Assignment(Right),
    Conditional(Right),
    NullCoalescing,
    LogicalOr,
    LogicalAnd,
    InclusiveOr,
    ExclusiveOr,
    And,
    Equality,
    Relational,
    Shift,
    Additive,
    Multiplicative,
    Cast(Right),
    Prefix(Right),
    Postfix,
    Atom,
    ;

    // Also copied from JavaOperatorDefinition.
    override fun canNest(inner: OperatorDefinition, childIndex: Int) = when {
        inner !is CSharpOperatorDefinition -> false
        ordinal < inner.ordinal -> true
        ordinal > inner.ordinal -> false
        else -> (childIndex == 0) == (associativity != Right)
    }
}
