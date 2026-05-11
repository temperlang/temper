package lang.temper.builtin

import lang.temper.value.MacroValue

/**
 * Keys match the operator specification format described with [lang.temper.value.operatorSymbol].
 */
val builtinOperatorSpecs = mapOf<String, List<MacroValue>>(
    "+_" to listOf(
        BuiltinFuns.plusIntFn,
        BuiltinFuns.plusLongFn,
        BuiltinFuns.plusFloatFn,
    ),
    "_+_" to listOf(
        BuiltinFuns.plusIntIntFn,
        BuiltinFuns.plusLongLongFn,
        BuiltinFuns.plusFloatFloatFn,
    ),
)
