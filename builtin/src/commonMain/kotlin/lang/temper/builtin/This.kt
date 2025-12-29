package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.value.Fail
import lang.temper.value.MacroEnvironment
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.typeShapeAtLeafOrNull

/**
 * Generated via `this` syntax in Grammar.
 *
 * Collaborates with DisAmbiguateStage's class body handling to issue errors when it appears outside
 * a class body, and is responsible, during SyntaxMacroStage, for desugaring idioms.
 */
internal object This : BuiltinMacro("this", null, nameIsKeyword = true) {
    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        val stage = macroEnv.stage
        val isOk = when {
            // `this` needs to be rewritten to an implicit `this` parameter before
            // being fully evaluated.
            interpMode == InterpMode.Full -> false
            // `this` is quiescent before the syntax stage
            stage < Stage.SyntaxMacro -> return NotYet
            stage == Stage.SyntaxMacro ->
                // The DisAmbiguate stage should have added a type shape to uses of `this`
                // lexically contained in a class body.
                args.size == 1 &&
                    null != args.valueTree(0).typeShapeAtLeafOrNull
            // Rewritten at end of define stage
            stage == Stage.Define -> return NotYet
            else ->
                // should not exist
                false
        }
        return if (isOk) {
            NotYet
        } else {
            val pos = macroEnv.callee.pos
            macroEnv.logSink.log(
                level = Log.Error,
                template = MessageTemplate.ThisOutsideClassBody,
                pos = pos,
                values = emptyList(),
            )
            if (macroEnv.call != null) {
                macroEnv.replaceMacroCallWithErrorNode()
            }
            Fail
        }
    }
}
