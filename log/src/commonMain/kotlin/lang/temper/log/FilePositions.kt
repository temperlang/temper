package lang.temper.log

import lang.temper.common.binarySearch
import lang.temper.common.emptyIntArray
import lang.temper.common.toStringViaBuilder
import java.lang.StringBuilder

/**
 * Maps character offsets like those in [Position] to human-readable positions with line numbers.
 */
class FilePositions private constructor(
    val codeLocation: CodeLocation?,
    private val lineEndOffsets: IntArray,
) {

    fun spanning(position: Position): Pair<FilePosition, FilePosition>? =
        if (position.loc == codeLocation) {
            val leftPos = filePositionAtOffset(position.left)
            Pair(
                leftPos,
                if (position.left == position.right) {
                    leftPos
                } else {
                    filePositionAtOffset(position.right)
                },
            )
        } else {
            null
        }

    fun filePositionAtOffset(offset: Int): FilePosition {
        val insertionPoint = binarySearch(lineEndOffsets, offset)
        val lineNumZeroIndexed = if (insertionPoint < 0) {
            // If it's negative, its inverse is the index of the line ending after it.
            insertionPoint.inv()
        } else {
            // If it's positive, then it points just after a line break.
            insertionPoint + 1
        }
        val lineStartOffset = if (lineNumZeroIndexed == 0) {
            0
        } else {
            lineEndOffsets[lineNumZeroIndexed - 1]
        }
        return FilePosition(
            line = lineNumZeroIndexed + 1,
            charInLine = offset - lineStartOffset,
        )
    }

    /** Returns null for invalid position. */
    fun offsetAtFilePosition(pos: FilePosition): Int? {
        // Go from 1-based to 0-based.
        val line = pos.line - 1
        if (line < 0 || lineEndOffsets.size < line) {
            return null
        }
        var offset = 0
        if (line > 0) {
            offset += lineEndOffsets[line - 1]
        }
        // This allows placement after endlines, which could be two chars. Chill enough???
        // And lineEndOffsets has no knowledge of the last line at all.
        if (line < lineEndOffsets.size && pos.charInLine >= lineEndOffsets[line] - offset) {
            return null
        }
        return offset + pos.charInLine
    }

    companion object {
        fun fromSource(loc: CodeLocation, input: CharSequence): FilePositions {
            val offsets = mutableListOf<Int>()
            var i = 0
            val n = input.length
            while (i < n) {
                val c = input[i]
                i += 1
                when (c) {
                    '\n' -> offsets.add(i)
                    '\r' -> {
                        if (i < n && input[i] == '\n') { // CRLF is special
                            i += 1
                        }
                        offsets.add(i)
                    }
                }
            }
            return FilePositions(loc, offsets.toIntArray())
        }

        fun fromLineLengths(loc: CodeLocation, lineLengths: List<Int>): FilePositions {
            var nLines = lineLengths.size
            if (nLines != 0 && lineLengths[nLines - 1] == 0) {
                nLines -= 1
            }
            var lengthSoFar = 0
            val charOffsetArray = IntArray(nLines)
            for (i in 0 until nLines) {
                val lineLength = lineLengths[i]
                require(lineLength > 0)
                lengthSoFar += lineLength
                charOffsetArray[i] = lengthSoFar
            }
            return FilePositions(loc, charOffsetArray)
        }

        val nil = FilePositions(null, emptyIntArray)
    }
}

data class FilePosition(
    /**
     * The one-indexed line in the file;
     * one more than the number of line breaks preceding the zero-indexed character global offset in the file.
     */
    val line: Int,
    /** The zero-indexed count of characters between the start of [line] and the global character offset. */
    val charInLine: Int,
) {
    override fun toString() = this.toReadablePosition()
}

fun (FilePosition).toReadablePosition(fileName: String? = null) = toStringViaBuilder { sb ->
    if (fileName != null) {
        sb.append(fileName)
        sb.append(':')
    }
    sb.append(this.line)
    sb.append('+')
    sb.append(this.charInLine)
}

fun (Pair<FilePosition, FilePosition>).toReadablePosition(fileName: String? = null): String =
    toStringViaBuilder { sb ->
        val (p, q) = this
        if (fileName != null) {
            sb.append(fileName)
            sb.append(':')
        }
        appendReadableLineAndColumn(p, q, sb)
    }

fun appendReadableLineAndColumn(p: FilePosition, q: FilePosition, out: StringBuilder) {
    val pLine = p.line
    val qLine = q.line
    val pChar = p.charInLine
    val qChar = q.charInLine
    if (pLine == qLine) {
        out.append(pLine)
        out.append('+')
        out.append(pChar)
        if (pChar != qChar) {
            out.append('-')
            out.append(qChar)
        }
    } else {
        out.append(pLine).append('+').append(pChar).append(" - ")
            .append(qLine).append('+').append(qChar)
    }
}
