package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.removeTrailingSpaceFromLinesIn
import lang.temper.common.splitAfter
import lang.temper.common.subListToEnd
import lang.temper.log.FilePath

/**
 * Extracts sections in `/** */` comments whose comments are markdown looking for markers like
 *
 *     <!-- snippet: ... -->
 *
 * where the ... is a [SnippetId] whose content must not start or end with space.
 *
 * A snippet may be given a default short title by including it after another colon
 *
 *     <!-- snippet: ... : Short Description -->
 *
 * so that, a link to the snippet like `[snippet/my/snippet]` will get rewritten into a
 * link like `[Short Description](/path/to/snippet/content)`.
 *
 * A snippet's body runs from the start of the comment to:
 * - the start of the next snippet or
 * - a `<!-- /snippet -->` marker.
 * - the end of the comment.
 *
 * Any run of whitespace followed by a `*`, commonly used for indentation,
 * is stripped from the start of snippet lines.
 *
 * A snippet does not include any lines in a KDoc comment that start with a KDoc
 * [block tag](https://kotlinlang.org/docs/kotlin-doc.html#block-tags)
 */
internal fun extractSnippetsFromDelimitedComment(
    commentContent: String,
    /** The file whose content contains [commentContent] */
    from: FilePath,
    /** A character offset into [from]. */
    contentStartOffset: Int,
    /** Receives snippets found. */
    onto: MutableCollection<Snippet>,
    extractor: SnippetExtractor,
) {
    forEachMatchedSnippet(
        commentContent,
        from = from,
    ) { _, id, explicitShortTitle, matchStartOffset, matchEndOffset ->
        val snippetContent = commentContentToSnippetContent(
            commentContent.substring(matchStartOffset, matchEndOffset),
        )

        var shortTitle = explicitShortTitle
        if (shortTitle == null) {
            // If there's a header at the start of the content that is higher priority than any
            // other header, derive the title from that.
            shortTitle = shortTitleFromMarkdownHeader(snippetContent.text)
        }

        onto.add(
            Snippet(
                id = id,
                shortTitle = shortTitle,
                source = from,
                mimeType = MimeType.markdown,
                sourceStartOffset = contentStartOffset + matchStartOffset,
                content = snippetContent,
                derivation = ExtractedBy(extractor),
                isIntermediate = false,
            ),
        )
    }
}

private fun forEachMatchedSnippet(
    commentContent: String,
    from: FilePath,
    f: (MatchResult, SnippetId, String?, Int, Int) -> Unit,
) {
    val matches = snippetMarker.findAll(commentContent).toList()
    for (matchIndex in matches.indices) {
        val match = matches[matchIndex]
        val idStr = match.groups[1]?.value
            ?: continue // <!-- /snippet --> end marker
        if (idStr == "..." && from.segments.any { it.fullName == "build-user-docs" }) {
            // This is from example code in a comment showing how to mark a snippet in comments.
            continue
        }

        val idParts = idStr.split("/").map { decodePercentEncodedBytes(it) }
        require(idParts.none { it.isEmpty() }) { "Bad snippet id in $from: $idStr" }
        val id = SnippetId(idParts, extension = ".md")

        val shortTitle = match.groups[2]?.value?.trim()
        val matchStartOffset = match.range.first
        var matchEndOffset = matches.getOrNull(matchIndex + 1)?.range?.first
            ?: commentContent.offsetInCommentTokenBeforeEnd

        val kdocEnd = kdocBlockTagsLine.find(commentContent.substring(matchStartOffset, matchEndOffset))
        if (kdocEnd != null) {
            matchEndOffset = matchStartOffset + kdocEnd.range.first
        }

        // Back up so that we do entire lines.
        // If we end at the start of another snippet as in
        //     /**
        //      * <!-- snippet: current -->
        //      * foo
        //      * <!-- snippet: next -->
        //      * ...
        //      */
        // It's inconvenient if callers have to treat the
        // " * " before the next snippet's marker as special.
        matchEndOffset = run backupToStartOfLine@{
            var beforeAsterisksAndSpaces = matchEndOffset
            while (beforeAsterisksAndSpaces != 0) {
                val c = commentContent[beforeAsterisksAndSpaces - 1]
                if (c == '*' || c == '\t' || c == ' ') {
                    beforeAsterisksAndSpaces -= 1
                } else {
                    break
                }
            }
            when (commentContent.getOrNull(beforeAsterisksAndSpaces - 1)) {
                null, '\n', '\r' -> beforeAsterisksAndSpaces
                else -> matchEndOffset
            }
        }

        f(match, id, shortTitle, matchStartOffset, matchEndOffset)
    }
}

/**
 * Takes a chunk of comment content and filters out non-snippet content.
 */
internal fun commentContentToSnippetContent(commentContent: String): TextDocContent {
    val lines = commentContent.split(crLfOrLfPattern)
    val cleanedLines = lines
        .mapIndexed { i, line ->
            val delimiterMatcher = if (i == lines.lastIndex) {
                // For the last line, strip `*/`, `**/`, or similar from the end
                commentLineAffix
            } else {
                commentLinePrefix
            }
            line.replace(delimiterMatcher, "")
        }
        .filter { !kdocBlockTags.matchesAt(it, 0) }
    return TextDocContent(cleanedLines.joinToString("\n").trim())
}

/**
 * Replace the content of the snippet with the given ID
 */
internal fun reincorporateSnippetsIntoDelimitedContent(
    idToEdit: SnippetId,
    buffer: StringBuilder,
    commentTokens: List<Pair<String, Int>>,
    newContent: String,
    sourcePath: FilePath,
    problemTracker: ProblemTracker,
): Boolean {
    var nReplaced = 0
    for ((commentText, commentOffset) in commentTokens) {
        forEachMatchedSnippet(
            commentText,
            sourcePath,
        ) { _, id, _, startOffsetInComment, endOffsetInComment ->
            val startOffset = commentOffset + startOffsetInComment
            val endOffset = commentOffset + endOffsetInComment
            if (id == idToEdit) {
                val beforeLines = buffer.substring(startOffset, endOffset)
                    .splitAfter(crLfOrLfPattern)
                // Look at the second and subsequent lines to derive a prefix.
                // The first line is more likely to differ.
                // For example, it may start with `/**` instead of `*/`.
                val prefixes = beforeLines.subListToEnd(1).mapNotNull {
                    commentLinePrefix.find(it)
                }
                // Sort by length so that we get `  * ` instead of `  *`.
                // The latter might be a prefix for blank lines.
                val prefix = prefixes
                    .maxByOrNull { it.range.last - it.range.first }
                    ?.value
                    ?: ""
                val newContentFullLines = run {
                    val lastNewContentChar = newContent.lastOrNull()
                    if (lastNewContentChar == '\n' || lastNewContentChar == '\r') {
                        newContent
                    } else {
                        "$newContent\n"
                    }
                }
                val newContentLines = newContentFullLines.splitAfter(crLfOrLfPattern)
                val prefixedNewContent = newContentLines
                    .mapIndexed { index, line ->
                        if (
                            // First line already has a prefix since the match doesn't start until
                            // the first character of the snippet marker comment.
                            index == 0 ||
                            // Filtering out blank lines means we don't over-indent the line following the
                            // replacement
                            line.isEmpty() && index == newContentLines.lastIndex
                        ) {
                            line
                        } else {
                            "$prefix$line"
                        }
                    }
                    .joinToString("")
                buffer.replace(
                    startOffset,
                    endOffset,
                    prefixedNewContent,
                )
                // Now scan and remove any trailing space in the comment that
                // was introduced because we used a prefix with a trailing space
                // to before a blank line.
                // This is easier to do here than above because the line following the
                // snippet has white-space.
                var scanEnd = startOffset + prefixedNewContent.length
                while (scanEnd < buffer.length) {
                    val c = buffer[scanEnd]
                    if (c == '\r' || c == '\n') { break }
                    scanEnd += 1
                }
                removeTrailingSpaceFromLinesIn(buffer, startOffset until scanEnd)

                nReplaced += 1
            }
        }
    }
    when (nReplaced) {
        0 -> problemTracker.error(
            "$sourcePath: Could not find snippet ${
                idToEdit.shortCanonString(false)
            } to replace with edited content",
        )
        1 -> Unit // cool, cool
        else -> problemTracker.error(
            "$sourcePath: Possible ambiguity.  Found $nReplaced occurrences of snippet ${
                idToEdit.shortCanonString(false)
            } to replace with edited content",
        )
    }
    return nReplaced != 0
}

// Zero or more space characters that do not break lines.
private const val S = """[\t ]*"""

// We can allow dashes that are not followed by `->`
private const val SAFE_DASH = """-(?!-+>)"""

// Char that does not break line nor end `-->` and is not a colon
private const val C = """(?:[^\n\r:\-]|$SAFE_DASH)"""

// Like C, but excluding spaces
private const val NSC = """(?:[^\t\n\r :\-]|$SAFE_DASH)"""

// Snippet IDs cannot start nor end with a colon.
// They are in group 1.
private const val SNIPPET_ID = """($NSC(?:$C*$NSC)?)"""

// Suggested link text is in group 2.
private const val SUGGESTED_LINK_TEXT = """($NSC(?:$C*$NSC)?)"""

internal val snippetMarker = Regex(
    """<!--$S(?:snippet:$S$SNIPPET_ID(?:$S[:]$S$SUGGESTED_LINK_TEXT)?|/snippet)$S--+>""",
)

internal val builtinHeading = Regex("#[^\n]+\n")

/** Derived from https://kotlinlang.org/docs/kotlin-doc.html#block-tags */
private const val KDOC_BLOCK_TAGS_PATTERN =
    """@(?:param|return|constructor|receiver|property|throws|exception|sample|see|${
        ""
    }author|since|suppress)\b"""

private val kdocBlockTags = Regex("""[ \t]*$KDOC_BLOCK_TAGS_PATTERN""")
private val kdocBlockTagsLine = Regex(
    """^[ \t*]*$KDOC_BLOCK_TAGS_PATTERN""",
    setOf(RegexOption.MULTILINE),
)

/** Ignorable characters at the start and end of `/**...*/` comment lines. */
private val commentLineAffix = Regex(
    """^[ \t]*[/]?[*]+[/ ]?|[*]+[/]$""",
    setOf(RegexOption.MULTILINE),
)

/**
 * Ignorable characters at the start of a single input line.
 * Like [commentLineAffix] but does not deal with trailing characters and is
 * meant to match a single line at a time, so has different Regex flags.
 */
private val commentLinePrefix = Regex(
    """^[ \t]*[/]?[*]*[ ]?""",
)

/** For a comment text, the position before any end marker. */
private val String.offsetInCommentTokenBeforeEnd: Int
    get() {
        var end = length
        if (end > 0 && this[end - 1] == '/') { end -= 1 }
        while (end > 0) {
            val c = this[end - 1]
            if (c == '*') {
                end -= 1
            } else {
                break
            }
        }
        while (end > 0) {
            val c = this[end - 1]
            if (c == ' ' || c == '\t') {
                end -= 1
            } else {
                break
            }
        }
        return end
    }
