package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.assertStructure
import lang.temper.common.diff.Diff
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.withCapturingConsole
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinCommentExtractorTest {
    private val fileContent = """
        |package com.example;
        |
        |/**
        | * A block comment can contain snippets.
        | *
        | * <!-- snippet: sample/foo -->
        | * Content of snippet foo.
        | *
        | * <!-- snippet: sample/bar : Bar -->
        | * Content of bar snippet.
        | * <!-- /snippet -->
        | *
        | * <!-- snippet: has/encoded/char/%2A : has `*` -->
        | * '/' followed by '*' in a Kotlin comment creates a nested comment so
        | * it's convenient to be able to encode characters in path parts.
        | * Also handy if we want to document the division operator ('/') by name.
        | *
        | * <!-- snippet: another: Yet another snippet! -->
        | * Content of another snippet.
        | *
        | * @param foo
        | * @return foo
        | */
        |fun f(foo: Int) = foo
        |
        |console.log(
        |    // There is no snippet in this string
        |    ${"\"\"\""}
        |    * <!-- snippet: not/a/snippet -->
        |    * No snippet here
        |    * <!-- /snippet -->
        |    ${"\"\"\""}
        |)
        |
        |/** Another comment with a snippet that goes to the end
        | *
        | * <!-- snippet: ends-at-comment-end -->
        | * Content that ends at comment end.
        | */
        |val x = 123
        |
    """.trimMargin()

    private val sourcePath = filePath("foo", "Bar.kt")

    private val derivation = ExtractedBy(KotlinCommentExtractor)

    private val snippetFoo = Snippet(
        id = SnippetId(listOf("sample", "foo"), extension = ".md"),
        shortTitle = null,
        source = sourcePath,
        sourceStartOffset = fileContent.indexOf("<!-- snippet: sample/foo"),
        mimeType = MimeType.markdown,
        content = TextDocContent(
            "<!-- snippet: sample/foo -->\nContent of snippet foo.",
        ),
        isIntermediate = false,
        derivation = derivation,
    )

    private val snippetBar = Snippet(
        id = SnippetId(listOf("sample", "bar"), extension = ".md"),
        shortTitle = "Bar",
        source = sourcePath,
        sourceStartOffset = fileContent.indexOf("<!-- snippet: sample/bar"),
        mimeType = MimeType.markdown,
        content = TextDocContent(
            "<!-- snippet: sample/bar : Bar -->\nContent of bar snippet.",
        ),
        isIntermediate = false,
        derivation = derivation,
    )

    private val snippetHasEncodedChar = Snippet(
        id = SnippetId(listOf("has", "encoded", "char", "*"), extension = ".md"),
        shortTitle = "has `*`",
        source = sourcePath,
        sourceStartOffset = fileContent.indexOf("<!-- snippet: has/encoded/"),
        mimeType = MimeType.markdown,
        content = TextDocContent(
            """
                |<!-- snippet: has/encoded/char/%2A : has `*` -->
                |'/' followed by '*' in a Kotlin comment creates a nested comment so
                |it's convenient to be able to encode characters in path parts.
                |Also handy if we want to document the division operator ('/') by name.
            """.trimMargin(),
        ),
        isIntermediate = false,
        derivation = derivation,
    )

    private val snippetAnother = Snippet(
        id = SnippetId(listOf("another"), extension = ".md"),
        shortTitle = "Yet another snippet!",
        source = sourcePath,
        sourceStartOffset = fileContent.indexOf("<!-- snippet: another"),
        mimeType = MimeType.markdown,
        content = TextDocContent(
            """
                |<!-- snippet: another: Yet another snippet! -->
                |Content of another snippet.
            """.trimMargin(),
            // The @param KDoc tags are not included.
        ),
        isIntermediate = false,
        derivation = derivation,
    )

    private val snippetEndsAtCommentEnd = Snippet(
        id = SnippetId(listOf("ends-at-comment-end"), extension = ".md"),
        shortTitle = null,
        source = sourcePath,
        sourceStartOffset = fileContent.indexOf("<!-- snippet: ends-at-comment-end"),
        mimeType = MimeType.markdown,
        content = TextDocContent(
            """
                |<!-- snippet: ends-at-comment-end -->
                |Content that ends at comment end.
            """.trimMargin(),
            // The @param KDoc tags are not included.
        ),
        isIntermediate = false,
        derivation = derivation,
    )

    @Test
    fun extractOfKotlinFile() {
        val snippets = mutableListOf<Snippet>()

        KotlinCommentExtractor.extractSnippets(
            sourcePath,
            KotlinContent(fileContent),
            MimeType.kotlinSource,
            snippets,
        )

        assertStructure(
            JsonValueBuilder.build {
                value(
                    listOf(
                        snippetFoo,
                        snippetBar,
                        snippetHasEncodedChar,
                        snippetAnother,
                        snippetEndsAtCommentEnd,
                    ),
                )
            },
            JsonValueBuilder.build {
                value(snippets.toList())
            },
        )
    }

    @Test
    fun reincorporateCommentTest() = assertBackPortedChange(
        snippet = snippetFoo,
        newContent = """
            |<!-- snippet: sample/foo -->
            |The new foo
            |
            |
        """.trimMargin(),
        wantedSourceDiff = """
            |@@ -4,5 +4,5 @@
            |  *
            |  * <!-- snippet: sample/foo -->
            |- * Content of snippet foo.
            |+ * The new foo
            |  *
            |  * <!-- snippet: sample/bar : Bar -->
            |
        """.trimMargin(),
    )

    @Suppress("SpellCheckingInspection")
    @Test
    fun badBackPortThatLacksValidSnippetHeader() = assertBackPortedChange(
        snippet = snippetBar,
        // Misspelling below of "snippet" fails consistency checks.
        newContent = """
            |<!-- stoppit: sample/bar -->
            |The new bar.
            |
            |
        """.trimMargin(),
        wantedSourceDiff = """
            |@@ -6,6 +6,7 @@
            |  * Content of snippet foo.
            |  *
            |- * <!-- snippet: sample/bar : Bar -->
            |- * Content of bar snippet.
            |+ * <!-- stoppit: sample/bar -->
            |+ * The new bar.
            |+ *
            |  * <!-- /snippet -->
            |  *
            |
        """.trimMargin(),
        wantedProblemCount = 1,
        wantedLogs = """
            |$sourcePath: Applying edits to sample/bar changed the set of available snippets:${
            ""
        } SetDelta(+[], -[build-user-docs/build/snippet/sample/bar/snippet.md])
            |
        """.trimMargin(),
    )

    @Test
    fun badBackPortBreaksCommentToken() = assertBackPortedChange(
        snippet = snippetBar,
        // regex below embeds a Kotlin comment terminator.
        newContent = """
            |<!-- snippet: sample/bar : Bar -->
            |My favourite regular expression is /.*/
            |
        """.trimMargin(),
        wantedSourceDiff = """
            |@@ -7,5 +7,5 @@
            |  *
            |  * <!-- snippet: sample/bar : Bar -->
            |- * Content of bar snippet.
            |+ * My favourite regular expression is /.*/
            |  * <!-- /snippet -->
            |  *
            |
        """.trimMargin(),
        wantedProblemCount = 1,
        wantedLogs = """
            |$sourcePath: Applying edits to sample/bar changed the set of available snippets:${
            ""
        } SetDelta(+[], -[build-user-docs/build/snippet/has/encoded/char/${'$'}2a/snippet.md,${
            ""
        } build-user-docs/build/snippet/another/snippet.md])
            |
        """.trimMargin(),
    )

    @Test
    fun snippetThatEndsAtCommentEnd() = assertBackPortedChange(
        snippet = snippetEndsAtCommentEnd,
        newContent = """
            |<!-- snippet: ends-at-comment-end -->
            |Content that still ends at comment end.
        """.trimMargin(),
        wantedSourceDiff = """
            |@@ -35,5 +35,5 @@
            |  *
            |  * <!-- snippet: ends-at-comment-end -->
            |- * Content that ends at comment end.
            |+ * Content that still ends at comment end.
            |  */
            | val x = 123
            |
        """.trimMargin(),
    )

    @Test
    fun newContentMissingTrailingNewlines() {
        assertBackPortedChange(
            snippet = snippetFoo,
            newContent = "<!-- snippet: sample/foo -->",
            wantedSourceDiff = """
                |@@ -4,6 +4,4 @@
                |  *
                |  * <!-- snippet: sample/foo -->
                |- * Content of snippet foo.
                |- *
                |  * <!-- snippet: sample/bar : Bar -->
                |  * Content of bar snippet.
                |
            """.trimMargin(),
        )
        assertBackPortedChange(
            snippet = snippetBar,
            newContent = "<!-- snippet: sample/bar : Bar -->",
            wantedSourceDiff = """
                |@@ -7,5 +7,4 @@
                |  *
                |  * <!-- snippet: sample/bar : Bar -->
                |- * Content of bar snippet.
                |  * <!-- /snippet -->
                |  *
                |
            """.trimMargin(),
        )
        assertBackPortedChange(
            snippet = snippetAnother,
            newContent = "<!-- snippet: another: Yet another snippet! -->",
            wantedSourceDiff = """
                |@@ -16,6 +16,4 @@
                |  *
                |  * <!-- snippet: another: Yet another snippet! -->
                |- * Content of another snippet.
                |- *
                |  * @param foo
                |  * @return foo
                |
            """.trimMargin(),
        )
    }

    @Test
    fun nestedCommentsExtracted() {
        val input = """
            |/**
            | * Here's a Kotlin comment
            | *
            | * <!-- snippet: yes -->
            | * Have some code:
            | * ```
            | * /* A line comment */
            | * ```
            | */
        """.trimMargin()
        val got = buildList {
            KotlinCommentExtractor.extractSnippets(
                filePath("file.kt"),
                KotlinContent(input),
                MimeType.kotlinSource,
                this,
            )
        }
        assertEquals(1, got.size)
        assertEquals(
            """
                |<!-- snippet: yes -->
                |Have some code:
                |```
                |/* A line comment */
                |```
            """.trimMargin(),
            (got.first().content as TextDocContent).text,
        )
    }

    private fun assertBackPortedChange(
        originalSourceCode: String = fileContent,
        snippet: Snippet,
        newContent: String,
        wantedSourceDiff: String,
        wantedProblemCount: ProblemCount = 0,
        wantedLogs: String = "",
    ) {
        val content = StringBuilder(originalSourceCode)
        val (problemCount, logs) = withCapturingConsole { console ->
            val problemTracker = ProblemTracker(console)
            KotlinCommentExtractor.backPortSnippetChange(
                snippet = snippet,
                newContent = MarkdownContent(newContent),
                into = content,
                problemTracker = problemTracker,
            )
            problemTracker.problemCount
        }
        val sourceDiff = Diff.formatPatch(
            Diff.differencesBetween(fileContent, "$content"),
            context = 2,
        )
        assertStructure(
            ResultBundle(wantedSourceDiff, wantedProblemCount, wantedLogs),
            ResultBundle(sourceDiff, problemCount, logs),
        )
    }
}

private data class ResultBundle(
    val sourceDiff: String,
    val problemCount: Int,
    val logs: String,
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("diff") { value(sourceDiff) }
        key("problemCount", isDefault = problemCount == 0) {
            value(problemCount)
        }
        key("logs", isDefault = logs == "") {
            value(logs)
        }
    }
}
