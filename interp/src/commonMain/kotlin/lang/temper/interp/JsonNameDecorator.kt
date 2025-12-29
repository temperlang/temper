package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.value.Fail
import lang.temper.value.NotYet
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.jsonNameSymbol

internal val jsonNameDecorator = MetadataDecorator(
    jsonNameSymbol,
    argumentTypes = listOf(Types.string),
) { actuals ->
    when (val nameResult = actuals.evaluate(1, InterpMode.Partial)) {
        is NotYet, is Fail -> nameResult
        is Value<*> ->
            if (nameResult.typeTag != TString) {
                Fail(
                    LogEntry(
                        Log.Error,
                        MessageTemplate.ExpectedValueOfType,
                        actuals.pos(1),
                        listOf(Types.string, nameResult),
                    ),
                )
            } else {
                nameResult
            }
    }
}
