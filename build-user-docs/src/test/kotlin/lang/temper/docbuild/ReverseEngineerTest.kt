package lang.temper.docbuild

import lang.temper.common.Log
import lang.temper.common.assertStringsEqual
import lang.temper.common.assertStructure
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.withCapturingConsole
import kotlin.test.Test

class ReverseEngineerTest {
    @Test
    fun nestedSnippet() = assertReversed(
        """
            |# A Snippet
            |
            |Before insertion.
            |
            |<!-- snippet: foo/bar -->
            |
            |Some nested content
            |
            |<!-- /snippet: foo/bar -->
            |
            |After insertion
            |
        """.trimMargin(),
        Reversed.SourcedMarkdown(
            relFilePath = UserDocsContentForTest.filePathForTest,
            inlinedSnippetId = null,
            content = Reversed.Concatenation(
                listOf(
                    Nested.Literal(
                        """
                            |# A Snippet
                            |
                            |Before insertion.
                            |
                            |
                        """.trimMargin(),
                    ),
                    Reversed.SourcedMarkdown(
                        relFilePath = pathToSnippet("foo/bar"),
                        inlinedSnippetId = mdSnippetId("foo/bar"),
                        content = Reversed.Concatenation(
                            listOf(
                                Nested.MarkerComment("<!-- snippet: foo/bar -->"),
                                Nested.Literal(
                                    """
                                        |
                                        |
                                        |Some nested content
                                        |
                                        |
                                    """.trimMargin(),
                                ),
                                Nested.MarkerComment("<!-- /snippet: foo/bar -->"),
                            ),
                        ),
                    ),
                    Nested.Literal(
                        """
                            |
                            |
                            |After insertion
                            |
                        """.trimMargin(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun anchorsAreCutOut() = assertReversed(
        """
            |
            |<a name="this-snippet" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
            |
            |# Lorem Ipsum
            |
            |sic dolor amet n'at.
            |
        """.trimMargin(),
        Reversed.SourcedMarkdown(
            UserDocsContentForTest.filePathForTest,
            null,
            Reversed.Concatenation(
                listOf(
                    Nested.Literal(
                        """
                            |
                            |
                            |
                            |# Lorem Ipsum
                            |
                            |sic dolor amet n'at.
                            |
                        """.trimMargin(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun indentedNestedSnippet() = assertReversed(
        """
            |# Header
            |
            |A list of things
            |
            |- <!-- snippet: indented -->
            |
            |  Indented content
            |
            |  <!-- /snippet: indented -->
            |- After
        """.trimMargin(),
        Reversed.SourcedMarkdown(
            UserDocsContentForTest.filePathForTest,
            null,
            Reversed.Concatenation(
                listOf(
                    Nested.Literal(
                        """
                        |# Header
                        |
                        |A list of things
                        |
                        |- ${""}
                        """.trimMargin(),
                    ),
                    Reversed.SourcedMarkdown(
                        pathToSnippet("indented"),
                        mdSnippetId("indented"),
                        Reversed.Concatenation(
                            listOf(
                                Reversed.IndentedRegion(
                                    "  ",
                                    Reversed.Concatenation(
                                        listOf(
                                            Nested.MarkerComment("<!-- snippet: indented -->"),
                                            Nested.Literal(
                                                """
                                                |
                                                |
                                                |  Indented content
                                                |
                                                |  ${""}
                                                """.trimMargin(),
                                            ),
                                            Nested.MarkerComment("<!-- /snippet: indented -->"),
                                        ),
                                    ),
                                    indentFirstLine = false,
                                ),
                            ),
                        ),
                    ),
                    Nested.Literal(
                        """
                        |
                        |- After
                        """.trimMargin(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun failureModes() = assertReversed(
        """
            |# Header
            |
            |<!-- snippet: foo -->
            |
            |<a name="foo" class="$SNIPPET_ANCHOR_CLASSNAME">
            |
            |Foo
            |
            |> <!-- snippet: foo/bar -->
            |>
            |> Bar
            |>
            |<!-- /snippet: foo/bar -->
            |
            |<!-- /snippet: bar -->
        """.trimMargin(),
        Reversed.SourcedMarkdown(
            UserDocsContentForTest.filePathForTest,
            null,
            Reversed.Concatenation(
                listOf(
                    Nested.Literal(
                        """
                            |# Header
                            |
                            |
                        """.trimMargin(),
                    ),
                    Nested.MarkerComment("<!-- snippet: foo -->"), // not paired
                    Nested.Literal(
                        """
                            |
                            |
                            |
                            |
                            |Foo
                            |
                            |> ${""}
                        """.trimMargin(),
                    ),
                    Reversed.SourcedMarkdown(
                        pathToSnippet("foo/bar"),
                        mdSnippetId("foo/bar"),
                        Reversed.Concatenation(
                            listOf(
                                Nested.MarkerComment("<!-- snippet: foo/bar -->"),
                                Nested.Literal(
                                    """
                                        |
                                        |>
                                        |> Bar
                                        |>
                                        |
                                    """.trimMargin(),
                                ),
                                Nested.MarkerComment("<!-- /snippet: foo/bar -->"),
                            ),
                        ),
                    ),
                    Nested.Literal(
                        """
                            |
                            |
                            |
                        """.trimMargin(),
                    ),
                    Nested.MarkerComment("<!-- /snippet: bar -->"), // not paired
                ),
            ),
        ),
        wantedMessages = """
            |test/test.md: Missing close tag for `<a name="foo" class="$SNIPPET_ANCHOR_CLASSNAME">`
            |test/test.md: Mismatched indentation for markers of foo/bar
            |test/test.md: Unmatched close snippet marker for bar
            |test/test.md: Unclosed snippet foo
            |
        """.trimMargin(),
    )

    @Test
    fun linksInMarkdown() = assertReversed(
        """
            |# A bunch of different kinds of links
            |
            |A [link](https://example.com/) to some text.
            |[Another link] to some other text.
            |[Another **link**][1] to a foot note.
            |
        """.trimMargin(),
        Reversed.SourcedMarkdown(
            UserDocsContentForTest.filePathForTest,
            null,
            Reversed.Concatenation(
                listOf(
                    Nested.Literal(
                        """
                            |# A bunch of different kinds of links
                            |
                            |A ${""}
                        """.trimMargin(),
                    ),
                    Reversed.LinkedMarkdown(
                        Reversed.Concatenation(
                            listOf(Nested.Literal("[link]")),
                        ),
                        Link.Kind.Parenthetical,
                        "https://example.com/",
                    ),
                    Nested.Literal(
                        """
                            | to some text.
                            |
                        """.trimMargin(),
                    ),
                    Reversed.LinkedMarkdown(
                        Reversed.Concatenation(
                            listOf(Nested.Literal("[Another link]")),
                        ),
                        Link.Kind.SquareBracketed,
                        null,
                    ),
                    Nested.Literal(
                        """
                            | to some other text.
                            |
                        """.trimMargin(),
                    ),
                    Reversed.LinkedMarkdown(
                        Reversed.Concatenation(
                            listOf(Nested.Literal("[Another **link**]")),
                        ),
                        Link.Kind.SquareBracketed,
                        "1",
                    ),
                    Nested.Literal(
                        """
                            | to a foot note.
                            |
                        """.trimMargin(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun indentedSnippetEndMarkers() = assertReversed(
        inputMarkdown = """
            |Overheard:
            |
            |> - Nice to meet you.  I'm confused.
            |> - <!-- snippet: about/that -->
            |>
            |>   That's not a goat.  🐐 is a goat.
            |>
            |>   <!-- /snippet: about/that -->
            |> - When was I, again?
            |
        """.trimMargin(),
        wanted = Reversed.SourcedMarkdown(
            UserDocsContentForTest.filePathForTest,
            null,
            Reversed.Concatenation(
                listOf(
                    Nested.Literal(
                        """
                            |Overheard:
                            |
                            |> - Nice to meet you.  I'm confused.
                            |> - ${""}
                        """.trimMargin(),
                    ),
                    Reversed.SourcedMarkdown(
                        pathToSnippet("about/that"),
                        mdSnippetId("about/that"),
                        Reversed.Concatenation(
                            listOf(
                                Reversed.IndentedRegion(
                                    ">   ",
                                    Reversed.Concatenation(
                                        listOf(
                                            Nested.MarkerComment("<!-- snippet: about/that -->"),
                                            Nested.Literal(
                                                """
                                                    |
                                                    |>
                                                    |>   That's not a goat.  🐐 is a goat.
                                                    |>
                                                    |>   ${""}
                                                """.trimMargin(),
                                            ),
                                            Nested.MarkerComment("<!-- /snippet: about/that -->"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    Nested.Literal(
                        """
                            |
                            |> - When was I, again?
                            |
                        """.trimMargin(),
                    ),
                ),
            ),
        ),
    )

    private fun assertReversed(
        inputMarkdown: String,
        wanted: Reversed.SourcedMarkdown,
        wantedMessages: String = "",
    ) {
        val userDocsContent = UserDocsContentForTest(
            inputMarkdown,
            emptyMap(),
        )

        val (got, gotMessages) = withCapturingConsole(Log.Warn) { testConsole ->
            val problemTracker = ProblemTracker(testConsole)
            userDocsContent.reverseUserDocs(
                relFilePath = UserDocsContentForTest.filePathForTest,
                content = MarkdownContent(inputMarkdown),
                problemTracker = problemTracker,
            )
        }

        assertStructure(
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("reversed") {
                        value(wanted)
                    }
                    key("log") {
                        value(wantedMessages)
                    }
                }
            },
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("reversed") {
                        value(got)
                    }
                    key("log") {
                        value(gotMessages)
                    }
                }
            },
            inputContext = emptyMap(),
        )
    }

    @Test
    fun singlyInsertedSnippetsChanged() {
        assertReverseEngineered(
            embeddingMarkdownFileContent = """
                |# Header
                |
                |$INSERTION_MARKER_CHAR snippet/one
                |
                |## Sub-header
                |
                |$INSERTION_MARKER_CHAR snippet/two
                |
                |Text after snippet two.
                |
            """.trimMargin(),
            snippetContents = mapOf(
                "snippet/one" to """
                    |<!-- snippet: snippet/one -->
                    |
                    |Text for snippet one before.
                    |
                """.trimMargin(),

                "snippet/two" to """
                    |<!-- snippet: snippet/two -->
                    |
                    |Text for snippet two before.
                    |
                    |$INSERTION_MARKER_CHAR snippet/three
                    |
                """.trimMargin(),

                "snippet/three" to """
                    |<!-- snippet: snippet/three -->
                    |
                    |# Header for snippet three
                    |
                    |Text for snippet three before.
                    |
                """.trimMargin(),
            ),
            editedContent = """
                |# Header
                |
                |<!-- snippet: snippet/one -->
                |
                |Text for snippet one after.
                |
                |<!-- /snippet: snippet/one -->
                |
                |## Sub-header
                |
                |<!-- snippet: snippet/two -->
                |
                |Text for snippet two before.
                |
                |<!-- snippet: snippet/three -->
                |
                |### Header for snippet three.
                |
                |Text for snippet three after.
                |
                |<!-- /snippet: snippet/three -->
                |
                |<!-- /snippet: snippet/two -->
                |
                |Text after snippet two changed a bit.
                |
            """.trimMargin(),
            wantedChanges = """
                |{
                |  snippetChanges: {
                |    "snippet/one": ```
                |      <!-- snippet: snippet/one -->
                |
                |      Text for snippet one after.
                |
                |      ```,
                |    // snippet two was not substantively changed even
                |    // though a snippet it nested changed.
                |    "snippet/three": ```
                |      <!-- snippet: snippet/three -->
                |
                |      # Header for snippet three.
                |
                |      Text for snippet three after.
                |
                |      ```,
                |  },
                |  fileChanges: {
                |    "test/test.md": ```
                |      # Header
                |
                |      $INSERTION_MARKER_CHAR snippet/one
                |
                |      ## Sub-header
                |
                |      $INSERTION_MARKER_CHAR snippet/two
                |
                |      Text after snippet two changed a bit.
                |
                |      ```,
                |  }
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun snippetInsertedWithAttributes() = assertReverseEngineered(
        embeddingMarkdownFileContent = """
            |# Title
            |
            |$INSERTION_MARKER_CHAR foo  -heading
            |
        """.trimMargin(),
        snippetContents = mapOf(
            "foo" to """
                |# Sub-title
                |
                |This sentence has one typos.
                |
                |
            """.trimMargin(),
        ),
        editedContent = """
            |# Title
            |
            |<!-- snippet: foo -->
            |
            |This sentence has one typo.
            |
            |<!-- /snippet: foo -->
            |
        """.trimMargin(),
        wantedChanges = """
            |{
            |  snippetChanges: {
            |    foo: ```
            |      <!-- snippet: foo -->
            |
            |      # Sub-title
            |
            |      This sentence has one typo.
            |
            |
            |      ```,
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun snippetStartingWithInsertion() = assertReverseEngineered(
        embeddingMarkdownFileContent = """
            |$INSERTION_MARKER_CHAR a
            |
            |$INSERTION_MARKER_CHAR b
            |
        """.trimMargin(),
        snippetContents = mapOf(
            "a" to
                """
                    |<!-- snippet: a -->
                    |$INSERTION_MARKER_CHAR a1
                    |
                    |$INSERTION_MARKER_CHAR a2
                    |
                """.trimMargin(),
            "a1" to
                """
                    |<!-- snippet: a1 -->
                    |a1
                    |
                """.trimMargin(),
            "a2" to
                """
                    |<!-- snippet: a2 -->
                    |a2
                    |
                """.trimMargin(),
            "b" to
                """
                    |<!-- snippet: b -->
                    |b
                    |
                """.trimMargin(),
        ),
        editedContent = """
            |<!-- snippet: a -->
            |
            |<a name="a" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
            |
            |<!-- snippet: a1 -->
            |
            |<a name="a1" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
            |
            |a1
            |
            |<!-- /snippet: a1 -->
            |
            |<!-- snippet: a2 -->
            |
            |<a name="a2" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
            |
            |a2${
            /* this is an edit */
            "'"
        }
            |
            |<!-- /snippet: a2 -->
            |
            |<!-- /snippet: a -->
            |
            |<!-- snippet: b -->
            |
            |<a name="b" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
            |
            |b
            |
            |<!-- /snippet: b -->
            |
        """.trimMargin(),
        wantedChanges = """
            |{
            |  snippetChanges: {
            |    "a2":
            |      ```
            |      <!-- snippet: a2 -->
            |      a2'
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun snippetEndingWithInsertion() = assertReverseEngineered(
        embeddingMarkdownFileContent = """
            |$INSERTION_MARKER_CHAR a
            |
        """.trimMargin(),
        snippetContents = mapOf(
            "a" to
                """
                    |<!-- snippet: a -->
                    |# Header before
                    |
                    |$INSERTION_MARKER_CHAR b
                    |
                    |
                """.trimMargin(),
            "b" to
                """
                    |<!-- snippet: b -->
                    |b
                    |
                """.trimMargin(),
        ),
        editedContent = """
            |<!-- snippet: a -->
            |
            |<a name="a" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
            |
            |# Header after
            |
            |<!-- snippet: b -->
            |
            |<a name="b" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
            |
            |b
            |
            |<!-- /snippet: b -->
            |
            |<!-- /snippet: a -->
            |
        """.trimMargin(),
        wantedChanges = """
            |{
            |  snippetChanges: {
            |    "a":
            |      ```
            |      <!-- snippet: a -->
            |      # Header after
            |
            |      $INSERTION_MARKER_CHAR b
            |
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    private fun assertReverseEngineered(
        embeddingMarkdownFileContent: String,
        snippetContents: Map<String, String>,
        editedContent: String,
        wantedChanges: String,
        wantedMessages: String = "",
    ) {
        val userDocsContent = UserDocsContentForTest(
            embeddingMarkdownFileContent = embeddingMarkdownFileContent,
            snippetContents = snippetContents,
        )
        userDocsContent.editedContent[UserDocsContentForTest.filePathForTest] =
            MarkdownContent(editedContent)

        val (got, gotMessages) = withCapturingConsole(Log.Warn) { testConsole ->
            val problemTracker = ProblemTracker(testConsole)
            userDocsContent.reverseEngineer(problemTracker)
        }

        assertStringsEqual(wantedMessages, gotMessages)
        assertStructure(wantedChanges, got)
    }
}
