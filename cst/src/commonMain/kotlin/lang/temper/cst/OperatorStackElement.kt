package lang.temper.cst

import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.lexer.TokenType
import lang.temper.lexer.closeBrackets
import lang.temper.lexer.openBrackets

/** A partially built [ConcreteSyntaxTree] */
interface OperatorStackElement {
    val operator: Operator
    val tokenText: String?
    val tokenType: TokenType?

    val childCount: Int

    // The child count once the stack has been committed
    val eventualChildCount: Int get() = childCount

    fun child(i: Int): OperatorStackElement
}

interface LeafOperatorStackElement : OperatorStackElement {
    override val tokenText: String
    override val tokenType: TokenType

    override val childCount get() = 0
    override fun child(i: Int): OperatorStackElement = throw NoSuchElementException()
}

interface InnerOperatorStackElement : OperatorStackElement {
    override val tokenText: String? get() = null
    override val tokenType: TokenType? get() = null
}

fun (OperatorStackElement).isEmpty(): Boolean = childCount == 0
fun (OperatorStackElement).isNotEmpty() = !isEmpty()

/**
 * A tree "needs" a close bracket if it has an open bracket like '('
 * without a corresponding ')'.
 *
 * This tests whether the count of open brackets exceeds the count
 * of close brackets without worrying about whether open parenthesis ('(')
 * pairs properly close square (']').
 *
 * For example, these need a close
 *   foo(x   // An incomplete application of an infix bracket operator
 *   [       // An incomplete prefix bracket operation
 *   { stmt  // Another incomplete prefix bracket operation with one operand.
 * but these do not
 *   foo(x)
 *   []
 *   { stmt }
 *   [ 0 , 1 )
 *   ( ) )   // Extra closed does not need a close
 */
fun needsCloseBracket(stackElement: OperatorStackElement) =
    // TODO: Can this be made O(1) by just committing anything that has its close bracket, and
    // doing a simple check of the operator property?
    openBracketCount(stackElement) > 0

/**
 * True if the given stack element needs an operand, assuming there is no uncommitted stack element
 * above it on the stack.
 */
fun needsOperand(stackElement: OperatorStackElement) = when (stackElement.operator.operatorType) {
    OperatorType.Infix, OperatorType.Prefix -> {
        // If the last element is the token for the operator, then it needs an operand.
        val lastIndex = stackElement.childCount - 1
        lastIndex < 0 || stackElement.child(lastIndex).tokenText != null
    }
    else -> false
}

/**
 * The count of open brackets minus the count of close brackets.
 *
 * Returns resultIfNegative if it is not undefined and any prefix
 * of tokens contains more close brackets than open brackets.
 */
fun openBracketCount(e: OperatorStackElement, resultIfNegative: Int? = null): Int {
    val op = e.operator

    if (!op.closer) {
        // This returns a sensible value for non bracket operators, including
        // infix '<' (less than operator) which needs to be treated differently
        // from angle brackets used in types like `T<X>`.
        return 0
    }

    var count = 0
    for (i in 0 until e.childCount) {
        val child = e.child(i)
        val tokenText = child.tokenText
        val tokenType = child.tokenType
        if (tokenType == TokenType.RightDelimiter || tokenText in closeBrackets) {
            count -= 1
            if (count < 0 && resultIfNegative != null) {
                return resultIfNegative
            }
        } else if (tokenType == TokenType.LeftDelimiter || tokenText in openBrackets) {
            count += 1
        }
    }
    return count
}
