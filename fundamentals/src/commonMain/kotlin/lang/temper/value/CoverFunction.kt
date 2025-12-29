package lang.temper.value

import lang.temper.common.LeftOrRight
import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.type2.Signature2

/**
 * Groups a bunch of functions together until we can use type tag information to (hopefully)
 * settle on a particular callee.
 */
class CoverFunction(
    val covered: List<CallableValue>,
    val otherwise: CallableValue? = null,
) : CallableValue, TokenSerializable {
    override val isPure: Boolean = covered.all { it.isPure } &&
        (otherwise !is CallableValue || otherwise.isPure)

    override val callMayFailPerSe: Boolean
        get() = covered.any { it.callMayFailPerSe } ||
            (otherwise?.callMayFailPerSe ?: false)

    fun uncover(
        args: Actuals,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Pair<Result, Arguments?>? {
        val beforeTypeCheck = cb.failLog.markBeforeRecoverableFailure()

        val message = DynamicMessage(args, interpMode)
        var toRun: MacroValue? = otherwise
        var argumentsToUse: Arguments? = null
        fnLoop@
        for (c in covered) {
            if (c is CoverFunction) {
                when (val p = c.uncover(args, cb, interpMode)) {
                    null -> return null
                    else -> {
                        if (p.first is Value<*>) {
                            return p
                        }
                        continue@fnLoop
                    }
                }
            }

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

    override fun addStays(s: StaySink) {
        for (f in covered) {
            f.addStays(s)
        }
        otherwise?.addStays(s)
    }

    override fun renderTo(tokenSink: TokenSink) {
        val groupedTokenCounts = mutableMapOf<List<OutputToken>, Int>()
        val tokens = mutableListOf<OutputToken>()
        val collectingSink = CollectingSink(tokens)
        for (f in covered) {
            tokens.clear()
            TFunction.renderValue(f, collectingSink)
            val tokenList = tokens.toList()
            groupedTokenCounts[tokenList] = (groupedTokenCounts[tokenList] ?: 0) + 1
        }

        val otherwise = otherwise
        if (otherwise != null) {
            tokens.clear()
            TFunction.renderValue(otherwise, collectingSink)
            val tokenList = tokens.toList()
            groupedTokenCounts[tokenList] = (groupedTokenCounts[tokenList] ?: 0) + 1
        }

        tokenSink.emit(OutToks.fnWord)
        tokenSink.emit(OutToks.leftParen)
        groupedTokenCounts.entries.forEachIndexed { i, (ls, count) ->
            if (i != 0) {
                tokenSink.emit(OutToks.bar)
            }
            for (t in ls) {
                tokenSink.emit(t)
            }
            if (count != 1) {
                tokenSink.emit(OutToks.timesDisplayName)
                tokenSink.emit(OutputToken("$count", OutputTokenType.NumericValue))
            }
        }
        tokenSink.emit(OutToks.rightParen)
        return
    }

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        return when (val uncovered = uncover(args, cb, interpMode)) {
            null -> Fail
            else -> {
                val (toRun, arguments) = uncovered
                when (toRun) {
                    is Fail -> Fail
                    is Value<*> -> when (
                        val callable = TFunction.unpackOrNull(toRun) as? CallableValue
                    ) {
                        null -> {
                            cb.explain(MessageTemplate.CannotInvokeMacroAsFunction, cb.pos)
                            Fail
                        }
                        else -> {
                            val uncoveredArgs = when {
                                arguments != null ->
                                    arguments.toPositionalActuals(cb) ?: return Fail
                                else -> args
                            }
                            callable.invoke(uncoveredArgs, cb, interpMode)
                        }
                    }
                }
            }
        }
    }

    override val sigs: List<Signature2> = buildSet {
        for (f in covered) {
            addAll(f.sigs ?: continue)
        }
        if (otherwise?.sigs != null) {
            addAll(otherwise.sigs!!)
        }
    }.toList()
}

private class CollectingSink(val out: MutableList<OutputToken>) : TokenSink {
    override fun emit(token: OutputToken) {
        out.add(token)
    }

    override fun position(pos: Position, side: LeftOrRight) {
        // ignored
    }

    override fun endLine() {
        // ignored
    }

    override fun finish() {
        // ignored
    }
}
