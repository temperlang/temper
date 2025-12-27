package lang.temper.value

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.log.FailLog
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.unknownPos
import lang.temper.stage.Readiness
import lang.temper.stage.Stage

/**
 * Provided by an interpreter to allows interpreting sub-expression in the context of that
 * interpreter.
 */
interface InterpreterCallback : Positioned {
    val failLog: FailLog
    val logSink: LogSink

    val stage: Stage

    val readiness: Readiness

    /**
     * True if the interpreter should try to evaluate expressions whose parts may be unsatisfied
     * because those parts are going out of style; failing while generating error messages that
     * point at the involved parts may be more useful than doing nothing.
     */
    val isPushy get() = readiness == Readiness.GoingOutOfStyle

    val promises: Promises?

    fun apply(f: Value<*>, args: ActualValues, interpMode: InterpMode): PartialResult

    fun explain(
        template: MessageTemplate,
        pos: Position = this.pos,
        values: List<Any> = emptyList(),
        pinned: Boolean = false,
    ) = failLog.explain(template, pos = pos, values = values, pinned = pinned)

    fun explain(problem: LogEntry, pinned: Boolean = false) =
        failLog.explain(problem = problem, pinned = pinned)

    fun fail(
        template: MessageTemplate,
        pos: Position = this.pos,
        values: List<Any> = emptyList(),
    ): Fail {
        explain(template, pos = pos, values = values, pinned = true)
        return Fail(LogEntry(template = template, pos = pos, values = values, level = Log.Error))
    }

    /** Seems [fail] doesn't always report, so use this one if needed. */
    fun failer(
        template: MessageTemplate,
        pos: Position = this.pos,
        values: List<Any> = emptyList(),
    ): Fail {
        failLog.fail(template, pos = pos, values = values)
        return Fail(LogEntry(template = template, pos = pos, values = values, level = Log.Error))
    }

    /**
     * Sometimes it's convenient to define a macro value in a common place, like *BuiltinFuns*, but
     * have it delegate its implementation to something defined elsewhere.
     *
     * This helps avoid dependency and initialization cycles.  For example, the `:interp` subproject
     * may define `class` and `interface` macros once which delegate to the stage-aware feature
     * implementations provided by the `:frontend` subproject without dependency cycles between
     * those subprojects, and without each of `:frontend`s stage-specific macro environments
     * providing a different implementation of `class` and `interface`.
     *
     * It also lets us define things like `print` to work generically via the compiler log but
     * override when an interpreter is run in a context that allows for tight integration with
     * an IDE console.
     *
     * @param key an internal compiler name that is used to lookup in a scope provided by the
     *     host environment to the interpreter.
     */
    fun getFeatureImplementation(key: InternalFeatureKey): Result

    object NullInterpreterCallback : InterpreterCallback {
        override val failLog: FailLog = FailLog.NullFailLog
        override val logSink: LogSink = LogSink.devNull
        override val stage: Stage = Stage.Run
        override val readiness: Readiness = Readiness.Unready
        override fun apply(f: Value<*>, args: ActualValues, interpMode: InterpMode) = NotYet
        override fun getFeatureImplementation(key: InternalFeatureKey): Result = Fail
        override val pos: Position = unknownPos
        override val promises: Promises? = null
    }
}

/**
 * A key that identifies a function that exposes some interpreter or compiler internals so that
 * this module can avoid deep references to those low-level modules.
 */
typealias InternalFeatureKey = String
