package lang.temper.cli.repl

import lang.temper.builtin.Types
import lang.temper.env.InterpMode
import lang.temper.interp.importExport.ResolvedModuleSpecifier
import lang.temper.log.Position
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.ValueFormalKind
import lang.temper.value.LeafTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.MacroValue
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.StayLeaf
import lang.temper.value.TString
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.Value
import lang.temper.value.void

internal class ReplImportFeatureMacro(val repl: Repl) : MacroValue, NamedBuiltinFun {
    override val name: String = "importPending"

    override val sigs = listOf(
        MacroSignature(
            returnType = Types.void,
            requiredValueFormals = listOf(
                MacroValueFormal(null, Types.string, ValueFormalKind.Required),
            ),
            optionalValueFormals = listOf(
                MacroValueFormal(
                    null,
                    TreeTypeStructureExpectation(setOf(LeafTreeType.Stay)),
                    ValueFormalKind.Optional,
                ),
            ),
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        val resolvedModuleSpecifier = ResolvedModuleSpecifier(
            TString.unpack(args.evaluate(0, interpMode) as Value<*>),
        )
        val importStayLeaf = if (args.size == 2) {
            args.valueTree(1) as StayLeaf
        } else {
            null
        }

        repl.addPendingImport(
            PendingImportForRepl(
                resolvedModuleSpecifier = resolvedModuleSpecifier,
                specifierPos = args.valueTree(0).pos,
                stayLeaf = importStayLeaf,
            ),
        )
        return void
    }
}

internal data class PendingImportForRepl(
    val resolvedModuleSpecifier: ResolvedModuleSpecifier,
    val specifierPos: Position,
    val stayLeaf: StayLeaf?,
)
