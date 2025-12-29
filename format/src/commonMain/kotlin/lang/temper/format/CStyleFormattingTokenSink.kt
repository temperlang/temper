package lang.temper.format

import lang.temper.common.LeftOrRight
import lang.temper.common.TriState
import lang.temper.common.jsonEscaper
import lang.temper.log.FilePositions
import lang.temper.log.Position
import kotlin.math.max

/** Indents, styles, and spaces tokens nicely based on C-style language conventions. */
class CStyleFormattingTokenSink(
    override val out: TokenSink,
    private val filePositions: FilePositions,
    override val formattingHints: FormattingHints,
    private val singleLine: Boolean,
) : FormattingTokenSink {

    init {
        require(!singleLine || formattingHints.allowSingleLine) {
            "Sink is set to single line, but hints prohibit it."
        }
    }

    /**
     * Max line number seen from [position] markers used to try to mirror the line structure of
     * the source when [filePositions] are given.
     */
    private var lineNo = 1

    /**
     * Sets of bit which are true when the current indentation level has a significant token since
     * the last statement terminator, so correspond to "not beginning a statement".
     */
    private val indentation = mutableListOf(false)

    /**
     * True if, when we see the next significant token, so know whether it's safe to break a line,
     * we should.
     */
    private var lineBreakPending = false

    /**
     * True if we are at the beginning of the current output line.
     */
    private var atLineStart = true

    /**
     * The last token emitted.
     */
    private var lastToken: OutputToken? = null

    /**
     * The last non-comment, non-space token emitted.
     */
    private var lastSignificantToken: OutputToken? = null

    /**
     * Anything that has been deferred.
     * We defer close brackets so that, if they are the first token on a line, we can dedent by
     * all the close brackets on that line before writing the indentation for the line.
     * This lets us produce output like
     *
     *     foo(bar(baz(
     *           x
     *     ))) // <-- The first `)` is dedented based on the stack of 3 close parentheses.
     *
     * We also defer position markers, and newlines along with these so that we send messages to
     * [out] in the right order.
     */
    private var deferred = mutableListOf<Deferred>()

    override fun position(pos: Position, side: LeftOrRight) {
        if (filePositions != FilePositions.nil && pos.loc == filePositions.codeLocation) {
            val ln = filePositions.filePositionAtOffset(
                when (side) {
                    LeftOrRight.Left -> pos.left
                    LeftOrRight.Right -> pos.right
                },
            ).line
            if (ln > lineNo) {
                flushDeferred()
                lineNo = ln
                deferred.add(DeferredLineBreak)
            }
        }
        if (deferred.isNotEmpty()) {
            deferred.add(DeferredPosition(pos, side))
        } else {
            out.position(pos, side)
        }
    }

    override fun endLine() {
        if (deferred.isEmpty()) {
            lineBreakPending = true
        } else {
            deferred.add(DeferredLineBreak)
        }
    }

    override fun emit(token: OutputToken) {
        val isSimpleToken = token is SimpleOutputToken
        val dedents = isSimpleToken && formattingHints.dedents(token)
        if (dedents) {
            val last = lastToken
            // Deferring tokens that can't have line breaks before them unnecessarily complicates
            // the endLine checks since it can't rely on lastToken being the first argument to
            // mayBreakLine
            val shouldDefer = deferred.isNotEmpty() || last == null ||
                formattingHints.mayBreakLineBetween(last, token)
            if (shouldDefer) {
                // This lets us output multiple close brackets on the same line after un-indenting
                // by the number of close brackets seen together.
                deferred.add(DeferredToken(token))
                deferred.add(DeferredDedent)
                if (formattingHints.indents(token)) {
                    // Some things, like "else" in Lua both dedent (itself) and indent (what follows).
                    deferred.add(DeferredIndent)
                }
                return
            }
        }

        val indentAfter = isSimpleToken && formattingHints.indents(token)
        var localLevel: Boolean? = if (indentAfter) null else true
        if (isSimpleToken) {
            when (token.type) {
                OutputTokenType.Word,
                OutputTokenType.Name,
                OutputTokenType.NumericValue,
                OutputTokenType.QuotedValue,
                OutputTokenType.NotEmitted,
                OutputTokenType.OtherValue,
                is OutputTokenType.Custom,
                -> {
                    // No adjustments
                }
                OutputTokenType.Punctuation -> {
                    when (token.text) {
                        ",", ";" -> localLevel = false
                    }
                }
                OutputTokenType.Comment -> {
                    localLevel = null
                }
                OutputTokenType.Space -> {
                    if (!(dedents || indentAfter)) return
                    localLevel = null
                }
            }
            localLevel = formattingHints.localLevel(token, localLevel)
        }
        if (localLevel == false) {
            localLevelIndent(false)
        }
        if (token.type != OutputTokenType.NotEmitted) {
            prepareForToken(token)
            emitToOut(token)
        }
        if (indentAfter) {
            indentation.add(false)
        }
        if (localLevel != null) {
            localLevelIndent(localLevel)
        }
    }

    override fun finish() {
        flushDeferred()
        if (!atLineStart && !singleLine) {
            endLineOnOut()
            atLineStart = true
            lineBreakPending = false
        }
        out.finish()
    }

    private fun breakBetween(lastSignificant: OutputToken?, token: OutputToken): Boolean {
        // When file positions provide no break hints, fall back to using hints.
        if (filePositions != FilePositions.nil || singleLine || lastSignificant == null) {
            return false
        }
        try {
            if (!formattingHints.mayBreakLineBetween(lastSignificant, token)) {
                return false
            }
            when (formattingHints.shouldBreakBetween(lastSignificant, token)) {
                TriState.OTHER -> {}
                TriState.TRUE -> return true
                TriState.FALSE -> return false
            }
            if (formattingHints.shouldBreakBefore(token)) {
                return true
            }
            if (formattingHints.shouldBreakAfter(lastSignificant)) {
                return true
            }
            return false
        } finally {
            formattingHints.tokenProcessed(lastSignificant)
        }
    }

    private fun prepareForToken(token: OutputToken) {
        flushDeferred()
        val isSignificant = token.isSignificant()
        if (isSignificant && breakBetween(lastSignificantToken, token)) {
            lineBreakPending = true
        }
        if (lineBreakPending) {
            var endLine = false
            if (isSignificant) {
                lineBreakPending = false
                val lastSig = lastSignificantToken
                if (
                    !singleLine && !atLineStart &&
                    (lastSig == null || formattingHints.mayBreakLineBetween(lastSig, token))
                ) {
                    endLine = true
                }
            } else if (token.type == OutputTokenType.Comment && !atLineStart) {
                // Allow .endLine to break before line comments that are metadata markers
                endLine = true
            }
            if (endLine) {
                endLineOnOut()
                atLineStart = true
            }
        }
        if (atLineStart) {
            val indentSteps = if (indentation.isNotEmpty()) {
                indentation.size + (if (indentation.last()) 0 else -1)
            } else {
                0
            }
            indentToken(indentSteps)
            atLineStart = false
        } else {
            val last = lastToken
            val spaceBetween = last != null && formattingHints.spaceBetween(last, token)
            if (spaceBetween) {
                emitToOut(OutToks.oneSpace)
            }
        }
        lastToken = token
        if (isSignificant) {
            lastSignificantToken = token
        }
    }

    private fun indentToken(steps: Int) {
        if (steps >= 0) {
            emitToOut(OutputToken(formattingHints.standardIndent.repeat(steps), OutputTokenType.Space))
        }
    }

    private fun localLevelIndent(byOneMoreStep: Boolean) {
        if (formattingHints.localLevelIndents && indentation.size != 0) {
            indentation[indentation.size - 1] = byOneMoreStep
        }
    }

    private fun flushDeferred() {
        if (deferred.isEmpty()) {
            return
        }
        val toFlush = deferred.toMutableList()
        deferred.clear()

        // Make line breaks based on auto-formatting explicit.
        var last = lastSignificantToken
        run {
            var i = 0
            while (i < toFlush.size) {
                val d = toFlush[i]
                if (d is DeferredToken) {
                    val t = d.outputToken
                    if (t.isSignificant()) {
                        if (breakBetween(last, t)) {
                            toFlush.add(i, DeferredLineBreak)
                            i += 1
                        }
                        last = t
                    }
                }
                i += 1
            }
        }
        // Shift dedents left as long as it's not across a line-break boundary.
        run {
            var dedentInsertionPoint = 0
            var i = 0
            while (i < toFlush.size) {
                when (toFlush[i]) {
                    DeferredDedent -> {
                        reinsertEndAtStart(toFlush, dedentInsertionPoint..i)
                        dedentInsertionPoint += 1
                    }
                    DeferredLineBreak -> dedentInsertionPoint = i + 1
                    else -> {}
                }
                i += 1
            }
        }

        for (d in toFlush) {
            when (d) {
                is DeferredToken -> {
                    val outputToken = d.outputToken
                    localLevelIndent(false)
                    if (outputToken.type != OutputTokenType.NotEmitted) {
                        prepareForToken(outputToken)
                        emitToOut(outputToken)
                    }
                }
                is DeferredPosition -> out.position(d.pos, d.side)
                DeferredLineBreak -> lineBreakPending = true
                DeferredDedent -> {
                    val lastIndentationIndex = indentation.size - 1
                    if (lastIndentationIndex >= 0) {
                        indentation.removeAt(lastIndentationIndex)
                    }
                }
                DeferredIndent -> indentation.add(false)
            }
        }
    }

    private var columnOnOut = 0
    private fun endLineOnOut() {
        columnOnOut = 0
        out.endLine()
    }

    private fun emitToOut(token: OutputToken) {
        var adjustedToken = token
        if (token.type == OutputTokenType.Comment && subsequentLineStart.find(token.text) != null) {
            // If the token is multiline like
            //   /**
            //    *
            //    *
            //    */
            //
            // then see if we need to line its subsequent line up with its start position.
            // Comment tokens opt into this by starting lines with a tab.
            val adjustedTokenText = token.text.replace(
                subsequentLineStart,
                "$1${" ".repeat(columnOnOut)}",
            )
            adjustedToken = OutputToken(adjustedTokenText, token.type)
        }
        out.emit(adjustedToken)

        val emittedTokenText = adjustedToken.text
        // Update the column to match
        val lastLineTerminator = max(
            emittedTokenText.lastIndexOf('\n'),
            emittedTokenText.lastIndexOf('\r'),
        )
        if (lastLineTerminator >= 0) {
            columnOnOut = emittedTokenText.lastIndex - lastLineTerminator
        } else {
            columnOnOut += emittedTokenText.length
        }
    }
}

private sealed class Deferred
private data class DeferredToken(val outputToken: OutputToken) : Deferred() {
    override fun toString() = jsonEscaper.escape(outputToken.text)
}
private data class DeferredPosition(val pos: Position, val side: LeftOrRight) : Deferred() {
    override fun toString() = when (side) {
        LeftOrRight.Left -> "Left:${pos.left}"
        LeftOrRight.Right -> "Right:${pos.right}"
    }
}
private object DeferredLineBreak : Deferred() {
    override fun toString() = "LineBreak"
}
private object DeferredDedent : Deferred() {
    override fun toString() = "Dedent"
}
private object DeferredIndent : Deferred() {
    override fun toString() = "Indent"
}

private fun <T> reinsertEndAtStart(ls: MutableList<T>, range: IntRange) {
    val source = range.last
    val dest = range.first
    val toMoveLeft = ls[source]
    for (i in (source - 1) downTo dest) {
        ls[i + 1] = ls[i]
    }
    ls[dest] = toMoveLeft
}

/** See re-formatting of comment tokens above */
private val subsequentLineStart = Regex("""(\n|\r\n?)\t""")
