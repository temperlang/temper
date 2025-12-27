package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.ignore
import lang.temper.type.StaticType
import lang.temper.value.CallTree
import lang.temper.value.CoverFunction
import lang.temper.value.MacroValue
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.valueContained

internal fun doExtraCoverFunctionVariantRefinement(
    coverFn: CoverFunction,
    callTree: CallTree,
    looseRefinedType: StaticType,
    variantsFiltered: MutableList<MacroValue>,
) {
    ignore(coverFn) // These may come in useful in the future.  More context is better.
    ignore(looseRefinedType)

    if (variantsFiltered.size == 1) {
        when (variantsFiltered[0]) {
            BuiltinFuns.divIntIntFn -> {
                val rightOperand = callTree.childOrNull(2)
                val rightVal = rightOperand?.valueContained(TInt)
                if (rightVal != null && rightVal != 0) {
                    variantsFiltered.clear()
                    variantsFiltered.add(BuiltinFuns.divIntIntSafeFn)
                }
            }

            BuiltinFuns.divLongLongFn -> {
                val rightOperand = callTree.childOrNull(2)
                val rightVal = rightOperand?.valueContained(TInt64)
                if (rightVal != null && rightVal != 0L) {
                    variantsFiltered.clear()
                    variantsFiltered.add(BuiltinFuns.divLongLongSafeFn)
                }
            }

            BuiltinFuns.modIntIntFn -> {
                val rightOperand = callTree.childOrNull(2)
                val rightVal = rightOperand?.valueContained(TInt)
                if (rightVal != null && rightVal > 0) {
                    variantsFiltered.clear()
                    variantsFiltered.add(BuiltinFuns.modIntIntSafeFn)
                }
            }

            BuiltinFuns.modLongLongFn -> {
                val rightOperand = callTree.childOrNull(2)
                val rightVal = rightOperand?.valueContained(TInt64)
                if (rightVal != null && rightVal > 0L) {
                    variantsFiltered.clear()
                    variantsFiltered.add(BuiltinFuns.modLongLongSafeFn)
                }
            }

            BuiltinFuns.plusIntFn,
            BuiltinFuns.plusLongFn,
            BuiltinFuns.plusFloatFn,
            -> {
                variantsFiltered.clear()
                variantsFiltered.add(BuiltinFuns.identityFn)
            }

            else -> {}
        }
    }
}
