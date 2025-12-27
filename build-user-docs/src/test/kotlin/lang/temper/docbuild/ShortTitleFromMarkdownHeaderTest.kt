package lang.temper.docbuild

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortTitleFromMarkdownHeaderTest {
    @Test
    fun nothing() {
        assertEquals(
            null,
            shortTitleFromMarkdownHeader("No headers here."),
        )
    }

    @Test
    fun hasAtxHeader1() {
        assertEquals(
            "My Header",
            shortTitleFromMarkdownHeader(
                """
                    |# My Header
                    |
                    |Some none header text
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun hasSetextHeader() {
        assertEquals(
            "My Header",
            shortTitleFromMarkdownHeader(
                """
                    |My Header
                    |=========
                    |
                    |Some none header text
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun hasHeader2() {
        assertEquals(
            "A level-*two* header",
            shortTitleFromMarkdownHeader(
                """
                    |## A level-*two* header
                    |
                    |Some none header text
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun duelingHeaders() {
        assertEquals(
            null,
            shortTitleFromMarkdownHeader(
                """
                    |## Foo
                    |
                    |Info about Foo
                    |
                    |## Bar
                    |
                    |Info about Bar
                """.trimMargin(),
            ),
        )
    }

    @Ignore // Do we need some markdown config to parse those specially
    @Test
    fun leaveAnchorSpecsAlone() {
        assertEquals(
            "Some Text",
            shortTitleFromMarkdownHeader(
                // https://talk.commonmark.org/t/anchors-in-markdown/247/17
                """
                    |## Some Text {#not-text}
                    |
                    |Lorem Ipsum
                """.trimMargin(),
            ),
        )
    }
}
