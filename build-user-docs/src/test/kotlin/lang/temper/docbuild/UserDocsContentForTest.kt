package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.log.FilePath
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.test.fail

internal class UserDocsContentForTest(
    /** The Markdown content of the file that embeds the snippets. */
    private val embeddingMarkdownFileContent: String,
    /** Maps slashed snippet strings like `foo/bar` to Markdown content for those snippets. */
    private val snippetContents: Map<String, String>,
) : AbstractUserDocsContent() {
    private val snippetMap = snippetContents.mapValues { (idStr, content) ->
        val id = mdSnippetId(idStr)
        val marker = snippetMarker.find(content)
        var shortTitle = marker?.groupValues?.getOrNull(2)
        if (shortTitle.isNullOrBlank()) {
            shortTitle = shortTitleFromMarkdownHeader(content)
        }
        Snippet(
            id = id,
            shortTitle = shortTitle,
            source = testSnippetSource,
            sourceStartOffset = 123,
            mimeType = MimeType.markdown,
            content = TextDocContent(
                buildString {
                    if (marker == null) {
                        append("<!-- snippet: ").append(idStr).append(" -->\n\n")
                    }
                    append(content)
                },
            ),
            isIntermediate = false,
            derivation = ExtractedBy(StubSnippetExtractor),
        )
    }

    override val snippets: List<Snippet> = snippetMap.values.toList()

    private val insertionsByLoc = mutableMapOf<SnippetInsertionLocation, List<AbstractSnippetInsertion>>()

    init {
        scanForInsertions(SourceFileInsertionLocation(filePathForTest))
    }

    override fun findInsertions(loc: SnippetInsertionLocation): List<AbstractSnippetInsertion> =
        insertionsByLoc[loc] ?: emptyList()

    private fun scanForInsertions(loc: SnippetInsertionLocation) {
        val sourceCode = when (loc) {
            is SourceFileInsertionLocation ->
                if (loc.filePath == filePathForTest) {
                    embeddingMarkdownFileContent
                } else {
                    null
                }
            is NestedInsertion -> {
                snippetContents[loc.snippetId.shortCanonString(false)]
            }
        } ?: fail("No such location $loc")

        val insertions = mutableListOf<AbstractSnippetInsertion>()
        val anchorsUsed = mutableSetOf<String>()
        val scanner = InsertionScanner(
            loc = loc,
            anchorFor = {
                if (it.extension == MD_EXTENSION) {
                    val possibleAnchor = it.shortCanonString(false).replace('/', '-')
                    if (possibleAnchor !in anchorsUsed) {
                        anchorsUsed.add(possibleAnchor)
                        "#$possibleAnchor"
                    } else {
                        null
                    }
                } else {
                    null
                }
            },
            insertions = insertions,
        ) { slashString, referent, insertionText ->
            snippetMap[slashString]?.let { RSuccess(it) }
                ?: RFailure(
                    IllegalArgumentException("$referent: No snippet for $insertionText"),
                )
        }
        scanner.scanForInsertions(MarkdownContent(sourceCode))
        insertionsByLoc[loc] = insertions.toList()

        for (insertion in insertions) {
            if (insertion is SnippetInsertion) {
                scanForInsertions(NestedInsertion(insertion))
            }
        }
    }

    override val templateFiles: Map<FilePath, MarkdownContent>
        get() = mapOf(filePathForTest to MarkdownContent(embeddingMarkdownFileContent))

    override val snippetsAvailableAsFiles: Map<Snippet, FilePath> =
        snippetMap.values.associateWith { it.id.filePath }

    override fun resolveSnippetId(
        slashedString: String,
        from: FilePath,
        referenceText: String,
    ): RResult<SnippetId, IllegalArgumentException> =
        RSuccess(SnippetId(slashedString.split('/'), MD_EXTENSION))

    override fun insertionsWithId(id: SnippetId): List<SnippetInsertion> = buildList {
        for (insertionList in insertionsByLoc.values) {
            for (insertion in insertionList) {
                if (insertion is SnippetInsertion && insertion.snippet.id == id) {
                    add(insertion)
                }
            }
        }
    }

    override fun snippetWithId(id: SnippetId): Snippet? = snippetMap.values.firstOrNull { it.id == id }

    val editedContent = mutableMapOf<FilePath, MarkdownContent>()
    override fun editedContentFor(relFilePath: FilePath): RResult<MarkdownContent, IOException> =
        when (val content = editedContent[relFilePath]) {
            null -> RFailure(FileNotFoundException("No edited content for $relFilePath"))
            else -> RSuccess(content)
        }

    companion object {
        internal val filePathForTest = filePath("test", "test.md")
        internal val testSnippetSource = filePath("test", "snippets.txt")
    }
}

internal fun mdSnippetId(slashSeparatedString: String): SnippetId {
    return SnippetId(slashSeparatedString.split('/'), MD_EXTENSION)
}
internal fun pathToSnippet(slashSeparatedString: String): FilePath {
    var path = snippetPathPrefix
    for (s in slashSeparatedString.split('/')) {
        path = path.resolveDir(s)
    }
    return path.resolveFile("snippet.md")
}
