package lang.temper.lexer

import lang.temper.common.compatRemoveLast
import lang.temper.common.console
import lang.temper.common.emptyBooleanArray
import lang.temper.common.logIf

private const val DEBUG = false
private inline fun debug(b: Boolean = DEBUG, message: () -> String) =
    console.logIf(b, message = message)

/**
 * This class is nested because it is closely tangled with its container.
 * This is necessary to handle inputs like
 *      a < "...${ b > d }..." >
 * but if we didn't have access to pushed back state, we would be observing two different
 * token streams.
 */
internal fun classifyAngleBrackets(lexer: Lexer): Pair<Int, BooleanArray> {
    // Collect classifications of angle brackets by position in the source text.
    // We'll sort them later and create a bit mask.
    val angleBracketPositions = mutableListOf<Int>()

    // Keep track of open brackets: `(`, `[`, and `{`
    // along with `<` tokens that have yet to be classified as open angle brackets or
    // infix less than operators.
    val openBracketStack = mutableListOf<BracketInfo>()
    lookaheadLoop@
    do {
        // Merge content from the string reinforcer with the lookahead and filter out
        // comments and spaces.

        if (!lexer.hasNext()) { break@lookaheadLoop }
        // If none left, any brackets that are still open are relational or shifty operators.

        val current = lexer.next()
        val (_, tokenText, tokenType) = current
        val tokenStart = current.pos.left - lexer.offset

        // `;;;` is a super token that indicates a module section boundary.
        // Angle brackets cannot cross them, so just stop processing tokens.
        if (tokenText == ";;;" && tokenType == TokenType.Punctuation) {
            break@lookaheadLoop
        }

        val angleBracketInfo = AngleBracketInfo.of(tokenText, tokenType)
        debug {
            "Got $tokenText:$tokenType@$tokenStart.  Open=$openBracketStack.${
                " "
            }ABI=$angleBracketInfo"
        }

        if (angleBracketInfo != AngleBracketInfo.zeros) {
            repeat(angleBracketInfo.openerCount) {
                openBracketStack.add(BracketInfo("<", tokenType, tokenStart))
            }
            var closerCount = angleBracketInfo.closerCount
            var closerPos = tokenStart + angleBracketInfo.indexOfFirstCloser
            while (closerCount > 0) {
                // If we have a sequence like `>>=` we need to potentially treat it as `>`, `>`, `=`
                // depending on how many `<` tokens are on the top of the open bracket stack.
                // Since we optimistically treat `>` as a single token we should see each here.
                val lastBracket = openBracketStack.lastOrNull() ?: break
                if (!isOpenAngleBracketText(lastBracket.text)) {
                    break
                }
                openBracketStack.compatRemoveLast()
                // Promote corresponding '<' to angle bracket.
                angleBracketPositions.add(lastBracket.index)
                angleBracketPositions.add(closerPos)

                closerCount -= 1
                closerPos += 1
            }
        } else {
            var openBracket: String? = null
            var closeBracket: String? = null
            if (tokenType == TokenType.Punctuation) {
                if (tokenText in openBrackets) {
                    openBracket = tokenText
                } else if (tokenText in closeBrackets) {
                    closeBracket = tokenText
                }
            }
            if (openBracket != null) {
                openBracketStack.add(BracketInfo(openBracket, tokenType, tokenStart))
            } else if (closeBracket != null) {
                // Update the stack.
                while (true) {
                    val lastBracket = openBracketStack.removeLastOrNull() ?: break
                    if (bracketPartners[lastBracket.text] == closeBracket) {
                        break
                    }
                }
            } else if (!compatibleWithAngles(tokenText, tokenType)) {
                // Cancel any potential angle brackets
                while (true) {
                    val lastBracket = openBracketStack.lastOrNull()
                    if (!isOpenAngleBracketText(lastBracket?.text)) {
                        break
                    }
                    openBracketStack.compatRemoveLast()
                }
            }
        }
    } while (openBracketStack.isNotEmpty())

    debug { "angleBracketPositions: $angleBracketPositions" }
    if (angleBracketPositions.isNotEmpty()) {
        angleBracketPositions.sort()
        val minPos = angleBracketPositions.first()
        val maxPos = angleBracketPositions.last()
        val mask = BooleanArray(maxPos - minPos + 1)
        for (angleBracketPosition in angleBracketPositions) {
            mask[angleBracketPosition - minPos] = true
        }
        debug {
            "minPos=$minPos, maxPos=$maxPos, mask=${mask.asList()}"
        }
        return minPos to mask
    }
    return 0 to emptyBooleanArray
}

private fun compatibleWithAngles(tokenText: String, tokenType: TokenType): Boolean {
    if (tokenText == Operator.LowColon.text) {
        // Do allow colons as in
        //    a < fn ( ) : T >
        return true
    }
    val precedence = Operator.lowestPrecedenceMatching(tokenText, tokenType)
        ?: return true // Sure, whatever.
    // `extends` is allowed because it's used to specify upper bounds for formal type parameters.
    return (
        // Do not match angle brackets around low precedence operators like
        //    a < b; c > d
        // but do around commas and other low precedence operators
        //    a < b, c >
        //    a < b | c >
        precedence >= Operator.Comma.precedence &&
            // Do not match angle brackets around obvious logical operations:
            //    a < b && c > d
            //    a < b || c > d
            precedence != Operator.AmpAmp.precedence &&
            precedence != Operator.BarBar.precedence &&
            precedence != Operator.Caret.precedence
        )
}

private data class BracketInfo(
    val text: String,
    val type: TokenType,
    val index: Int,
)

internal fun isOpenAngleBracketText(text: String?) = text == "<" || text == "</"
