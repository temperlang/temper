package lang.temper.interp

import lang.temper.env.InterpMode
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.lexer.isIdentifier
import lang.temper.log.LogEntry
import lang.temper.log.Position
import lang.temper.name.Symbol
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.ControlFlow
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.Panic
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.Result
import lang.temper.value.StaylessMacroValue
import lang.temper.value.StructuredFlow
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.freeTarget
import lang.temper.value.symbolContained
import lang.temper.value.void

/**
 * Makes it easy for control flow macros to walk over arguments, get references to parts that they
 * need and assemble a block with a complex flow from them.
 */
class MacroCursor(
    val macroEnvironment: MacroEnvironment,
) {
    private var i = 0
    private var rawTrees = macroEnvironment.args.rawTreeList

    /** True if the cursor is at the end of the argument list. */
    fun isEmpty() = nRemaining == 0

    val nRemaining get() = rawTrees.size - i

    fun peek(): TEdge? = if (nRemaining > 0) {
        rawTrees[i].incoming
    } else {
        null
    }

    /** Advances the cursor and returns true if the argument at the cursor is the symbol. */
    fun consumeSymbol(symbol: Symbol) =
        if (nRemaining > 0 && symbol == rawTrees[i].symbolContained) {
            i += 1
            true
        } else {
            false
        }

    /**
     * Consumes the current argument and returns it as a tree if it is a function node.
     * Else null.
     */
    fun rawBody(): TEdge? {
        if (nRemaining > 0) {
            val t = rawTrees[i]
            if (t is FunTree) {
                val fp = t.parts
                if (fp?.formals?.isEmpty() == true) {
                    i += 1
                    return fp.body.incoming
                }
            }
        }
        return null
    }

    fun expectNameLeaf(): NameLeaf? = expectTree { it is NameLeaf } as NameLeaf?

    private inline fun expectTree(p: (Tree) -> Boolean) = if (nRemaining > 0) {
        val t = rawTrees[i]
        if (p(t)) {
            i += 1
            t
        } else {
            null
        }
    } else {
        null
    }

    fun evaluate(interpMode: InterpMode): PartialResult = if (nRemaining > 0) {
        i += 1
        macroEnvironment.evaluateTree(rawTrees[i - 1], interpMode)
    } else {
        NotYet
    }

    /**
     * Consumes the current argument and returns it as a tree.  Null if no such tree.
     */
    fun nextTEdge(): TEdge? = if (nRemaining > 0) {
        val e = rawTrees[i].incoming
        i += 1
        e
    } else {
        null
    }

    private val childList = mutableListOf<TEdge>()
    fun referenceTo(wrappedEdge: TEdge): BlockChildReference {
        val i = childList.size
        childList.add(wrappedEdge)
        return BlockChildReference(i, wrappedEdge.target.pos)
    }

    internal fun referenceToVoid(pos: Position) = referenceToValue(pos, void)

    internal fun referenceToBoolean(pos: Position, b: Boolean) =
        referenceToValue(pos, TBoolean.value(b))

    internal fun referenceToValue(pos: Position, value: Value<*>): BlockChildReference {
        val edge = macroEnvironment.treeFarm.grow(pos) {
            Block(pos) {
                V(pos, value)
            }
        }.edge(0)
        val index = childList.size
        childList.add(edge)
        return BlockChildReference(index, pos)
    }

    internal fun dereference(ref: BlockChildReference): TEdge? {
        val index = ref.index ?: return null
        return childList[index]
    }

    /** Frees all referenced wrapped trees, and incorporates them into a block. */
    fun structuredBlock(controlFlow: ControlFlow.StmtBlock): BlockTree {
        val complexFlow = StructuredFlow(controlFlow)
        val children = mutableListOf<Tree>()
        for (childEdge in childList) {
            val child = childEdge.target
            freeTarget(childEdge)
            children.add(child)
        }
        return BlockTree(macroEnvironment.document, macroEnvironment.pos, children, complexFlow)
    }
}

abstract class ControlFlowTransform(
    override val name: String,
) : StaylessMacroValue, TokenSerializable, NamedBuiltinFun {
    protected abstract fun complicate(
        macroCursor: MacroCursor,
    ): ControlFlowReplacement?

    protected open val desugarsEarly get() = false

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult = when (interpMode) {
        InterpMode.Full -> tryEmulate(macroEnv)
        InterpMode.Partial -> {
            if (isEarly(macroEnv) && !desugarsEarly) {
                // Too soon
                NotYet
            } else {
                val cursor = MacroCursor(macroEnv)
                when (val replacement = complicate(cursor)) {
                    null -> Fail
                    is ControlFlowSubflow -> {
                        macroEnv.replaceMacroCallWith(
                            cursor.structuredBlock(
                                ControlFlow.StmtBlock.wrap(replacement.controlFlow),
                            ),
                        )
                        NotYet
                    }
                    is Desugaring -> {
                        macroEnv.replaceMacroCallWith(replacement.makeReplacement)
                        NotYet
                    }
                    is TransformFailed -> {
                        macroEnv.replaceMacroCallWithErrorNode(replacement.problem)
                        Fail(replacement.problem)
                    }
                }
            }
        }
    }

    // TODO: override in subtypes if necessary
    open fun tryEmulate(env: MacroEnvironment): Result =
        throw Panic()

    override val sigs: List<Signature2>? get() = null

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(
            OutputToken(
                name,
                if (isIdentifier(name)) {
                    OutputTokenType.Name
                } else {
                    OutputTokenType.Punctuation
                },
            ),
        )
    }

    sealed class ControlFlowReplacement

    data class ControlFlowSubflow(
        val controlFlow: ControlFlow,
    ) : ControlFlowReplacement()

    data class Desugaring(
        val makeReplacement: Planting.() -> Unit,
    ) : ControlFlowReplacement()

    data class TransformFailed(
        val problem: LogEntry,
    ) : ControlFlowReplacement()

    companion object {
        fun isEarly(macroEnv: MacroEnvironment) = macroEnv.stage < Stage.Type
    }
}
