package lang.temper.value

import lang.temper.common.Log
import lang.temper.env.DeclarationBits
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.env.ReferentBitSet
import lang.temper.log.ConfigurationKey
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.NameMaker
import lang.temper.name.TemperName
import lang.temper.type2.AnySignature
import lang.temper.type2.Signature2

/** How a macro interacts with its environment */
interface MacroEnvironment : InterpreterCallback, Positioned, ConfigurationKey.Holder {
    /**
     * A tree referencing the macro called.
     * If [call] is non-null then this is the zero-th child as long as the AST has not been mutated.
     * It retains its value even after mutation of the [call].
     * If [call] is null, this is a stub value.
     */
    val callee: Tree

    /**
     * If the macro call is rooted in an AST, the call.
     * Access to this may be logged.
     *
     * It may be null when one macro [dispatches][MacroEnvironment.dispatchCallTo] a call to
     * another.
     */
    val call: CallTree?

    val document: Document get() = callee.document

    val args: MacroActuals

    /**
     * Invokes the interpreter on the given tree.
     * @param tree must have come from this macro environment.
     */
    fun evaluateTree(tree: Tree, interpMode: InterpMode): PartialResult

    /**
     * Invokes the interpreter on the given edge.
     * @param edge must have come from this macro environment.
     */
    fun evaluateEdge(edge: TEdge, interpMode: InterpMode): PartialResult =
        evaluateTree(edge.target, interpMode)

    /**
     * Declares a local variable with the name from [nameLeaf] and the given initial value.
     */
    fun declareLocal(
        nameLeaf: Tree,
        declarationBits: DeclarationBits,
    ): PartialResult

    /**
     * Replaces the macro invocation itself with the given tree.
     *
     * Do not call if the call is null; it's a runtime error to try to replace a macro call that
     * isn't rooted in the AST.
     */
    fun replaceMacroCallWith(replacement: Tree)

    /**
     * Replaces the macro invocation itself with the result of the given function apply to the
     * macro call itself, but after the call has been unlinked so is available to embed.
     */
    fun replaceMacroCallWith(
        makeReplacement: (Planting).() -> Unit,
    )

    /**
     * Replaces a tree that contains the macro call.
     * After the replacement has been placed, macro traversal will
     * re-walk the replacement to make sure there are no unprocessed
     * subtrees.
     */
    fun replaceMacroCallAncestorWith(
        edge: TEdge,
        makeReplacement: (Planting).() -> Unit,
    )

    fun replaceMacroCallWithErrorNode()

    fun replaceMacroCallWithErrorNode(cause: LogEntry)

    /**
     * Schedules a pass that receives the body after [replacements][replaceMacroCallWith] are done.
     * If the same post pass (according to equals/hashCode) is specified multiple times while expanding
     * macros, then it is only applied once.
     */
    fun runAfterReplacement(postPass: PostPass)

    /** Allows creating name leaves in a way appropriate to the current stage. */
    val nameMaker: NameMaker get() = document.nameMaker

    /**
     * Any label associated with the call.
     *
     * A name corresponding to `foo` when the macro is invoked thus:
     *
     *     foo: macro() { ... }
     */
    val label: TemperName?

    /**
     * Consume [label] so that it will not attach to anything else.
     */
    fun consumeLabel()

    /** Sets a previously declared local variable. */
    fun setLocal(nameLeaf: LeftNameLeaf, newValue: Value<*>): Result

    /** Reads a local variable. */
    fun getLocal(nameLeaf: RightNameLeaf): PartialResult

    /** The completeness of the named environment binding. */
    fun completeness(nameLeaf: NameLeaf): ReferentBitSet?

    /** Logs on behalf of the macro. */
    fun log(message: String, level: Log.Level = Log.Info)

    /** Logs on behalf of the macro. */
    fun log(level: Log.Level, messageTemplate: MessageTemplate, pos: Position, values: List<Any>)

    /** Executes [action] in a scope where the interpreter acts as if it's going out of style. */
    fun <T> goingOutOfStyle(action: () -> T): T

    /** The lexically containing function or module definition if any. */
    fun innermostContainingScope(t: Tree = this.callee): Tree

    /**
     * Dispatch a call to a function using the interpreter's machinery to match arguments to
     * named or positional formal parameters.
     *
     * The trees do not need to be reachable from the same root but must share the same document.
     */
    fun dispatchCallTo(
        calleeTree: Tree,
        callee: Value<*>,
        argTrees: List<Tree>,
        interpMode: InterpMode,
    ): PartialResult

    /**
     * Make a best effort to run any macros in children early as necessary to convert child trees
     * to fit one of the given function types which may use [StructureExpectation]s.
     */
    fun orderChildMacrosEarly(signatures: List<AnySignature>)

    /**
     * The environment in which the macro is being evaluated.
     * Access to this will be logged, so macros should attempt to use [getLocal] and related
     * where possible.
     */
    val environment: Environment

    val treeFarm get() = document.treeFarm

    /**
     * Connections for example to connect functions in Implicits to backing Kotlin code
     * based on decorations like `@connected("Type::member")`.
     */
    fun connection(connectedKey: String): ((Signature2) -> Value<*>)?

    /**
     * True if the macro call is in the Implicits module.
     * This lets us avoid some chicken-egg problems when dealing with bootstrapping the Implicits
     * modules, and also lets us do printf-debugging without logging uses of macros in the
     * Implicits module.
     */
    val isProcessingImplicits: Boolean get() = false

    override val promises: Promises

    override val configurationKey: ConfigurationKey
        get() = document.context.configurationKey
}
