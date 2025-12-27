package lang.temper.interp.docgenalts

import lang.temper.builtin.PureCallableValue
import lang.temper.builtin.Types
import lang.temper.env.InterpMode
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.Symbol
import lang.temper.type.NominalType
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.unpackPositionedOr

/**
 * Marks an expression as an implicit result for a module or function body.
 * This allows us to move it into the documentation fold.
 */
object AltImpliedResultFn : NamedBuiltinFun, DocGenAltImpliedResultFn, PureCallableValue {
    override val name: String = "identityForDocGen"

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (x) = args.unpackPositionedOr(1, cb) { return@invoke it }
        return x
    }

    override val sigs = listOf(
        run {
            val t = TypeFormal(
                pos = Position(ImplicitsCodeLocation, 0, 0),
                name = BuiltinName("$name.T"),
                symbol = Symbol("T"),
                variance = Variance.Invariant,
                mutationCount = WellKnownTypes.anyValueTypeDefinition.mutationCount,
                upperBounds = listOf(Types.anyValue.type as NominalType),
            )
            val typeT = MkType2(t).get()
            Signature2(
                returnType2 = typeT,
                hasThisFormal = false,
                requiredInputTypes = listOf(typeT),
                typeFormals = listOf(t),
            )
        },
    )
}
