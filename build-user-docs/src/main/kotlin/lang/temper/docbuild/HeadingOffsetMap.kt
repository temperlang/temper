package lang.temper.docbuild

import lang.temper.common.binarySearch
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Heading

/**
 * Finds headers in Markdown so that we can find the level (1-6) of a heading before a point.
 *
 * This lets us adjust the levels of headings in a snippet to match the context in which it's
 * inserted.
 */
internal class HeadingOffsetMap(
    content: MarkdownContent,
) {
    /** Sorted array of character offsets that can be binary searched into */
    private val positions: IntArray

    /** Elements correspond to positions */
    private val headerLevels: IntArray

    init {
        val positionsAndHeadingLevels = findPositionsAndHeadingLevels(content)
        this.positions = positionsAndHeadingLevels.map { it.first }.toIntArray()
        this.headerLevels = positionsAndHeadingLevels.map { it.second }.toIntArray()
    }

    /**
     * The level of the heading at or preceding the given character offset or `0` if
     * there is none.
     *
     * So if [offset] is 123 and the heading with max offset <= 123 is `### Foo`, returns 3.
     *
     * If [offset] falls within a heading, then the level of that heading.
     */
    operator fun get(offset: Int): Int {
        val searchMark = binarySearch(positions, offset)
        return if (searchMark < 0) {
            val insertionPoint = searchMark.inv()
            // [1, 2, 5]
            if (insertionPoint == 0) {
                0 // Before first heading
            } else {
                headerLevels[insertionPoint - 1]
            }
        } else {
            headerLevels[searchMark]
        }
    }
}

/**
 * (Position, Level) pairs of headings in the given markdown.
 * Positions are character offsets into the Markdown text like those used in AST nodes.
 * Levels are integers in [1-6] that mirror the numbers in HTML `<h1>`-`<h6>`.
 */
internal fun findPositionsAndHeadingLevels(markdownContent: MarkdownContent): List<Pair<Int, Int>> {
    return buildList {
        markdownContent.root.accept(
            object : AbstractVisitor() {
                override fun visit(heading: Heading) {
                    add(markdownContent.startIndex(heading) to heading.level)
                }
            },
        )
    }
}
