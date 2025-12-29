package lang.temper.interp

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.name.Symbol
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MacroSignature
import lang.temper.type2.MacroValueFormal
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.ValueFormalKind
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.Fail
import lang.temper.value.LeafTreeType
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.ReifiedType
import lang.temper.value.Result
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TreeTypeStructureExpectation
import lang.temper.value.TypeTag
import lang.temper.value.Value
import lang.temper.value.void

/**
 * Placeholder until `compile.log` works
 *
 * <!-- snippet: builtin/compilelog -->
 * # `compilelog`
 *
 * TODO: Migrate the logging APIs to `console` style objects APIs per issue #1180
 * Name the logger that runs during early stages `compile` so that
 * `compile.log(...)` aids in debugging macro calls.
 */
internal object CompileLog : BuiltinStatelessMacroValue, NamedBuiltinFun {
    @Suppress("SpellCheckingInspection")
    override val name: String = "compilelog"
    override val sigs = listOf(
        MacroSignature(
            returnType = ReifiedType(
                // May fail if argument is a value but is not a string value
                MkType2.result(WellKnownTypes.voidType2, WellKnownTypes.bubbleType2).get(),
            ),
            requiredValueFormals = listOf(
                MacroValueFormal(
                    Symbol("messageText"),
                    TreeTypeStructureExpectation(setOf(LeafTreeType.Value)),
                    ValueFormalKind.Required,
                ),
                MacroValueFormal(
                    Symbol("stages"),
                    TreeTypeStructureExpectation(setOf(LeafTreeType.Value)),
                    ValueFormalKind.Required,
                ),
            ),
            restValuesFormal = null,
        ),
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.stringType2),
            optionalInputTypes = listOf(MkType2(WellKnownTypes.stageRangeTypeDefinition).get()),
        ),
    )

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): Result {
        val args = macroEnv.args

        // We intentionally evaluate out of order so that we can get a stage range to decide
        // whether to evaluate the message.
        fun <V : Any> unpackAs(argIndex: Int, type: TypeTag<V>): V? {
            val result = args.evaluate(argIndex, InterpMode.Full)
            val stateVector = type.unpackOrNull(result as? Value<*>)
            if (stateVector == null) {
                macroEnv.explain(
                    MessageTemplate.ExpectedValueOfType,
                    values = listOf(TString, result),
                )
                return null
            }
            return stateVector
        }

        val stageRange = when (args.size) {
            1 -> null
            2 -> (unpackAs(1, TStageRange) ?: return Fail)
            else -> {
                macroEnv.explain(MessageTemplate.ArityMismatch, values = listOf("1 or 2"))
                return Fail
            }
        }

        if (stageRange == null || macroEnv.stage in stageRange) {
            val messageText = unpackAs(0, TString) ?: return Fail
            macroEnv.logSink.log(
                level = Log.Info,
                template = MessageTemplate.StandardOut,
                pos = macroEnv.pos,
                values = listOf(
                    "clog:${macroEnv.stage.abbrev}: $messageText",
                ),
            )
        }
        return void
    }

    override val callMayFailPerSe: Boolean = false
}
