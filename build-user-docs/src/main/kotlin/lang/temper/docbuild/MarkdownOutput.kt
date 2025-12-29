package lang.temper.docbuild

import lang.temper.common.compatRemoveLast
import kotlin.math.max

internal class MarkdownOutput : Appendable {
    private data class BreakNeeded(
        val preserveIndentation: Boolean,
        val lineCount: Int,
    ) {
        companion object {
            fun merge(a: BreakNeeded?, b: BreakNeeded?): BreakNeeded? = when {
                b == null -> a
                a == null -> b
                else ->
                    BreakNeeded(
                        preserveIndentation = a.preserveIndentation || b.preserveIndentation,
                        lineCount = max(a.lineCount, b.lineCount),
                    )
            }
        }
    }

    private val buffer = StringBuilder()
    private var breakNeeded: BreakNeeded? = null

    /** used to prevent appending when post-processing */
    private var canAppend = true

    /**
     * Markdown separates content, at a high-level, into blocks.
     *
     * Blocks are separated by blank lines.
     *
     * Breaking blocks means ensuring there is a blank line before
     * the next content written.
     *
     * But too many blank lines are ugly, so when this is called,
     * we set a switch.
     *
     * The next non-space character written will cause us to
     * fold any space suffix to ensure a proper block boundary.
     */
    fun breakBlock(preserveIndentation: Boolean = true, lineCount: Int = 1) {
        breakNeeded = BreakNeeded.merge(
            breakNeeded,
            BreakNeeded(preserveIndentation = preserveIndentation, lineCount = lineCount),
        )
    }

    val markdownContent get() = MarkdownContent("$buffer")

    private val postProcessMarkers = mutableListOf<Int>()

    /**
     * Allows building a chunk that requires some post-processing but
     * leaves flags pending at the end of the building in place.
     *
     * This proceeds by:
     *
     * 1. Marking the position at the end of the built output
     * 2. Calls [buildChunk]
     * 3. Extracts the content right of the mark to [MarkdownContent]
     * 4. Pass that content to [processChunk]
     * 5. Replace the content right of the mark with the output from [processChunk]
     */
    fun postProcessChunk(
        buildChunk: MarkdownOutput.() -> Unit,
        processChunk: (MarkdownContent) -> CharSequence,
    ) {
        check(canAppend)
        postProcessMarkers.add(buffer.length)
        val breakNeededBefore = breakNeeded
        buildChunk()
        val breakNeededAfter = breakNeeded
        val end = buffer.length

        var start = postProcessMarkers.compatRemoveLast()
        if (breakNeeded != null) {
            var beforeEndSpace = end
            while (beforeEndSpace != 0 && isMarkdownSpaceChar(buffer[beforeEndSpace - 1])) {
                beforeEndSpace -= 1
            }
            if (start == beforeEndSpace) {
                // Move pos forward since no content that wouldn't be rewritten
                // by the break check has been processed.
                start = end
            }
        }
        val content = MarkdownContent(buffer.substring(start, end))
        buffer.setLength(start)
        breakNeeded = breakNeededBefore

        canAppend = false
        val processed = processChunk(content)
        canAppend = true

        append(processed)
        breakNeeded = BreakNeeded.merge(breakNeeded, breakNeededAfter)
    }

    override fun append(csq: CharSequence?): Appendable {
        return if (csq == null) {
            append("null")
        } else {
            append(csq, 0, csq.length)
        }
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
        if (csq == null) { return append("null", start, end) }

        check(canAppend)
        if (breakNeeded == null) {
            buffer.append(csq, start, end)
        } else {
            for (i in start until end) {
                append(csq[i])
            }
        }
        return this
    }

    override fun append(c: Char): Appendable {
        check(canAppend)
        val breakNeeded = this.breakNeeded
        if (breakNeeded != null && !isMarkdownSpaceChar(c)) {
            this.breakNeeded = null
            fixupSpaceBeforeBreak(breakNeeded)
        }
        buffer.append(c)
        return this
    }

    private fun fixupSpaceBeforeBreak(breakNeeded: BreakNeeded) {
        val (preserveIndentation, lineCount) = breakNeeded
        // We need to preserve indentation on the current line.
        var endOfReplaced = buffer.length
        if (preserveIndentation) {
            while (endOfReplaced != 0 && buffer[endOfReplaced - 1].isTabOrSpace) {
                endOfReplaced -= 1
            }
        }
        var startOfSpaceRun = endOfReplaced
        while (startOfSpaceRun != 0 && isMarkdownSpaceChar(buffer[startOfSpaceRun - 1])) {
            startOfSpaceRun -= 1
        }
        val replacement = if (startOfSpaceRun == 0) {
            ""
        } else {
            // One to end the preceding line and one for each blank line requested
            "\n".repeat(lineCount + 1)
        }
        postProcessMarkers.indices.forEach { i ->
            // If we're post-processing anything, and we've got a mark
            // after some spaces, move the mark back to where we reset
            // content.
            if (postProcessMarkers[i] > startOfSpaceRun) {
                postProcessMarkers[i] = startOfSpaceRun
            }
        }
        buffer.replace(
            startOfSpaceRun,
            endOfReplaced,
            replacement,
        )
    }
}

internal val Char.isTabOrSpace get() = this == '\t' || this == ' '
