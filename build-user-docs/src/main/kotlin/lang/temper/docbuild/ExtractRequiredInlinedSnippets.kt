package lang.temper.docbuild

import org.commonmark.renderer.text.TextContentRenderer

/**
 * Finds processed snippet content used in the REPL help function to display portions of
 * our documentation on demand.
 *
 * Reads back through the generated Markdown files after snippet inclusion markers have
 * been replaced with the content.
 *
 * It uses the snippet start and end markers to
 */
internal fun extractRequiredInlinedSnippets(
    markdowns: List<MarkdownContent>,
    required: Set<SnippetId>,
    onto: MutableMap<SnippetId, String>,
) {
    val requiredIgnoringExtension = required.map { it.copy(extension = "") }.toSet()
    for (markdown in markdowns) {
        val fileContent = markdown.fileContent
        val open = mutableListOf<Pair<SnippetId, MutableList<String>>>()
        for (line in fileContent.lines()) {
            val lineTrimmed = line.trim()
            val markerMatch = markerPattern.matchEntire(lineTrimmed)
            if (markerMatch != null) {
                val isClose = markerMatch.groups[1]?.value == "/"
                val snippetIdStr = markerMatch.groups[2]!!
                val snippetId = SnippetId(snippetIdStr.value.toIdParts(), "")
                if (snippetId in requiredIgnoringExtension) {
                    if (isClose) {
                        val index = open.indexOfLast { it.first == snippetId }
                        val (_, snippetLines) = open.removeAt(index)
                        onto[snippetId] = combineMarkdownLinesDroppingIncidentalSpace(snippetLines)
                    } else {
                        open.add(snippetId to mutableListOf())
                    }
                }
            } else {
                for ((_, lineList) in open) {
                    lineList.add(line)
                }
            }
        }
    }
}

private fun combineMarkdownLinesDroppingIncidentalSpace(
    lines: MutableList<String>,
): String {
    while (lines.isNotEmpty() && lines.last().isBlank()) {
        lines.removeLast()
    }
    while (lines.isNotEmpty() && lines.first().isBlank()) {
        lines.removeFirst()
    }

    // Remove adjacent blank lines
    for (i in lines.indices.reversed()) {
        if (i > 0 && lines[i].isBlank() && lines[i - 1].isBlank()) {
            lines.removeAt(i)
        }
    }

    // Remove common space prefix. A snippet might be inserted into
    // a Markdown list requiring indentation that does not cause treating
    // as an unfenced codeblock when in the context of a larger Markdown
    // file, but would when extracted.
    val commonSpacePrefix = lines.fold(Int.MAX_VALUE) { nLeadingSpace, line ->
        if (!line.isBlank()) {
            for (i in line.indices) {
                if (nLeadingSpace <= i) { break }
                if (line[i] != ' ') {
                    return@fold i
                }
            }
        }
        nLeadingSpace
    }

    for (i in lines.indices) {
        val line = lines[i]
        if (line.isBlank()) {
            lines[i] = ""
        } else {
            lines[i] = line.substring(commonSpacePrefix)
        }
    }

    return TextContentRenderer.builder()
        .build()
        .render(MarkdownContent(lines.joinToString(separator = "\n")).root)
        .trimEnd()
}
