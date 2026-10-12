package lang.temper.be.elixir

import lang.temper.common.LeftOrRight
import lang.temper.common.LeftOrRight.Left
import lang.temper.common.LeftOrRight.Right
import lang.temper.format.OperatorDefinition

/**
 * Elixir's precedence ladder for the operators this backend emits, lowest
 * binding first, from the table in Elixir's operator reference and checked
 * against `elixir` 1.19 (see journal/probes/02_precedence.exs).
 *
 * Comparison and equality are left-associative in Elixir, but a chain of them
 * is never what Temper meant: `1 < 2 < 3` parses as `(1 < 2) < 3`, which is
 * `true < 3`, which is `false` because an atom sorts after every number. They
 * are marked non-associative so the formatter parenthesises instead.
 */
enum class ElixirOperatorDefinition(
    private val associativity: LeftOrRight = Left,
    private val nonAssociative: Boolean = false,
) : OperatorDefinition {
    /** `=`, right-associative: `a = b = 1` binds both. */
    Match(Right),

    /** `||` and `or` */
    Or,

    /** `&&` and `and` */
    And,
    Equality(nonAssociative = true),
    Comparison(nonAssociative = true),
    Pipe,
    In(nonAssociative = true),

    /** `++`, `--` and `<>` are right-associative: `[1] ++ [2] -- [2]` is `[1]`. */
    Concat(Right),
    Additive,
    Multiplicative,

    /** `**` is right-associative and binds looser than unary minus: `-2 ** 2` is 4. */
    Power(Right),

    /** `not`, `!` and unary `-` bind tighter than every binary operator. */
    Prefix(Right),
    Postfix,
    ;

    override fun canNest(inner: OperatorDefinition, childIndex: Int) = when {
        inner !is ElixirOperatorDefinition -> false
        // a call's arguments sit inside its own parentheses: `f(a + b)`, not `f((a + b))`
        this == Postfix && childIndex > 0 -> true
        ordinal < inner.ordinal -> true
        ordinal > inner.ordinal -> false
        nonAssociative -> false
        else -> (childIndex == 0) == (associativity != Right)
    }
}
