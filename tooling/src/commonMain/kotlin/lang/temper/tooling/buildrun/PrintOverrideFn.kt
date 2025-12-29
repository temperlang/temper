package lang.temper.tooling.buildrun

import lang.temper.builtin.BuiltinFuns
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.InterpreterCallback
import lang.temper.value.PartialResult
import lang.temper.value.Stayless
import lang.temper.value.TString
import lang.temper.value.void

class PrintOverrideFn(val printFn: (String) -> Unit) : CallableValue, Stayless {
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val (message) = args.unpackPositioned(1, cb) ?: return Fail
        val text = TString.unpackOrNull(message) ?: run {
            cb.explain(
                MessageTemplate.ExpectedValueOfType,
                values = listOf(
                    TString,
                    message,
                ),
            )
            return@invoke Fail
        }
        printFn(text)
        return void
    }

    override val sigs: List<Signature2> = (BuiltinFuns.print as CallableValue).sigs!!
}
