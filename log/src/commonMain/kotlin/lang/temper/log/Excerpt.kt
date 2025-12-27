package lang.temper.log

import lang.temper.common.TextOutput
import lang.temper.common.TtyCode
import lang.temper.common.sprintf
import lang.temper.common.toStringViaBuilder
import kotlin.math.max
import kotlin.math.min

const val DEFAULT_CONTEXT_LEN = 20

// At the beginning of the line comes the padded line number then 2 characters:
// a colon and either a space or a hugging excerpt marker bar.
private const val LINE_NUMBER_FOLLOWER_LENGTH = 2
private val nonHuggingLineNumberFollower = listOf(
    TextChunk(null, ": ", TtyCode.FgDefault),
)
private val huggingLineNumberFollower = listOf(
    TextChunk(null, ":", null),
    TextChunk(TtyCode.FgDefault, "┃", null),
)

const val DEFAULT_MAX_EXCERPT_LINE_LENGTH = 100
private const val MIN_ABBREVIATED_CHUNK_LEN = 30
const val DEFAULT_MAX_LINES_PER_EXCERPT = 5
const val DEFAULT_LINE_NUMBER_AT_START_OF_INPUT = 1
private const val MIN_EXCERPT_MAX_LINE_LENGTH = 64 // Yes, one can have a minimum maximum.

/**
 * Excerpts source code, e.g. so that an error message can show alongside relevant code.
 */
fun excerpt(
    /** The position to excerpt. */
    p: Position,
    /** The source code. */
    input: CharSequence,
    /** Receives the chunks of output constituting the excerpt. */
    textOutput: TextOutput,
    /** The amount of context to provide.  Think `diff -C` but in characters, not lines. */
    contextLen: Int = DEFAULT_CONTEXT_LEN,
    /**
     * The number of the line on which input\[0] falls.
     * By text editor convention, line numbers are one-indexed.
     */
    lineNumberAtStartOfInput: Int = DEFAULT_LINE_NUMBER_AT_START_OF_INPUT,
    maxLinesPerExcerpt: Int = DEFAULT_MAX_LINES_PER_EXCERPT,
    maxExcerptLineLength: Int = DEFAULT_MAX_EXCERPT_LINE_LENGTH,
) {
    val left = max(0, p.left)
    val right = max(left, p.right)
    require(contextLen >= 0 && maxLinesPerExcerpt >= 1 && maxExcerptLineLength >= 0)
    val maxExcerptLineLengthReasonable = max(MIN_EXCERPT_MAX_LINE_LENGTH, maxExcerptLineLength)

    val inputLength = input.length
    var excerptLeft = left
    var excerptRight = right
    val leftLimit = max(0, left - contextLen)
    val rightLimit = min(inputLength, right + contextLen)

    while (excerptLeft > leftLimit) {
        val c = input[excerptLeft - 1]
        if (isLinebreakChar(c)) {
            break
        }
        excerptLeft -= 1
    }

    while (excerptRight < rightLimit) {
        val c = input[excerptRight]
        if (isLinebreakChar(c)) {
            break
        }
        excerptRight += 1
    }

    val excerptedLineBounds = mutableListOf<Pair<Int, Int>>()
    var lineStartIndex = excerptLeft
    forEachLineBreakBetween(input, excerptLeft, excerptRight) { s, e ->
        excerptedLineBounds.add(lineStartIndex to s)
        lineStartIndex = e
    }
    excerptedLineBounds.add(lineStartIndex to excerptRight)

    // If all lines start with some common space chars, strip it off so that
    // we don't push deeply indented chunks all the way to the right.
    run {
        val (start0, end0) = excerptedLineBounds[0]
        var commonSpacePrefix = 0
        for (i in start0 until end0) {
            val c = input[i]
            if (c == ' ' || c == '\t') {
                commonSpacePrefix += 1
            } else {
                break
            }
        }
        for (lineIndex in 1 until excerptedLineBounds.size) {
            val (start, end) = excerptedLineBounds[lineIndex]
            commonSpacePrefix = min(commonSpacePrefix, end - start)
            for (i in 0 until commonSpacePrefix) {
                if (input[start0 + i] != input[start + i]) {
                    commonSpacePrefix = i
                    break
                }
            }
            if (commonSpacePrefix == 0) break
        }
        for (i in excerptedLineBounds.indices) {
            val untrimmed = excerptedLineBounds[i]
            excerptedLineBounds[i] = (untrimmed.first + commonSpacePrefix) to untrimmed.second
        }
    }

    // Determine the line number of the start of the excerpt in input.
    var startLineNumber = lineNumberAtStartOfInput
    forEachLineBreakBetween(input, 0, excerptLeft) { _, _ -> startLineNumber += 1 }

    @Suppress("MagicNumber")
    val showOnOneLine = excerptedLineBounds.size > maxLinesPerExcerpt &&
        // 1 for the line with the start marker, 1 for the line with the end marker, and
        // 1 for the vertical ellipsis
        maxLinesPerExcerpt < 3
    // If there's no space to abbreviate whole lines out while leaving space for vertical ellipsis,
    // then flatten the whole thing into one line and use horizontal ellipses.
    val unflattenedLineBounds = excerptedLineBounds.toList()
    if (showOnOneLine) {
        // Make later code think there's no line breaks.
        // We'll later adjust text chunks to replace CR and LF with spaces to make this appear true
        excerptedLineBounds.clear()
        excerptedLineBounds.add(
            unflattenedLineBounds.first().first to unflattenedLineBounds.last().second,
        )
    }

    val nLines = excerptedLineBounds.size
    // Multiple lines.  Put start marker above, and end marker below.
    val abbreviatedLineNumbers = if (nLines > maxLinesPerExcerpt) {
        // Abbreviate lines in the middle leaving space for one line that is just "..."
        (maxLinesPerExcerpt / 2) until (nLines - ((maxLinesPerExcerpt - 1) / 2))
    } else {
        IntRange.EMPTY
    }

    val lastLineNumber = startLineNumber + nLines - 1
    val lineNumberDigitCount = "$lastLineNumber".length
    // A hugging marker wraps around a multiline block of code like the
    // `if` body block below.
    // An excerpt marker is a hugger if it spans multiple, physical lines.
    //
    //   ┏━━━━━━━┓
    // 1:┃if (x) {
    // 2:┃  body()
    // 3:┃}
    //   ┗┛
    val isHugger = lastLineNumber != startLineNumber
    val lineNumberFollower = if (isHugger) {
        huggingLineNumberFollower
    } else {
        nonHuggingLineNumberFollower
    }
    val linePrefixSize = lineNumberDigitCount + LINE_NUMBER_FOLLOWER_LENGTH

    var wroteAbbreviatedLineMarker = false
    for (excerptLineIndex in 0 until nLines) {
        if (excerptLineIndex in abbreviatedLineNumbers) {
            if (!wroteAbbreviatedLineMarker) {
                wroteAbbreviatedLineMarker = true
                textOutput.emitTty(TtyCode.FgDefault)
                textOutput.emitLineChunk(
                    toStringViaBuilder { buffer ->
                        repeatCharsOnto(linePrefixSize - 1, ' ', buffer)
                        buffer.append(if (isHugger) '┃' else ' ')
                    },
                )
                textOutput.emitTty(TtyCode.FgGrey)
                textOutput.emitLineChunk("\u22EE") // Vertical ellipsis
                textOutput.endLine()
            }
            continue
        }
        val (lineStart, lineEnd) = excerptedLineBounds[excerptLineIndex]
        val lineNumber = startLineNumber + excerptLineIndex
        val lineNumberPadded = sprintf(
            "%0*d",
            listOf(lineNumberDigitCount, lineNumber),
        )

        // Break the line into chunks so that we can reduce it properly, line up markers with it,
        // and format it properly.
        val chunks = mutableListOf<LineChunk>()
        chunks.add(TextChunk(TtyCode.FgGrey, lineNumberPadded, null))
        chunks.addAll(lineNumberFollower)
        val physicalLines = if (showOnOneLine) {
            unflattenedLineBounds
        } else {
            listOf(lineStart to lineEnd)
        }
        for ((physicalLineStart, physicalLineEnd) in physicalLines) {
            if (showOnOneLine && lineStart != physicalLineStart) {
                // Replace line breaks with spaces when flowing the code onto one line.
                chunks.add(TextChunk(null, " ", null))
            }

            val lineRange = physicalLineStart..physicalLineEnd
            var emittedTo = physicalLineStart
            if (left in lineRange) {
                chunks.add(TextChunk(null, input.subSequence(emittedTo, left), null))
                chunks.add(LeftMarker)
                emittedTo = left
            }
            if (right in lineRange) {
                val rightInclusive = max(physicalLineStart, right - 1)
                if (rightInclusive > emittedTo) {
                    chunks.add(
                        TextChunk(
                            TtyCode.Bold,
                            input.subSequence(emittedTo, rightInclusive),
                            TtyCode.NormalColorIntensity,
                        ),
                    )
                    emittedTo = rightInclusive
                }
                chunks.add(RightMarker)
                if (right > emittedTo) {
                    // The actual character pointed to by the right marker, if any needs to be bolded.
                    chunks.add(
                        TextChunk(
                            TtyCode.Bold,
                            input.subSequence(emittedTo, right),
                            TtyCode.NormalColorIntensity,
                        ),
                    )
                    emittedTo = right
                }
            }
            val restMatched = emittedTo in left until right
            chunks.add(
                TextChunk(
                    if (restMatched) TtyCode.Bold else null,
                    input.subSequence(emittedTo, physicalLineEnd),
                    if (restMatched) TtyCode.NormalColorIntensity else null,
                ),
            )
        }

        // Abbreviate chunks if the line is overlong.
        var totalLen = chunks.fold(0) { n, chunk -> n + chunk.length }
        while (totalLen > maxExcerptLineLengthReasonable) {
            // Find the longest chunk and ellipsize it.
            val (chunkIndex) = chunks.foldIndexed(null) { index, best: Pair<Int, Int>?, chunk ->
                if (chunk is TextChunk && (best == null || best.second < chunk.length)) {
                    index to chunk.length
                } else {
                    best
                }
            }!!
            val chunk = chunks[chunkIndex] as TextChunk
            val goalLen = max(
                MIN_ABBREVIATED_CHUNK_LEN,
                chunk.length - (totalLen - maxExcerptLineLengthReasonable) -
                    ABBREVIATED_REGION_PLACEHOLDER.length,
            )
            val replacements = chunk.ellipsize(goalLen)
            val replacementLength = replacements.fold(0) { n, c -> n + c.length }
            val slice = chunks.subList(chunkIndex, chunkIndex + 1)
            slice.clear()
            slice.addAll(replacements)
            val lengthDelta = replacementLength - chunk.length
            require(lengthDelta < 0) // Otherwise we might loop indefinitely
            totalLen += lengthDelta
        }

        // Figure out where to put markers, if any.
        val leftMarkerVisibleOffset: Int?
        val rightMarkerVisibleOffset: Int?

        run {
            var leftMarkerCharOffset: Int? = null
            var rightMarkerCharOffset: Int? = null

            val chunkChars = StringBuilder()
            for (chunk in chunks) {
                when (chunk) {
                    LeftMarker -> leftMarkerCharOffset = chunkChars.length
                    RightMarker -> rightMarkerCharOffset = chunkChars.length
                    is TextChunk -> {}
                }
                chunk.appendCharsTo(chunkChars)
            }

            leftMarkerVisibleOffset = if (leftMarkerCharOffset != null) {
                visibleOffsetOf(chunkChars, leftMarkerCharOffset, textOutput.tabWidth)
            } else {
                null
            }
            rightMarkerVisibleOffset = if (rightMarkerCharOffset != null) {
                visibleOffsetOf(chunkChars, rightMarkerCharOffset, textOutput.tabWidth)
            } else {
                null
            }
        }
        val showMarker = leftMarkerVisibleOffset != null || rightMarkerVisibleOffset != null
        val showMarkerAfter = showMarker && excerptLineIndex + 1 == nLines
        val showMarkerBefore = showMarker && !showMarkerAfter

        // Now output the chunks with markers as needed.
        if (showMarkerBefore) {
            val marker = lineMarker(
                beginOffset = leftMarkerVisibleOffset,
                endOffset = rightMarkerVisibleOffset,
                linePrefixSize = linePrefixSize,
                isHugger = isHugger,
                below = false,
            )
            if (marker != null) {
                textOutput.emitLine(marker)
            }
        }

        chunks.forEach { it.outputText(textOutput) }
        textOutput.endLine()

        if (showMarkerAfter) {
            val marker = lineMarker(
                beginOffset = leftMarkerVisibleOffset,
                endOffset = rightMarkerVisibleOffset,
                linePrefixSize = linePrefixSize,
                isHugger = isHugger,
                below = true,
            )
            if (marker != null) {
                textOutput.emitLine(marker)
            }
        }
    }
}

private fun visibleOffsetOf(cs: CharSequence, charOffset: Int, tabWidth: Int): Int {
    var visibleOffset = 0
    for (i in 0 until charOffset) {
        val c = cs[i]
        if (c == '\t') {
            visibleOffset = visibleOffset + tabWidth - visibleOffset.rem(tabWidth)
        } else {
            visibleOffset += 1
        }
    }
    return visibleOffset
}

private fun lineMarker(
    beginOffset: Int?,
    endOffset: Int?,
    linePrefixSize: Int,
    isHugger: Boolean,
    below: Boolean,
): String? {
    if (beginOffset == null && endOffset == null) {
        return null
    }
    val left: Int?
    val right: Int?
    if (isHugger) {
        check(beginOffset == null || endOffset == null)
        left = linePrefixSize - 1
        // Add 1 to leave space for the left hanging bar to connect
        right = beginOffset ?: endOffset!!
    } else {
        left = beginOffset
        right = endOffset
    }

    return buildString {
        if (left != null && right != null && left <= right - 1) {
            repeatCharsOnto(left, ' ', this)
            // Emit u-shape with line drawing characters
            append(if (below) '\u2517' else '\u250F')
            repeatCharsOnto(right - (left + 1), '\u2501', this)
            append(if (below) '\u251B' else '\u2513')
        } else {
            repeatCharsOnto(left ?: right ?: 0, ' ', this)
            append(if (below) '⇧' else '⇩')
        }
    }
}

// TODO: this should be shared with LexicalDefinitions
private fun isLinebreakChar(c: Char) = c == '\n' || c == '\r'
private fun forEachLineBreakBetween(
    cs: CharSequence,
    left: Int,
    right: Int,
    action: (before: Int, after: Int) -> Unit,
) {
    var i = left
    while (i < right) {
        val c = cs[i]
        i += 1
        when (c) {
            '\r' -> {
                val before = i - 1
                if (i < right && cs[i] == '\n') {
                    i += 1 // do not treat the LF in CRLF as its own line break.
                }
                val after = i
                action(before, after)
            }
            '\n' -> action(i - 1, i)
        }
    }
}

private const val ABBREVIATED_REGION_PLACEHOLDER = "\u22EF" // Middle horizontal ellipses

private fun repeatCharsOnto(count: Int, c: Char, out: StringBuilder) {
    repeat(count) { out.append(c) }
}

private sealed class LineChunk {
    abstract fun outputText(textOutput: TextOutput)
    abstract fun appendCharsTo(sb: StringBuilder)
    abstract val length: Int
}

private object LeftMarker : LineChunk() {
    override fun outputText(textOutput: TextOutput) {
        // Not visible.  Exists to aid marker alignment.
    }
    override fun appendCharsTo(sb: StringBuilder) {
        // ditto
    }
    override val length = 0
    override fun toString() = "LeftMarker"
}

private object RightMarker : LineChunk() {
    override fun outputText(textOutput: TextOutput) {
        // Not visible.  Exists to aid marker alignment.
    }
    override fun appendCharsTo(sb: StringBuilder) {
        // ditto
    }
    override val length = 0
    override fun toString() = "RightMarker"
}

private data class TextChunk(
    val before: TtyCode? = null,
    val text: CharSequence,
    val after: TtyCode?,
) : LineChunk() {
    override fun outputText(textOutput: TextOutput) {
        if (before != null) { textOutput.emitTty(before) }
        textOutput.emitLineChunk(text)
        if (after != null) { textOutput.emitTty(after) }
    }

    override fun appendCharsTo(sb: StringBuilder) {
        sb.append(text)
    }

    override val length = text.length

    /**
     * Abbreviates a long text chunk by replacing a chunk in the middle with "...".
     */
    fun ellipsize(goalLen: Int): List<LineChunk> {
        val n = text.length
        require(n > goalLen)
        val targetToSubtract = n - goalLen
        val middle = n / 2
        // Tentatively establish the bounds of the hole.
        var holeLeft = middle - (targetToSubtract / 2)
        var holeRight = middle + ((targetToSubtract + 1) / 2)
        // Suck line breaks into the hole.
        while (holeLeft > 0 && isLinebreakChar(text[holeLeft])) {
            holeLeft -= 1
        }
        while (holeRight < n && isLinebreakChar(text[holeRight])) {
            holeRight += 1
        }
        // Adjust to include full surrogate pairs.
        // If the left points to the right-half of a surrogate pair, move it right so that
        // the whole pair is not ellipsized.
        if (holeLeft < holeRight && text[holeLeft] in '\uDC00'..'\uDFFF') {
            holeLeft += 1
        }
        // Since the right endpoint is exclusive, if the right points at the right half, move it left
        // so that the exclusive right points at the left of the surrogate pair which will then be
        // outside the ellipsized region.
        if (holeRight > holeLeft && text[holeRight] in '\uDC00'..'\uDFFF') {
            // Right is exclusive
            holeRight -= 1
        }
        return if (holeLeft >= holeRight) {
            listOf(this)
        } else {
            listOf(
                TextChunk(before, text.subSequence(0, holeLeft), after),
                elidedRegion,
                TextChunk(before, text.subSequence(holeRight, n), after),
            )
        }
    }
}

private val elidedRegion =
    TextChunk(TtyCode.FgGrey, ABBREVIATED_REGION_PLACEHOLDER, TtyCode.FgDefault)
