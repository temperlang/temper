package lang.temper.be.js

import lang.temper.common.LeftOrRight
import lang.temper.common.compatRemoveFirst
import lang.temper.common.compatRemoveLast
import lang.temper.common.toStringViaBuilder
import lang.temper.format.FormattingHints
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.Position

/** Intercepts tokens like `/*` and `*/` and combines the tokens between them into a comment token. */
open class CommentGroupingTokenSink(
    private val tokenSink: TokenSink,
    private val formattingHints: FormattingHints,
) : TokenSink {
    private val delayed = mutableListOf<Delayed>()

    /** Indexes into delayed of elements that start a comment */
    private val commentStarts = mutableListOf<Int>()

    private sealed class Delayed
    private data class DelayedPosition(val pos: Position, val side: LeftOrRight) : Delayed()
    private object DelayedEndLine : Delayed()
    private data class DelayedEmit(val token: OutputToken) : Delayed()

    override fun position(pos: Position, side: LeftOrRight) {
        if (commentStarts.isEmpty()) {
            tokenSink.position(pos, side)
        } else {
            delayed.add(DelayedPosition(pos, side))
        }
    }

    override fun endLine() {
        if (commentStarts.isEmpty()) {
            tokenSink.endLine()
        } else {
            delayed.add(DelayedEndLine)
        }
    }

    override fun emit(token: OutputToken) {
        if (isCommentOpen(token)) {
            delayed.add(DelayedEmit(token))
            commentStarts.add(delayed.lastIndex)
        } else if (commentStarts.isNotEmpty() && isCommentClose(token)) {
            delayed.add(DelayedEmit(token))
            finishStart()
        } else if (commentStarts.isEmpty()) {
            tokenSink.emit(token)
        } else {
            delayed.add(DelayedEmit(token))
        }
    }

    override fun finish() {
        while (commentStarts.isNotEmpty()) {
            finishStart()
        }
        tokenSink.finish()
    }

    open fun isCommentOpen(token: OutputToken) =
        token.type == OutputTokenType.Comment && token.association == TokenAssociation.Bracket &&
            token.text.startsWith("/*")
    open fun isCommentClose(token: OutputToken) =
        token.type == OutputTokenType.Comment && token.association == TokenAssociation.Bracket &&
            token.text.endsWith("*/")

    private fun finishStart() {
        val start = commentStarts.compatRemoveLast()
        val items = delayed.subList(start, delayed.size)
        val replacement = combineIntoComment(items.toList())
        items.clear()
        if (commentStarts.isEmpty()) {
            tokenSink.emit(replacement)
        } else {
            delayed.add(DelayedEmit(replacement))
        }
    }

    private fun combineIntoComment(delayed: List<Delayed>): OutputToken {
        val commentBody = delayed.toMutableList()
        var opener: OutputToken? = null
        var closer: OutputToken? = null

        if (commentBody.isNotEmpty()) {
            val first = commentBody.first()
            if (first is DelayedEmit && isCommentOpen(first.token)) {
                opener = first.token
                commentBody.compatRemoveFirst()
            }
        }

        if (commentBody.isNotEmpty()) {
            val last = commentBody.last()
            if (last is DelayedEmit && isCommentClose(last.token)) {
                closer = last.token
                commentBody.compatRemoveLast()
            }
        }

        val commentText =
            // Recursively apply formatting to the comment body tokens.
            toStringViaTokenSink(formattingHints = formattingHints, singleLine = false) {
                for (d in commentBody) {
                    when (d) {
                        is DelayedEmit -> it.emit(d.token)
                        DelayedEndLine -> it.endLine()
                        is DelayedPosition -> it.position(d.pos, d.side)
                    }
                }
            }
                .replace("*/", "* /") // Body content is safe
                .replace(leadingBlanks, "") // Trim blank lines from start
                .replace(trailingBlanks, "") // and end

        // If it fits on one line, do it like /** body */
        if (crlf.find(commentText) == null) {
            return OutputToken(
                toStringViaBuilder {
                    if (opener != null) {
                        it.append(opener.text)
                        it.append(' ')
                    }
                    it.append(commentText)
                    if (closer != null) {
                        if (commentText.isNotEmpty()) {
                            it.append(' ')
                        }
                        it.append(closer.text)
                    }
                },
                OutputTokenType.Comment,
            )
        }

        // Otherwise, we start each body line with a tab, which the
        // FormattingTokenSink treats as instructions to line up with
        // the indentation of the first line of the comment.
        return OutputToken(
            toStringViaBuilder {
                if (opener != null) {
                    it.append(opener.text)
                }
                for (line in commentText.split(crlf)) {
                    it.append("\n\t * ")
                    it.append(line)
                }
                if (closer != null) {
                    it.append("\n\t ")
                    it.append(closer.text)
                }
            },
            OutputTokenType.Comment,
        )
    }
}

@Suppress("RegExpUnnecessaryNonCapturingGroup")
private const val CRLF_PATTERN_TEXT = """(?:\n|\r\n?)"""
private val crlf = Regex(CRLF_PATTERN_TEXT)
private val leadingBlanks = Regex("""^$CRLF_PATTERN_TEXT+""")
private val trailingBlanks = Regex("""$CRLF_PATTERN_TEXT+\z""")
