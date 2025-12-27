package lang.temper.docbuild

import lang.temper.common.SetDelta.Companion.buildSetDelta

/**
 * Tries to re-incorporate a changed snippet into the comment token from which
 * it was extracted.
 *
 * This reverses [extractSnippetsFromDelimitedComment].
 */
internal fun backPortIntoCommentTokens(
    extractor: SnippetExtractor,
    snippet: Snippet,
    newContent: MarkdownContent,
    /** Modified in place per [SnippetExtractor.backPortSnippetChange] */
    into: StringBuilder,
    problemTracker: ProblemTracker,
    /** Applies a source language specific lexer to find comments and list their comments and offsets. */
    findCommentTokensAndOffsets: (CharSequence) -> List<Pair<String, Int>>?,
): Boolean {
    val sourcePath = snippet.source

    fun parseAndExtractSnippets(
        isBefore: Boolean,
    ): Pair<List<Pair<String, Int>>, List<Snippet>>? {
        val commentTokens = findCommentTokensAndOffsets(into)
        if (commentTokens == null) {
            problemTracker.error(
                "Could not parse tokens from $sourcePath ${
                    if (isBefore) "before" else "after"
                } applying edits",
            )
            return null
        }

        val snippets = mutableListOf<Snippet>()
        for ((token, offset) in commentTokens) {
            extractSnippetsFromDelimitedComment(
                commentContent = token,
                from = sourcePath,
                contentStartOffset = offset,
                onto = snippets,
                extractor = extractor,
            )
        }

        return commentTokens to snippets
    }

    val (commentTokens, snippetsBefore) = parseAndExtractSnippets(
        isBefore = true,
    ) ?: return false // Error dumped to problemTracker

    // Edit the matched region.
    val changed = reincorporateSnippetsIntoDelimitedContent(
        idToEdit = snippet.id,
        buffer = into,
        commentTokens = commentTokens,
        newContent = newContent.fileContent,
        sourcePath = sourcePath,
        problemTracker = problemTracker,
    )

    val (_, snippetsAfter) = parseAndExtractSnippets(
        isBefore = false,
    ) ?: return changed

    val beforeById = snippetsBefore.associateBy { it.id }
    val afterById = snippetsAfter.associateBy { it.id }

    if (beforeById.keys != afterById.keys) {
        val idSetDelta = beforeById.keys.buildSetDelta {
            clear()
            addAll(afterById.keys)
        }

        problemTracker.error(
            "$sourcePath: Applying edits to ${
                snippet.id.shortCanonString(false)
            } changed the set of available snippets: $idSetDelta",
        )
    } else {
        for ((id, snippetBefore) in beforeById) {
            val snippetAfter = afterById.getValue(id)
            if (id != snippet.id && snippetBefore.content != snippetAfter.content) {
                problemTracker.error(
                    "$sourcePath: Applying edits to ${
                        snippet.id.shortCanonString(false)
                    } also changed the content of ${
                        id.shortCanonString(false)
                    }",
                )
            }
        }
    }

    return changed
}
