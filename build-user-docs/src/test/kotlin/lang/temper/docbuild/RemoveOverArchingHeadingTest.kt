package lang.temper.docbuild

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RemoveOverArchingHeadingTest {
    @Test
    fun empty() {
        assertEquals(
            "",
            removeOverArchingHeading(MarkdownContent("")),
        )
    }

    @Test
    fun noHeading() {
        assertEquals(
            """
            |Foo Bar
            |
            """.trimMargin(),
            removeOverArchingHeading(
                MarkdownContent(
                    """
                    |Foo Bar
                    |
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun removed() {
        assertEquals(
            """
            |Content
            """.trimMargin(),
            removeOverArchingHeading(
                MarkdownContent(
                    """
                    |## OverArching Heading
                    |
                    |Content
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun lowerAfter() {
        assertEquals(
            """
            |### Foo Title
            |Foo
            |
            |## Bar Title
            |Bar
            """.trimMargin(),
            removeOverArchingHeading(
                MarkdownContent(
                    """
                    |### Foo Title
                    |Foo
                    |
                    |## Bar Title
                    |Bar
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun sameAfter() {
        assertEquals(
            """
            |## Foo Title
            |About Foo
            |
            |### Foo Details
            |More about Foo
            |
            |## Bar Title
            |About Bar
            |
            """.trimMargin(),
            removeOverArchingHeading(
                MarkdownContent(
                    """
                    |## Foo Title
                    |About Foo
                    |
                    |### Foo Details
                    |More about Foo
                    |
                    |## Bar Title
                    |About Bar
                    |
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun contentBefore() {
        assertEquals(
            """
            |before
            |
            |# Header
            |Relevant to header
            """.trimMargin(),
            removeOverArchingHeading(
                MarkdownContent(
                    """
                    |before
                    |
                    |# Header
                    |Relevant to header
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun regression1() {
        val content = """
            |<!-- snippet: syntax/multi-quoted-strings -->
            |
            |<a name="syntax&#45;multi&#45;quoted&#45;strings" class="snippet-anchor-name"></a>
            |
            |# Multi-quoted strings
            |
        """.trimMargin()
        assertNotNull(findOverarchingHeading(MarkdownContent(content)))
    }
}
