package lang.temper.interp.importExport

import lang.temper.builtin.Types
import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.ExportedName
import lang.temper.name.NameMaker
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.ValueFormalKind
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.InnerTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.freeTree

/**
 * `export let x = ...` -> `let /* ExportedName */x = ...`
 *
 * <!-- snippet: builtin/@export -->
 * # `@export`
 * The `@export` decorator keyword makes the declaration available outside the module.
 * Exported declarations may be linked to via [snippet/builtin/import].
 *
 * It is a [legacy decorator][snippet/legacy-decorator], so the `export` keyword
 * is shorthand for the `@export` decorator.
 *
 * Exported symbols are also visible within the module following normal scoping rules.
 *
 * ```temper
 * export let x = 3;
 * x == 3
 * ```
 */
object ExportDecorator : NamedBuiltinFun, BuiltinStatelessMacroValue {
    override val name: String = "@export"
    override val sigs: List<MacroSignature> get() = listOf(
        MacroSignature(
            requiredValueFormals = listOf(
                MacroValueFormal(
                    Symbol("decorated"),
                    reifiedType = TreeTypeStructureExpectation(
                        setOf(InnerTreeType.Decl),
                    ),
                    ValueFormalKind.Required,
                ),
            ),
            restValuesFormal = null,
            returnType = Types.void,
        ),
    )

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args
        if (args.size != 1) {
            macroEnv.explain(MessageTemplate.ArityMismatch, values = listOf(1))
            return Fail
        }
        val decl = args.valueTree(0) as? DeclTree
        val parts = decl?.parts
        val nameTree = parts?.name
        val name = nameTree?.content
        if (name !is ExportedName) {
            if (name !is ParsedName) {
                return if (macroEnv.stage == Stage.GenerateCode) {
                    // Too late
                    val cause = LogEntry(
                        Log.Error,
                        MessageTemplate.CannotExport,
                        args.pos(0),
                        emptyList(),
                    )
                    macroEnv.replaceMacroCallWithErrorNode(cause)
                    Fail(cause)
                } else {
                    macroEnv.explain(MessageTemplate.CannotExport)
                    NotYet
                }
            }
            val nameEdge = nameTree.incoming!! // Parent is decl
            nameEdge.replace {
                Ln(nameTree.pos, convertName(name, macroEnv.nameMaker))
            }
        } // If it is an exported name, we're all good.
        macroEnv.replaceMacroCallWith { Replant(freeTree(decl)) }
        return NotYet
    }

    fun convertName(nameBefore: ParsedName, nameMaker: NameMaker): ExportedName {
        return ExportedName(nameMaker.namingContext, nameBefore)
    }
}
