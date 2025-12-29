package lang.temper.value

import lang.temper.common.AtomicCounter
import lang.temper.env.InterpMode
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.Symbol
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.withNullity

/**
 * Simplified from `x == null`, `x != null`, and flipped variants.
 */
object IsNullFn : NamedBuiltinFun, CallableValue {
    val sig = run {
        val tSymbol = Symbol("isNullT")
        val tf = TypeFormal(
            Position(ImplicitsCodeLocation, 0, 0),
            BuiltinName(tSymbol.text),
            tSymbol,
            Variance.Invariant,
            AtomicCounter(),
            listOf(WellKnownTypes.anyValueType),
        )
        val tt = MkType2(tf).get()
        Signature2(
            returnType2 = WellKnownTypes.booleanType2,
            requiredInputTypes = listOf(tt.withNullity(Nullity.OrNull)),
            hasThisFormal = false,
            typeFormals = listOf(tf),
        )
    }

    override val sigs: List<Signature2> = listOf(sig)

    override val isPure = true
    override val callMayFailPerSe = false
    override val name = "isNull"
    override val builtinOperatorId = BuiltinOperatorId.IsNull

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (arg) = args.unpackPositionedOr(1, cb) {
            return@invoke it
        }
        return TBoolean.value(arg == TNull.value)
    }
}

val vIsNullFn = Value(IsNullFn)
