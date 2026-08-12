package lang.temper.value

import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2

/**
 * <!-- snippet: builtin/! -->
 * # `!`
 * The prefix `!` operator performs [snippet/type/Boolean] inverse.
 *
 * `!`[snippet/builtin/false] is [snippet/builtin/true] and vice versa.
 */
@HelpSnippet("Boolean inverse", "builtin/!")
object NotFn : NamedBuiltinFun, CallableValue {
    override val name: String = "!"
    override val callMayFailPerSe: Boolean get() = false
    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.booleanType2,
            requiredInputTypes = listOf(WellKnownTypes.booleanType2),
            hasThisFormal = false,
        ),
    )

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): Result {
        val (a) = args.unpackPositionedOr(1, cb) {
            return@invoke it
        }
        if (a.typeTag != TBoolean) {
            return Fail(
                LogEntry(
                    MessageTemplate.ExpectedSubType,
                    args.pos(0) ?: cb.pos,
                    listOf(WellKnownTypes.booleanType2, a.typeTag),
                ),
            )
        }
        return Value(
            !(TBoolean.unpack(a)),
            TBoolean,
        )
    }
}

val vNotFn = Value(NotFn)
