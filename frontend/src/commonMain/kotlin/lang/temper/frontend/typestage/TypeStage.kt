package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.common.Console
import lang.temper.common.benchmarkIf
import lang.temper.common.calledFor
import lang.temper.common.doIfLogs
import lang.temper.common.effect
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.CleanupTemporaries
import lang.temper.frontend.MagicSecurityDust
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.UseBeforeInit
import lang.temper.frontend.Weaver
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.frontend.simplifyFlow
import lang.temper.lexer.Genre
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.log.snapshot
import lang.temper.name.ResolvedName
import lang.temper.stage.Stage
import lang.temper.type.InvalidType
import lang.temper.type.StaticType
import lang.temper.value.BlockTree
import lang.temper.value.TBoolean
import lang.temper.value.Tree
import lang.temper.value.toLispy
import lang.temper.value.InterpreterCallback.NullInterpreterCallback as nullCallback

private const val BENCHMARK = true

internal class TypeStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
) {
    private val configKey = root.configurationKey

    fun process(callback: (outputs: StageOutputs, resultName: ResolvedName?, resultType: StaticType?) -> Unit) {
        var outputName: ResolvedName? = null
        var outputType: StaticType? = null
        val outputs = Debug.Frontend.TypeStage(configKey).group("Type Stage") {
            interpretiveDanceStage(
                stage = Stage.Type,
                root = root,
                failLog = failLog,
                logSink = logSink,
                module = module,
                beforeInterpretation = { root, _ ->
                    doBeforeInterpretation(root)
                },
                afterInterpretation = { (root), _ ->
                    val outputNameAndType = doAfterInterpretation(root)
                    if (outputNameAndType != null) {
                        outputName = outputNameAndType.first
                        outputType = outputNameAndType.second
                    }
                },
            )
        }
        callback(outputs, outputName, outputType)
    }

    private fun doBeforeInterpretation(root: BlockTree) {
        Debug.Frontend.TypeStage.Before.snapshot(configKey, AstSnapshotKey, root)
    }

    private fun doAfterInterpretation(root: BlockTree): Pair<ResolvedName, StaticType>? {
        val genre = root.document.context.genre

        val builtinEnvironment = module.freeNameEnvironment!!

        Debug.Frontend.TypeStage.AfterInterpretation.snapshot(configKey, AstSnapshotKey, root)

        flipDeclaredNames(root)

        AutoCast(root).apply()

        // Genre.Documentation requires statements start in statement position, and assumes some
        // block level idiom for failure gathering.
        if (genre != Genre.Documentation) {
            // Make failure explicit
            Debug.Frontend.TypeStage.MagicSecurityDust(configKey)
                .benchmarkIf(BENCHMARK, "MagicSecurityDust") {
                    MagicSecurityDust().sprinkle(root) calledFor effect
                }

            Debug.Frontend.TypeStage.AfterSprinkle.snapshot(configKey, AstSnapshotKey, root)

            // Pull statement-ish stuff to the root so that we have one control flow graph per
            // function/module body with failure paths.
            Debug.Frontend.TypeStage.Weaver(configKey).benchmarkIf(BENCHMARK, "Weaver") {
                Weaver.weave(
                    module,
                    root,
                    pullSpecialsRootward = true,
                    nameAllFunctions = false,
                ) calledFor effect
            }

            Debug.Frontend.TypeStage.AfterWeave.snapshot(configKey, AstSnapshotKey, root)

            Debug.Frontend.TypeStage.SimplifyFlow(configKey).benchmarkIf(BENCHMARK, "SimplifyFlow") {
                simplifyFlow(root, assumeAllJumpsResolved = false) calledFor effect
            }

            Debug.Frontend.TypeStage.AfterSimplifyFlow.snapshot(configKey, AstSnapshotKey, root)
        }

        // Find terminal expressions and introduce explicit assignments to function output
        // variables.  Since we wove blocks together, and simplified the flow,
        // ControlFlow.getTerminalExpressions is accurate.
        val outputName = Debug.Frontend.TypeStage.MakeResultsExplicit(configKey)
            .benchmarkIf(BENCHMARK, "MakeResultsExplicit") {
                when (module.genre) {
                    Genre.Library -> MakeResultsExplicit(
                        module,
                        moduleRoot = root,
                        needResultForModuleRoot = TBoolean.valueTrue == (
                            builtinEnvironment[StagingFlags.moduleResultNeeded, nullCallback]
                            ),
                    )
                    // For documentation, we do not rely on CFGs, and use our alt `return` function instead of
                    // assignments to the result variable.
                    Genre.Documentation -> {
                        MakeResultsExplicitForDocs(module, root)
                        null // Documentation fragments do not capture the module result.
                    }
                }
            }

        Debug.Frontend.TypeStage.AfterExplicitResults.snapshot(configKey, AstSnapshotKey, root)

        val nameToType =
            Debug.Frontend.TypeStage.Type(configKey).benchmarkIf(BENCHMARK, "Type") {
                Typer(module, builtinEnvironment).type(root)
            }

        Debug.Frontend.TypeStage.AfterTyper.snapshot(configKey, AstSnapshotKey, root)
        Debug.Frontend.TypeStage.AfterTyper(configKey).doIfLogs { console ->
            dumpMissingTypeInfo(root, "After typer", console)
        }

        if (genre != Genre.Documentation) {
            Debug.Frontend.TypeStage.UseBeforeInit(configKey)
                .benchmarkIf(BENCHMARK, "UseBeforeInit") {
                    UseBeforeInit(module, root, outputName).check() calledFor effect
                }
            Debug.Frontend.TypeStage.AfterUseBeforeInit.snapshot(configKey, AstSnapshotKey, root)
        }

        // Correct evaluation order here depends on weave above, which doesn't run for documentation.
        if (genre != Genre.Documentation) {
            Debug.Frontend.TypeStage.ReorderArgs(configKey).benchmarkIf(BENCHMARK, "ReorderArgs") {
                ReorderArgs(root).process()
            }
            Debug.Frontend.TypeStage.AfterReorderArgs.snapshot(configKey, AstSnapshotKey, root)
        }

        // The Typer replaces failure variables with false when the use is determined to be
        // safe.  Simplify the flow graph for function macros.
        Debug.Frontend.TypeStage.SimplifyFlow2(configKey)
            .benchmarkIf(BENCHMARK, "SimplifyFlow2") {
                simplifyFlow(
                    root,
                    assumeAllJumpsResolved = false,
                    assumeResultsCaptured = true,
                ) calledFor effect
            }

        Debug.Frontend.TypeStage.AfterSimplifyFlow2.snapshot(configKey, AstSnapshotKey, root)
        Debug.Frontend.TypeStage.AfterSimplifyFlow2(configKey).doIfLogs { console ->
            dumpMissingTypeInfo(root, "After simplify flow 2", console)
        }

        // Clean-up temporaries introduced so we have a scrutable output.
        if (genre != Genre.Documentation) {
            Debug.Frontend.TypeStage.CleanupTemporaries(configKey)
                .benchmarkIf(BENCHMARK, "CleanupTemporaries") {
                    CleanupTemporaries.cleanup(
                        module,
                        root,
                        beforeResultsExplicit = false,
                        outputName = outputName,
                        snapshotId = Debug.Frontend.TypeStage.CleanupTemporaries,
                    ) calledFor effect
                }
        }

        Debug.Frontend.TypeStage.AfterCleanupTemporaries.snapshot(configKey, AstSnapshotKey, root)
        Debug.Frontend.TypeStage.AfterCleanupTemporaries(configKey).doIfLogs { console ->
            dumpMissingTypeInfo(root, "After temporaries cleaned", console)
        }

        Debug.Frontend.TypeStage.SimplifyFlow3(configKey).benchmarkIf(BENCHMARK, "SimplifyFlow3") {
            simplifyFlow(root, assumeAllJumpsResolved = false) calledFor effect
        }
        Debug.Frontend.TypeStage.AfterSimplifyFlow3.snapshot(configKey, AstSnapshotKey, root)

        if (genre != Genre.Documentation) {
            Debug.Frontend.TypeStage.RepairUnrealizedGoals(configKey)
                .benchmarkIf(BENCHMARK, "RepairUnrealizedGoals") {
                    val needReweaving = inlineToRepairUnrealizedGoals(root, logSink)
                    if (needReweaving.isNotEmpty()) {
                        Weaver.reweaveSelected(needReweaving, logSink, module.filePositions)
                        simplifyFlow(root, assumeAllJumpsResolved = false) calledFor effect
                    }
                }
        }
        Debug.Frontend.TypeStage.AfterRepairUnrealizedGoals.snapshot(configKey, AstSnapshotKey, root)

        Debug.Frontend.TypeStage.After.snapshot(configKey, AstSnapshotKey, root)
        Debug.Frontend.TypeStage.After(configKey).doIfLogs { console ->
            dumpMissingTypeInfo(root, "After trimming loose threads", console)
        }

        return outputName?.let { it to (nameToType[it] ?: InvalidType) }
    }
}

private fun findMissingTypeInfo(ast: Tree): List<Tree> {
    val missingTypeInformation = mutableListOf<Tree>()
    TreeVisit.startingAt(ast)
        .forEachContinuing {
            val needsTypeInfo = it.needsTypeInfo
            if (needsTypeInfo && it.typeInferences == null) {
                missingTypeInformation.add(it)
            }
        }
        .visitPreOrder()
    return missingTypeInformation.toList()
}

private fun dumpMissingTypeInfo(ast: Tree, description: String, console: Console) {
    val missing = findMissingTypeInfo(ast)
    if (missing.isNotEmpty()) {
        console.group("Missing type info $description") {
            missing.forEach {
                console.log(it.toLispy())
            }
        }
    }
}
