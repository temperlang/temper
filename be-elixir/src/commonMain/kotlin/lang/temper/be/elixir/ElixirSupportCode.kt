package lang.temper.be.elixir

import lang.temper.be.tmpl.InlineSupportCode
import lang.temper.be.tmpl.NamedSupportCode
import lang.temper.be.tmpl.TypedArg
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId

/**
 * Support code that becomes Elixir syntax at the call site, shaped like
 * be-blimp's and be-lua's inline support code.
 */
internal abstract class ElixirInlineSupportCode(
    /** The `@connected` key, e.g. `core.type Console.log()`. Doubles as the name hint. */
    val connectedKey: String,
    override val builtinOperatorId: BuiltinOperatorId? = null,
) : InlineSupportCode<Elixir.Tree, Any>, NamedSupportCode {

    override val baseName: ParsedName get() = ParsedName(connectedKey)

    override val needsThisEquivalent: Boolean get() = false

    override fun renderTo(tokenSink: TokenSink) = tokenSink.name(baseName, inOperatorPosition = false)

    /**
     * Builds the Elixir expression for one call. For a `@connected` method,
     * `args[0]` is the receiver and the declared parameters follow.
     */
    abstract fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr

    final override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<Elixir.Tree>>,
        returnType: Type2,
        translator: Any,
    ): Elixir.Tree = callFactory(pos, arguments.map { it.expr as Elixir.Expr })

    final override fun equals(other: Any?): Boolean =
        this === other || (other is ElixirInlineSupportCode && connectedKey == other.connectedKey)

    final override fun hashCode(): Int = connectedKey.hashCode()

    final override fun toString(): String = "ElixirSupportCode($connectedKey)"
}

/**
 * `core.getConsole()`.
 *
 * Elixir has no console object, and the only thing Temper does with this
 * value is call `log` on it, which [ConsoleLog] inlines while dropping the
 * receiver. So it is `nil`, as be-blimp's is.
 */
internal object GetConsole : ElixirInlineSupportCode("core.getConsole()") {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr = Elixir.NilLit(pos)
}

/**
 * `core.type Console.log()` becomes `IO.puts(message)`.
 *
 * `args` is `[receiver, message]`, and the receiver is [GetConsole]'s `nil`.
 * `IO.puts` writes the binary as it is and a newline, which is what
 * `console.log` of a string means; `IO.inspect` would quote it.
 */
internal object ConsoleLog : ElixirInlineSupportCode("core.type Console.log()") {
    override fun callFactory(pos: Position, args: List<Elixir.Expr>): Elixir.Expr =
        Elixir.RemoteCall(
            pos,
            module = Elixir.ModuleName(pos, listOf(Elixir.Id(pos, OutName("IO", null)))),
            fn = Elixir.Id(pos, OutName("puts", null)),
            args = listOf(args.last()),
        )
}

/** Every `@connected` key be-elixir understands. */
internal val elixirConnectedReferences: Map<String, ElixirInlineSupportCode> =
    listOf(
        ConsoleLog,
        GetConsole,
    ).associateBy { it.connectedKey }
