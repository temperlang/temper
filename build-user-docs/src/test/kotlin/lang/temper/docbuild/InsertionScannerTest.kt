package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RSuccess
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonBoolean
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonProperty
import lang.temper.common.json.JsonString
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals

class InsertionScannerTest {
    private val stubSnippet = Snippet(
        SnippetId(listOf("foo", "bar"), extension = ".md"),
        shortTitle = "Title",
        source = filePath("other.md"),
        sourceStartOffset = 0,
        mimeType = MimeType.markdown,
        content = TextDocContent("Snippet content"),
        isIntermediate = false,
        derivation = ExtractedBy(StubSnippetExtractor),
    )

    private val from = SourceFileInsertionLocation(filePath("my", "file.md"))

    private fun assertSoleInsertion(
        text: String,
        want: SnippetInsertion,
    ) {
        val markdownContent = MarkdownContent(text)
        val got = mutableListOf<AbstractSnippetInsertion>()
        InsertionScanner(
            loc = from,
            anchorFor = { TODO("Explicit anchor given") },
            insertions = got,
        ) { path, _, _ ->
            require(path == "foo/bar")
            RSuccess(stubSnippet)
        }.scanForInsertions(markdownContent)
        assertEquals(
            listOf(want),
            got.toList(),
        )
    }

    @Test
    fun withAttributes() {
        val insertionMaker = "$INSERTION_MARKER_CHAR foo/bar -heading anchor=\"#MyAnchor\""
        val text = """
            |Foo
            |
            |$insertionMaker
        """.trimMargin()

        val range = text.indexOf(INSERTION_MARKER_CHAR) until text.length

        assertSoleInsertion(
            text,
            SnippetInsertion(
                stubSnippet,
                location = from,
                range = range,
                replacedContent = TextDocContent(insertionMaker),
                attributes = mapOf(
                    SnippetInsertionAttributeKey.Heading to JsonBoolean.valueFalse,
                    SnippetInsertionAttributeKey.Anchor to JsonString("#MyAnchor"),
                ),
            ),
        )
    }

    @Test
    fun withJsonObjectAttributeValue() {
        val insertionMarker = "$INSERTION_MARKER_CHAR foo/bar anchor={ \"text\": \"#MyAnchor\" }"
        val text = """
            |Foo
            |
            |$insertionMarker
        """.trimMargin()

        val range = text.indexOf(INSERTION_MARKER_CHAR) until text.length

        assertSoleInsertion(
            text,
            SnippetInsertion(
                stubSnippet,
                location = from,
                range = range,
                replacedContent = TextDocContent(insertionMarker),
                attributes = mapOf(
                    SnippetInsertionAttributeKey.Anchor to JsonObject(
                        listOf(
                            JsonProperty("text", JsonString("#MyAnchor"), emptySet()),
                        ),
                    ),

                ),
            ),
        )
    }

    @Test
    fun withJsonArrayAttributeValue() {
        val insertionMarker = "$INSERTION_MARKER_CHAR foo/bar anchor=[\"#MyAnchor\"]"
        val text = """
            |Foo
            |
            |$insertionMarker
        """.trimMargin()

        val range = text.indexOf(INSERTION_MARKER_CHAR) until text.length

        assertSoleInsertion(
            text,
            SnippetInsertion(
                stubSnippet,
                location = from,
                range = range,
                replacedContent = TextDocContent(insertionMarker),
                attributes = mapOf(
                    SnippetInsertionAttributeKey.Anchor to JsonArray(
                        listOf(
                            JsonString("#MyAnchor"),
                        ),
                    ),
                ),
            ),
        )
    }
}
