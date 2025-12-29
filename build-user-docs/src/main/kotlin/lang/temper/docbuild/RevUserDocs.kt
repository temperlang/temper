package lang.temper.docbuild

import lang.temper.common.LeftOrRight
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.compatRemoveLast
import lang.temper.common.mutSubListToEnd
import lang.temper.log.FilePath
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline

/**
 * Derive a structure similar to [Nested] but in the opposite
 * direction: from generated (and maybe subsequently edited) Markdown
 * files.
 */
internal fun AbstractUserDocsContent.reverseUserDocs(
    relFilePath: FilePath,
    content: MarkdownContent,
    problemTracker: ProblemTracker,
): Reversed.SourcedMarkdown {
    val fileContent = content.fileContent

    // First collect chunks
    var pos = 0 // Position in fileContent past last char on chunks
    val chunks = mutableListOf<RevMdChunk>()

    // Keep track of marker comment snippet IDs so that we can post-process by splitting out
    data class MarkerInfoItem(val leftOrRight: LeftOrRight, val snippetId: SnippetId, val nodeOffset: Int)
    val markerInfo = mutableMapOf<Nested.MarkerComment, MarkerInfoItem>()

    // Add textual content from pos to newPos onto chunks.
    fun emitTo(newPos: Int) {
        if (pos < newPos) {
            chunks.add(Nested.Literal(fileContent.substring(pos, newPos)))
        }
        pos = newPos
    }

    // Once we've got a run of chunks constituting a scope, pair start and end markers,
    // and slice them out into nested sourced chunks.
    fun postProcess(chunks: MutableList<RevMdChunk>) {
        // Keep a stack as we walk.
        // We walk left to right, and since deeper nesting pairs end earlier, we can slice
        // ranges of chunks out to create nested SourcedMarkdown instances.
        // The Int is the index in chunks at which the snippet with SnippetId started.
        data class StackItem(val snippetId: SnippetId, val startIndex: Int, val nodeOffset: Int)
        val stack = mutableListOf<StackItem>()
        fun truncateStackTo(newSize: Int) {
            // Some open markers have no corresponding close marker.
            for (unclosedIndex in stack.lastIndex downTo newSize) {
                val (unclosed) = stack[unclosedIndex]
                problemTracker.error(
                    "$relFilePath: Unclosed snippet ${
                        unclosed.shortCanonString(false)
                    }",
                )
            }
            stack.mutSubListToEnd(newSize).clear()
        }

        var i = 0
        while (i < chunks.size) {
            val chunk = chunks[i]
            i += 1

            if (chunk is Nested.MarkerComment) {
                val (leftOrRight, snippetId, nodeOffset) = markerInfo.getValue(chunk)
                when (leftOrRight) {
                    LeftOrRight.Left -> stack.add(StackItem(snippetId, i - 1, nodeOffset))
                    LeftOrRight.Right -> {
                        val stackIndex = stack.indexOfLast { it.snippetId == snippetId }
                        if (stackIndex >= 0) {
                            truncateStackTo(stackIndex + 1)
                            val (_, startIndex, startNodeOffset) = stack.compatRemoveLast()
                            val chunksInSnippet = chunks.subList(startIndex, i)

                            val replacement = Reversed.SourcedMarkdown(
                                relFilePath = snippetId.filePath,
                                inlinedSnippetId = snippetId,
                                content = Reversed.Concatenation(chunksInSnippet),
                            )

                            // If the markers are indented, then we need to know enough to
                            // subtract that indentation from the body.
                            val startIndentation = markdownIndentationLevel(content, startNodeOffset)
                            val endIndentation = markdownIndentationLevel(content, nodeOffset)
                            if (startIndentation != "" || endIndentation != "") {
                                if (startIndentation == endIndentation) {
                                    val indentedRegion = Reversed.IndentedRegion(
                                        startIndentation,
                                        Reversed.Concatenation(replacement.content.chunks),
                                    )
                                    replacement.content.chunks.clear()
                                    replacement.content.chunks.add(indentedRegion)
                                } else {
                                    problemTracker.error(
                                        "${
                                            relFilePath
                                        }: Mismatched indentation for markers of ${
                                            snippetId.shortCanonString(false)
                                        }",
                                    )
                                }
                            }

                            chunksInSnippet.clear()
                            chunksInSnippet.add(replacement)
                            i = startIndex + 1
                        } else {
                            problemTracker.error(
                                "$relFilePath: Unmatched close snippet marker for ${
                                    snippetId.shortCanonString(false)
                                }",
                            )
                        }
                    }
                }
            }
        }
        truncateStackTo(0)
    }

    // Process AST nodes depth-first.
    // When we notice an AST node that constitutes a boundary between
    // two Reversed chunks, we add to chunks and advance pos via emitTo.
    fun emitTo(range: IntRange) {
        emitTo(range.first)
        pos = range.last + 1
    }
    content.root.accept(
        object : LinkUnifyingVisitor(content) {
            override fun visit(htmlBlock: HtmlBlock) {
                val range = content.range(htmlBlock)
                val token = content.fileContent.substring(range).trim()
                val match = markerPattern.matchEntire(token) ?: run {
                    if (Nested.SnippetAnchor.pattern.matches(token)) {
                        // We see this as a block only if it's alone in a paragraph.
                        emitTo(range)
                        problemTracker.error("$relFilePath: Missing close tag for `$token`")
                    }
                    return@visit
                }
                emitTo(range)
                val isOpen = match.groupValues[1].isEmpty()
                val snippetIdText = match.groupValues[2]
                val snippetId = resolveSnippetId(
                    snippetIdText,
                    relFilePath,
                    token,
                )
                val markerComment = Nested.MarkerComment(match.value)
                when (snippetId) {
                    is RSuccess ->
                        markerInfo[markerComment] = MarkerInfoItem(
                            (if (isOpen) { LeftOrRight.Left } else { LeftOrRight.Right }),
                            snippetId.result,
                            range.first,
                        )
                    is RFailure ->
                        problemTracker.error(
                            snippetId.failure.message
                                ?: "$relFilePath: Bad snippet ID: $snippetIdText",
                        )
                }
                chunks.add(markerComment)
            }

            override fun visit(htmlInline: HtmlInline) {
                val token = content.text(htmlInline).trim()
                Nested.SnippetAnchor.pattern.matches(token) || return
                val range = content.range(htmlInline)
                val cutBefore = range.first
                var cutAfter = range.last + 1

                // Look for a matching close tag.
                val nextSibling = htmlInline.next
                if (nextSibling?.let { content.text(it) } == "</a>") {
                    cutAfter = content.endIndex(nextSibling)
                } else {
                    problemTracker.error("$relFilePath: Missing close tag for `$token`")
                }
                emitTo(cutBefore until cutAfter)
            }

            override fun visit(link: Link<IntRange>) {
                emitTo(link.whole)
                // Conventions here are slightly different, so adapt.
                val text = link.linked?.let { content.fileContent.substring(it) } ?: link.target
                val destinationText = link.linked?.let { link.target }
                chunks.add(
                    Reversed.LinkedMarkdown(
                        // TODO Change wherever expects these brackets to avoid needing them.
                        linked = Reversed.Concatenation(listOf(Nested.Literal("[$text]"))),
                        kind = link.kind,
                        target = destinationText,
                    ),
                )
            }
        },
    )
    emitTo(fileContent.length)

    // Join adjacent literals.  Sometimes we split around things like anchor markers.
    // Having to look through multiple literals makes it unnecessarily difficult to do
    // things like counting lines.
    normalizeLiterals(chunks)
    postProcess(chunks)

    return Reversed.SourcedMarkdown(
        relFilePath = relFilePath,
        inlinedSnippetId = null,
        content = Reversed.Concatenation(chunks),
    )
}

internal fun String.unbracketed(left: Char, right: Char): String? =
    if (length >= 2 && first() == left && last() == right) {
        substring(1, lastIndex)
    } else {
        null
    }

private fun normalizeLiterals(ls: MutableList<RevMdChunk>) {
    var i = ls.lastIndex
    while (i > 0) {
        val el = ls[i]
        i -= 1

        if (el is Nested.Literal) {
            val prior = ls[i]
            if (prior is Nested.Literal) {
                ls.removeAt(i)
                ls[i] = Nested.Literal("${prior.markdownText}${el.markdownText}")
            }
        }
    }
}
