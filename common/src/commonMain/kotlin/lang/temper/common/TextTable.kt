package lang.temper.common

import kotlin.math.max

class TextTable(
    val rows: List<List<MultilineOutput>>,
) : MultilineOutput {

    constructor(
        headerRow: List<MultilineOutput>,
        otherRows: List<List<MultilineOutput>>,
    ) : this(listOf(headerRow) + otherRows)

    private fun computeRenderInfo(columnWidthHints: List<Int>? = null): RenderInfo {
        val nRows = rows.size
        val nColumns = rows.fold(0) { n, row -> max(n, row.size) }
        // Convert to a cell list where
        //     cell[i + j * nColumns]
        // is the cell at column i and row j.
        val cells = (0 until (nRows * nColumns)).map { k ->
            val i = k.rem(nColumns)
            val j = k / nColumns
            val o = rows[j].getOrNull(i) ?: MultilineOutput.Empty
            val lines = mutableListOf<String>()
            o.addOutputLines(lines)
            lines
        }

        val colWidths = (0 until nColumns).map { i ->
            (0 until nRows).fold(0) { width, j ->
                val cell = cells[i + j * nColumns]
                var widthRequired =
                    max(width, cell.fold(width) { w, line -> max(w, line.length) })
                if (columnWidthHints != null && i in columnWidthHints.indices) {
                    widthRequired = max(widthRequired, columnWidthHints[i])
                }
                widthRequired
            }
        }
        val rowHeights = (0 until nRows).map { j ->
            (0 until nColumns).fold(0) { height, i ->
                val cell = cells[i + j * nColumns]
                max(height, cell.size)
            }
        }
        return RenderInfo(nRows, nColumns, cells, colWidths, rowHeights)
    }

    val columnWidths: List<Int> get() = computeRenderInfo().colWidths

    override fun addOutputLines(ls: MutableList<String>) = addOutputLines(ls, null)

    fun addOutputLines(ls: MutableList<String>, columnWidthHints: List<Int>?) {
        val (nRows, nColumns, cells, colWidths, rowHeights) = computeRenderInfo(columnWidthHints)
        val sb = StringBuilder()
        for (j in 0 until nRows) {
            ls.add(horizontalRule(colWidths, above = j != 0, below = nRows != 0))
            val rowHeight = rowHeights[j]
            for (rl in 0 until rowHeight) {
                sb.clear()
                for (i in 0 until nColumns) {
                    sb.append('║')
                    val colWidth = colWidths[i]
                    val lineText = cells[i + j * nColumns].getOrNull(rl) ?: ""
                    sb.append(lineText)
                    repeat(colWidth - lineText.length) {
                        sb.append(' ')
                    }
                }
                sb.append('║')
                ls.add(sb.toString())
            }
        }
        ls.add(horizontalRule(colWidths, above = nRows != 0, below = false))
    }

    override fun toString(): String = toString(null)

    fun toString(columnWidthHints: List<Int>?) = toStringViaBuilder {
        toStringBuilder(it, columnWidthHints)
    }

    fun toStringBuilder(out: StringBuilder, columnWidthHints: List<Int>? = null) {
        val ls = mutableListOf<String>()
        addOutputLines(ls, columnWidthHints)
        ls.joinTo(out, "\n")
    }
}

private fun horizontalRule(widths: List<Int>, above: Boolean, below: Boolean) =
    toStringViaBuilder { sb ->
        val connectorIndex = (if (above) 2 else 0) or (if (below) 1 else 0)
        sb.append(LEFT_CONNECTOR[connectorIndex])
        for (i in widths.indices) {
            if (i != 0) {
                sb.append(MIDDLE_CONNECTOR[connectorIndex])
            }
            repeat(widths[i]) { sb.append('═') }
        }
        sb.append(RIGHT_CONNECTOR[connectorIndex])
    }

private const val LEFT_CONNECTOR = "╺╔╚╠"
private const val MIDDLE_CONNECTOR = "═╦╩╬"
private const val RIGHT_CONNECTOR = "╸╗╝╣"

private data class RenderInfo(
    val nRows: Int,
    val nColumns: Int,
    val cells: List<MutableList<String>>,
    val colWidths: List<Int>,
    val rowHeights: List<Int>,
)
