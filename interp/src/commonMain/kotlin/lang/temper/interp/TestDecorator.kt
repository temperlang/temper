package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.value.Fail
import lang.temper.value.NotYet
import lang.temper.value.TProblem
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.testSymbol

/**
 * <!-- snippet: builtin/@test -->
 * # `@test` decorator
 * `@test` may decorate a function, indicating the function is a test.
 * The intention is that this not be used directly but through a `test`
 * macro that builds an appropriate function.
 */
internal val testDecorator = MetadataDecorator(
    testSymbol,
    argumentTypes = listOf(Types.string),
) { args ->
    when (val r = args.evaluate(1, InterpMode.Partial)) {
        NotYet, is Fail -> r
        is Value<*> -> {
            if (r.typeTag == TString) {
                r
            } else {
                Value(
                    LogEntry(
                        level = Log.Error,
                        template = MessageTemplate.ExpectedValueOfType,
                        pos = args.pos(1),
                        values = listOf(TString, r),
                    ),
                    TProblem,
                )
            }
        }
    }
}

val vTestDecorator = Value(testDecorator)
