package lang.temper.be.elixir

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.tmpl.BubbleBranchStrategy
import lang.temper.be.tmpl.ComputedJumpStrategy
import lang.temper.be.tmpl.CoroutineStrategy
import lang.temper.be.tmpl.FunctionTypeStrategy
import lang.temper.be.tmpl.OptionalSupportCodeKind
import lang.temper.be.tmpl.RepresentationOfVoid
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.SupportNetwork
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.NamedBuiltinFun

/**
 * Wires Temper builtins to Elixir.
 *
 * - Bubbles become exceptions: Elixir has `raise` and `rescue`, and a
 *   bubble that is not caught should crash the process the way an
 *   uncaught exception does.
 * - Function types stay functions: `fn ... end` closes over its scope.
 * - Elixir has no jump table, only `case` and `cond`, so computed jumps are
 *   never used.
 * - `nil` gives void a value to be.
 *
 * Coroutines use the frontend's state machine
 * (TranslateToRegularFunction): Elixir has no `yield`, and the obvious BEAM
 * answer, a process per generator, does not work here. Every mutable Temper
 * object, promises and the heap cells holding a coroutine's locals included,
 * is a TemperCore.Heap entry in the process dictionary, so it is visible only
 * to the process that made it. Generators and promises therefore stay in the
 * caller's process; `async` enqueues onto a run queue (TemperCore.Async) that
 * the library's `main/0` drains as its last statement.
 */
object ElixirSupportNetwork : SupportNetwork {
    override val backendDescription: String
        get() = "Elixir Backend"

    override val bubbleStrategy: BubbleBranchStrategy = BubbleBranchStrategy.Exceptions

    override val coroutineStrategy: CoroutineStrategy = CoroutineStrategy.TranslateToRegularFunction

    override val functionTypeStrategy: FunctionTypeStrategy = FunctionTypeStrategy.ToFunctionType

    override val computedJumpStrategy = ComputedJumpStrategy.NeverUse

    override fun representationOfVoid(genre: Genre): RepresentationOfVoid = RepresentationOfVoid.ReifyVoid

    /** Temper's builtin operators, as Elixir operators or temper-core calls. */
    override fun getSupportCode(pos: Position, builtin: NamedBuiltinFun, genre: Genre): SupportCode? = when {
        // the body of an abstract method: reaching it is a panic, not a bubble
        builtin.name == PureVirtual.connectedKey -> PureVirtual
        builtin.name == AwakeUpon.connectedKey -> AwakeUpon
        builtin.name == GetPromiseResultSync.connectedKey -> GetPromiseResultSync
        else -> builtin.builtinOperatorId?.let { elixirOperators[it] }
    }

    override fun optionalSupportCode(
        optionalSupportCodeKind: OptionalSupportCodeKind,
    ): Pair<SupportCode, Signature2>? = null

    /**
     * Where `console.log` is answered. A null here is what made every build
     * that logged anything fail with "Cannot translate value fn getConsole".
     */
    override fun translateConnectedReference(pos: Position, connectedKey: String, genre: Genre): SupportCode? =
        elixirConnected[connectedKey]

    override fun translatedConnectedType(
        pos: Position,
        connectedKey: String,
        genre: Genre,
        temperType: Type2,
    ): Pair<TargetLanguageTypeName, List<Type2>>? = null
}
