package lang.temper.langserver

import lang.temper.common.console
import lang.temper.common.jsonEscaper
import lang.temper.common.replaceSubList
import lang.temper.common.toStringViaBuilder
import lang.temper.log.CodeLocation
import lang.temper.log.FilePositions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

private const val DEBUG_REPLACE = false

private inline fun debugReplace(action: () -> Unit) {
    @Suppress("ConstantConditionIf")
    if (DEBUG_REPLACE) {
        action()
    }
}

/**
 * A representation of a file that meshes with the LSP way of indexing documents by (line, char)
 * pairs via [org.eclipse.lsp4j.Position].
 *
 * This supports range replacement.
 */
internal class DocumentText(val loc: CodeLocation) {
    private var _positions: FilePositions? = null
    val positions: FilePositions
        @Synchronized get() {
            var memoized = _positions
            if (memoized == null) {
                memoized = FilePositions.fromLineLengths(loc, lines.map { it.length })
                _positions = memoized
            }
            return memoized
        }

    private val lines = mutableListOf("")
    // Things are simplified when there is always at least one line and at least one line after the
    // last line break.

    val wholeTextRange get() = Range(
        startPosition,
        if (lines.isEmpty()) {
            startPosition
        } else {
            val lastLineIndex = lines.lastIndex
            val lastLine = lines[lastLineIndex]
            Position(lastLineIndex, lastLine.length)
        },
    )

    val fullText: String
        @Synchronized
        get() = toStringViaBuilder {
            for (line in lines) { it.append(line) }
        }

    @Synchronized
    fun replace(range: Range, replacementText: String) {
        val start = range.start
        val end = range.end
        var startLine = start.line
        var startChar = start.character
        var endLine = end.line
        var endChar = end.character

        var newText = replacementText

        // Normalize the end position.  If it is at the start of a line, move it to the end of the
        // preceding line.
        if (endChar == 0 && endLine > startLine && endLine - 1 < lines.size) {
            endLine -= 1
            endChar = lines[endLine].length
        }

        // Normalize the start position.  If it is at the end of a line, shift it to the beginning
        // of the next line.
        if (
            startLine + 1 < lines.size &&
            startChar == lines[startLine].length
        ) {
            if (endLine == startLine && endChar == startChar) {
                endLine += 1
                endChar = 0
            }
            startLine += 1
            startChar = 0
        }

        // Insertion at end is tricky.  We've pushed start positions at the end of a line forward.
        // So it's consistently represented as replacement of char 0 of a non-existent line.
        // But if the last line doesn't end with a line break, and the replacement text doesn't
        // start with a line break, then they need to combine, so back the insertion point up.
        if (
            startChar == 0 && startLine == lines.size &&
            startChar == endChar && endLine == startLine
        ) {
            when {
                replacementText.isEmpty() -> return // Inserting nothing at end
                lines.lastOrNull()?.endsWithLineBreak != false -> {
                    lines.add("") // Make startLine and endLine index into line
                }
                else -> {
                    startLine -= 1
                    startChar = lines[startLine].length
                    endLine = startLine
                    endChar = startChar
                }
            }
        }

        // Coherence checks
        require(
            startLine in lines.indices &&
                endLine in lines.indices &&
                endChar in 0..lines[endLine].length &&
                (
                    (startChar == endChar && startLine == endLine) ||
                        startChar in lines[startLine].indices
                    ) &&
                (endLine > startLine || endChar >= startChar),
        ) {
            "range=$range, lines.size=${lines.size}, lineLengths=[${
                lines.joinToString { "${it.length}" }
            }], startLine=$startLine, startChar=$startChar, endLine=$endLine, endChar=$endChar"
        }

        // Simplify things by expanding newText if necessary so that all CRLF line-break sequences
        // are entirely included or excluded in newText.
        // For example
        //     oldText = "f o o \r \n b a z"
        //                        ^
        //     replacement = "\n b a r \r"
        // is equivalent to
        //     oldText = "f o o \r \n b a z"
        //     replace         ^-----^
        //     replacement = "\r \n bar \r \n"
        // since both yield the changed text
        //               "f o o \r \n bar \r \n b a z"
        var prependCarriageReturn = false
        if (startChar != 0) {
            val startLineText = lines[startLine]
            if (
                startChar < startLineText.length &&
                startLineText[startChar] == '\n' &&
                startLineText[startChar - 1] == '\r'
            ) {
                startChar -= 1
                prependCarriageReturn = true
            }
        }
        var appendLineFeed = false
        if (endChar != 0) {
            val endLineText = lines[endLine]
            if (
                endChar < endLineText.length &&
                endLineText[endChar] == '\n' &&
                endLineText[endChar - 1] == '\r'
            ) {
                endChar += 1
                appendLineFeed = true
            }
        }
        if (prependCarriageReturn || appendLineFeed) {
            newText = "${
                if (prependCarriageReturn) { "\r" } else { "" }
            }$newText${
                if (appendLineFeed) { "\n" } else { "" }
            }"
        }

        // Now we can count whole lines in newText easily to figure out whether the net change to
        // the overall line count.
        val updatedLines = run {
            val lineList = mutableListOf<String>()
            var pos = 0
            for (i in newText.indices) {
                if (newText.lineBreakAt(i)) {
                    lineList.add(newText.substring(pos, i + 1))
                    pos = i + 1
                }
            }
            if (pos < newText.length) {
                lineList.add(newText.substring(pos, newText.length))
            }
            lineList
        }
        debugReplace {
            console.log("newText=${jsonEscaper.escape(newText)} range=$range")
            console.log("startChar=$startChar, startLine=$startLine")
            console.log("endChar  =$endChar,   endLine  =$endLine")
        }
        // Now make sure any unchanged prefix of the first line is in newLines
        // and any unchanged suffix of the last line.
        val prefixFromBefore = if (startChar != 0) {
            lines[startLine].substring(0, startChar)
        } else {
            ""
        }
        val suffixFromBefore = lines[endLine].substring(endChar)
        debugReplace {
            console.log("prefixFromBefore=${jsonEscaper.escape(prefixFromBefore)}")
            console.log("suffixFromBefore=${jsonEscaper.escape(suffixFromBefore)}")
        }

        if (
            updatedLines.size == 1 && !updatedLines[0].endsWithLineBreak &&
            !prefixFromBefore.endsWithLineBreak &&
            // Don't miss the line merging in the else branch below if we need it.
            suffixFromBefore.isNotEmpty()
        ) {
            // Can glom both prefix and suffix on at once.
            updatedLines[0] = "$prefixFromBefore${updatedLines[0]}$suffixFromBefore"
        } else {
            when {
                prefixFromBefore.isEmpty() -> Unit
                prefixFromBefore.endsWithLineBreak -> {
                    // Can this happen given the normalization steps above?
                    updatedLines.add(0, prefixFromBefore)
                }
                updatedLines.isEmpty() -> updatedLines.add(prefixFromBefore)
                else -> updatedLines[0] = "$prefixFromBefore${updatedLines[0]}"
            }
            if (suffixFromBefore.isNotEmpty()) {
                val lastIndex = updatedLines.lastIndex
                val lastLine = updatedLines.getOrNull(lastIndex)
                // If the last line ends with a line break, and we've got
                // a suffix from before, that suffix should go on its own line.
                val addAsSeparateLine = lastLine == null || lastLine.endsWithLineBreak
                if (addAsSeparateLine) {
                    updatedLines.add(suffixFromBefore)
                } else {
                    updatedLines[lastIndex] = "$lastLine$suffixFromBefore"
                }
            } else if (endLine + 1 < lines.size) {
                // We need to have a line break on every line that is not the last, so
                // if there's no suffix from before, then we may not have a line break on the
                // end of the updated lines, so expand the newline.
                val lastUpdatedLineIndex = updatedLines.lastIndex
                if (lastUpdatedLineIndex >= 0) {
                    val lastUpdatedLine = updatedLines[lastUpdatedLineIndex]
                    if (!lastUpdatedLine.endsWithLineBreak) {
                        updatedLines[lastUpdatedLineIndex] += lines[endLine + 1]
                        // Make sure our replacement below blows away the next line since it's
                        // been accounted for.
                        endLine += 1
                        // We don't need to update endChar since we're only operating on whole
                        // lines from here on out.
                    }
                }
            }
        }

        // When we're recombining prefixes and suffixes, there's a tricky case that can arise.
        // "\r\n" is one line break, but if a prefix ends with "\r" by itself, and the first
        // update line consists entirely of "\n", we get three separate lines, but a left-to-right
        // linear scan of the whole text would only find two.
        // And a similar problem occurs when the last update line ends with "\r" and the suffix
        // starts with "\n".
        // First we widen the updated lines to include everything that might need to merge.
        if (startLine != 0) {
            val linePrecedingUpdatedRegion = lines[startLine - 1]
            val lineAfterPreceding = updatedLines.getOrNull(0) ?: lines.getOrNull(endLine + 1)
            if (linePrecedingUpdatedRegion.endsWith("\r") && lineAfterPreceding == "\n") {
                startLine -= 1
                updatedLines.add(0, linePrecedingUpdatedRegion)
            }
        }
        if (endLine + 1 < lines.size) {
            val lineFollowingUpdatedRegion = lines[endLine + 1]
            if (lineFollowingUpdatedRegion == "\n") {
                val lineBeforeFollowing =
                    updatedLines.lastOrNull() ?: lines.getOrNull(startLine - 1)
                if (lineBeforeFollowing?.endsWith("\r") == true) {
                    endLine += 1
                    updatedLines.add(lineFollowingUpdatedRegion)
                }
            }
        }

        // Mutate update lines list in place to merge adjacent CR LF
        for (i in (1 until updatedLines.size).reversed()) {
            if (updatedLines[i] == "\n" && updatedLines[i - 1].endsWith("\r")) {
                updatedLines[i - 1] += updatedLines.removeAt(i)
            }
        }

        debugReplace {
            console.group("updatedLines") {
                for (line in updatedLines) {
                    console.log(jsonEscaper.escape(line))
                }
            }
        }

        lines.replaceSubList(startLine, endLine + 1, updatedLines)

        if (lines.isEmpty()) {
            lines.add("") // See not in initializer above
        }

        this._positions = null
    }

    /** Direct access to the lines list for test code. */
    internal val debugLines get() = lines.toList()
}

/**
 * Position at the beginning of a file.
 */
private val startPosition = Position(0, 0)

/**
 * From the LSP spec:
 *     const EOL: string[] = ['\n', '\r\n', '\r']
 */
internal fun (CharSequence).lineBreakAt(i: Int): Boolean {
    val c = this[i]
    return c == '\n' || // LF
        (c == '\r' && (i + 1 == this.length || this[i + 1] != '\n')) // lone CR
}

internal val (CharSequence).endsWithLineBreak: Boolean
    get() = this.isNotEmpty() && lineBreakAt(this.length - 1)
