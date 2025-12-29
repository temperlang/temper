package lang.temper.docbuild

import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.TextOutput
import lang.temper.common.assertStringsEqual
import lang.temper.common.withCapturingConsole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ReconcileUserDocsTest {
    @Test
    fun strippedHeaderRecovered() {
        val snippets = SnippetsBeforeAndAfter(
            snippetContents = mapOf(
                "foo/bar" to
                    """
                    |<!-- snippet: foo/bar -->
                    |
                    |# Foo
                    |
                    |Some content
                    |
                    """.trimMargin(),
            ),
            // Because of the `-heading`, the inlined snippet loses `#Foo`
            before = """$INSERTION_MARKER_CHAR foo/bar -heading""",
            after = """
                |<!-- snippet: foo/bar -->
                |
                |<a name="foo-bar" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
                |
                |Some changed content
                |
                |<!-- /snippet: foo/bar -->
            """.trimMargin(),
        )
        assertReconciled(
            snippets = snippets,
            abbreviatedId = "foo/bar",
            reconciled = """
                |<!-- snippet: foo/bar -->
                |
                |# Foo
                |
                |Some changed content
                |
            """.trimMargin(),
        )
    }

    @Test
    fun indentationStripped() {
        val snippets = SnippetsBeforeAndAfter(
            snippetContents = mapOf(
                "more-things" to
                    """
                    |<!-- snippet: more-things -->
                    |
                    |Snippets
                    |
                    """.trimMargin(),
            ),
            before = """
                |A list of things I like:
                |
                |- Ice cream
                |- Puppies
                |- $INSERTION_MARKER_CHAR more-things
                |- Movies with unsurprising surprise endings
            """.trimMargin(),
            after = """
                |A list of things I like:
                |
                |- Ice cream
                |- Puppies
                |- <!-- snippet: more-things -->
                |
                |  Snippets
                |
                |  Also, sending birthday cards to people I don't know.
                |
                |  <!-- /snippet: more-things -->
                |- Movies with unsurprising surprise endings
            """.trimMargin(),
        )
        assertReconciled(
            snippets = snippets,
            abbreviatedId = "more-things",
            reconciled = """
                |<!-- snippet: more-things -->
                |
                |Snippets
                |
                |Also, sending birthday cards to people I don't know.
                |
            """.trimMargin(),
        )
    }

    @Test
    fun indentationChangedAroundSnippet() {
        val snippets = SnippetsBeforeAndAfter(
            snippetContents = mapOf(
                "about/that" to
                    """
                    |<!-- snippet: about/that -->
                    |
                    |That's not a goat.  :goat: is a goat.
                    |
                    """.trimMargin(),
            ),
            before = """
                |Apropos of nothing
                |
                |$INSERTION_MARKER_CHAR about/that
                |
            """.trimMargin(),
            after = """
                |Overheard:
                |
                |> - Nice to meet you.  I'm confused.
                |> - <!-- snippet: about/that -->
                |>
                |>   That's not a goat.  🐐 is a goat.
                |>
                |>   <!-- /snippet: about/that -->
                |> - When was I, again?
            """.trimMargin(),
        )
        assertReconciled(
            snippets = snippets,
            abbreviatedId = "about/that",
            reconciled = """
                |<!-- snippet: about/that -->
                |
                |That's not a goat.  🐐 is a goat.
                |
            """.trimMargin(),
        )
    }

    @Test
    fun reconcileSnippetLink() {
        val snippets = SnippetsBeforeAndAfter(
            snippetContents = mapOf(
                "about/bananas" to
                    """
                    |<!-- snippet: about/bananas : the yellow fruit -->
                    |
                    |Bananas are pretty cool
                    |
                    """.trimMargin(),
                "about/apples" to
                    """
                    |<!-- snippet: about/apples -->
                    |
                    |# The round-ish fruit
                    |
                    |Apples are pretty cool
                    |
                    """.trimMargin(),
                "toc" to
                    """
                    |<!-- snippet: toc -->
                    |
                    |Table of contents
                    |
                    |- [snippet/about/apples]
                    |- [snippet/about/bananas]
                    |
                    """.trimMargin(),
            ),
            before = """
                |Here are some fruits.
                |
                |$INSERTION_MARKER_CHAR toc
                |
                |$INSERTION_MARKER_CHAR about/apples
                |
                |$INSERTION_MARKER_CHAR about/bananas
                |
            """.trimMargin(),
            after = """
                |Here are two fruits.
                |
                |<!-- snippet: toc -->
                |
                |<a name="#toc" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
                |
                |Table of contents
                |
                |- [The round-ish fruit](#about-apples)
                |- [the yellow fruit](#about-bananas)
                |- Maybe mangoes
                |
                |<!-- /snippet: toc -->
                |
                |<!-- snippet: about/apples -->
                |
                |<a name="#about-apples" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
                |
                |# The round-ish fruit
                |
                |Apples are pretty cool
                |
                |<!-- /snippet: about/apples -->
                |
                |<!-- snippet: about/bananas : the yellow fruit -->
                |
                |<a name="#about-bananas" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
                |
                |Bananas are pretty cool
                |
                |<!-- /snippet: about/bananas -->
            """.trimMargin(),
        )
        assertReconciled(
            snippets = snippets,
            abbreviatedId = "toc",
            reconciled = """
                |<!-- snippet: toc -->
                |
                |Table of contents
                |
                |- [snippet/about/apples]
                |- [snippet/about/bananas]
                |- Maybe mangoes
                |
            """.trimMargin(),
        )
    }

    @Test
    fun nonCanonicalLinksPreserved() {
        val url = "${
            PROJECT_GITHUB_URL
        }/blob/main/build-user-docs/src/test/kotlin/lang/temper/docbuild/ReconcileUserDocsTest.kt"
        val snippets = SnippetsBeforeAndAfter(
            snippetContents = mapOf(
                "foo" to
                    """
                    |<!-- snippet: foo -->
                    |
                    |Source code in [temper/**/ReconcileUserDocsTest.kt]
                    |
                    """.trimMargin(),
            ),
            before = """
                |# Links
                |
                |$INSERTION_MARKER_CHAR foo
                |
            """.trimMargin(),
            after = """
                |# Links
                |
                |<!-- snippet: foo -->
                |
                |<a name="#foo" class="$SNIPPET_ANCHOR_CLASSNAME"></a>
                |
                |Source in [*ReconciledUserDocsTest.kt*]($url)
                |
                |<!-- /snippet: foo -->
                |
            """.trimMargin(),
        )
        assertReconciled(
            snippets = snippets,
            abbreviatedId = "foo",
            reconciled = """
                |<!-- snippet: foo -->
                |
                |Source in [temper/**/ReconcileUserDocsTest.kt]
                |
            """.trimMargin(),
        )
    }

    private class SnippetsBeforeAndAfter(
        val snippetContents: Map<String, String>,
        before: String,
        after: String,
    ) {
        val nested: Nested.SourcedMarkdown
        val reversed: Reversed.SourcedMarkdown
        val byId: Map<SnippetId?, Triple<MdChunk?, Reversed.SourcedMarkdown?, InsertionAttributeMap>>
        val userDocsContent: AbstractUserDocsContent

        init {
            class FailOnErrors : TextOutput() {
                override val isTtyLike: Boolean get() = false
                override fun emitLineChunk(text: CharSequence) {
                    fail("$text")
                }
                override fun flush() {
                    // No buffers that need flushing
                }
            }
            val testConsole = Console(FailOnErrors(), Log.Warn)

            userDocsContent = UserDocsContentForTest(before, snippetContents)
            nested = userDocsContent.extractNestedForward(
                MarkdownContent(before),
                UserDocsContentForTest.filePathForTest,
            )
            reversed = userDocsContent.reverseUserDocs(
                UserDocsContentForTest.filePathForTest,
                MarkdownContent(after),
                problemTracker = ProblemTracker(testConsole),
            )

            // Walk the Nested and Reversed trees so that we can pair them together.
            val chunkById = mutableMapOf<SnippetId, Pair<MdChunk, InsertionAttributeMap>>()
            val reversedById = mutableMapOf<SnippetId, Reversed.SourcedMarkdown>()
            fun findNested(mdChunk: MdChunk) {
                if (mdChunk is Nested.Concatenation) {
                    for (chunk in mdChunk.chunks) {
                        // Look through adjusting layers that require adjusting content when reconciling
                        var possibleInsertion = chunk as? MdChunk ?: continue
                        while (true) {
                            possibleInsertion = when (possibleInsertion) {
                                is Nested.Concatenation -> if (possibleInsertion.chunks.size == 1) {
                                    possibleInsertion.chunks[0]
                                } else {
                                    break
                                }
                                is Nested.HeadingAdjustment -> possibleInsertion.content
                                is Nested.IndentedRegion -> possibleInsertion.indented
                                is Nested.StripHeading -> possibleInsertion.content
                                is Nested.Invalid,
                                is Nested.Literal,
                                is Nested.Link,
                                is Nested.MarkerComment,
                                is Nested.SourcedMarkdown,
                                is Nested.SnippetAnchor,
                                -> break
                            }
                        }
                        if (possibleInsertion is Nested.SourcedMarkdown) {
                            val id = possibleInsertion.inlinedSnippet!!.id
                            val attributes = when (val loc = possibleInsertion.loc) {
                                is NestedInsertion -> loc.insertion.attributes
                                is SourceFileInsertionLocation -> emptyMap()
                            }
                            chunkById[id] = chunk to attributes
                            findNested(possibleInsertion.content)
                        } else {
                            findNested(chunk)
                        }
                    }
                } else if (mdChunk is Nested) {
                    mdChunk.children.forEach { findNested(it) }
                }
            }
            findNested(nested.content)
            fun findReversed(reversed: Reversed) {
                if (reversed is Reversed.Concatenation) {
                    reversed.chunks.forEach {
                        when (it) {
                            is Reversed.SourcedMarkdown -> {
                                val id = it.inlinedSnippetId
                                if (id != null) {
                                    reversedById[id] = it
                                }
                                findReversed(it.content)
                            }
                            is Reversed -> findReversed(it)
                            is Nested.Literal,
                            is Nested.MarkerComment,
                            -> Unit
                        }
                    }
                } else {
                    reversed.children.forEach { findReversed(it) }
                }
            }
            findReversed(reversed.content)

            val byId = mutableMapOf<
                SnippetId?,
                Triple<MdChunk?, Reversed.SourcedMarkdown?, InsertionAttributeMap>,
                >(null to Triple(nested, reversed, emptyMap()))
            for (id in (chunkById.keys + reversedById.keys)) {
                val (nested, attributes) =
                    chunkById[id] ?: (null to emptyMap())
                val reversed = reversedById[id]
                byId[id] = Triple(nested, reversed, attributes)
            }
            this.byId = byId.toMap()
        }
    }

    @Test
    fun urlsPreserved() {
        val snippets = SnippetsBeforeAndAfter(
            snippetContents = mapOf(
                "foo/bar" to
                    """
                    |<!-- snippet: foo/bar -->
                    |
                    |# Sub title
                    |
                    |Foo is like a bar.
                    |See also [issue#123].
                    |
                    """.trimMargin(),
            ),
            before = """
                |# Page Title
                |
                |$INSERTION_MARKER_CHAR foo/bar
                |
                |## Other sub title
                |
                |Blah blah
                |
            """.trimMargin(),
            after = """
                |# Page Title
                |
                |<!-- snippet: foo/bar -->
                |
                |## Sub title
                |
                |Foo is like a bar.
                |See also [issue#123]($PROJECT_GITHUB_URL/issues/123) and [issue#124]($PROJECT_GITHUB_URL/issues/124).
                |
                |<!-- /snippet: foo/bar -->
                |
                |## Other sub title
                |
                |Blah blah
                |
            """.trimMargin(),
        )

        assertReconciled(
            snippets = snippets,
            "foo/bar",
            reconciled = """
                |<!-- snippet: foo/bar -->
                |
                |# Sub title
                |
                |Foo is like a bar.
                |See also [issue#123] and [issue#124].
                |
            """.trimMargin(),
        )
    }

    @Test
    fun matchWhitespaceAroundSnippetMarker() {
        val snippets = SnippetsBeforeAndAfter(
            snippetContents = mapOf(
                "no-blank-lines" to
                    """
                        |<!-- snippet: no-blank-lines -->
                        |Before
                    """.trimMargin(),
                "one-blank-line" to
                    """
                        |<!-- snippet: one-blank-lines -->
                        |
                        |Before
                        |
                    """.trimMargin(),
                "two-blank-lines" to
                    """
                        |<!-- snippet: two-blank-lines -->
                        |
                        |
                        |Before
                        |
                        |
                    """.trimMargin(),
            ),
            before = """
                |$INSERTION_MARKER_CHAR no-blank-lines
                |
                |$INSERTION_MARKER_CHAR one-blank-line
                |
                |$INSERTION_MARKER_CHAR two-blank-lines
                |
            """.trimMargin(),
            after = """
                |<!-- snippet: no-blank-lines -->
                |
                |After
                |
                |<!-- /snippet: no-blank-lines -->
                |
                |<!-- snippet: one-blank-line -->
                |
                |After
                |
                |<!-- /snippet: one-blank-line -->
                |
                |<!-- snippet: two-blank-lines -->
                |
                |After
                |
                |<!-- /snippet: two-blank-lines -->
                |
            """.trimMargin(),
        )

        assertReconciled(
            snippets = snippets,
            abbreviatedId = "no-blank-lines",
            reconciled = """
                |<!-- snippet: no-blank-lines -->
                |After
            """.trimMargin(),
        )
        assertReconciled(
            snippets = snippets,
            abbreviatedId = "one-blank-line",
            reconciled = """
                |<!-- snippet: one-blank-line -->
                |
                |After
                |
            """.trimMargin(),
        )
        assertReconciled(
            snippets = snippets,
            abbreviatedId = "two-blank-lines",
            reconciled = """
                |<!-- snippet: two-blank-lines -->
                |
                |
                |After
                |
                |
            """.trimMargin(),
        )
    }

    private fun assertReconciled(
        snippets: SnippetsBeforeAndAfter,
        abbreviatedId: String,
        reconciled: String?,
        wantErrors: String = "",
    ) {
        val locallyDefined = findLocallyDefinedLinkTargets(snippets.reversed.toMarkdown())

        // If both nested and reversed are simple SourcedMarkdown(filePathForTest, ...) envelopes
        // around nested snippets, strip that off so that we can reconcile the snippets.
        val id = mdSnippetId(abbreviatedId)
        val (nested, reversed) = snippets.byId.getValue(id)
        assertTrue(
            nested != null && reversed != null,
            message = "id=$id, nested=$nested, reversed=$reversed",
        )
        val (ok, gotErrors) = withCapturingConsole(Log.Warn) { testConsole ->
            val problemTracker = ProblemTracker(testConsole)
            snippets.userDocsContent.reconcile(
                original = nested,
                reversed = reversed,
                contentBefore = MarkdownContent(snippets.snippetContents.getValue(abbreviatedId)),
                locallyDefined = locallyDefined.keys,
                problemTracker = problemTracker,
            ) { id, _ ->
                MarkdownContent(
                    "$INSERTION_MARKER_CHAR ${id.shortCanonString(false)}",
                )
            }
        }

        assertStringsEqual(wantErrors, gotErrors)

        if (reconciled != null) {
            assertStringsEqual(
                reconciled,
                reversed.toMarkdown().fileContent,
            )
        }
        assertEquals(ok, wantErrors.isEmpty())
    }
}
