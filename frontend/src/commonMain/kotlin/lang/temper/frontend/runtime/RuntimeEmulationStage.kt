package lang.temper.frontend.runtime

import lang.temper.env.InterpMode
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.TopLevelBindings
import lang.temper.frontend.findExportsAndDeclaredTypes
import lang.temper.interp.Interpreter
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.name.ResolvedName
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BlockTree
import lang.temper.value.Fail
import lang.temper.value.InternalFeatureKey
import lang.temper.value.InterpreterCallback
import lang.temper.value.Promises
import lang.temper.value.TBoolean
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.infoOr
import lang.temper.value.void

internal class RuntimeEmulationStage(
    private val module: Module,
    private val topLevelBindings: TopLevelBindings,
    private val outputName: ResolvedName?,
    private val ast: Tree,
    private val failLog: FailLog,
    private val logSink: LogSink,
    private val continueCondition: () -> Boolean,
    private val features: Map<InternalFeatureKey, Value<*>>,
    private val connecteds: Map<String, (Signature2) -> Value<*>>,
    private val promises: Promises?,
) {
    fun process(): StageOutputs {
        val root = BlockTree.maybeWrap(ast)
        val nameMaker = root.document.nameMaker
        val ownsPromises = promises == null

        val interpreter = Interpreter(
            failLog,
            logSink,
            Stage.Run,
            nameMaker,
            continueCondition,
            features,
            connecteds = connecteds,
            promises = promises ?: Promises(),
        )

        val allowTopLevelAwait = TBoolean.valueTrue == topLevelBindings[
            StagingFlags.allowTopLevelAwait,
            InterpreterCallback.NullInterpreterCallback,
        ]

        val interpreterResult = interpreter.interpretAndWaitForAsyncTasksToSettle(
            root,
            topLevelBindings,
            InterpMode.Full,
            mayWrapEnvironment = false,
            allowTopLevelAwait = allowTopLevelAwait,
            // If the promises came in from outside, let the creator decide
            // when to warn.
            warnAboutUnresolved = ownsPromises,
        ).infoOr { Fail }
        val moduleResult = if (outputName != null) {
            topLevelBindings[outputName, InterpreterCallback.NullInterpreterCallback]
        } else {
            // If we're not interpreting in a REPL-like context, then use void as the
            // result.
            void
        }
        val (exports, declaredTypeShapes) =
            findExportsAndDeclaredTypes(module, root, topLevelBindings, Stage.Run)

        // If interpretation failed, use that.  Otherwise, use the value
        // assigned to the output variable.
        val result = when (interpreterResult) {
            is Value<*> -> moduleResult
            is Fail -> interpreterResult
        }

        return StageOutputs(
            root = root,
            result = result,
            exports = exports,
            declaredTypeShapes = declaredTypeShapes,
        )
    }
}
