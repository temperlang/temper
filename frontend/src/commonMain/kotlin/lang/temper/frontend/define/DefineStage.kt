package lang.temper.frontend.define

import lang.temper.common.benchmarkIf
import lang.temper.common.calledFor
import lang.temper.common.effect
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.frontend.json.mixinJsonInterop
import lang.temper.frontend.syntax.DotOperationDesugarer
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.log.snapshot
import lang.temper.stage.Stage
import lang.temper.value.BlockTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.TBoolean

private const val BENCHMARK = true

internal class DefineStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
) {
    fun process(callback: (outputs: StageOutputs) -> Unit) {
        val configKey = root.configurationKey
        var addTemperTestInstructions = false
        val outputs = Debug.Frontend.DefineStage(configKey).group("Debug stage") {
            interpretiveDanceStage(
                stage = Stage.Define,
                root = root,
                failLog = failLog,
                logSink = logSink,
                module = module,
                beforeInterpretation = { root, env ->
                    addTemperTestInstructions = TBoolean.valueTrue == env[
                        StagingFlags.defineStageHookCreateAndRunClasses,
                        InterpreterCallback.NullInterpreterCallback,
                    ]
                    Debug.Frontend.DefineStage.Before.snapshot(configKey, AstSnapshotKey, root)
                },
                afterInterpretation = afterInterpretation@{ (root), _ ->
                    Debug.Frontend.DefineStage.AfterInterpretation
                        .snapshot(configKey, AstSnapshotKey, root)

                    flipDeclaredNames(root)

                    val builtinEnvironment = module.freeNameEnvironment!!
                    if (
                        builtinEnvironment[
                            StagingFlags.haltBeforeMixingIn,
                            InterpreterCallback.NullInterpreterCallback,
                        ] == TBoolean.valueTrue
                    ) {
                        return@afterInterpretation
                    }

                    mixinJsonInterop(module, root, logSink)

                    InheritPropertyReassignability().process(root)

                    Debug.Frontend.DefineStage.AfterInheritPropertyReassignability
                        .snapshot(configKey, AstSnapshotKey, root)

                    Debug.Frontend.DefineStage.DesugarDotOperations(configKey)
                        .benchmarkIf(BENCHMARK, "DesugarDotOperations") {
                            DotOperationDesugarer(root, logSink, considerExtensions = false).desugar()
                        }

                    Debug.Frontend.DefineStage.AfterDesugarDotOperations
                        .snapshot(configKey, AstSnapshotKey, root)

                    Debug.Frontend.DefineStage.LinkThis(configKey)
                        .benchmarkIf(BENCHMARK, "LinkThis") {
                            relinkThisReferences(root, failLog)
                        }

                    Debug.Frontend.DefineStage.AfterLinkThis.snapshot(configKey, AstSnapshotKey, root)

                    val convertedTypeInfo = Debug.Frontend.DefineStage.ConvertClasses(configKey)
                        .benchmarkIf(BENCHMARK, "ClosureConvertClasses") {
                            closureConvertClasses(root, logSink)
                        }
                    checkTypeDefinitions(convertedTypeInfo, logSink)

                    Debug.Frontend.DefineStage.AfterConvertClasses
                        .snapshot(configKey, AstSnapshotKey, root)

                    if (addTemperTestInstructions) {
                        addTemperTestInstructionsTo(module, root)
                        Debug.Frontend.DefineStage.AfterAddTemperTestInstructions
                            .snapshot(configKey, AstSnapshotKey, root)
                    }

                    Debug.Frontend.DefineStage.SimplifyDeclarations(configKey)
                        .benchmarkIf(BENCHMARK, "SimplifyDeclarations") {
                            SimplifyDeclarations().simplify(root)
                                .calledFor(effect)
                        }

                    Debug.Frontend.DefineStage.AfterSimplifyDeclarations
                        .snapshot(configKey, AstSnapshotKey, root)

                    Debug.Frontend.DefineStage.ConvertObjectSyntax(configKey)
                        .benchmarkIf(BENCHMARK, "ConvertObjectSyntax") {
                            convertObjectSyntax(root)
                        }

                    Debug.Frontend.DefineStage.After.snapshot(configKey, AstSnapshotKey, root)
                },
            )
        }
        callback(outputs)
    }
}
