package lang.temper.interp

import lang.temper.value.InterpreterCallback
import lang.temper.value.Promises

/** An interpreter callback associated with a particular interpreter. */
internal interface BoundInterpreterCallback : InterpreterCallback {
    val interpreter: Interpreter
    override val promises: Promises
}
