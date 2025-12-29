package lang.temper.parser

import lang.temper.common.C_NINE
import lang.temper.common.C_ZERO
import lang.temper.common.backtickEscaper
import lang.temper.common.console
import lang.temper.common.jsonEscaper
import lang.temper.common.logIf
import lang.temper.common.subListToEnd
import lang.temper.common.toStringViaBuilder
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf
import lang.temper.lexer.IdParts
import lang.temper.lexer.LexicalDefinitions
import lang.temper.lexer.Operator
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.LogSink
import lang.temper.log.spanningPosition

private const val DEBUG = false
private inline fun debug(b: Boolean = DEBUG, message: () -> String) =
    console.logIf(b, message = message)

internal fun postProcessCst(cst: CstInner, logSink: LogSink): CstInner {
    var processed: CstInner = cst
    var replacementOperands: MutableList<ConcreteSyntaxTree>? = null
    for ((index, operand) in cst.operands.withIndex()) {
        val processedOperand = when (operand) {
            is CstLeaf -> operand
            is CstInner -> postProcessCst(operand, logSink)
        }
        if (replacementOperands == null && operand !== processedOperand) {
            replacementOperands = mutableListOf()
            replacementOperands.addAll(cst.operands.subList(0, index))
        }
        replacementOperands?.add(processedOperand)
    }
    if (replacementOperands != null) {
        processed = processed.copy(operands = replacementOperands.toList())
    }
    if (processed.operator == Operator.QuotedGroup) {
        processed = processQuotedGroup(processed)
    }
    if (processed.operator == Operator.UnicodeRun) {
        processed = processUnicodeRun(processed)
    }
    return processed
}

fun processUnicodeRun(processed: CstInner): CstInner {
    // See what we're working on.
    val operands = processed.operands
    val middle = operands.getOrNull(1) as? CstInner
    val middleIsComma = middle != null && middle.operator == Operator.Comma
    // See if we have work to do.
    when {
        middleIsComma -> middle
        else -> processed
    }.operands.any { it.isEmptyHole() } || return processed
    // Do the work.
    val mergedOperands = buildList {
        add(operands.first())
        when {
            middleIsComma -> {
                check(operands.size == UNICODE_RUN_COMMA_SIZE)
                add(middle.copy(operands = buildList { processUnicodeRunOperands(middle.operands) }))
            }
            else -> processUnicodeRunOperands(operands.subList(1, operands.size - 1))
        }
        add(operands.last())
    }
    return processed.copy(operands = mergedOperands)
}

private const val UNICODE_RUN_COMMA_SIZE = 3

/** Remove holes and merge number/word-like leaves. */
private fun MutableList<ConcreteSyntaxTree>.processUnicodeRunOperands(operands: List<ConcreteSyntaxTree>) {
    val leafOperands = mutableListOf<ConcreteSyntaxTree>()
    var lastLeafStart: CstInner? = null
    fun pushLeafIfNeeded() {
        if (lastLeafStart != null) {
            val pos = leafOperands.spanningPosition(lastLeafStart!!.pos)
            add(lastLeafStart!!.copy(pos = pos, operands = leafOperands.toList()))
            lastLeafStart = null
            leafOperands.clear()
        }
    }
    fun CstLeaf.isHexLike() = tokenText.codePointAt(0).let { code ->
        // Be flexible with hex-like since we are in lexer. Catch errors later, grouped for better intuition.
        // Importantly, commas are excluded from this list.
        code in IdParts.Start || code in C_ZERO..C_NINE
    }
    // Loop over inner operands.
    for (operand in operands) {
        when {
            operand.isAllQuotedStringLeaf() -> {
                val copyStart = when (lastLeafStart) {
                    null -> {
                        lastLeafStart = operand as CstInner
                        0
                    }
                    else -> {
                        val last = leafOperands.last() as CstLeaf
                        val current = operand.operands.first() as CstLeaf
                        when {
                            last.isHexLike() && current.isHexLike() -> {
                                val merged = last.temperToken.copy(
                                    tokenText = last.temperToken.tokenText + current.temperToken.tokenText,
                                )
                                leafOperands[leafOperands.size - 1] = CstLeaf(merged)
                                1
                            }
                            else -> 0
                        }
                    }
                }
                leafOperands.addAll(operand.operands.subListToEnd(copyStart))
            }
            operand.isEmptyHole() -> {} // skip
            else -> {
                pushLeafIfNeeded()
                add(operand)
            }
        }
    }
    pushLeafIfNeeded()
}

private fun processQuotedGroup(quotedGroup: CstInner): CstInner {
    // Check for a delimiter.  If we've got a multi-quoted string then
    // remove incidental space characters.
    val operands = quotedGroup.operands
    val leftDelimiterIndex = operands.indexOfFirst {
        it is CstLeaf && it.tokenType == TokenType.LeftDelimiter
    }
    if (leftDelimiterIndex < 0) { return quotedGroup }
    var rightDelimiterIndex = operands.indexOfLast {
        it is CstLeaf && it.tokenType == TokenType.RightDelimiter
    }
    if (rightDelimiterIndex == 0) { rightDelimiterIndex = operands.size }

    // Look at items between the left and right delimiter for
    //
    //   (Leaf
    //    (Token : QuotedString ...)
    //    (Token : QuotedString ...))
    //
    // We find index pairs:
    // - The index of the leaf
    // - The index within the leaf's operands of the QuotedString
    val quotedStringIndices = buildList {
        for (leafIndex in (leftDelimiterIndex + 1) until rightDelimiterIndex) {
            val element = quotedGroup.operands[leafIndex] as? CstInner ?: continue
            if (element.operator == Operator.Leaf) {
                val qsIndices = buildList {
                    for (qsIndex in element.operands.indices) {
                        val qs = element.operands[qsIndex]
                        if (qs is CstLeaf && qs.tokenType == TokenType.QuotedString) {
                            add(qsIndex)
                        }
                    }
                }
                if (qsIndices.isNotEmpty()) {
                    add(leafIndex to qsIndices)
                }
            }
        }
    }

    return processQuotedGroup(quotedGroup, quotedStringIndices)
}

/**
 * Mutates a part list in place to remove incidental spaces from strings.
 *
 * <!-- snippet: syntax/string/incidental-space-removal -->
 * # Incidental spaces in multi-line strings
 *
 * When a string spans multiple lines, some space is *significant*;
 * it contributes to the content of the resulting string value.
 * Spaces that do not contribute to the content are called *incidental spaces*.
 * Incidental spaces include:
 *
 * - those used for code indentation, and
 * - those that appear at the end of a line so are invisible to readers, and
 *   often automatically stripped by editors, and
 * - carriage returns which may be inserted or removed depending on
 *   whether a file is edited on Windows or UNIX.
 *
 * Normalizing incidental space steps include:
 *
 * 1. Removing leading space on each line that match the indentation of the close quote.
 * 2. Removing the newline after the open quote, and before the close quote.
 * 3. Removing space at the end of each line.
 * 4. Normalizing line break sequences CRLF, CR, and LF to LF.
 *
 * For the purposes of identifying incidental space, we imagine that any
 * interpolation `${...}`, scriptlet `{:...:}`, or hole `${}` contributes
 * 1 or more non-space, non-line-break characters.
 *
 * Indentation matching the close quote is incidental, hence removed.
 *
 * ```temper
 * """
 *     "Line 1
 *     "Line 2
 * == "Line 1\nLine 2"
 * ```
 *
 * Each content line is stripped up to and including the margin character.
 * It's good style to line up the margin characters, but not necessary.
 *
 * ```temper
 * """
 *     " Line 1
 *    "  Line 2
 *     "   Line 3
 * == " Line 1\n  Line 2\n   Line 3"
 * ```
 *
 * It's an error if a line is not un-indented from the close quote.
 *
 * ```temper FAIL
 * """
 *     "Line 1
 *      Line 2 missing margin character
 *     "Line 3
 * ```
 *
 * Spaces are removed from the end of a line, but not if there is an
 * interpolation or hole:
 *
 * ```temper
 * """
 *     "Line 1  ${"interpolation"}
 *     "Line 2  ${/*hole*/}
 *     "Line 3
 *     == "Line 1  interpolation\nLine 2  \nLine 3"
 * ```
 *
 * For the purpose of this, space includes:
 *
 * - Space character: U+20 ' '
 * - Tab character: U+9 '\t'
 *
 * A line consists of any maximal sequence of characters other than
 * CR (U+A '\n') and LF (U+D '\r').
 *
 * A line break is any of the following sequences:
 *
 * - LF
 * - CR
 * - CR LF
 */
private fun processQuotedGroup(
    quotedGroup: CstInner,
    /**
     * indices into [quotedGroup].operands of string [CstLeaf]s that have
     * tokens with type [TokenType.QuotedString].  In part order.
     */
    stringContentIndices: List<Pair<Int, List<Int>>>,
): CstInner {
    // Size 0 means empty string.
    if (stringContentIndices.isEmpty()) {
        return quotedGroup
    }

    val partTokens = mutableListOf<TemperToken>()
    stringContentIndices.forEach { (leafIndex, qsIndices) ->
        val inner = quotedGroup.operands[leafIndex] as CstInner
        qsIndices.forEach { qsIndex ->
            val cstLeaf = inner.operands[qsIndex] as CstLeaf
            partTokens.add(cstLeaf.temperToken)
        }
    }

    stripIncidentalSpace(partTokens)

    var partIndex = 0
    val newQuotedGroupOperands = quotedGroup.operands.toMutableList()
    var changedQuotedGroup = false
    stringContentIndices.forEach { (leafIndex, qsIndices) ->
        val inner = quotedGroup.operands[leafIndex] as CstInner
        val newInnerOperands = inner.operands.toMutableList()
        var changedInner = false
        qsIndices.forEach { qsIndex ->
            val cstLeaf = newInnerOperands[qsIndex] as CstLeaf
            val newToken = partTokens[partIndex++]
            if (cstLeaf.temperToken !== newToken) {
                newInnerOperands[qsIndex] = cstLeaf.copy(temperToken = newToken)
                changedInner = true
            }
        }
        if (changedInner) {
            newQuotedGroupOperands[leafIndex] = inner.copy(operands = newInnerOperands.toList())
            changedQuotedGroup = true
        }
    }

    // See if we can collapse adjacent string chunks into one.
    // This is important for empty interpolations to serve consistently as
    // a meta-character disable mechanism only.
    //
    // tag should receive the chunk "foobar", not two chunks "foo" and "bar"
    // in the below.
    //
    //     tag"foo${}bar"

    // Step 1: Remove any holes
    // Step 2: Merge QuotedString tokens in each leaf
    // Step 3: Merge Adjacent leaves that contain one QuotedString

    // Remove holes
    val sizeBefore = newQuotedGroupOperands.size
    newQuotedGroupOperands.removeAll { it.isEmptyHole() }
    if (newQuotedGroupOperands.size != sizeBefore) { changedQuotedGroup = true }

    // Merge adjacent leaves
    for (i in newQuotedGroupOperands.indices.reversed()) {
        if (i == 0) { break }
        val atI = newQuotedGroupOperands[i]
        val before = newQuotedGroupOperands[i - 1]
        if (atI.isAllQuotedStringLeaf() && before.isAllQuotedStringLeaf()) {
            newQuotedGroupOperands[i - 1] = CstInner(
                pos = listOf(before, atI).spanningPosition(before.pos),
                operator = Operator.Leaf,
                // In bad cases, this could be N * M in leaves vs holes.
                // TODO Just build up a list in a single go instead of piecewise newQuotedGroupOperands changes?
                operands = before.operands + atI.operands,
            )
            newQuotedGroupOperands.removeAt(i)
            changedQuotedGroup = true
        }
    }
    // Merge QuotedString tokens in each leaf
    for (i in newQuotedGroupOperands.indices) {
        val possibleLeaf = newQuotedGroupOperands[i]
        if (possibleLeaf is CstInner && possibleLeaf.operator == Operator.Leaf) {
            val leafOperands = possibleLeaf.operands
            if (leafOperands.size > 1 && leafOperands.all { it.tokenType == TokenType.QuotedString }) {
                newQuotedGroupOperands[i] = possibleLeaf.copy(
                    operands = mergeQuotedStringTokens(leafOperands.map { it as CstLeaf }),
                )
                changedQuotedGroup = true
            }
        }
    }

    return if (changedQuotedGroup) {
        quotedGroup.copy(operands = newQuotedGroupOperands.toList())
    } else {
        quotedGroup
    }
}

private fun ConcreteSyntaxTree.isAllQuotedStringLeaf() =
    this is CstInner && operator == Operator.Leaf &&
        operands.all { it.tokenType == TokenType.QuotedString }

private fun ConcreteSyntaxTree.isEmptyHole() =
    this is CstInner && operator == Operator.DollarCurly &&
        operands.size == 2 && operands[0].tokenText == $$"${" &&
        operands[1].tokenText == "}"

/** Merge leaves that aren't escapes, so we can single out escape positions in messaging when needed. */
private fun mergeQuotedStringTokens(leaves: List<CstLeaf>): List<CstLeaf> = buildList {
    val limit = leaves.size
    var start = 0
    while (start < limit) {
        val startLeaf = leaves[start]
        var end = start + 1
        // Find leaf runs that aren't escapes.
        // Retain escapes as separate tokens to help later processing, which might or might not want raw content.
        if (!startLeaf.tokenText.startsWith('\\')) {
            end@ while (end < limit) {
                val endLeaf = leaves[end]
                if (endLeaf.tokenText.startsWith('\\')) {
                    break@end
                }
                end += 1
            }
        }
        // Merge multiple into a single leaf if needed.
        when (end) {
            start + 1 -> startLeaf
            else -> {
                val mergedLeaves = leaves.subList(start, end)
                CstLeaf(
                    startLeaf.temperToken.copy(
                        pos = mergedLeaves.spanningPosition(startLeaf.pos),
                        tokenText = toStringViaBuilder { newTokenText ->
                            mergedLeaves.map { newTokenText.append(it.tokenText) }
                        },
                    ),
                )
            }
        }.also { add(it) }
        // Move forward.
        start = end
    }
}

internal fun stripIncidentalSpace(partTokens: MutableList<TemperToken>) {
    console.groupIf(
        DEBUG,
        "String group : [${
            partTokens.joinToString { backtickEscaper.escape(it.tokenText) }
        }]",
    ) {
        // If no part contains a CR or LF, then it's not a multiline string literal so leave as is.
        val isMultiline = partTokens.any { token ->
            token.tokenText.any { LexicalDefinitions.isLineBreak(it) }
        }
        if (!isMultiline) {
            debug { "group is not multiline" }
            return@stripIncidentalSpace
        }

        // Find the space prefix of the last line.
        // Ideally the last line would consist only of spaces and the close quote.
        // We'll try to guide devs to that in the IDE, so here we just grab space and tab characters.

        debug { "multiline $partTokens" }

        val lastPartLeafIndex = partTokens.lastIndex
        partTokens.forEachIndexed { i, token ->
            val tokenText = token.tokenText
            val clean = cleanupFirstAndLast(
                tokenText = tokenText,
                isFirstPart = i == 0,
                isLastPart = i == partTokens.lastIndex,
            )
            val replacement = normalizeSpaceAtEndOfLine(
                clean,
                isLast = i == lastPartLeafIndex,
            )
            debug {
                ". $i: ${
                    jsonEscaper.escape(tokenText)
                } -> ${
                    jsonEscaper.escape(replacement)
                }"
            }
            partTokens[i] = token.copy(tokenText = replacement)
        }
    }
}

private fun cleanupFirstAndLast(
    tokenText: String,
    isFirstPart: Boolean,
    isLastPart: Boolean,
): String {
    val replacement = toStringViaBuilder { sb ->
        // For each part, find the region between the start and end delimiter.
        var pos = 0
        // Figure out when to scan to, and what to emit after we're all done.
        val (limit, suffix) = if (isLastPart) {
            // Strip any newline followed by indenting spaces at the end.
            // This allows us to always end a multiline string template with the
            // close quote on a line by itself.
            var limit = tokenText.length
            // Where `\r\n` are literal bytes, not escape sequences,
            // a last part of `  \r\n    ` becomes `  `.
            var beforeLastSpaceRun = limit
            while (
                beforeLastSpaceRun > pos &&
                LexicalDefinitions.isIndentingSpace(tokenText[beforeLastSpaceRun - 1])
            ) {
                beforeLastSpaceRun -= 1
            }
            if (
                beforeLastSpaceRun > pos &&
                LexicalDefinitions.isLineBreak(tokenText[beforeLastSpaceRun - 1])
            ) {
                if ( // Eat both of CRLF
                    beforeLastSpaceRun - 2 >= pos &&
                    tokenText[beforeLastSpaceRun - 2] == '\r' &&
                    tokenText[beforeLastSpaceRun - 1] == '\n'
                ) {
                    beforeLastSpaceRun -= 1
                }
                limit = beforeLastSpaceRun - 1
            }
            limit to ""
        } else {
            tokenText.length to ""
        }
        // Strip any blank line from the front.
        // If the first part starts with indenting space then a newline, eat it.
        // `\n    foo${` becomes `"    foo`
        // after which we start stripping.
        // This is symmetric with how we eliminate the last blank line and allows doing
        //
        //      let myString = """
        //      significant lines
        //      """;
        //
        // so that the first line which may be long does not have to kludge onto the open
        // quote.
        // We only remove one blank from the beginning and the end so blank lines can still
        // be included in a string literal by just doubling up.
        if (isFirstPart) {
            var afterLeadingSpace = pos
            while (
                afterLeadingSpace < limit &&
                LexicalDefinitions.isIndentingSpace(tokenText[afterLeadingSpace])
            ) {
                afterLeadingSpace += 1
            }
            if (
                afterLeadingSpace < limit &&
                LexicalDefinitions.isLineBreak(tokenText[afterLeadingSpace])
            ) {
                pos = afterLeadingSpace + if (
                    afterLeadingSpace + 1 < limit &&
                    tokenText[afterLeadingSpace] == '\r' &&
                    tokenText[afterLeadingSpace + 1] == '\n'
                ) {
                    2 // CR LF
                } else {
                    1
                }
            }
        }
        // Now we know that the relevant content is between pos and limit.
        // Replay it onto the output buffer.
        debug {
            "content is ${backtickEscaper.escape(tokenText.substring(pos, limit))}"
        }
        while (pos < limit) {
            var afterBreaks = pos
            while (afterBreaks < limit && LexicalDefinitions.isLineBreak(tokenText[afterBreaks])) {
                afterBreaks += 1
            }
            if (afterBreaks != pos) {
                // Saw some line breaks.
                // There may be many line breaks, but we don't require indentation for blank lines.
                sb.append(tokenText, pos, afterBreaks)
                pos = afterBreaks
            } else {
                sb.append(tokenText[pos])
                pos += 1
            }
        }

        sb.append(suffix)
    }
    return replacement
}

private fun normalizeSpaceAtEndOfLine(s: String, isLast: Boolean) = toStringViaBuilder { sb ->
    var i = 0
    val n = s.length
    while (i < n) {
        val c = s[i]
        i += 1

        when (c) {
            '\r', '\n' -> {
                // Remove any trailing space before the newline.
                var newLength = sb.length
                while (
                    newLength != 0 &&
                    LexicalDefinitions.isIndentingSpace(sb[newLength - 1])
                ) {
                    newLength -= 1
                }
                sb.setLength(newLength)

                sb.append('\n') // Normalize the newline
                if (c == '\r' && i < n && s[i] == '\n') {
                    // Handle CR LF
                    i += 1
                }
            }
            else -> sb.append(c)
        }
    }

    if (isLast) {
        var newLength = sb.length
        while (
            newLength != 0 &&
            LexicalDefinitions.isIndentingSpace(sb[newLength - 1])
        ) {
            newLength -= 1
        }
        sb.setLength(newLength)
    }
}
