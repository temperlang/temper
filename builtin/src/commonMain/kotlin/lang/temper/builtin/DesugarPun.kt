package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type2.MacroSignature
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.symbolContained

/**
 * Desugars a property pun.
 *
 * https://rescript-lang.org/docs/manual/latest/jsx#punning explains "punning":
 *
 * > "Punning" refers to the syntax shorthand for when a label and a value are the same.
 * > For example, in JavaScript, instead of doing `return {name: name}`, you can do `return {name}`.
 *
 * A bag of properties like `({ p: 1, q })` has two properties: one with a value, `p`,
 * and one without, `q`.
 *
 * The property `q`'s value is implied to be that of `q` from the enclosing scope.
 *
 * The parse stage converts that bag of properties to a call with:
 * 1. the symbol `\p`
 * 2. the value `1`
 * 3. the symbol `\q`
 * 4. a call to this macro
 *
 * This macro looks for the preceding symbol, `\q` in the case above.
 * The macro then replaces its call with a [parsed name][lang.temper.name.ParsedName]
 * whose text is that of the symbol.
 */
internal object DesugarPun :
    BuiltinMacro(
        name = "desugarPun",
        signature = MacroSignature(
            requiredValueFormals = listOf(),
            restValuesFormal = null,
            returnType = null,
        ),
    ),
    BuiltinStatelessMacroValue {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (interpMode != InterpMode.Partial) { return Fail }
        if (macroEnv.args.size != 0) {
            return Fail(
                LogEntry(Log.Error, MessageTemplate.ArityMismatch, macroEnv.pos, listOf(0)),
            )
        }

        return if (macroEnv.document.isResolved) {
            // Too late to create a parsed name from the preceding parameter name text.
            macroEnv.replaceMacroCallWithErrorNode(
                LogEntry(
                    level = Log.Error,
                    template = MessageTemplate.TooLateForMacro,
                    pos = macroEnv.pos,
                    values = listOf(DesugarPun, macroEnv.stage, Stage.SyntaxMacro),
                ),
            )
            Fail
        } else {
            // Look for a symbol before the call to find the name that we're punning.
            val edge = macroEnv.call?.incoming
            val parent = edge?.source
            if (edge != null && parent is CallTree) {
                // Look for the preceding parameter name.
                val edgeIndex = edge.edgeIndex
                if (edgeIndex > 0) {
                    val predecessor = parent.child(edgeIndex - 1)
                    val parameterName = predecessor.symbolContained
                    if (parameterName != null) {
                        macroEnv.replaceMacroCallWith {
                            Rn(predecessor.pos) {
                                it.parsedName(parameterName.text)!!
                                // Safe because of !isResolved check above
                            }
                        }
                    }
                }
            }
            NotYet
        }
    }
}
