package lang.temper.docbuild

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Heading
import java.lang.Integer.min

/**
 * Makes sure that the lowest-level (most important) heading matches [contextLevel]+1.
 *
 * So if the most important heading level is `## Foo`, and [contextLevel] is 3,
 * then rewrites that to `#### Foo` and rewrites any `###`-level headings to `#####` level headings.
 *
 * Does not exceed 6 levels of headings.
 */
internal fun adjustHeadingLevels(markdownContent: MarkdownContent, contextLevel: Int): String {
    val content = markdownContent.fileContent

    if (contextLevel <= 0) {
        // No adjustments to be made
        return content
    }

    val headings = mutableListOf<Heading>()

    val bogusHeadingValue = MAX_ALLOWED_HEADING_LEVEL + 1
    var mostImportantHeading = bogusHeadingValue
    markdownContent.root.accept(
        object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                headings.add(heading)
                if (heading.level < mostImportantHeading) {
                    mostImportantHeading = heading.level
                }
            }
        },
    )
    if (mostImportantHeading == bogusHeadingValue) {
        // Contains no headings
        return content
    }

    val adjusted = StringBuilder(content)
    headings.reverse() // Rewrite headings in reverse order so that later offsets are stable
    for (heading in headings) {
        val contentText = markdownContent.subText(heading)
        val adjustedHeaderLevel = min(
            MAX_ALLOWED_HEADING_LEVEL,
            contextLevel + 1 + (heading.level - mostImportantHeading),
        )
        val linePrefix = "#".repeat(adjustedHeaderLevel)
        val replacementText = contentText.split(crLfOrLfPattern)
            .joinToString("\n") {
                val padding =
                    if (it.getOrNull(0)?.isWhitespace() == false) {
                        " "
                    } else {
                        ""
                    }
                "$linePrefix$padding$it"
            }
        val range = markdownContent.range(heading)
        adjusted.replace(range.first, range.last + 1, replacementText)
    }

    return "$adjusted"
}

const val MAX_ALLOWED_HEADING_LEVEL = 6
