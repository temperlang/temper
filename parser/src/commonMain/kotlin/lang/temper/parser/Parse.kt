package lang.temper.parser

import lang.temper.common.console
import lang.temper.common.logIf
import lang.temper.cst.CstComment
import lang.temper.cst.CstInner
import lang.temper.cst.InnerOperatorStackElement
import lang.temper.cst.OperatorStackElement
import lang.temper.cst.canNest
import lang.temper.cst.needsCloseBracket
import lang.temper.cst.needsOperand
import lang.temper.lexer.Followers
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.lexer.TokenSource
import lang.temper.lexer.TokenType
import lang.temper.lexer.bracketPartners
import lang.temper.lexer.closeBrackets
import lang.temper.lexer.isAmbiguousCloser
import lang.temper.log.LogSink

private const val DEBUG = false
private inline fun debug(b: Boolean = DEBUG, message: () -> String) =
    console.logIf(b, message = message)

/**
 * A candidate stack element that is not part of the operator stack, but which can be used with
 * [canNest] as if it were.
 */
private data class Candidate(
    override val operator: Operator,
) : InnerOperatorStackElement {
    constructor(tokenStackElement: TokenStackElement, operator: Operator) : this(operator = operator) {
        add(tokenStackElement)
    }

    private val children = mutableListOf<OperatorStackElement>()
    override val childCount get() = children.size
    override fun child(i: Int): OperatorStackElement = children[i]

    private fun add(el: OperatorStackElement) { children.add(el) }
}

/**
 * Given a series of tokens with the structure produced by [lang.temper.lexer.Lexer],
 * returns a parse tree.
 */
fun parse(
    /** Tokens possibly including [ignorable][lang.temper.lexer.TokenType.ignorable] tokens. */
    rawTokens: TokenSource,
    /** Receives error messages related to parsing. */
    logSink: LogSink,
    /** If not null, receives comments preserved from [rawTokens]. */
    comments: MutableList<CstComment>? = null,
    tokenSourceAdapterBuilder: TokenSourceAdapterBuilder = TokenSourceAdapterBuilder(),
): CstInner {
    val tokenSource = tokenSourceAdapterBuilder.build(rawTokens, comments)

    // We maintain a stack as we consume tokens left to right.
    // operatorStack[i+1] should be added as an operand to operatorStack[i]
    // unless an infix or postfix operator subsumes it.
    //
    // Each stack element has the following properties:
    //   op:          an Operator
    //   tree:        the list of operands being built
    //   left, right: source position metadata
    val operatorStack = OperatorStack(rawTokens.codeLocation, logSink)

    var tokenStackElement: TokenStackElement? = null
    token_loop@
    while (true) {
        if (tokenStackElement != null) {
            // Consume the token from the last iteration through.
            // We do this at the top to avoid any chance of infinite recursion, and to make it clear
            // that every token gets added to the tree exactly once, and that the in-order traversal
            // of the built tree is the token stream (with synthetics).
            operatorStack.addToTop(tokenStackElement)
        }

        tokenStackElement = tokenSource.get() ?: break
        debug {
            "Parsing token `${tokenStackElement.tokenText
            }`: ${tokenStackElement.tokenType
            } mayPrefix=${tokenStackElement.mayPrefix
            } mayInfix=${tokenStackElement.mayInfix
            } mayBracket=${tokenStackElement.mayBracket}"
        }

        // The ordering in which we try strategies comes from a
        // preference to complete existing structures over
        // starting new structures.
        // This matters when a token might be interpretable as
        // *  both an infix operator or a ternary follower (':')
        // *  a prefix and postfix operator ('++', '--')
        // *  an infix and a prefix operator ('-', '+')
        // *  an infix operator and a close bracket ('>')

        val tok = tokenStackElement.tokenText

        if (tok == "=") {
            // Convert Operator.LowColon to Operator.HighColon if there's an equals sign seen while
            // it's open.
            // This is an ugly hack that allows us to properly handle:
            //     propertyName: PropertyType = initialValueExpression;
            // in a class body.  The first three tokens could be a prefix of a labeled statement,
            // but we do not allow labeling of un-bracketed assignment or comma expressions, so
            // we convert the LowColon to a HighColon which allows us to treat that as
            //     (propertyName: PropertyType) = initialValueExpression;
            // See Operator.LowColon for more details on how `:` is exceptionally weird.
            for (i in operatorStack.indices.reversed()) {
                val el = operatorStack[i]
                val op = el.operator
                if (op.precedence < Operator.LowColon.precedence || op.closer) {
                    break
                }
                if (op == Operator.LowColon) {
                    operatorStack.overrideOperator(i, Operator.HighColon)
                    break
                }
            }
        }

        // See if tok might be part of a ternary or other operator.
        if (tok in Operator.followsAny) {
            for (i in operatorStack.indices.reversed()) {
                val el = operatorStack[i]
                if (needsCloseBracket(el)) {
                    break
                }
                // The last should not be a token indicating that a follower can come next.
                // We need the element to be in a position to accept an operator, not an operand
                // or else, we should prefer a different interpretation for the current token.
                // There are two cases when we're ready for a following operator
                // - el is the top, and its last element is not a Token
                // - el is not the top, so an operand is ready to be committed into it.
                val lastIndex = el.childCount - 1
                if (
                    i + 1 < operatorStack.size ||
                    el.child(lastIndex) !is TokenStackElement
                ) {
                    // Count the number of tokens to figure out the follower index.
                    val followerIndex = (0..lastIndex).count {
                        el.child(it) is TokenStackElement
                    } - 1 // Subtract one since the token for el.operator is not a follower.
                    val op = el.operator
                    if (
                        followerIndex >= 0 &&
                        op.followers.mayFollowAtOperandIndex(
                            followerIndex,
                            tok,
                            tokenStackElement.tokenType,
                        )
                    ) {
                        operatorStack.commitTo(i + 1)
                        continue@token_loop
                    }
                }
            }
        }

        // See if we can close an unclosed bracket operation.
        if (tok in closeBrackets && tokenStackElement.tokenType.grammatical) {
            debug { "closer $tokenStackElement" }
            for (i in operatorStack.indices.reversed()) {
                val el = operatorStack[i]
                debug { ". i=$i : ${el.operator}" }
                val partner = bracketPartners[el.operator.text] ?: continue
                if (!needsCloseBracket(el)) {
                    // We leave closed stack elements on the stack in case they turn out to be
                    // the left operand to an infix operation.
                    continue
                }
                if (partner == tok) {
                    debug { ". closing" }
                    operatorStack.commitTo(i + 1)
                    continue@token_loop
                }
                if (isAmbiguousCloser(tok)) {
                    debug { ". breaking ambiguous" }
                    break
                }
            }
        }

        if (tokenStackElement.tokenType == TokenType.RightDelimiter) {
            // See if we can close a quoted group.
            var quotedGroupIndex = operatorStack.lastIndex
            if (operatorStack.getOrNull(quotedGroupIndex)?.operator == Operator.Leaf) {
                quotedGroupIndex -= 1
            } else {
                while (quotedGroupIndex >= 0) {
                    val el = operatorStack[quotedGroupIndex]
                    if (el.operator == Operator.QuotedGroup || needsCloseBracket(el)) {
                        break
                    }
                    quotedGroupIndex -= 1
                }
            }
            if (operatorStack.getOrNull(quotedGroupIndex)?.operator == Operator.QuotedGroup) {
                operatorStack.commitTo(quotedGroupIndex + 1)
                continue@token_loop
            }
        }

        if (trySeparators(operatorStack, tokenStackElement)) {
            continue@token_loop
        }

        val mayPrefix = tokenStackElement.mayPrefix
        val prefixOperatorsMatching =
            Operator.matching(tok, tokenStackElement.tokenType, OperatorType.Prefix)

        var mayInfix = tokenStackElement.mayInfix
        if (mayInfix && prefixOperatorsMatching.isNotEmpty()) {
            // Don't try as an infix operator if el is incomplete and tok could be a prefix operator
            // that would complete it.
            // When parsing
            //     x - -y
            // the second `-` might be interpreted as an infix operator or prefix.
            // If interpreted as an infix operator we'd go from a stack like
            //     DASH: (x) -
            //     ROOT:
            // to
            //     DASH: ((x) -) -
            //     ROOT:
            // where the incomplete binary operator is the left operand of the new subtraction.
            // Eventually, when we process "y" and commit the stack we'd end up with an expression
            // like
            //     (x - ?) - y
            // This check makes sure that the second `-` is interpreted as a prefix operator, so
            // that the stack after processing "y" and before committing looks like
            //     PRE_DASH: -(y)
            //     DASH:     (x) -
            //     ROOT:     ()
            // which commits to
            //     ROOT:     ((x) - (- (y)))

            // We only check the top stack element, since any others would presumably get an operand
            // when the higher depth is committed into them.
            val topIndex = operatorStack.lastIndex
            val top = operatorStack[topIndex]
            if (needsOperand(top)) {
                val grandParent = operatorStack.getOrNull(topIndex - 1)
                for (op in prefixOperatorsMatching) {
                    val candidate = Candidate(tokenStackElement, op)
                    if (canNest(grandParent = grandParent, parent = top, child = candidate)) {
                        mayInfix = false
                        break
                    }
                }
            }
        }

        // See if the token continues an existing stack element.
        // For infix and postfix operators, we're looking for a safe place to
        // insert in the middle of the stack.
        // (Yes, pedants, this means our stack isn't a stack.)
        //
        // We insert just below the bottommost stack element that could serve
        // as a left operand according to canNest.
        //
        // We take care not to break out of unclosed brackets.
        if (mayInfix) {
            // Synthetic '(...)' should not participate in function calling.
            // See lexical pre-parsing to handle strings that split on interpolations.
            for (opType in listOf(OperatorType.Postfix, OperatorType.Infix)) {
                if (tryOperatorsThatPrecedeAnOperand(operatorStack, tokenStackElement, opType)) {
                    continue@token_loop
                }
            }
        }

        // See if the token indicates that we need a new stack element.
        // Any prefix operator starts a new stack element, but first we may
        // pop any operations that cannot contain that prefix operator.
        if (mayPrefix && tryPrefixOperators(operatorStack, tokenStackElement, prefixOperatorsMatching)) {
            continue@token_loop
        }

        // If we've got a quotation, create a leaf for it.
        if (
            tokenStackElement.tokenType == TokenType.LeftDelimiter &&
            tryPrefixOperators(operatorStack, tokenStackElement, quotedGroupOperators)
        ) {
            continue@token_loop
        }

        // No token left behind.
        // If no other strategy applied, treat the token as a parse tree leaf.
        val candidate = Candidate(tokenStackElement, Operator.Leaf)
        var closeTo: Int? = null
        for (i in operatorStack.indices.reversed()) {
            val el = operatorStack[i]
            if (
                el.operator.operatorType != OperatorType.Postfix &&
                canNest(
                    grandParent = operatorStack.getOrNull(i - 1),
                    parent = el,
                    child = candidate,
                )
            ) {
                break
            }
            closeTo = i
        }
        if (closeTo !== null) {
            operatorStack.commitTo(closeTo)
        }

        var top = operatorStack.last()
        // In certain contexts, we close operators rather than adding extra operands.
        while (
            top.operator.maxArity != Int.MAX_VALUE && // Rules out Root
            !top.operator.closer && top.operator.followers == Followers.None &&
            top.childCount >= top.operator.maxArity + 1 // for operator text
        ) {
            val topIndex = operatorStack.lastIndex
            val parentOp = top.operator
            // It's safe to fetch at size - 2 because parentOp is not Root.
            val grandParentOp = operatorStack[topIndex - 1].operator
            val greatGrandParentOp = operatorStack.getOrNull(topIndex - 2)?.operator
            val shouldShunt = shuntContentAsIfImpliedSeparator(
                greatGrandParentOp,
                grandParentOp,
                parentOp,
            )
            if (shouldShunt) {
                operatorStack.commitTo(topIndex)
                top = operatorStack.last()
            } else {
                break
            }
        }
        // If we ever wanted to extend our precedence checks to
        // include a canTokenAppearIn(op, nonOperatorToken) or
        // canMergeInto(nonOperatorTokens, nonOperatorToken)
        // predicates, this is where they'd go.
        if (top.operator != Operator.Leaf) {
            operatorStack.push(tokenStackElement.pos, candidate.operator)
        } else if (operatorStack.size >= 2) {
            val afterTop = operatorStack[operatorStack.size - 2]
            val afterTopOp = afterTop.operator
            if (
                afterTopOp.operatorType == OperatorType.Prefix &&
                afterTopOp != Operator.Root &&
                !afterTopOp.closer
            ) {
                val nChildren = afterTop.eventualChildCount - 1 // Sub 1 for operator
                if (nChildren < afterTopOp.maxArity) {
                    operatorStack.commitTo(operatorStack.size - 1)
                    operatorStack.push(tokenStackElement.pos, candidate.operator)
                }
            }
        }
    }

    // After processing all tokens, fold stack elements into their parents.
    operatorStack.commitTo(1)
    return postProcessCst(operatorStack.finishRoot(), logSink)
}

private fun tryOperatorsThatPrecedeAnOperand(
    operatorStack: OperatorStack,
    tokenStackElement: TokenStackElement,
    opType: OperatorType,
): Boolean {
    for (op in Operator.matching(tokenStackElement.tokenText, tokenStackElement.tokenType, opType)) {
        if (tokenStackElement.mayBracket != op.closer) {
            // This skips over Operator.Angle in favor of Operator.LessThan when
            // TokenSourceAdapter classified a `<` token as a non-bracket operator,
            // and it skips over Operator.Lt when not marked as a bracket.
            continue
        }
        var leftDepth: Int? = null
        val candidate = Candidate(op)
        for (i in (1 until operatorStack.size).reversed()) {
            val el = operatorStack[i]
            // Don't swap an open bracket into something while we're
            // looking for the close bracket.
            if (needsCloseBracket(el)) { break }

            // Can we swap el into a new binary operator as the left argument?
            val grandParent = operatorStack[i - 1]
            if ( // Can candidate contain el?
                canNest(grandParent = grandParent, parent = candidate, child = el) &&
                // Can el's existing parent contain candidate?
                canNest(
                    grandParent = operatorStack.getOrNull(i - 2),
                    parent = grandParent,
                    child = candidate,
                )
            ) {
                leftDepth = i
            }
        }
        if (leftDepth != null) {
            operatorStack.commitTo(leftDepth + 1)
            operatorStack.swapTopIntoNew(op)
            return true
        }
    }
    return false
}

private fun trySeparators(
    operatorStack: OperatorStack,
    tokenStackElement: TokenStackElement,
): Boolean {
    val operators = Operator.matching(
        tokenStackElement.tokenText,
        tokenStackElement.tokenType,
        OperatorType.Separator,
    )
    for (op in operators) {
        var leftDepth: Int? = null
        var rightDepth: Int? = null
        val outerCandidate = Candidate(op)
        val innerCandidate = Candidate(tokenStackElement, op)
        for (i in operatorStack.indices.reversed()) {
            val el = operatorStack[i]

            if (el.operator == op) {
                // If we can continue an existing operation, do so.
                operatorStack.commitTo(i + 1)
                return true
            }

            val elParent = operatorStack.getOrNull(i - 1)

            // Maybe we have a separator without a left operand.
            if (canNest(grandParent = elParent, parent = el, child = innerCandidate)) {
                rightDepth = i
            }

            if (needsCloseBracket(el)) { break }

            // Can we swap el into a new separator run as the left argument?
            if (
                elParent != null &&
                // Can candidate contain el?
                canNest(grandParent = elParent, parent = outerCandidate, child = el) &&
                // Can el's existing parent contain candidate?
                canNest(
                    grandParent = operatorStack.getOrNull(i - 2),
                    parent = elParent,
                    child = outerCandidate,
                )
            ) {
                leftDepth = i
            }
        }
        if (leftDepth != null) {
            operatorStack.commitTo(leftDepth + 1)
            // If the separator had been recognized as such when the last children of the grandparent were
            // parsed, would they have been included as children of the separator?
            // If so, op should adopt them.
            var nToAdopt = 0
            operatorStack.getOrNull(operatorStack.size - 2)?.let { grandParent ->
                while (nToAdopt < grandParent.childCount) {
                    val sibling = grandParent.child(grandParent.childCount - nToAdopt - 1)
                    if (sibling.tokenText != null) {
                        // We can adopt whole operations but not tokens that
                        // fill the grandparent's operator pattern.
                        break
                    }
                    val grandParentWithoutAdopted = object : OperatorStackElement by grandParent {
                        override val childCount: Int = grandParent.childCount - nToAdopt
                        override val eventualChildCount: Int get() = childCount
                    }
                    val parentAfterSwap = object : OperatorStackElement {
                        override val operator: Operator = op
                        override val tokenText: String? = null
                        override val tokenType: TokenType? = null
                        override val childCount: Int = nToAdopt
                        override val eventualChildCount: Int get() = childCount + 1
                        override fun child(i: Int): OperatorStackElement =
                            grandParent.child(grandParent.childCount - nToAdopt + i)
                    }

                    if (canNest(grandParentWithoutAdopted, parentAfterSwap, sibling)) {
                        nToAdopt += 1
                    } else {
                        break
                    }
                }
            }
            operatorStack.swapTopIntoNew(op, nToAdopt = nToAdopt)
            return true
        } else if (rightDepth != null) {
            operatorStack.commitTo(rightDepth + 1)
            operatorStack.push(tokenStackElement.pos, op)
            return true
        }
    }
    return false
}

private fun tryPrefixOperators(
    operatorStack: OperatorStack,
    tokenStackElement: TokenStackElement,
    prefixOperatorsMatching: List<Operator>,
): Boolean {
    for (op in prefixOperatorsMatching) {
        val candidate = Candidate(tokenStackElement, op)
        // Commit stack elements that cannot contain candidate, so we
        // can start a new operand.
        for (i in operatorStack.indices.reversed()) {
            val el = operatorStack[i]
            val elParent = operatorStack.getOrNull(i - 1)
            val stackOp = el.operator
            if (
                stackOp.operatorType != OperatorType.Postfix &&
                canNest(grandParent = elParent, parent = el, child = candidate)
            ) {
                operatorStack.commitTo(i + 1)
                operatorStack.push(tokenStackElement.pos, candidate.operator)
                return true
            }
            if (needsCloseBracket(el)) {
                break
            }
        }
    }
    return false
}

/**
 * When are we allowed to treat two adjacent things as being separated?
 *
 * This function provides a narrowly tailored answer to the question,
 *
 * > If I have enough operands for [parent] which is nested inside [grandParent] which is either
 * > [Operator.Root] or is nested in [greatGrandParent], should I close [parent] rather than glom
 * > another operand onto it?
 *
 * In C-like languages, separators are explicit.  `;` or `,` are used to separate adjacent items
 * in a list.
 *
 * So our broad strategy is to glom tokens onto the end of existing constructs.  But some JSX like
 * constructs don't have this property.
 * In tags,
 *
 *     <name attr1="value1" attr2="value2">
 *
 * there are no explicit separator tokens.
 */
private fun shuntContentAsIfImpliedSeparator(
    greatGrandParent: Operator?,
    grandParent: Operator,
    parent: Operator,
) = when {
    grandParent == Operator.Tag ->
        // HighColon to shunt after `namespace:tagOrAttributeName`
        // Eq        to shunt after `attribute=value`
        // Leaf      to shunt between `tagName` and `attributeName`
        parent == Operator.HighColon || parent == Operator.Eq || parent == Operator.Leaf
    greatGrandParent == Operator.Tag ->
        // namespace:tagOrAttributeName shunts
        (grandParent == Operator.HighColon && parent == Operator.Leaf) ||
            // shunt between `attribute=value` and `followingAttribute`
            (grandParent == Operator.Eq)
    else -> false
}

private val quotedGroupOperators = listOf(Operator.QuotedGroup)
