package lang.temper.docbuild

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class MarkdownEscapeTest {
    @Test
    fun escape() {
        assertStringsEqual("foo", MarkdownEscape.escape("foo"))
        assertStringsEqual("\\*", MarkdownEscape.escape("*"))
        assertStringsEqual("&lt;b&gt;", MarkdownEscape.escape("<b>"))
        assertStringsEqual("\\\\\\[b\\\\\\]", MarkdownEscape.escape("\\[b\\]"))
        assertStringsEqual("foo\\\nbar", MarkdownEscape.escape("foo\r\nbar"))
    }

    @Test
    fun codeSpan() {
        assertStringsEqual(
            "",
            MarkdownEscape.codeSpan(""),
        )
        assertStringsEqual(
            "`foo`",
            MarkdownEscape.codeSpan("foo"),
        )
        assertStringsEqual(
            "`` ` ``",
            MarkdownEscape.codeSpan("`"),
        )
        assertStringsEqual(
            "`` foo ` bar ``",
            MarkdownEscape.codeSpan("foo ` bar"),
        )
        assertStringsEqual(
            "``` `` ```",
            MarkdownEscape.codeSpan("``"),
        )
        assertStringsEqual(
            "```   ``   ```",
            MarkdownEscape.codeSpan("  ``  "),
        )
        assertStringsEqual(
            "` a`",
            MarkdownEscape.codeSpan(" a"),
        )
        assertStringsEqual(
            "`foo  bar baz`",
            MarkdownEscape.codeSpan("foo  bar baz"),
        )
        assertStringsEqual(
            "`` foo\\`baz ``",
            MarkdownEscape.codeSpan("foo\\`baz"),
        )
        assertStringsEqual(
            "`*`",
            MarkdownEscape.codeSpan("*"),
        )
    }
}
