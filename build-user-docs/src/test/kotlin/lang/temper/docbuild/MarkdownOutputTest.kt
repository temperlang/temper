package lang.temper.docbuild

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class MarkdownOutputTest {
    @Test
    fun empty() {
        assertStringsEqual(
            "",
            MarkdownOutput().run {
                this.markdownContent.fileContent
            },
        )
    }

    @Test
    fun justAString() {
        assertStringsEqual(
            "foo",
            MarkdownOutput().run {
                append("foo")
                this.markdownContent.fileContent
            },
        )
    }

    @Test
    fun breakAtEnd() {
        assertStringsEqual(
            "foo",
            MarkdownOutput().run {
                append("foo")
                breakBlock()
                this.markdownContent.fileContent
            },
        )
    }

    @Test
    fun breakAtStart() {
        assertStringsEqual(
            "foo",
            MarkdownOutput().run {
                breakBlock()
                append("foo")
                this.markdownContent.fileContent
            },
        )
    }

    @Test
    fun breakBetween() {
        assertStringsEqual(
            """
                |foo
                |
                |bar
            """.trimMargin(),
            MarkdownOutput().run {
                append("foo")
                breakBlock()
                append("bar")
                this.markdownContent.fileContent
            },
        )
    }

    @Test
    fun breakBetweenWithExplicitNewlinesAndIndentation() {
        assertStringsEqual(
            """
                |foo:
                |
                |  - bar
                |  - baz
                |
            """.trimMargin(),
            MarkdownOutput().run {
                append("foo:  \n")
                breakBlock()
                append("\n  - bar\n  - baz\n")
                this.markdownContent.fileContent
            },
        )
    }

    @Test
    fun extraneousBlankIndentedLines() {
        assertStringsEqual(
            """
                |foo
                |
                |bar
                |
            """.trimMargin(),
            MarkdownOutput().run {
                append("foo\n  \r\n\n")
                breakBlock()
                append("bar\n")
                this.markdownContent.fileContent
            },
        )
    }
}
