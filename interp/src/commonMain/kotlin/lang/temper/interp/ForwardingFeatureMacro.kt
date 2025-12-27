package lang.temper.interp

import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.Fail
import lang.temper.value.FunctionSpecies
import lang.temper.value.InternalFeatureKey
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.TFunction
import lang.temper.value.Value

private fun forwardingFeatureMacro(
    internalFeatureKey: InternalFeatureKey,
    functionSpecies: FunctionSpecies,
): Value<MacroValue> = Value(ForwardingFeatureMacro(internalFeatureKey, functionSpecies))

internal fun forwardingFeatureMacro(
    fk: InternalFeatureKeys,
) = forwardingFeatureMacro(fk.featureKey, fk.functionSpecies)

private class ForwardingFeatureMacro(
    val internalFeatureKey: InternalFeatureKey,
    override val functionSpecies: FunctionSpecies,
) : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val sigs: List<Signature2>? get() = null
    override val name: String get() = internalFeatureKey

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult =
        when (val implementation = macroEnv.getFeatureImplementation(internalFeatureKey)) {
            is Fail -> {
                macroEnv.explain(
                    MessageTemplate.UnsupportedByInterpreter,
                    values = listOf(internalFeatureKey),
                )
                macroEnv.replaceMacroCallWithErrorNode()
                Fail
            }
            is Value<*> -> when (val fn = TFunction.unpackOrNull(implementation)) {
                null -> {
                    macroEnv.explain(
                        MessageTemplate.UnsupportedByInterpreter,
                        values = listOf(internalFeatureKey),
                    )
                    macroEnv.replaceMacroCallWithErrorNode()
                    Fail
                }
                else -> fn.invoke(macroEnv, interpMode)
            }
        }
}
