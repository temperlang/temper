package lang.temper.be.tmpl

import lang.temper.type.WellKnownTypes
import lang.temper.type2.Type2

/**
 * A typed variant of something that is builtin in many languages, and
 * often represented using an infix operator so its natural for us to
 * treat it in the tree as an operation.
 *
 * Multiple variants may point to the same [TmpLOperatorDefinition] which
 * handle associativity and precedence.
 *
 * For example, there are multiple `+` variants for `Int` and other input
 * types.
 */
sealed class TmpLOperator(
    val returnType: Type2,
) {
    abstract val kind: TmpLOperatorDefinition
    override fun toString(): String = "${kind.outputToken}:$returnType"

    sealed class Infix(
        returnType: Type2,
        override val kind: TmpLOperatorDefinition.Infix,
    ) : TmpLOperator(returnType)

    sealed class Prefix(
        returnType: Type2,
        override val kind: TmpLOperatorDefinition.Prefix,
    ) : TmpLOperator(returnType)

    /** Logical `&&` */
    object AmpAmp : Infix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.AmpAmp)

    /** Logical `!` */
    object Bang : Prefix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.Bang)

    /** Logical `||` */
    object BarBar : Infix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.BarBar)

    /** `==` applied to Ints */
    object EqEqInt : Infix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.EqEq)

    /** `<=` applied to Ints */
    object LeInt : Infix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.Le)

    /** `<=` applied to Ints */
    object LtInt : Infix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.Lt)

    /** `<=` applied to Ints */
    object GeInt : Infix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.Ge)

    /** `<=` applied to Ints */
    object GtInt : Infix(WellKnownTypes.booleanType2, TmpLOperatorDefinition.Gt)

    /** `+` applied to Ints */
    object PlusInt : Infix(WellKnownTypes.intType2, TmpLOperatorDefinition.Plus)
}
