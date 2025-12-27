package lang.temper.value

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate

inline fun <V : Any> (TypeTag<V>).unpackOrFail(
    args: Actuals,
    index: Int,
    cb: InterpreterCallback,
    interpMode: InterpMode,
    onWrongResult: (PartialResult) -> Nothing,
): V {
    val result = args.result(index, interpMode = interpMode)
    if (result is Value<*> && result.typeTag == this) {
        return unpack(result)
    }
    val nonValueResult = when (result) {
        is NotYet -> NotYet
        is Fail, is Value<*> -> {
            var failure: Fail? = null
            if (result is Fail && result.info != null) {
                failure = result
            }
            failure
                ?: cb.fail(
                    MessageTemplate.ExpectedValueOfType,
                    pos = args.pos(index) ?: cb.pos,
                    values = listOf(TList, result),
                )
        }
    }
    onWrongResult(nonValueResult)
}
