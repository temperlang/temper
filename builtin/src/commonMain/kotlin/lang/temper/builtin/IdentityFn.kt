package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.type2.Signature2
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.freeTree

/** Returns its sole argument.  Called as a macro, replaces its call with its sole argument */
internal object IdentityFn : NamedBuiltinFun, SpecialFunction {
    override val name: String = "identity"
    override val sigs: List<Signature2> = run {
        val (typeFormalT, typeT) = makeTypeFormal(name, "T")
        listOf(
            Signature2(
                returnType2 = typeT,
                hasThisFormal = false,
                requiredInputTypes = listOf(typeT),
            ),
        )
    }

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        if (args.size != 1) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, values = listOf(1))
        }
        return when (interpMode) {
            InterpMode.Partial -> {
                // Replace self with single argument
                val arg = args.valueTree(0)
                macroEnv.replaceMacroCallWith {
                    Replant(freeTree(arg))
                }
                NotYet
            }
            InterpMode.Full -> {
                args.evaluate(0, interpMode)
            }
        }
    }
}
