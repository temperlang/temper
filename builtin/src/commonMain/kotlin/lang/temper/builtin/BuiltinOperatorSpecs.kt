package lang.temper.builtin

import lang.temper.lexer.Operator
import lang.temper.lexer.TokenType
import lang.temper.value.CallableValue

/**
 * Keys match the operator specification format described with [lang.temper.value.operatorSymbol].
 */
val builtinOperatorSpecs: Map<String, List<CallableValue>> = mapOf(
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

/**
 * Given a compound assignment operator, like `+=`, returns the simple operator
 * like `+`.  This is meant to allow desugaring complex operations, e.g.
 * `x += y` might desugar to `x = x + y` which combines regular assignment and a
 * simple operator instead of using a compound assignment operator.
 */
fun simpleBuiltinKeyFromCompoundOperator(builtinKey: String?): String? =
    if (
        builtinKey != null &&
        builtinKey != "=" && // is an assignment operator, but is not compound
        Operator.isProbablyAssignmentOperator(builtinKey, TokenType.Punctuation)
    ) {
        builtinKey.dropLast(1)
    } else {
        null
    }
