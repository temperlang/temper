package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.log.FilePath

/** A stub snippet extractor used for snippets created by test code. */
internal object StubSnippetExtractor : SnippetExtractor() {
    override fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        // Doesn't actually extract any snippets.
    }

    override fun backPortInsertion(
        inserted: Snippet,
        priorInsertion: TextDocContent?,
        readInlined: () -> TextDocContent,
    ): RResult<TextDocContent, IllegalStateException> =
        if (priorInsertion != null) {
            RSuccess(priorInsertion)
        } else {
            RFailure(IllegalStateException("${inserted.id} was not inlined"))
        }

    override fun backPortSnippetChange(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean {
        TODO("Not yet implemented $snippet")
    }
}
