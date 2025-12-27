package lang.temper.frontend.implicits

import lang.temper.env.InterpMode
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.NotYet
import lang.temper.value.Result
import lang.temper.value.TBoolean
import lang.temper.value.TList
import lang.temper.value.Value

internal fun filterValues(
    unfiltered: List<Value<*>>,
    f: CallableValue,
    callback: InterpreterCallback,
): Result {
    val filtered = mutableListOf<Value<*>>()
    unfiltered.filterTo(filtered) {
        // Even though mayFail = false,
        // we propagate failure out when the predicate does not return a boolean, because
        // the static type of List::filter in ImplicitsModule requires that predicate has
        // type (T) -> Boolean
        when (val result = f.invoke(ActualValues.from(it), callback, InterpMode.Full)) {
            is Fail, NotYet -> return (result as? Fail) ?: Fail
            is Value<*> -> {
                TBoolean.unpackOrNull(result) ?: return Fail
            }
        }
    }
    return Value(filtered, TList)
}
