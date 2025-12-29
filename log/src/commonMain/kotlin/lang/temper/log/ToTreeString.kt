package lang.temper.log

import lang.temper.common.MultilineOutput
import lang.temper.common.commonPrefixBy
import lang.temper.common.compatRemoveLast
import lang.temper.common.subListToEnd
import lang.temper.log.FilePath.Companion.join
import kotlin.math.min

/**
 * Given [a/b/c, a/b/d, a/b/], ascii art like the below
 *
 *     a/
 *     ┗━b/
 *       ┣━c
 *       ┗━d
 */
fun Iterable<FilePath>.toFileTreeString(): String =
    toTreeString(
        unpack = { it.segments },
        render = { whole, segments ->
            val isDir = whole?.isDir ?: true
            segments
                .ifEmpty { listOf(SameDirPseudoFilePathSegment) }
                .join(isDir = isDir)
        },
    )

fun <E, SEGMENT : Comparable<SEGMENT>> Iterable<E>.toTreeString(
    unpack: (E) -> List<SEGMENT>,
    render: (E?, List<SEGMENT>) -> String,
) = this.toTreeString(unpack = unpack, comparator = naturalOrder(), render = render)

fun <E, SEGMENT> Iterable<E>.toTreeString(
    unpack: (E) -> List<SEGMENT>,
    comparator: Comparator<SEGMENT>,
    render: (E?, List<SEGMENT>) -> String,
): String {
    val rows = this.map { it to unpack(it) }
        .sortedWith { (_, a), (_, b) ->
            for (i in 0 until min(a.size, b.size)) {
                val delta = comparator.compare(a[i], b[i])
                if (delta != 0) {
                    return@sortedWith delta
                }
            }
            a.size.compareTo(b.size)
        }
    if (rows.isEmpty()) { return "" }

    // If we have paths like
    //
    //   [a]
    //   [a, b, c]
    //
    // we need to insert missing elements so that we have [a, b] in between.

    // We generate rows as character buffers so that we can go back, and generate descending lines.
    // To that end, we track prefixes [a, a/b, a/b/c].
    // For each prefix, we know
    //
    // - the index of the character buffer for that row
    // - the offset of the left of that rendered prefix.

    data class RowInfo(
        val element: E?,
        val segments: List<SEGMENT>,
        val rowIndex: Int,
        val charOffset: Int,
    )

    val rowInfos = mutableListOf<RowInfo>()
    val rowChars = mutableListOf<StringBuilder>()

    fun emit(element: E?, segments: List<SEGMENT>) {
        val parentRowIndex = rowInfos.indexOfLast {
            it.segments.size + 1 == segments.size
        }
        val chars = StringBuilder()
        rowChars.add(chars)
        val rowIndex = rowChars.lastIndex

        var parentSegments = emptyList<SEGMENT>()
        if (parentRowIndex >= 0) {
            val parentRowInfo = rowInfos[parentRowIndex]
            parentSegments = parentRowInfo.segments
            val parentCharOffset = parentRowInfo.charOffset
            repeat(parentCharOffset) { chars.append(edges.blank) }
            chars.append(edges.upRight)
            chars.append(edges.horizontal)
            // Connect the vertical line to the parent
            var i = rowIndex
            while (--i > parentRowIndex) {
                val interveningRow = rowChars[i]
                if (interveningRow[parentCharOffset] == edges.blank) {
                    interveningRow[parentCharOffset] = edges.vertical
                    continue
                }
                if (interveningRow[parentCharOffset] == edges.upRight) {
                    interveningRow[parentCharOffset] = edges.verticalRight
                }
                break
            }
        }
        val charOffset = chars.length

        val rowInfo = RowInfo(
            element = element,
            segments = segments,
            rowIndex = rowIndex,
            charOffset = charOffset,
        )
        rowInfos.add(rowInfo)

        val rendered = render(element, segments.subListToEnd(parentSegments.size))
        val lines = buildList {
            MultilineOutput.of(rendered).addOutputLines(this)
        }
        if (lines.size <= 1) {
            chars.append(rendered)
        } else {
            chars.append(lines[0])
            // Create additional line buffers for subsequent rendered lines
            for (i in 1 until lines.size) {
                val lineChars = StringBuilder()
                repeat(charOffset) { lineChars.append(edges.blank) }
                lineChars.append(lines[i])
                rowChars.add(lineChars)
            }
        }
    }

    val commonPrefix = commonPrefixBy(rows) { it.second }
    if (rows.first().second.size == commonPrefix.size) {
        // The first row contains all the others.  Just iterate to it.
    } else {
        emit(null, commonPrefix)
    }

    for ((element, segments) in rows) {
        // Discard row infos that are not a prefix of row.
        while (rowInfos.isNotEmpty()) {
            val lastRowInfo = rowInfos.last()
            if (isPrefixOf(lastRowInfo.segments, segments)) { break }
            rowInfos.compatRemoveLast()
        }

        // Insert any missing rows
        rowInfos.lastOrNull()?.let { lastRow ->
            for (n in (lastRow.segments.size + 1) until segments.size) {
                emit(null, segments.subList(0, n))
            }
        }

        // Render a row
        emit(element, segments)
    }

    return buildString {
        for ((i, row) in rowChars.withIndex()) {
            if (i != 0) { append('\n') }
            append(row)
        }
    }
}

private class Edges(
    val blank: Char,
    val vertical: Char,
    val horizontal: Char,
    val upRight: Char,
    val verticalRight: Char,
)
private val edges = Edges(
    blank = ' ',
    vertical = '┃',
    horizontal = '━',
    upRight = '┗',
    verticalRight = '┣',
)

private fun <T> isPrefixOf(possiblePrefix: List<T>, list: List<T>): Boolean {
    val n = possiblePrefix.size
    if (list.size < n) { return false }
    for (i in 0 until n) {
        if (possiblePrefix[i] != list[i]) { return false }
    }
    return true
}
