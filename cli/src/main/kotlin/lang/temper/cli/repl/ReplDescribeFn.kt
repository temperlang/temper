package lang.temper.cli.repl

import lang.temper.common.DECIMAL_RADIX
import lang.temper.common.Either
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.logConfigurationsByName
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.Helpful
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.TInt
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.void
import lang.temper.type.WellKnownTypes as WKT

/**
 * Allows the REPL user to dump snapshots of code at various stages to the console.
 *
 * See [ReplDescribeFn.longHelp] for details.
 */
internal class ReplDescribeFn(
    private val repl: Repl,
) : NamedBuiltinFun, CallableValue, Helpful {
    override val sigs: List<Signature2> get() = sigsList
    override val name get() = NAME
    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val (locValue, stepValue) = args.unpackPositioned(2, cb)
            ?: run {
                return@invoke cb.fail(MessageTemplate.ArityMismatch, cb.pos, listOf(2))
            }

        val locOrFail = repl.commandLineLocationFromValue(
            locValue,
            args.pos(0) ?: cb.pos,
            cb,
        )
        val loc = when (locOrFail) {
            is Either.Left -> locOrFail.item
            is Either.Right -> return locOrFail.item
        }
        val stepId: String = TString.unpackOrNull(stepValue)
            ?: return cb.fail(
                MessageTemplate.ExpectedValueOfType,
                args.pos(1) ?: cb.pos,
                listOf("String in ${logConfigurationsByName.keys}", stepValue),
            )

        val snapshot = repl.snapshotStore.retrieveSnapshot(loc, AstAspect.PseudoCode, stepId)
            ?: repl.snapshotStore.retrieveSnapshot(loc, CstAspect, stepId)
            ?: repl.snapshotStore.retrieveSnapshot(loc, ExportsAspect, stepId)
        if (snapshot != null) {
            val console = repl.console
            console.group("Describe $loc @ $stepId") {
                console.textOutput.emitLine(snapshot)
            }
        }
        return void
    }

    override fun briefHelp(): String = "detailed information about an interactive result"

    override fun longHelp() = buildString {
        appendLine(
            """
            Dumps detailed information about the stage processing for an interactive result. See the frontend type
            stage processor for examples of code that sends snapshots that end up accessible here.

            Signature:
            """.trimIndent(),
        )
        for (sig in this@ReplDescribeFn.sigs) {
            appendLine("    $sig")
        }
        appendLine(
            """
                |The first argument, `${REPL_LOC_SYMBOL.text}` may be one of:
                |    A string like "interactive#0" that names a command chunk
                |    An integer like 123; shorthand for "interactive#123"
                |
                |The second, `$STEP_ARG` must be a string, one of the logger step names from [lang.temper.log.Debug];
                |use tab to get autocomplete or `${ReplHelpFn.NAME}("${LoggingStepNamesHelp.NAME}")` for a full list.
                |
            """.trimMargin(),
        )
    }

    companion object {
        const val NAME = "describe"
        private val sigsList = listOf(WKT.stringType2, WKT.intType2).map { inputType ->
            Signature2(
                returnType2 = WKT.voidType2,
                hasThisFormal = false,
                requiredInputTypes = listOf(inputType, WKT.stringType2),
            )
        }
    }
}

const val STEP_ARG = "step"

internal fun Repl.commandLineLocationFromValue(
    locValue: Value<*>,
    locValuePos: Position,
    cb: InterpreterCallback,
): Either<ReplChunkIndex, Fail> {
    val locIndex = when (locValue.typeTag) {
        TString -> {
            val str = TString.unpack(locValue)
            if (str.startsWith(REPL_LOC_PREFIX)) {
                try {
                    str.substring(REPL_LOC_PREFIX.length).toInt(DECIMAL_RADIX)
                } catch (_: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        }
        TInt -> TInt.unpack(locValue)
        else -> null
    }
    return if (locIndex == null || locIndex !in this.validCommandCounts) {
        Either.Right(
            cb.fail(
                MessageTemplate.ExpectedValueOfType,
                locValuePos,
                listOf(
                    "Int in ${this.validCommandCounts} | String like \"interactive#0\"",
                    locValue,
                ),
            ),
        )
    } else {
        val loc = ReplChunkIndex(locIndex)
        this.lastLocReferenced = loc
        Either.Left(loc)
    }
}
