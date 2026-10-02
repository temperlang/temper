package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.env.InterpMode
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type2.AnySignature
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.valueContained

object AutoDecorator : NamedBuiltinFun, MacroValue {
    override val name: String = "@auto"

    override val sigs: List<AnySignature> = listOf(
        // TODO What?
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        macroEnv.stage < Stage.Define && return NotYet
        return NotYet
    }
}

val autoDecorator = MetadataDecorator(
    symbolKey = Symbol("auto"),
    argumentTypes = listOf(Types.string),
) { args ->
    // TODO Combine with operatorImplementationDecorator logic?
    args.valueTree(1).valueContained
        ?: return@MetadataDecorator NotYet
}
