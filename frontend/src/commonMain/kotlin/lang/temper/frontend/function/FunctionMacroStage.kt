package lang.temper.frontend.function

import lang.temper.common.console
import lang.temper.frontend.DebugTreeRepresentation
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.stage.Stage
import lang.temper.value.BlockTree

private const val DEBUG = false
private val debugTreeRepresentation = DebugTreeRepresentation.Lispy
private inline fun debug(message: () -> Any?) {
    if (DEBUG) {
        val o = message()
        if (o != Unit) {
            console.log("$o")
        }
    }
}

internal class FunctionMacroStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
) {
    fun process(callback: (outputs: StageOutputs) -> Unit) {
        val outputs = interpretiveDanceStage(
            stage = Stage.FunctionMacro,
            root = root,
            failLog = failLog,
            logSink = logSink,
            module = module,
            beforeInterpretation = { root, _ ->
                debug {
                    console.log("Before functionMacro interpretation")
                    debugTreeRepresentation.dump(root)
                }
            },
            afterInterpretation = { (root), result ->
                debug {
                    console.log("After interpretation")
                    debugTreeRepresentation.dump(root)
                    console.log("Result was $result")
                }

                flipDeclaredNames(root)
            },
        )
        callback(outputs)
    }
}
