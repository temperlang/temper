package lang.temper.value

import lang.temper.common.AtomicCounter
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.Symbol
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2

/**
 * `preserve(x, y) == y` but there should not exist a call to `preserve(x, y)` unless that *y* was
 * derived by partial interpretation of *x*.
 *
 * This allows the interpreter to do partial interpretation while preserving the original structure.
 *
 * Takes two arguments, the first is a tree that reduced to the second which should be a value leaf.
 * On calling, evaluates only the second and yields its result.
 */
object PreserveFn : NamedBuiltinFun, BuiltinStatelessMacroValue, SpecialFunction {
    val sig = run {
        val defT = TypeFormal(
            Position(ImplicitsCodeLocation, 0, 0),
            BuiltinName("preserve.T"),
            Symbol("T"),
            Variance.Invariant,
            AtomicCounter(),
            listOf(WellKnownTypes.anyValueType),
        )
        val typeT = MkType2(defT).get()

        Signature2(
            typeFormals = listOf(defT),
            requiredInputTypes = listOf(WellKnownTypes.anyValueType2, typeT),
            hasThisFormal = false,
            returnType2 = typeT,
        )
    }

    override val sigs: List<Signature2> = listOf(sig)

    override val callMayFailPerSe: Boolean = false

    override val name: String = "preserve"

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        if (args.size != 2) {
            val problem = LogEntry(
                Log.Error,
                MessageTemplate.ArityMismatch,
                macroEnv.pos,
                listOf(2),
            )
            macroEnv.replaceMacroCallWithErrorNode(problem)
            return Fail(problem)
        }
        val reduced = args.rawTreeList[1]
        if (reduced !is ValueLeaf) {
            val problem = LogEntry(
                Log.Error,
                MessageTemplate.ExpectedConstant,
                reduced.pos,
                listOf(reduced.toPseudoCode()),
            )
            macroEnv.replaceMacroCallWithErrorNode(problem)
            return Fail(problem)
        }
        args.evaluate(0, interpMode) // Evaluate for sideeffect.
        return reduced.content
    }
}

const val PRESERVE_FN_CALL_SIZE = 3 // (preserveFn preserved reduced)
