package lang.temper.frontend.generate

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.benchmarkIf
import lang.temper.common.calledFor
import lang.temper.common.effect
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.CleanupTemporaries
import lang.temper.frontend.MagicSecurityDust
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.Weaver
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.frontend.simplifyFlow
import lang.temper.frontend.typestage.Typer
import lang.temper.interp.ReplacementPolicy
import lang.temper.interp.docgenalts.DocGenAltFn
import lang.temper.interp.restorePreserved
import lang.temper.lexer.Genre
import lang.temper.log.ConfigurationKey
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.log.snapshot
import lang.temper.name.BuiltinName
import lang.temper.name.ResolvedName
import lang.temper.stage.Stage
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.LinearFlow
import lang.temper.value.RightNameLeaf
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf
import lang.temper.value.functionContained
import lang.temper.value.reifiedTypeContained
import lang.temper.value.void

private const val BENCHMARK = false

/**
 * A stage that runs just before module contents is passed to backends.  It makes sure that all the
 * ducks are in a row; that the TmpL translator will be able to recreate a statement / expression
 * layering without introducing temporaries.
 */
class GenerateCodeStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
) {
    private val configKey = root.configurationKey

    fun process(callback: (outputs: StageOutputs) -> Unit) {
        val ckey: ConfigurationKey = module
        val outputs = Debug.Frontend.GenerateCodeStage(ckey).group("Generate Code Stage") {
            interpretiveDanceStage(
                stage = Stage.GenerateCode,
                root = root,
                failLog = failLog,
                logSink = logSink,
                module = module,
                beforeInterpretation = { root, _ ->
                    doBeforeInterpretation(root)
                },
                afterInterpretation = { (root), _ ->
                    doAfterInterpretation(root)
                },
            )
        }
        callback(outputs)
    }

    private fun doBeforeInterpretation(root: BlockTree) {
        Debug.Frontend.GenerateCodeStage.Before.snapshot(root, AstSnapshotKey, root)
    }

    private fun doAfterInterpretation(root: BlockTree) {
        val genre = root.document.context.genre

        Debug.Frontend.GenerateCodeStage.AfterInterpretation
            .snapshot(configKey, AstSnapshotKey, root)

        flipDeclaredNames(root)

        if (genre != Genre.Documentation) {
            // Make failure explicit
            val newFailures: Set<ResolvedName> =
                Debug.Frontend.GenerateCodeStage.MagicSecurityDust(configKey)
                    .benchmarkIf(BENCHMARK, "MagicSecurityDust") {
                        val magicSecurityDust = MagicSecurityDust()
                        magicSecurityDust.sprinkle(root) calledFor effect
                        magicSecurityDust.failureVariables
                    }

            Debug.Frontend.GenerateCodeStage.AfterSprinkle.snapshot(configKey, AstSnapshotKey, root)

            Debug.Frontend.GenerateCodeStage.Weaver(root).benchmarkIf(BENCHMARK, "Weaver") {
                Weaver.weave(
                    module,
                    root,
                    pullSpecialsRootward = true,
                    nameAllFunctions = true,
                    failureConditionNeedsChecking = { nameLeaf ->
                        nameLeaf.content in newFailures
                    },
                ) calledFor effect
            }

            Debug.Frontend.GenerateCodeStage.AfterWeave.snapshot(configKey, AstSnapshotKey, root)
        }

        val builtinEnvironment = module.freeNameEnvironment!!
        Debug.Frontend.GenerateCodeStage.Type(configKey).benchmarkIf(BENCHMARK, "Type") {
            Typer(module, builtinEnvironment).type(root)
        }

        Debug.Frontend.GenerateCodeStage.AfterTyper.snapshot(configKey, AstSnapshotKey, root)

        // The Typer replaces failure variables with false when the use is determined
        // to be safe.  Simplify the flow graph for backends.
        Debug.Frontend.GenerateCodeStage.SimplifyFlow(configKey)
            .benchmarkIf(BENCHMARK, "SimplifyFlow") {
                simplifyFlow(
                    root,
                    assumeAllJumpsResolved = false,
                    assumeResultsCaptured = true,
                ) calledFor effect
            }

        Debug.Frontend.GenerateCodeStage.AfterSimplifyFlow.snapshot(configKey, AstSnapshotKey, root)

        Debug.Frontend.GenerateCodeStage.TypeCheck(configKey).benchmarkIf(BENCHMARK, "TypeCheck") {
            TypeChecker(module).check(root)
        }

        Debug.Frontend.GenerateCodeStage.AfterTypeCheck.snapshot(configKey, AstSnapshotKey, root)

        UnicodeScalarChecker(module).check(root)

        if (genre != Genre.Documentation) {
            Debug.Frontend.GenerateCodeStage.CleanupTemporaries(configKey)
                .benchmarkIf(BENCHMARK, "CleanupTemporaries") {
                    CleanupTemporaries.cleanup(
                        module,
                        root,
                        beforeResultsExplicit = false,
                        outputName = module.outputName,
                        snapshotId = Debug.Frontend.TypeStage.CleanupTemporaries,
                    ) calledFor effect
                }
        } else {
            eliminateVoids(root)
        }

        Debug.Frontend.GenerateCodeStage.SimplifyFlow2(configKey)
            .benchmarkIf(BENCHMARK, "TrimLooseThreads") {
                simplifyFlow(
                    root,
                    assumeAllJumpsResolved = true,
                    assumeResultsCaptured = true,
                ) calledFor effect
            }

        root.restorePreserved whichToUse@{ preserved, reduced ->
            if (preserved.isSimpleStringConcatenation) {
                // cat("foo") -> "foo"
                return@whichToUse ReplacementPolicy.Discard
            }

            if (preserved is RightNameLeaf && preserved.content is BuiltinName) {
                return@whichToUse ReplacementPolicy.Discard
            }

            val fn = reduced.functionContained
            // We need doc-gen alternates, not the names for them.
            if (fn is DocGenAltFn && preserved is RightNameLeaf) {
                return@whichToUse ReplacementPolicy.Discard
            }
            val reducedType = reduced.reifiedTypeContained
            // We need type expressions collapsed
            if (reducedType != null) {
                return@whichToUse ReplacementPolicy.Discard
            }

            ReplacementPolicy.Preserve
        }

        ReachabilityTracer().markReachability(root)
        ExportChecker(module).checkExports()

        Debug.Frontend.GenerateCodeStage.After.snapshot(configKey, AstSnapshotKey, root)
    }
}

/**
 * True for the tree structures produced by the TreeBuilder for simple strings
 * like
 *
 *     "foo"
 *
 * which, because of details of how we process AST parts, actually comes out
 * like
 *
 *     cat("foo")
 */
private val Tree.isSimpleStringConcatenation: Boolean
    get() {
        if (this is CallTree && this.size == 2) { // size == 2 for cat and "string"
            val callee = child(0)
            val arg0 = child(1)
            if (
                callee.functionContained == BuiltinFuns.strCatFn &&
                arg0 is ValueLeaf && arg0.content.typeTag == TString
            ) {
                return true
            }
        }
        return false
    }

fun eliminateVoids(tree: Tree) {
    TreeVisit.startingAt(tree)
        .forEachContinuing {
            if (tree is BlockTree && tree.flow is LinearFlow) {
                for (childIndex in tree.size - 1 downTo 0) {
                    val child = tree.child(childIndex)
                    if (child is ValueLeaf && child.content == void) {
                        tree.removeChildren(childIndex..childIndex)
                    }
                }
            }
        }
        .visitPostOrder()
}
