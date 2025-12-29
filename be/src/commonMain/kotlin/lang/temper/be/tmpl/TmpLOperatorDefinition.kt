package lang.temper.be.tmpl

import lang.temper.format.OperatorDefinition
import lang.temper.format.OutputToken
import lang.temper.format.TokenAssociation
import lang.temper.lexer.Associativity
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import kotlin.math.sign

/**
 * Adapts [Operator] to work as an [OperatorDefinition].
 * [TmpLOperator], which collects semantically significant operators,
 * refers here to handle precedence and associativity so
 * that our TmpL trees pretty print nicely in logs and test outputs.
 */
sealed class TmpLOperatorDefinition(
    val precedence: Int,
    val associativity: Associativity,
    val outputToken: OutputToken,
    private val inBrackets: IntRange = IntRange.EMPTY,
) : OperatorDefinition {
    sealed class Infix(operator: Operator) : TmpLOperatorDefinition(operator)
    sealed class Prefix(operator: Operator) : TmpLOperatorDefinition(operator)

    object Amp : Infix(Operator.Amp)
    object AmpAmp : Infix(Operator.AmpAmp)
    object Angle : TmpLOperatorDefinition(Operator.Angle)
    object As : Infix(Operator.As)
    object Bang : Prefix(Operator.Bang)
    object Bar : Infix(Operator.Bar)
    object BarBar : Infix(Operator.BarBar)
    object Plus : Infix(Operator.Plus)
    object Minus : Infix(Operator.Dash)
    object Star : Infix(Operator.Star)
    object Slash : Infix(Operator.Slash)
    object Dot : TmpLOperatorDefinition(Operator.Dot)
    object Ellipsis : TmpLOperatorDefinition(Operator.Ellipsis)
    object Paren : TmpLOperatorDefinition(Operator.Paren)
    object Square : TmpLOperatorDefinition(Operator.Square)
    object PreDash : Prefix(Operator.PreDash)
    object Comma : TmpLOperatorDefinition(Operator.Comma)
    object Eq : TmpLOperatorDefinition(Operator.Eq)
    object EqEq : Infix(Operator.EqEq)
    object Le : Infix(Operator.Le)
    object Lt : Infix(Operator.Lt)
    object Ge : Infix(Operator.Ge)
    object Gt : Infix(Operator.Gt)
    object Instanceof : TmpLOperatorDefinition(Operator.Instanceof)
    object ParenGroup : TmpLOperatorDefinition(Operator.ParenGroup)
    object Colon : TmpLOperatorDefinition(Operator.LowColon)

    constructor(operator: Operator) : this(
        operator.precedence.intValue,
        operator.associativity,
        OutputToken(
            operator.text ?: "",
            operatorTokenType(operator.text ?: ""),
            if (operator.closer) {
                TokenAssociation.Bracket
            } else {
                when (operator.operatorType) {
                    OperatorType.Prefix -> TokenAssociation.Prefix
                    OperatorType.Postfix -> TokenAssociation.Postfix
                    OperatorType.Infix -> TokenAssociation.Infix
                    else -> TokenAssociation.Unknown
                }
            },
        ),
        if (operator.closer) {
            when (operator.operatorType) {
                OperatorType.Infix -> IntRange(1, Int.MAX_VALUE)
                OperatorType.Prefix -> IntRange(0, Int.MAX_VALUE)
                else -> throw IllegalArgumentException(operator.operatorType.name)
            }
        } else {
            IntRange.EMPTY
        },
    )

    override val isCommaOperator: Boolean get() = this == Comma

    override fun canNest(inner: OperatorDefinition, childIndex: Int): Boolean =
        childIndex in inBrackets || (
            inner is TmpLOperatorDefinition &&
                when (this.precedence.compareTo(inner.precedence).sign) {
                    -1 -> true
                    1 -> false
                    0 -> (childIndex == 0) == (this.associativity != Associativity.Right)
                    else -> error("sign is not in [-1,1]")
                }
            )
}
