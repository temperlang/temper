package lang.temper.interp

import lang.temper.env.InterpMode
import lang.temper.stage.Stage
import lang.temper.type2.AnySignature
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.InterpreterCallback.NullInterpreterCallback.stage
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.freeTree

/**
 * For decorators that are specially handled as syntactic element by early
 * stages and so which just replace their calls with the decorated item.
 */
internal class DoNothingDecorator(override val name: String) : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val sigs: List<AnySignature>? get() = null

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val decorated = macroEnv.args.valueTree(0)
        if (stage >= Stage.SyntaxMacro) { // Already used by syntax-heavy stages.
            macroEnv.replaceMacroCallWith(freeTree(decorated))
        }
        return NotYet
    }
}
