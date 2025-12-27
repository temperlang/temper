package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.log.FilePath

/**
 * A comment extractor that applies to Kotlin source files, and which uses the conventions described
 * in [extractSnippetsFromDelimitedComment].
 */
internal object KotlinCommentExtractor : SnippetExtractor() {
    override fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        if (content !is KotlinContent) { return }

        val tokens = content.tokens

        forEachKotlinDoubleStarCommentToken(tokens) { token, tokenStartOffset ->
            val commentContent = token.text
            extractSnippetsFromDelimitedComment(
                commentContent = commentContent,
                from = from,
                contentStartOffset = tokenStartOffset,
                onto = onto,
                extractor = this,
            )
        }
    }

    override fun backPortInsertion(
        inserted: Snippet,
        priorInsertion: TextDocContent?,
        readInlined: () -> TextDocContent,
    ): RResult<TextDocContent, IllegalStateException> =
        if (priorInsertion != null) {
            RSuccess(priorInsertion)
        } else {
            RFailure(IllegalStateException("Cannot back port insertion ${inserted.id}"))
        }

    override fun backPortSnippetChange(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean =
        backPortIntoCommentTokens(
            extractor = this,
            snippet = snippet,
            newContent = newContent,
            into = into,
            problemTracker = problemTracker,
        ) { kotlinSource ->
            val kotlinContent = KotlinContent("$kotlinSource")
            val tokens = kotlinContent.tokens
            val commentTokens = mutableListOf<Pair<String, Int>>()
            forEachKotlinDoubleStarCommentToken(tokens) { token, tokenStartOffset ->
                commentTokens.add(token.text to tokenStartOffset)
            }
            commentTokens.toList()
        }
}

private fun forEachKotlinDoubleStarCommentToken(
    tokens: List<KotlinToken>,
    f: (KotlinToken, Int) -> Unit,
) {
    var tokenOffset = 0
    for (token in tokens) {
        val tokenStartOffset = tokenOffset
        tokenOffset += token.text.length
        if (!token.isCommentToken) { continue }
        val commentContent = token.text
        if (!commentContent.startsWith("/**")) { continue }
        f(token, tokenStartOffset)
    }
}
