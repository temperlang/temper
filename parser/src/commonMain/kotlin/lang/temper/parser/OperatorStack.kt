package lang.temper.parser

import lang.temper.common.Log
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf
import lang.temper.cst.InnerOperatorStackElement
import lang.temper.cst.LeafOperatorStackElement
import lang.temper.cst.OperatorStackElement
import lang.temper.lexer.Operator
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.CodeLocation
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.Positioned
import kotlin.math.max
import kotlin.math.min

internal sealed class PositionedOperatorStackElement : OperatorStackElement, Positioned

internal data class TokenStackElement(
    val temperToken: TemperToken,
    val mayPrefix: Boolean = true,
    val mayInfix: Boolean = true,
) : PositionedOperatorStackElement(), LeafOperatorStackElement {
    override val operator: Operator get() = Operator.Leaf
    override val tokenText: String get() = temperToken.tokenText
    override val tokenType: TokenType get() = temperToken.tokenType
    val mayBracket: Boolean get() = temperToken.mayBracket
    override val pos get() = temperToken.pos

    override fun toString() = "$temperToken"
}

/**
 * A partial parse tree is a nested structure where leaves are tokens.
 */
private class StackElement(
    override var operator: Operator,
    val loc: CodeLocation,
) : PositionedOperatorStackElement(), InnerOperatorStackElement {
    /** once known, index into the source of the start of leftmost token contained */
    var left: Int = -1

    /** once known, index into the source just past the end of rightmost token contained */
    var right: Int = -1

    override val pos get() = Position(loc, left, right)

    var children = mutableListOf<PositionedOperatorStackElement>()
    var isTop = false

    override val childCount get() = children.size
    override fun child(i: Int) = children[i]
    override val eventualChildCount get() = children.size + if (isTop) { 0 } else { 1 }

    override fun toString() = "StackElement(operator=$operator, children=$children)"

    fun add(child: PositionedOperatorStackElement) {
        children.add(child)
        val childPos = child.pos
        left = if (left < 0) childPos.left else min(left, childPos.left)
        right = max(right, childPos.right)
    }
}

/**
 * A stack of partially parsed operators parsed by an operator precedence parser.
 *
 * This allows
 * - adding an operand to an operator on the stack
 * - closing/committing an operator that has been completed
 * - folding the top of the stack into a newly recognized infix operator as its left operand
 *
 * See [TreeBuilder] for how these work together to build a tree.
 */
internal class OperatorStack(val codeLocation: CodeLocation, private val logSink: LogSink) {
    private val stack = mutableListOf<StackElement>()

    init {
        val rootElement = StackElement(Operator.Root, codeLocation)
        rootElement.isTop = true
        stack.add(rootElement)
    }

    operator fun get(i: Int): PositionedOperatorStackElement = stack[i]
    fun getOrNull(i: Int): PositionedOperatorStackElement? = stack.getOrNull(i)
    val size: Int get() = stack.size
    val indices get() = stack.indices
    fun last(): PositionedOperatorStackElement = stack.last()
    val lastIndex: Int get() = stack.size - 1

    /**
     * Truncate the stack by folding elements into their parents.
     * We do this lazily so that we can handle infix operations by
     * inserting them into the stack.
     *
     * After a call to commitTo(n) the length of the stack will be n.
     */
    fun commitTo(depth: Int) {
        var n = stack.size
        while (n > depth) {
            val parentIndex = n - 2
            if (parentIndex < 0) {
                break
            }
            val childIndex = n - 1
            // Not actually detached, but we take care to notify the listener
            val child = stack[childIndex]
            child.isTop = false
            stack[parentIndex].add(child)
            checkElementComplete(child)

            n -= 1
        }
        stack.subList(n, stack.size).clear()
        stack[n - 1].isTop = true
    }

    /** Pushes a new operator onto the stack that initially has zero operands. */
    fun push(pos: Position, operator: Operator) {
        val newTop = StackElement(
            loc = pos.loc,
            operator = operator,
        )
        newTop.left = pos.left
        newTop.right = pos.right

        val oldTop = stack.last()
        stack.add(newTop)
        oldTop.isTop = false
        newTop.isTop = true
    }

    /**
     * Removes the top of the stack and folds it into a new top as the first operand.
     * This happens when we recognize that an infix operator follows an operand.
     *
     * @param nToAdopt number to adopt from the grandparent into the new parent of top.
     */
    fun swapTopIntoNew(operator: Operator, nToAdopt: Int = 0) {
        val lastIndex = stack.size - 1
        val oldTop = stack[lastIndex]
        val newTop = StackElement(operator, codeLocation)
        checkElementComplete(oldTop)
        if (nToAdopt > 0) {
            val siblingsOfOldTop = stack[lastIndex - 1].children
            val nSiblings = siblingsOfOldTop.size
            val siblingsToAdopt = siblingsOfOldTop.subList(nSiblings - nToAdopt, nSiblings)
            newTop.children.addAll(siblingsToAdopt)
            siblingsToAdopt.clear()
        }
        newTop.add(oldTop)
        stack[lastIndex] = newTop
        newTop.isTop = true
        oldTop.isTop = false
    }

    /** Adds the token to the top operator as an operand. */
    fun addToTop(tokenStackElement: TokenStackElement) {
        val top = stack.last()
        top.add(tokenStackElement)
    }

    /**
     * Get the processed root of the CST.
     * Parsing is followed by a post-processing step which may result in
     * messages logged to [logSink] to:
     *
     * - remove incidental white-space from string literals including that
     *   at the end of lines of multi-line strings and the indentation leading
     *   up to the close quote.
     * - remove empty interpolations which serve as a meta-character disabling
     *   mechanism in string templates.
     */
    fun finishRoot(): CstInner {
        require(stack.size == 1)
        val root = stack.removeAt(0)
        checkElementComplete(root)
        root.isTop = false

        val el = if (root.children.size == 1) {
            (root.children[0] as? StackElement) ?: root
        } else {
            root
        }

        return buildCst(el)
    }

    private fun checkElementComplete(child: StackElement) {
        val arity = arityOf(child)
        val childOperator = child.operator
        if (arity in childOperator.minArity..childOperator.maxArity) {
            return
        }
        val arityBound: Int
        val message = if (arity < childOperator.minArity) {
            arityBound = childOperator.minArity
            MessageTemplate.TooFewOperands
        } else {
            arityBound = childOperator.maxArity
            MessageTemplate.TooManyOperands
        }
        val values = listOf(childOperator, arityBound, arity)
        val logEntry = LogEntry(level = Log.Error, template = message, pos = child.pos, values = values)
        logEntry.logTo(logSink)
    }

    internal fun overrideOperator(i: Int, newOperator: Operator) {
        stack[i].operator = newOperator
    }
}

private fun arityOf(el: StackElement): Int {
    var arity = 0
    var lastWasToken = true
    for (child in el.children) {
        if (child is TokenStackElement) {
            lastWasToken = true
        } else {
            if (lastWasToken) {
                arity += 1
            }
            lastWasToken = false
        }
    }
    return arity
}

private fun buildCst(el: StackElement): CstInner = CstInner(
    el.pos,
    el.operator,
    el.children.map { child ->
        when (child) {
            is StackElement -> buildCst(child)
            is TokenStackElement -> CstLeaf(child.temperToken)
        }
    },
)
