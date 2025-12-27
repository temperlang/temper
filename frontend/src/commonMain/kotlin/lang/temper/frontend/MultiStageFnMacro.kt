package lang.temper.frontend

import lang.temper.env.InterpMode
import lang.temper.type2.Signature2
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.StaylessMacroValue
import lang.temper.value.fnBuiltinName

/**
 * The `fn` macro has several forms.
 * - `fn (X): X` does not have a body.  It specifies a function type.
 * - `fn (x) { x }` has a body.  It specifies a function value.
 *
 * While the former resolves to a reified type, type formals require special processing, and
 * the latter requires tree rewriting, so this appears here in `:frontend` instead of being
 * a builtin and this is reached via internal feature keys.
 *
 * <!-- snippet: builtin/fn -->
 * # `fn`
 * The `fn` keyword is used to define function **values** and [snippet/type/FunctionTypes].
 * Contrast this with [snippet/builtin/let] which is used to **declare** functions
 *
 * TODO: more explanation needed.
 */
internal object MultiStageFnMacro : StaylessMacroValue, NamedBuiltinFun {
    override val name = fnBuiltinName.builtinKey
    override val sigs: List<Signature2>? = null
    override val nameIsKeyword: Boolean = true

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult =
        if (interpMode == InterpMode.Partial) {
            rewriteFunctionLikeSyntacticElement(macroEnv, isDeclaration = false)
        } else {
            Fail
        }
}
