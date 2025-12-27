package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RSuccess
import lang.temper.log.FilePathSegment
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals

class TemperCodeSnippetExtractorTest {
    @Test
    @Suppress("MagicNumber") // magic numbers used in test code
    fun extract() {
        val markdownContent = """
            |# ReplScriptExtractorTest
            |
            |A code block can embed its expected output in a comment.
            |
            |```temper
            |// Let's print Hello, World!
            |console.log("Hello, World!") //!outputs "Hello, World!"
            |```
            |
            |A code block can be a boolean expression.
            |
            |```temper
            |1 + 1 == 2
            |```
            |
            |If a code block is a predicate, that predicate ought be true.
            |
            |```temper
            |1 - 1 - 1 == 1 // Wrong because associativity
            |```
            |
            |A code block can give a JSON digest of expected output.
            |
            |> Also, markdown specific formatting elements that are lexically between fences do not
            |> affect the content.
            |>
            |> ```temper {"stateVector":2,"type":"Int32"}
            |> 1 + 1
            |> ```
            |
            |And we get a red checkmark when the module does not stage.
            |```temper FAIL
            |// Division by zero fails
            |1 / 0
            |```
            |
            |Non temper blocks are not decorated
            |
            |```js
            |alert("Whee!");
            |
            |```
            |
        """.trimMargin()

        val snippets = mutableListOf<Snippet>()
        val sourceFile = SkeletalDocsFiles.root.resolve(FilePathSegment("foo.md"), isDir = false)
        TemperCodeSnippetExtractor.extractSnippets(
            sourceFile,
            MarkdownContent(markdownContent),
            MimeType.markdown,
            snippets,
        )

        val tripleTickOffsets = run {
            val ls = mutableListOf<Int>()
            var offsetScanPosition = 0
            while (true) {
                val offset = markdownContent.indexOf("```", offsetScanPosition)
                if (offset < 0) { break }
                ls.add(offset)
                offsetScanPosition = offset + 3
            }
            ls.toList()
        }

        fun derivationFor(range: IntRange) = ExtractedAndReplacedBack(
            TemperCodeSnippetExtractor,
            replaceBackRange = range,
            extracted = TextDocContent(markdownContent.substring(range)),
        )

        assertEquals(
            listOf(
                Snippet(
                    SnippetId(
                        listOf("temper-code", "build-user-docs", "skeletal-docs", "foo.md", "0"),
                        MD_EXTENSION,
                    ),
                    shortTitle = null,
                    source = sourceFile,
                    sourceStartOffset = tripleTickOffsets[0],
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        """
                            |```temper
                            |// Let's print Hello, World!
                            |console.log("Hello, World!") //!outputs "Hello, World!"
                            |// ✅
                            |```
                        """.trimMargin(),
                    ),
                    isIntermediate = false,
                    derivation = derivationFor(
                        tripleTickOffsets[0] until (tripleTickOffsets[1] + 3),
                    ),
                ),
                Snippet(
                    SnippetId(
                        listOf("temper-code", "build-user-docs", "skeletal-docs", "foo.md", "1"),
                        MD_EXTENSION,
                    ),
                    shortTitle = null,
                    source = sourceFile,
                    sourceStartOffset = tripleTickOffsets[2],
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        """
                            |```temper
                            |1 + 1 == 2
                            |// ✅
                            |```
                        """.trimMargin(),
                    ),
                    isIntermediate = false,
                    derivation = derivationFor(
                        tripleTickOffsets[2] until (tripleTickOffsets[3] + 3),
                    ),
                ),
                Snippet(
                    SnippetId(
                        listOf("temper-code", "build-user-docs", "skeletal-docs", "foo.md", "2"),
                        MD_EXTENSION,
                    ),
                    shortTitle = null,
                    source = sourceFile,
                    sourceStartOffset = tripleTickOffsets[4],
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        // We report expectation mismatches via problems, not via crosses.
                        // Crosses indicate the Temper input fails to stage.
                        """
                            |```temper
                            |1 - 1 - 1 == 1 // Wrong because associativity
                            |// ✅ false
                            |```
                        """.trimMargin(),
                    ),
                    isIntermediate = false,
                    derivation = derivationFor(
                        tripleTickOffsets[4] until (tripleTickOffsets[5] + 3),
                    ),
                    problems = listOf("Temper code snippet produced false, not true"),
                ),
                Snippet(
                    SnippetId(
                        listOf("temper-code", "build-user-docs", "skeletal-docs", "foo.md", "3"),
                        MD_EXTENSION,
                    ),
                    shortTitle = null,
                    source = sourceFile,
                    sourceStartOffset = tripleTickOffsets[6],
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        """
                            |```temper
                            |1 + 1
                            |// ✅ { "type": "Int32", "stateVector": 2 }
                            |```
                        """.trimMargin(),
                    ),
                    isIntermediate = false,
                    derivation = derivationFor(
                        tripleTickOffsets[6] until (tripleTickOffsets[7] + 3),
                    ),
                ),
                Snippet(
                    SnippetId(
                        listOf("temper-code", "build-user-docs", "skeletal-docs", "foo.md", "4"),
                        MD_EXTENSION,
                    ),
                    shortTitle = null,
                    source = sourceFile,
                    sourceStartOffset = tripleTickOffsets[8],
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        """
                            |```temper
                            |// Division by zero fails
                            |1 / 0
                            |// ❌
                            |```
                        """.trimMargin(),
                    ),
                    isIntermediate = false,
                    derivation = derivationFor(
                        tripleTickOffsets[8] until (tripleTickOffsets[9] + 3),
                    ),
                ),
            ),
            snippets,
        )

        // Rerunning the extractor on the snippets it extracted should not find more snippets.
        // We need to reach a fixed point.
        val nestedSnippets = mutableListOf<Snippet>()
        for (snippet in snippets) {
            TemperCodeSnippetExtractor.extractNestedSnippets(snippet, nestedSnippets)
        }
        assertEquals("[]", "$nestedSnippets")
    }

    @Suppress("MagicNumber")
    @Test
    fun backPort() {
        val sourcePath = filePath("foo", "bar.temper")
        val result = TemperCodeSnippetExtractor.backPortInsertion(
            inserted = Snippet(
                id = SnippetId(listOf("my", "code"), MD_EXTENSION),
                shortTitle = null,
                source = sourcePath,
                sourceStartOffset = 123,
                mimeType = MimeType.markdown,
                content = TextDocContent(
                    """
                    |```temper
                    |1 + 1 == 1.9999999999
                    |// ✅
                    |```
                    """.trimMargin(),
                ),
                isIntermediate = false,
                derivation = ExtractedAndReplacedBack(
                    TemperCodeSnippetExtractor,
                    replaceBackRange = 123 until 223,
                    extracted = TextDocContent(
                        """
                        |```temper true
                        |1 + 1 == 1.9999999999
                        |```
                        """.trimMargin(),
                    ),
                ),
            ),
            priorInsertion = TextDocContent("$INSERTION_MARKER_CHAR foo/bar/1"),
        ) {
            TextDocContent(
                """
                    |<!-- snippet: foo/bar/1 -->
                    |
                    |```temper
                    |1 + 1 == 2
                    |// ✅
                    |```
                """.trimMargin(),
            )
        }

        assertEquals(
            RSuccess(
                TextDocContent(
                    // We strip the snippet marker HTML comment from the front.
                    // Within the fenced code block,
                    // we strip the report line from the end,
                    // and we restore the result expectations.
                    """
                        |```temper true
                        |1 + 1 == 2
                        |```
                    """.trimMargin(),
                ),
            ),
            result,
        )
    }
}
