package lang.temper.docbuild

import lang.temper.common.commonPrefixLength
import lang.temper.common.compatRemoveLast
import lang.temper.common.splitAfter
import lang.temper.common.splitAfterTo
import lang.temper.common.toStringViaBuilder
import java.lang.Integer.max

/**
 * Edits [reversed] in place to account for changes from [original].
 *
 * This does a number of changes:
 *
 * - remove any end marker comment
 * - adds back any header that was elided due to `strip` snippet insertion metadata
 * - removes indentation
 * - reworks URLs that were rewritten to match original
 * - adjusts leading and trailing blank lines
 * - replaces nested snippets with snippet insertions
 */
internal fun AbstractUserDocsContent.reconcile(
    original: MdChunk,
    reversed: Reversed.SourcedMarkdown,
    contentBefore: MarkdownContent,
    /** The set of `[...]: url` definitions that would mask external, target-less links. */
    locallyDefined: Set<String>,
    /** Receives error messages. */
    problemTracker: ProblemTracker,
    insertionTextFor: (SnippetId, SnippetInsertionLocation) -> MarkdownContent,
): Boolean {
    // Do some analysis to figure out which transforms we need to undo.
    var headerAdjustmentContextLevel: Int? = null
    var headingStripped = false
    var originalSourced: MdChunk = original
    while (true) {
        originalSourced = when (originalSourced) {
            is Nested.Concatenation -> {
                if (originalSourced.chunks.size == 1) {
                    originalSourced.chunks[0]
                } else {
                    break
                }
            }
            is Nested.HeadingAdjustment -> {
                check(headerAdjustmentContextLevel == null)
                headerAdjustmentContextLevel = originalSourced.contextLevel
                originalSourced.content
            }
            is Nested.IndentedRegion -> {
                // Whether we need to strip indentation depends on whether
                // there is indentation in the Markdown file *after* it was
                // edited, not before.
                // That's indicated by the presence of a Reverse.IndentedRegion
                originalSourced.indented
            }
            is Nested.Invalid,
            is Nested.Link,
            is Nested.Literal,
            is Nested.MarkerComment,
            is Nested.SnippetAnchor,
            is Nested.SourcedMarkdown,
            -> break
            is Nested.StripHeading -> {
                check(!headingStripped)
                headingStripped = true
                originalSourced.content
            }
        }
    }

    val nestedSourcedMarkdown = originalSourced as? Nested.SourcedMarkdown
        ?: run {
            problemTracker.error("Cannot determine source to reconcile ${reversed.relFilePath} with")
            return@reconcile false
        }
    val snippet = nestedSourcedMarkdown.inlinedSnippet
    val relFilePath = nestedSourcedMarkdown.relFilePath

    // Walk through reversed content and gather information too
    val reversedMeta = reversedMetaOf(reversed)
    val concatenation = reversedMeta.concatenation
    val indentation = reversedMeta.indentation
    val chunks = concatenation.chunks

    // Remove indentation from Literal chunks so that, below, when we normalize blank
    // lines, blank lines are already blank.
    if (indentation.isNotEmpty()) {
        var atStartOfLine = true
        fun stripIndentation(r: Reversed) {
            when (r) {
                is Reversed.AdjustTrailingBlankLines -> stripIndentation(r.content)
                is Reversed.Concatenation -> {
                    for (i in r.chunks.indices) {
                        when (val c = r.chunks[i]) {
                            is Nested.Literal -> {
                                val markdownText = c.markdownText
                                val lines = mutableListOf<String>()
                                markdownText.splitAfterTo(crLfOrLfPattern, lines)
                                for (lineIndex in lines.indices) {
                                    val line = lines[lineIndex]
                                    if (lineIndex != 0 || atStartOfLine) {
                                        // If indentation is "> " and the line is ">\n" because of
                                        // space elision, at least remove the ">".
                                        val nToStrip = commonPrefixLength(indentation, line)
                                        lines[lineIndex] = line.substring(nToStrip)
                                    }
                                }
                                r.chunks[i] = Nested.Literal(lines.joinToString(""))
                                atStartOfLine = when (markdownText.lastOrNull()) {
                                    null -> atStartOfLine
                                    '\n', '\r' -> true
                                    else -> false
                                }
                            }
                            is Nested.MarkerComment -> Unit
                            is Reversed.SourcedMarkdown -> Unit
                            is Reversed -> stripIndentation(c)
                        }
                    }
                }
                is Reversed.IndentedRegion -> error("IndentedRegions shouldn't nest")
                is Reversed.SnippetInsertion -> error("SnippetInsertions shouldn't have happened yet")
                is Reversed.LinkedMarkdown -> stripIndentation(r.linked)
                is Reversed.ReadjustHeadingLevels -> stripIndentation(r.content)
            }
        }
        stripIndentation(concatenation)
    }

    if (snippet != null) {
        // Normalize space around start marker comment
        val firstChunk = chunks.firstOrNull()

        if (firstChunk is Nested.MarkerComment && !firstChunk.isEndMarker) {
            val blankLineCountAfter = (snippet.content as? TextDocContent)?.let { content ->
                val match = snippetMarker.matchAt(content.text, 0)
                if (match != null) {
                    max(
                        0,
                        countBlankLinesAtStart(
                            content.text.substring(match.range.last + 1),
                        ).count - 1,
                    )
                } else {
                    null
                }
            }
            if (blankLineCountAfter != null) {
                firstChunk.blankLineCountAfter = blankLineCountAfter
            }
        }

        // Remove end marker comment

        val lastChunk = chunks.lastOrNull()
        if (lastChunk is Nested.MarkerComment && lastChunk.isEndMarker) {
            chunks.compatRemoveLast()
        }
    }

    val headingToReinsert = if (headingStripped) {
        // Ok if we get null here because someone writes
        // -heading on an insertion marker for a snippet
        // that has no over-arching header.
        findOverarchingHeading(contentBefore)
    } else {
        null
    }

    if (headingToReinsert != null) {
        // Re-insert before first text literal
        val insertionIndex = max(0, chunks.indexOfFirst { it is Nested.Literal })

        // We need to preserve the count of blank lines between the header and the next block
        if (insertionIndex >= 0) {
            val literal = chunks[insertionIndex] as Nested.Literal
            val markdownText = literal.markdownText
            val count = countBlankLinesAtStart(literal.markdownText)
            // Strip the leading newlines.
            chunks[insertionIndex] = Nested.Literal(markdownText.substring(count.offset))
        }

        val blankLinesFollowingInNested = countBlankLinesAtStart(
            contentBefore.fileContent.substring(headingToReinsert.last + 1),
        ).count
        val headingMarkdownText = toStringViaBuilder { sb ->
            sb.append(contentBefore.fileContent.substring(headingToReinsert))
            repeat(max(1, blankLinesFollowingInNested)) {
                sb.append('\n')
            }
        }
        chunks.add(
            insertionIndex,
            Nested.Literal(headingMarkdownText),
        )
    }

    // Rewrite URLs.
    // First look at the original, so that we can preserve any non-canonical conversions.
    val targetTextToLink = mutableMapOf<String, String>()
    fun findOriginalLinks(n: Nested) {
        if (n is Nested.Link && n.originalCombinedText != null) {
            targetTextToLink[n.target] = n.originalCombinedText
        }
        n.children.forEach { findOriginalLinks(it) }
    }
    findOriginalLinks(nestedSourcedMarkdown.content)

    // Then walk the tree and rewrite links
    fun rewriteLinks(r: Reversed) {
        if (r is Reversed.Concatenation) {
            r.chunks.forEachIndexed { index, chunk ->
                if (chunk is Reversed) {
                    rewriteLinks(chunk)
                }
                if (
                    chunk is Reversed.LinkedMarkdown &&
                    chunk.target != null &&
                    chunk.kind == Link.Kind.Parenthetical
                ) {
                    val linkText = chunk.toMarkdown().fileContent
                    val linkedText = chunk.linked.toMarkdown().fileContent
                    val unbracketedLinkText = linkedText.unbracketed('[', ']')!!

                    // See if we can reconcile back to a snippet/ link.
                    val chunkTarget: String = chunk.target
                    var replacement: RevMdChunk? = null
                    val pathAndAnchor = filePathAndAnchorFromUrl(chunkTarget)
                    if (pathAndAnchor != null) {
                        val (pathSegments, anchor) = pathAndAnchor
                        if (anchor != null) {
                            val destinationPath = if (pathSegments.isNotEmpty()) {
                                relFilePath.resolvePseudo(pathSegments, isDir = false)
                            } else {
                                relFilePath
                            }
                            if (destinationPath != null) {
                                val match =
                                    this.getInsertionForPathAndAnchor(destinationPath, "#$anchor")
                                if (match != null) {
                                    val shortTitle = match.snippet.shortTitle
                                    val shortId = match.snippet.id.shortCanonString(false)
                                    replacement = if (shortTitle != null && "[$shortTitle]" == linkedText) {
                                        Reversed.LinkedMarkdown(
                                            linked = Reversed.Concatenation(
                                                listOf(Nested.Literal("[snippet/$shortId]")),
                                            ),
                                            kind = Link.Kind.SquareBracketed,
                                            target = null,
                                        )
                                    } else {
                                        val snippetLinkTarget = "snippet/$shortId"
                                        Reversed.LinkedMarkdown(
                                            linked = chunk.linked,
                                            kind = Link.Kind.SquareBracketed,
                                            target = if (unbracketedLinkText == snippetLinkTarget) {
                                                null
                                            } else {
                                                snippetLinkTarget
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (replacement == null) {
                        // If the target matches one from the original, see if we can re-use that.
                        val possibleSimpleText = targetTextToLink[chunk.kind.bracket(chunk.target)]
                        if (possibleSimpleText != null) {
                            val rev = this.rewriteLinkTarget(
                                relFilePath = relFilePath,
                                link = Link(
                                    whole = linkText,
                                    linked = unbracketedLinkText,
                                    kind = Link.Kind.SquareBracketed,
                                    target = possibleSimpleText,
                                    locallyDefined = "[$possibleSimpleText]" in locallyDefined,
                                ),
                            )
                            if (rev is Nested.Link && rev.target.unbracketed('(', ')') == chunk.target) {
                                replacement = Reversed.LinkedMarkdown(
                                    linked = Reversed.Concatenation(
                                        listOf(
                                            Nested.Literal(
                                                Link.Kind.SquareBracketed.bracket(possibleSimpleText),
                                            ),
                                        ),
                                    ),
                                    target = null,
                                    kind = Link.Kind.SquareBracketed,
                                )
                            }
                        }
                    }

                    if (replacement == null) {
                        // If we have a URL like [foo](bar) and [foo] would have rewritten to [foo](bar) then
                        // reverse it to just [foo].
                        val rev = this.rewriteLinkTarget(
                            relFilePath = relFilePath,
                            link = Link(
                                whole = linkText,
                                linked = unbracketedLinkText,
                                kind = Link.Kind.SquareBracketed,
                                target = unbracketedLinkText,
                                locallyDefined = unbracketedLinkText in locallyDefined,
                            ),
                        )
                        if (rev is Nested.Link && rev.target.unbracketed('(', ')') == chunk.target) {
                            replacement = chunk.copy(
                                target = null,
                                kind = Link.Kind.SquareBracketed,
                            )
                        }
                    }

                    if (replacement != null) {
                        r.chunks[index] = replacement
                    }
                }
            }
        } else {
            for (c in r.children) {
                rewriteLinks(c)
            }
        }
    }
    rewriteLinks(reversed.content)

    // Replace nested snippets with insertions of their content.
    fun reinsertNested(chunks: MutableList<RevMdChunk>) {
        chunks.forEachIndexed { index, chunk ->
            when (chunk) {
                is Reversed.SourcedMarkdown -> {
                    val id = chunk.inlinedSnippetId
                    if (id != null) {
                        var replacement = insertionTextFor(id, nestedSourcedMarkdown.loc)
                        val nestedMeta = reversedMetaOf(chunk)
                        val nestedIndentation = nestedMeta.indentation
                        if (nestedIndentation.isNotEmpty()) {
                            // Reapply the indentation to the replacement.
                            val replacementLines = replacement.fileContent
                                .splitAfter(crLfOrLfPattern)
                                .mapIndexed { lineIndex, line ->
                                    if (lineIndex == 0 || line.isEmpty()) {
                                        line
                                    } else {
                                        "$nestedIndentation$line"
                                    }
                                }
                            replacement = MarkdownContent(replacementLines.joinToString(""))
                        }
                        chunks[index] = Reversed.SnippetInsertion(id, replacement.fileContent)
                    }
                }
                is Nested.Literal,
                is Nested.MarkerComment,
                -> Unit
                is Reversed.Concatenation -> reinsertNested(chunk.chunks)
                is Reversed.AdjustTrailingBlankLines -> reinsertNested(chunk.content.chunks)
                is Reversed.IndentedRegion -> reinsertNested(chunk.indented.chunks)
                is Reversed.LinkedMarkdown -> reinsertNested(chunk.linked.chunks)
                is Reversed.ReadjustHeadingLevels -> reinsertNested(chunk.content.chunks)
                is Reversed.SnippetInsertion -> Unit
            }
        }
    }
    reinsertNested(chunks)

    // Adjust header levels
    if (headerAdjustmentContextLevel != null || headingToReinsert != null) {
        // Get the greatest header level from original.
        // Get header level from changed.
        // Adjust back to match original where possible taking into
        // account that we may need to re-add the stripped header below.
        val originalLevels = findPositionsAndHeadingLevels(contentBefore)
        val originalMinLevel = originalLevels.map { it.second }.toSortedSet().firstOrNull() ?: 1

        val chunksToAdjust = chunks.toList()
        chunks.clear()
        chunks.add(
            Reversed.ReadjustHeadingLevels(
                isFirstHeadingExempt = headingToReinsert != null,
                minLevel = originalMinLevel,
                content = Reversed.Concatenation(chunksToAdjust),
            ),
        )
    }

    val numBlankLinesAtEndOfOriginal = countBlankLinesAtEnd(contentBefore.fileContent).count

    // Make sure we adjust trailing blank lines.
    // This is important to match since it's used to offset snippets that occur together
    // from one another in comments in Kotlin & Temper source files.
    val tailAdjustment = Reversed.AdjustTrailingBlankLines(
        wanted = numBlankLinesAtEndOfOriginal,
        content = Reversed.Concatenation(chunks.toList()),
    )
    chunks.clear()
    chunks.add(tailAdjustment)

    return true
}

private data class ReversedMeta(
    val concatenation: Reversed.Concatenation,
    val indentation: String,
)

private fun reversedMetaOf(reversed: Reversed.SourcedMarkdown): ReversedMeta {
    var concatenation = reversed.content
    var indentation = ""
    while (true) {
        if (concatenation.chunks.size != 1) { break }
        concatenation = when (val soleChunk = concatenation.chunks[0]) {
            // These are content we need to process
            is Nested.Literal,
            is Nested.MarkerComment,
            is Reversed.LinkedMarkdown,
            // Do not recurse into other snippets
            is Reversed.SnippetInsertion,
            is Reversed.SourcedMarkdown,
            // These are post-processing decisions we add as part of reconciliation
            is Reversed.AdjustTrailingBlankLines,
            is Reversed.ReadjustHeadingLevels,
            ->
                break
            is Reversed.Concatenation -> soleChunk
            is Reversed.IndentedRegion -> {
                check(indentation == "")
                indentation = soleChunk.indentation
                soleChunk.indented
            }
        }
    }
    return ReversedMeta(
        concatenation = concatenation,
        indentation = indentation,
    )
}
