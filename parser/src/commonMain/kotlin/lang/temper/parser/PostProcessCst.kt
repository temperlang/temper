package lang.temper.parser

import lang.temper.common.C_NINE
import lang.temper.common.C_ZERO
import lang.temper.common.subListToEnd
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf
import lang.temper.lexer.IdParts
import lang.temper.lexer.Operator
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.LogSink
import lang.temper.log.spanningPosition

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
    when (processed.operator) {
        Operator.QuotedGroup -> {
            processed = processQuotedGroup(processed)
        }
        Operator.UnicodeRun -> {
            processed = processUnicodeRun(processed)
        }
        else -> {}
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
                        tokenText = buildString {
                            mergedLeaves.joinTo(this, "") { it.tokenText }
                        },
                    ),
                )
            }
        }.also { add(it) }
        // Move forward.
        start = end
    }
}
