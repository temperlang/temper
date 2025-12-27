package lang.temper.docbuild

import lang.temper.common.toStringViaBuilder
import lang.temper.lexer.children
import org.commonmark.node.BlockQuote
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan

/**
 * The markdown indentation level at the given offset.
 *
 * There are several markdown constructs that change the indentation level.
 *
 * ```markdown
 * > Quotations are indented
 *   Lines that are following must be at the same indentation level.
 *   Or else they'd be outside the quotation.
 *
 * Lists are also indented.
 * 1. This line continues from the list item,
 *    but following lines in the same paragraph
 *    need to be indented to clearly be part of the same item element.
 *
 * Indented code blocks too.
 *
 *     thisCodeIsIndentedByFour &&
 *     ifIWereToAddLinesOrReplaceASubRange() then
 *     iWouldNeedToIndent()
 * ```
 *
 * This function searches through the tree for an atom that contains
 * the offset and then walks parent-ward adding up indentation from
 * containing list items and
 */
internal fun markdownIndentationLevel(
    content: MarkdownContent,
    offset: Int,
): String {
    var deepestContaining = content.root
    while (true) {
        deepestContaining = deepestContaining.children().asSequence().firstOrNull {
            offset in content.range(it)
        } ?: break
    }

    var ancestor: Node? = deepestContaining
    val prefix = StringBuilder()
    while (ancestor != null) {
        val indentationForAncestor = when (ancestor) {
            is BlockQuote, is ListItem -> {
                // Find the difference in indentation between us and the content.
                val start = ancestor.sourceSpans.first()
                ancestor.firstChild?.let { child ->
                    val childStartColumn = child.sourceSpans.first().columnIndex
                    val sourceSpan =
                        SourceSpan.of(start.lineIndex, start.columnIndex, childStartColumn - start.columnIndex)
                    content.text(sourceSpan)
                } ?: "  "
            }
            is IndentedCodeBlock -> {
                // Multiple lines all get fed into a single code block instance.
                val text = content.text(ancestor)
                if (text.startsWith('\t')) {
                    // CommonMark treats tab characters as equivalent to 4 spaces,
                    // but it's nice to indent lines with the same indentation as
                    // other's around so that things don't look jagged in browsers.
                    "\t"
                } else {
                    "    "
                }
            }
            else -> null
        }

        if (indentationForAncestor != null) {
            prefix.insert(0, indentationForAncestor)
        }

        ancestor = ancestor.parent
    }
    return markdownIndentation("$prefix", prefix.length)
}

/** A string that indents to pos based on the previous content on the same line. */
private fun markdownIndentation(s: String, pos: Int): String = toStringViaBuilder { sb ->
    var lineStart = pos
    while (lineStart > 0) {
        val c = s[lineStart - 1]
        if (c == '\n' || c == '\r') { break }
        lineStart -= 1
    }
    for (i in lineStart until pos) {
        sb.append(
            when (val c = s[i]) {
                // tabs should remain as they are to preserve tab-stopping
                // `>` which indicate quoted sections should also remain as they are
                '\t', '>' -> c
                else -> ' '
            },
        )
    }
}
