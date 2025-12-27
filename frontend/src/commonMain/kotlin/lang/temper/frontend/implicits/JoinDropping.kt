package lang.temper.frontend.implicits

import lang.temper.common.toStringViaBuilder
import lang.temper.env.InterpMode
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.InterpreterCallback
import lang.temper.value.TString
import lang.temper.value.Value

internal fun joinDropping(
    elements: Iterable<Value<*>>,
    separator: String,
    stringify: CallableValue,
    callback: InterpreterCallback,
): Value<String> {
    val str = toStringViaBuilder { out ->
        var wroteOne = false
        for (element in elements) {
            val elementText = TString.unpackOrNull(
                stringify.invoke(ActualValues.from(element), callback, InterpMode.Full) as? Value<*>,
            ) ?: continue
            if (wroteOne) {
                out.append(separator)
            } else {
                wroteOne = true
            }
            out.append(elementText)
        }
    }
    return Value(str, TString)
}
