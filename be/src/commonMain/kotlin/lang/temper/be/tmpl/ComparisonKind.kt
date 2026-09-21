package lang.temper.be.tmpl

import lang.temper.builtin.BuiltinFuns
import lang.temper.value.MacroValue

/**
 * In many languages it's idiomatic to say `x <= y` instead of `x.compareTo(y) <= 0`
 * so [SupportNetwork.simplifyPossibleComparison] uses this to pick the right kind of operator where
 * that's desired.
 */
enum class ComparisonKind(val intInfixer: TmpLOperator.Infix) {
    LessThan(TmpLOperator.LtInt),
    LessThanOrEqual(TmpLOperator.LeInt),
    GreaterThanOrEqual(TmpLOperator.GeInt),
    GreaterThan(TmpLOperator.GtInt),
    ;

    companion object {
        fun forPrimitiveIntOp(fn: MacroValue?) = primitives[fn]

        private val primitives = mapOf(
            BuiltinFuns.ltIntFn to LessThan,
            BuiltinFuns.leIntFn to LessThanOrEqual,
            BuiltinFuns.gtIntFn to GreaterThan,
            BuiltinFuns.geIntFn to GreaterThanOrEqual,
        )
    }
}
