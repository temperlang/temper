@file:Suppress("MatchingDeclarationName") // This file is named after the function.

package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.interp.Interpreter
import lang.temper.interp.ReplacementPolicy
import lang.temper.interp.checkInterpreterReachedAll
import lang.temper.interp.importExport.Export
import lang.temper.lexer.Genre
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.stage.Stage
import lang.temper.value.BlockTree
import lang.temper.value.InnerTree
import lang.temper.value.NameLeaf
import lang.temper.value.PartialResult
import lang.temper.value.PostPass
import lang.temper.value.StayLeaf
import lang.temper.value.StaySink
import lang.temper.value.Tree
import lang.temper.value.ValueLeaf

/** Bundles the root with some context for the interpreter. */
data class InterpretationContext(
    val root: BlockTree,
)

/**
 * Performs a [Stage] advancement by invoking the interpreter along with other things.
 *
 * Specifically this:
 *
 * 1. does some [before-macro-code work][beforeInterpretation] on the [root],
 * 2. does some [after-macro-code work][afterInterpretation] on the AST as modified by macros
 * 3. bundles up the results
 */
internal fun interpretiveDanceStage(
    /**
     * The stage given to the interpreter and which is visible to macros.
     * Macro and interpreter code may assume that the document is unresolved when
     * stage <= [Stage.SyntaxMacro] and is resolved otherwise.
     */
    stage: Stage,
    /**
     * The input AST.  This may be mutated in place by before and after steps and macros.
     * This is a block so that every macro call has an incoming edge and passes may assume
     * that any non-block tree has an incoming edge.
     */
    root: BlockTree,
    /**
     * Receives error messages from the interpreter and macros.
     */
    failLog: FailLog,
    /**
     * Receives diagnostics from the interpreter and macros.
     */
    logSink: LogSink,
    /** The module being advanced */
    module: Module,
    /** Compute the AST which will actually be interpreted. */
    beforeInterpretation: (root: BlockTree, env: Environment) -> Unit,
    /**
     * Given the AST that was modified in-place by any macros run by the interpreter, produce the
     * output AST.
     */
    afterInterpretation:
    (iCtx: InterpretationContext, result: PartialResult) -> Unit,
): StageOutputs {
    val features = module.features
    val connecteds = module.connecteds
    val continueCondition = module.continueCondition

    val genre = root.document.context.genre

    val env = module.topLevelBindings!!
    env.clearBeforeStaging()

    beforeInterpretation(root, env)

    adjustDeclarationMetadataWithSinglyAssignedHints(root)

    val replacementPolicy = when (genre) {
        Genre.Library -> ReplacementPolicy.Discard
        Genre.Documentation -> ReplacementPolicy.Preserve
    }

    val nameMaker = root.document.nameMaker
    val postPasses = mutableSetOf<PostPass>()
    val interpreter = Interpreter(
        failLog = failLog,
        logSink = logSink,
        stage = stage,
        nameMaker = nameMaker,
        continueCondition = continueCondition,
        features = features,
        connecteds = connecteds,
        postPasses = postPasses,
        replacementPolicy = replacementPolicy,
    )

    val result = interpreter.interpretReuseEnvironment(
        root,
        env,
        InterpMode.Partial,
        mayWrapEnvironment = false,
    )

    checkInterpreterReachedAll(root, stage, logSink)

    for (postPass in postPasses) {
        postPass.rewrite(root)
    }

    afterInterpretation(InterpretationContext(root), result)

    val (exports, declaredTypeShapes) =
        findExportsAndDeclaredTypes(module, root, env, stage)

    // Fail loudly on violations of the "every StayLeaf stays" invariant.
    // TODO: We should probably pass all exports exported at any stage.  checkStayLeaves only
    // collects same-document stays so each Module must commit to preserving all stays that ever
    // escape it.
    val (stayLeavesPresent, stayLeavesReferenced) = checkStayLeaves(root, exports)
    check(stayLeavesPresent.containsAll(stayLeavesReferenced)) {
        val missing = stayLeavesReferenced.toMutableSet()
        missing.removeAll(stayLeavesPresent)
        "Missing stay leaves: $missing in ${root.document.context.namingContext.loc}"
    }

    return StageOutputs(
        root = root,
        result = result,
        exports = exports,
        declaredTypeShapes = declaredTypeShapes,
    )
}

private fun checkStayLeaves(
    root: Tree,
    exports: List<Export>,
): Pair<Set<StayLeaf>, Set<StayLeaf>> {
    val referenced = StaySink(root.document)
    exports.forEach { it.addStays(referenced) }
    val present = mutableListOf<StayLeaf>()
    TreeVisit.startingAt(root)
        .forEach {
            when (it) {
                is StayLeaf -> present.add(it)
                is ValueLeaf -> it.content.addStays(referenced)
                is InnerTree -> Unit // Handled by continuing visit
                is NameLeaf -> Unit // No stays here
            }
            VisitCue.Continue
        }
        .visitPreOrder()
    return present.toSet() to referenced.allStays
}
