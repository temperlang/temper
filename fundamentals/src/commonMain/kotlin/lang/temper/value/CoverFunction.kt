package lang.temper.value

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate

/**
 * Groups a bunch of functions together until we can use type tag information to (hopefully)
 * settle on a particular callee.
 */
data object CoverFunction {
    fun uncover(
        args: Actuals,
        cb: InterpreterCallback,
        interpMode: InterpMode,
        covered: List<CallableValue>,
        otherwise: CallableValue? = null,
    ): Pair<Result, Arguments?>? {
        val beforeTypeCheck = cb.failLog.markBeforeRecoverableFailure()
        val message = DynamicMessage(args, interpMode)
        var toRun: MacroValue? = otherwise
        var argumentsToUse: Arguments? = null
        fnLoop@
        for (c in covered) {
            val cSigs = c.sigs
            if (cSigs == null) { // Applicable to all argument lists.
                toRun = c
                break
            }
            for (cSig in cSigs) {
                val resolutions = Resolutions(cb)
                val arguments = unify(message, cSig, resolutions)
                // Four cases
                // | arguments | contradiction | do                       |
                // | --------- | ------------- | ------------------------ |
                // | *         | true          | look at other signatures |
                // | null      | false         | stop, do not know        |
                // | !null     | false         | stop, use result         |
                if (resolutions.contradiction) {
                    cb.explain(
                        MessageTemplate.NotApplicable,
                        cb.pos,
                        listOf(cSig, args, resolutions.problem ?: ""),
                    )
                } else if (arguments == null) {
                    // Don't know if c was applicable
                    return null
                } else {
                    argumentsToUse = arguments
                    toRun = c
                    break@fnLoop
                }
            }
        }
        return when (toRun) {
            null -> null
            else -> {
                beforeTypeCheck.rollback()
                Value(toRun) to argumentsToUse
            }
        }
    }
}
