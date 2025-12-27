package lang.temper.docbuild

import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.children
import org.commonmark.node.SoftLineBreak

/**
 * The given Markdown but without any leading heading as long as it is the most important heading.
 */
internal fun removeOverArchingHeading(markdownContent: MarkdownContent): String {
    val fileContent = markdownContent.fileContent

    val headingNode = findOverarchingHeading(markdownContent) ?: return fileContent

    // Splice around it.
    return toStringViaBuilder {
        it.append(fileContent, 0, headingNode.first)
        var pos = headingNode.last + 1
        val limit = fileContent.length
        // Don't leave a bunch of blank lines in between since we just removed
        // a block element
        while (pos < limit) {
            when (fileContent[pos]) {
                '\n', '\r' -> pos += 1
                else -> break
            }
        }
        it.append(fileContent, pos, limit)
    }
}

/**
 * The given Markdown but without any leading heading as long as it is the most important heading.
 */
internal fun findOverarchingHeading(markdownContent: MarkdownContent): IntRange? {
    val positionsAndLevels = findPositionsAndHeadingLevels(markdownContent)

    if (positionsAndLevels.isEmpty()) { return null }
    val (firstHeaderPos, firstHeaderLevel) = positionsAndLevels[0]

    // We're looking for the first heading, and we want it if it has the lowest level (is most important),
    // and it's only preceded by EOL tokens or ignorable comments.

    if (positionsAndLevels.any { it.second <= firstHeaderLevel && it.first != firstHeaderPos }) {
        return null
    }

    for (child in markdownContent.root.children()) {
        if (child is SoftLineBreak || isIgnorableHtmlContent(markdownContent.text(child))) {
            continue
        }
        val range = markdownContent.range(child)
        if (range.first != firstHeaderPos) { break }
        return range
    }
    return null
}
