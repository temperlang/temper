package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Console
import lang.temper.common.benchmarkIf
import lang.temper.common.calledFor
import lang.temper.common.doIfLogs
import lang.temper.common.effect
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.CaptureInfo
import lang.temper.frontend.CleanupTemporaries
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.UseBeforeInit
import lang.temper.frontend.Weaver
import lang.temper.frontend.define.SimplifyDeclarations
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
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.TBoolean
import lang.temper.value.Tree
import lang.temper.value.VoidishPanicFn
import lang.temper.value.functionContained
import lang.temper.value.staticTypeContained
import lang.temper.value.toLispy
import lang.temper.value.void
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

        // Make sure any declarations with initializers really are simplified to separate assignments.
        // Most of these are simplified out by the define stage, but processing of imports can reintroduce these.
        SimplifyDeclarations(simplifyFunTrees = false).simplify(root)

        AutoCast(root).apply()

        val needResultForModuleRoot = TBoolean.valueTrue == (
            builtinEnvironment[StagingFlags.moduleResultNeeded, nullCallback]
            )

        // Find terminal expressions and introduce explicit assignments to function output
        // variables.
        val (outputName, outputInfo) = Debug.Frontend.TypeStage.MakeResultsExplicit(configKey)
            .benchmarkIf(BENCHMARK, "MakeResultsExplicit") {
                when (module.genre) {
                    Genre.Library -> MakeResultsExplicit.makeAllResultsExplicit(
                        console = module.console,
                        moduleRoot = root,
                        needResultForModuleRoot = needResultForModuleRoot,
                    )
                    // For documentation, we do not rely on CFGs and use our alt `return` function instead of
                    // assignments to the result variable.
                    Genre.Documentation -> {
                        MakeResultsExplicitForDocs(module, root)
                        null to CaptureInfo.empty // Documentation fragments do not capture the module result.
                    }
                }
            }

        Debug.Frontend.TypeStage.MakeResultsExplicit
            .snapshot(configKey, CaptureInfo.Key, outputInfo)
        Debug.Frontend.TypeStage.AfterExplicitResults.snapshot(configKey, AstSnapshotKey, root)

        // Genre.Documentation requires statements to start in statement position, and assumes some
        // block level idiom for failure gathering.
        if (genre != Genre.Documentation) {
            // Pull statement-ish stuff to the root so that we have one control flow graph per
            // function/module body with failure paths.
            Debug.Frontend.TypeStage.Weaver(configKey).benchmarkIf(BENCHMARK, "Weaver") {
                Weaver.weave(
                    root,
                    sprinkleSecurityDust = false, // Not enough type info yet.
                    simplifyRttiCalls = true,
                    pullSpecialsRootward = true,
                    nameAllFunctions = false,
                    resultsAlreadyCaptured = true,
                )
            }.also { captureInfo ->
                Debug.Frontend.TypeStage.AfterWeave
                    .snapshot(configKey, CaptureInfo.Key, captureInfo)
            }

            Debug.Frontend.TypeStage.AfterWeave.snapshot(configKey, AstSnapshotKey, root)

            Debug.Frontend.TypeStage.SimplifyFlow(configKey).benchmarkIf(BENCHMARK, "SimplifyFlow") {
                simplifyFlow(root, assumeAllJumpsResolved = false) calledFor effect
            }

            Debug.Frontend.TypeStage.AfterSimplifyFlow.snapshot(configKey, AstSnapshotKey, root)
        }

        val nameToType =
            Debug.Frontend.TypeStage.Type(configKey).benchmarkIf(BENCHMARK, "Type") {
                Typer(module, builtinEnvironment).type(root)
            }

        Debug.Frontend.TypeStage.AfterTyper.snapshot(configKey, AstSnapshotKey, root)
        Debug.Frontend.TypeStage.AfterTyper(configKey).doIfLogs { console ->
            dumpMissingTypeInfo(root, "After typer", console)
        }

        // With type info, we can finalize `else` values.
        Debug.Frontend.TypeStage.ReplaceVoidishPanics(configKey)
            .benchmarkIf(BENCHMARK, "ReplaceVoidishPanics") {
                replaceVoidishPanics(root)
            }
        Debug.Frontend.TypeStage.AfterReplaceVoidishPanics.snapshot(configKey, AstSnapshotKey, root)

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

        // Clean up temporaries introduced so we have a scrutable output.
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
                    inlineToRepairUnrealizedGoals(root, logSink)
                }
            Debug.Frontend.TypeStage.AfterRepairUnrealizedGoals.snapshot(configKey, AstSnapshotKey, root)

            Debug.Frontend.TypeStage.Weaver2(configKey)
                .benchmarkIf(BENCHMARK, "Weave2") {
                    Weaver.weave(
                        root,
                        // Make sure that failing expressions are not deeply nested so
                        // that backends can easily insert Result type testing and unpacking
                        // instructions.
                        sprinkleSecurityDust = true,
                        simplifyRttiCalls = false,
                        pullSpecialsRootward = true,
                        nameAllFunctions = false,
                        resultsAlreadyCaptured = true,
                    )
                    simplifyFlow(root, assumeAllJumpsResolved = false) calledFor effect
                }
            Debug.Frontend.TypeStage.AfterWeaver2.snapshot(configKey, AstSnapshotKey, root)
        }

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

/**
 * Replace void-like panics with either void or panic.
 * TODO Flow typing would be more general than this.
 */
private fun replaceVoidishPanics(root: BlockTree) {
    val expectedTreeSize = VoidishPanicFn.sigs.first().requiredInputTypes.size + 1
    TreeVisit.startingAt(root).forEach tree@{ tree ->
        tree is CallTree || return@tree VisitCue.Continue
        tree.size == expectedTreeSize || return@tree VisitCue.Continue
        tree.child(0).functionContained == VoidishPanicFn || return@tree VisitCue.Continue
        // From here down, we know we need either void or standard panic.
        val incoming = tree.incoming
        incoming?.replace {
            run exhaustive@{
                // Check fail bailing with null goes to void below.
                val foundType = tree.child(1).typeInferences?.type ?: return@exhaustive null
                val expectedType = tree.child(2).staticTypeContained ?: return@exhaustive null
                foundType is NominalType && expectedType is NominalType || return@exhaustive null
                // We don't support subtyping the same interface under different type actuals,
                // so simple subtyping is fine here. Any broken type actuals will cause static
                // type errors elsewhere.
                isSimpleSubtype(foundType, expectedType) || return@exhaustive null
                // Passed all checks, so panic it is.
                Call(BuiltinFuns.vPanic, tree.typeInferences) {}
            } ?: run {
                // Or bail here to void on check fails.
                V(void, WellKnownTypes.voidType)
            }
        }
        // Either way, these calls are effectively leaves, so don't bother with kids.
        VisitCue.SkipOne
    }.visitPreOrder() // Ok, because we never recurse target kids.
}

/** Just checks for the same type or a subtype, ignoring type actuals. */
private fun isSimpleSubtype(sub: NominalType, sup: NominalType): Boolean {
    sub.definition == sup.definition && return true
    return sub.definition.superTypes.any { isSimpleSubtype(it, sup) }
}
