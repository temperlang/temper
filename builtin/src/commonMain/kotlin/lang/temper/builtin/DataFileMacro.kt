package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.Fail
import lang.temper.value.HelpSnippet
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.declareDataFileSymbol
import lang.temper.value.unpackPositionedOr
import lang.temper.value.void

/**
 * <!-- snippet: builtin/dataFile -->
 * # Macro `datafile`
 * The `datafile` macro declares a data file that can be bundled with the library translation.
 * It takes three inputs:
 *
 * - The path to the data file relative to the module directory.
 *   For example, if the current module is `my-library//foo` and the path is `bar.json`
 *   then in `my-library`, the resource path `foo/bar.json` would allow retrieving the data
 *   file in some target-language specific way.
 * - The mime-type of the data.
 * - The data itself.
 *
 * ```temper null
 * dataFile(
 *   "./my-data.json",
 *   "application/json",
 *   '["My", "data", "goes", "here"]',
 * )
 * ```
 */
@HelpSnippet("The `datafile` macro", "builtin/dataFile")
object DataFileMacro : NamedBuiltinFun {
    override val name: String = "dataFile"
    override val sigs = listOf(
        Signature2(
            returnType2 = WellKnownTypes.voidType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(
                WellKnownTypes.stringType2, // data file path relative to module directory
                WellKnownTypes.stringType2, // mime-type
                WellKnownTypes.stringType2, // data
            ),
        ),
    )

    private const val ARITY = 3
    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val call = macroEnv.call ?: return NotYet
        val (path, mimeType, data) =
            macroEnv.args.unpackPositionedOr(ARITY, macroEnv) { return@invoke it }
        val pathStr = TString.unpackOrNull(path)
        val mimeTypeStr = TString.unpackOrNull(mimeType)
        val dataStr = TString.unpackOrNull(data)
        if (pathStr == null || mimeTypeStr == null || dataStr == null) {
            val problem = LogEntry(
                Log.Error,
                MessageTemplate.SignatureInputMismatch,
                call.pos,
                listOf(
                    sigs[0].requiredInputTypes,
                    listOf(path.typeTag, mimeType.typeTag, data.typeTag),
                ),
            )
            macroEnv.replaceMacroCallWithErrorNode(problem)
            return Fail(problem)
        }
        macroEnv.addTopLevelMetadata(
            declareDataFileSymbol,
            Value(
                listOf(Value(pathStr, TString), Value(mimeTypeStr, TString), Value(dataStr, TString)),
                TList,
            ),
        )
        macroEnv.replaceMacroCallWith { V(void) }
        return void
    }
}
