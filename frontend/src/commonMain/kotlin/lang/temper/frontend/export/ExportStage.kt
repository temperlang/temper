package lang.temper.frontend.export

import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.ExportList
import lang.temper.frontend.ExportsSnapshotKey
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.log.snapshot
import lang.temper.stage.Stage
import lang.temper.value.BlockTree

internal class ExportStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
) {
    fun process(callback: (outputs: StageOutputs) -> Unit) {
        val configKey = root.configurationKey
        val outputs: StageOutputs
        Debug.Frontend.ExportStage(configKey).group("Export stage") {
            outputs = interpretiveDanceStage(
                stage = Stage.Export,
                root = root,
                failLog = failLog,
                logSink = logSink,
                module = module,
                beforeInterpretation = { root, _ ->
                    Debug.Frontend.ExportStage.Before.snapshot(configKey, AstSnapshotKey, root)
                },
                afterInterpretation = { (root), _ ->
                    flipDeclaredNames(root)

                    Debug.Frontend.ExportStage.After.snapshot(configKey, AstSnapshotKey, root)
                },
            )

            Debug.Frontend.ExportStage.Exports
                .snapshot(configKey, ExportsSnapshotKey, ExportList(outputs.exports))
        }
        callback(outputs)
    }
}
