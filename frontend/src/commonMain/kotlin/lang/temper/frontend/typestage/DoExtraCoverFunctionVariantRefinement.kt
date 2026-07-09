package lang.temper.frontend.typestage

import lang.temper.builtin.BuiltinFuns
import lang.temper.value.MacroValue
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.Tree
import lang.temper.value.valueContained

internal fun doExtraCoverFunctionVariantRefinement(
    calleeFn: MacroValue,
    argTrees: List<Tree>,
): MacroValue? {
    when (calleeFn) {
        BuiltinFuns.divIntIntFn -> {
            val rightOperand = argTrees.getOrNull(1)
            val rightVal = rightOperand?.valueContained(TInt)
            if (rightVal != null && rightVal != 0) {
                return BuiltinFuns.divIntIntSafeFn
            }
        }

        BuiltinFuns.divLongLongFn -> {
            val rightOperand = argTrees.getOrNull(1)
            val rightVal = rightOperand?.valueContained(TInt64)
            if (rightVal != null && rightVal != 0L) {
                return BuiltinFuns.divLongLongSafeFn
            }
        }

        BuiltinFuns.modIntIntFn -> {
            val rightOperand = argTrees.getOrNull(1)
            val rightVal = rightOperand?.valueContained(TInt)
            if (rightVal != null && rightVal > 0) {
                return BuiltinFuns.modIntIntSafeFn
            }
        }

        BuiltinFuns.modLongLongFn -> {
            val rightOperand = argTrees.getOrNull(1)
            val rightVal = rightOperand?.valueContained(TInt64)
            if (rightVal != null && rightVal > 0L) {
                return BuiltinFuns.modLongLongSafeFn
            }
        }

        BuiltinFuns.plusIntFn,
        BuiltinFuns.plusLongFn,
        BuiltinFuns.plusFloatFn,
        -> {
            return BuiltinFuns.identityFn
        }

        else -> {}
    }
    return null
}
