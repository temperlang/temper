package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.common.isNegZero
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.value.Fail
import lang.temper.value.NotYet
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.jsonExtraSymbol

// Documentation in BuiltinEnvironment
internal val jsonExtraDecorator = MetadataDecorator(
    jsonExtraSymbol,
    argumentTypes = listOf(Types.string, Types.anyValue),
) decoratedValue@{ actuals ->
    val jsonPropertyNameResult = actuals.evaluate(1, InterpMode.Partial)
    when (jsonPropertyNameResult) {
        is NotYet, is Fail -> return@decoratedValue jsonPropertyNameResult
        is Value<*> -> if (jsonPropertyNameResult.typeTag != TString) {
            return@decoratedValue Fail(
                LogEntry(
                    Log.Error,
                    MessageTemplate.ExpectedValueOfType,
                    actuals.pos(1),
                    listOf(Types.string, jsonPropertyNameResult),
                ),
            )
        }
    }
    val knownValueResult = actuals.evaluate(2, InterpMode.Partial)
    var representableValue = true
    when (knownValueResult) {
        is NotYet, is Fail -> return@decoratedValue knownValueResult
        is Value<*> -> when (knownValueResult.typeTag) {
            TFloat64 -> {
                val f = TFloat64.unpack(knownValueResult)
                if (f.isInfinite() || f.isNaN() || f.isNegZero()) {
                    // Not representable as JSON
                    representableValue = false
                }
            }
            TString, TBoolean, TNull, TInt -> {}
            else -> representableValue = false
        }
    }
    if (!representableValue) {
        return@decoratedValue Fail(
            LogEntry(
                Log.Error,
                MessageTemplate.ExpectedValueOfType,
                actuals.pos(2),
                listOf(
                    "Boolean | Int | (most Float64) | Null | String",
                    knownValueResult,
                ),
            ),
        )
    }
    // Package for MixinJsonInterop
    val parts = mutableListOf(jsonPropertyNameResult, knownValueResult)
    Value(parts, TList)
}
