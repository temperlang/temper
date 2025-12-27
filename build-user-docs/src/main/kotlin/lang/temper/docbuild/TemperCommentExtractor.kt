package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.lexer.languageConfigForExtension
import lang.temper.lexer.sourceOffsetOf
import lang.temper.log.FilePath
import lang.temper.log.last

/**
 * A comment extractor that applies to Temper source files, and which uses the conventions described
 * in [extractSnippetsFromDelimitedComment].
 */
internal object TemperCommentExtractor : SnippetExtractor() {
    override fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        if (content !is TemperContent) { return }

        forEachTemperDoubleStarCommentToken(content) { token, tokenStartOffset ->
            extractSnippetsFromDelimitedComment(
                commentContent = token.tokenText,
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
        ) { temperSource ->
            val sourcePath = snippet.source
            val temperContent = TemperContent(
                source = sourcePath,
                fileContent = "$temperSource",
                config = languageConfigForExtension(sourcePath.last().extension),
            )
            val commentTokens = mutableListOf<Pair<String, Int>>()
            forEachTemperDoubleStarCommentToken(temperContent) { token, tokenStartOffset ->
                commentTokens.add(token.tokenText to tokenStartOffset)
            }
            commentTokens.toList()
        }
}

private fun forEachTemperDoubleStarCommentToken(
    content: TemperContent,
    f: (TemperToken, Int) -> Unit,
) {
    val tokens = content.lexer()
    for (token in tokens) {
        if (token.tokenType == TokenType.Comment && token.tokenText.startsWith("/**")) {
            val tokenStartOffset = tokens.sourceOffsetOf(token)
            f(token, tokenStartOffset)
        }
    }
}
