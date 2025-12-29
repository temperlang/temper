package lang.temper.interp.docgenalts

import lang.temper.env.InterpMode
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction

/** Alternate implementation of `return` for [lang.temper.lexer.Genre.Documentation]. */
object AltReturnFn : NamedBuiltinFun, SpecialFunction, DocGenAltReturnFn {
    override val name: String = "returnForDocGen"
    override val sigs: List<Signature2> = listOf(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.anyValueOrNullType2),
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (interpMode == InterpMode.Full) {
            TODO("Examine the stack to figure out the return variable and assign to it.")
        } else if (macroEnv.stage == Stage.Define) {
            unlessInStatementPosition(macroEnv) { return@invoke it }
        }
        return NotYet
    }
}
