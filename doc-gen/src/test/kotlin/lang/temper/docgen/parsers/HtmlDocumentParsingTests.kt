package lang.temper.docgen.parsers

import lang.temper.common.assertStringsEqual
import lang.temper.docgen.BlockQuoteFragment
import lang.temper.docgen.Code
import lang.temper.docgen.CodeFragment
import lang.temper.docgen.Prose
import lang.temper.name.LanguageLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class HtmlDocumentParsingTests {
    private val parser = HtmlDocumentParser()

    @Test
    fun unparsableSample() {
        val blocks = parser.parse("""<div>foo</di>""")

        // Not sure an exception wouldn't be more appropriate but sticking with this for now.
        assert(blocks.codeFragments.isEmpty())
        assert(blocks.first == null)
    }

    @Test
    fun parsesSampleWithNoCodeBlocks() {
        val blocks = parser.parse("""<div>foo</div>""")

        assert(blocks.codeFragments.isEmpty())
        assert(blocks.first == null)
    }

    @Test
    fun extractsCodeBlock() {
        val blocks = parser.parse("""<code class="language-temper">foo</code>""")

        assert(blocks.codeFragments.size == 1)
        assert(blocks.first?.sourceText == "foo")
    }

    @Test
    fun extractsCodeBlocks() {
        val blocks = parser.parse(
            """<code class="language-temper">foo</code><div>bar</div><code class="language-temper">baz</code>""",
        )

        assert(blocks.codeFragments.size == 2)
    }

    @Test
    fun associatesBlocks() {
        val blocks = parser.parse(
            """<code class="language-temper">foo</code><div>bar</div><code class="language-temper">baz</code>""",
        )

        assert(blocks.first?.nextCodeFragment?.sourceText == "baz")
    }

    @Test
    fun codeBlockWithDifferentLanguageIsIgnored() {
        val blocks = parser.parse("""<code class="language-js">foo</code>""")

        assert(blocks.codeFragments.isEmpty())
    }

    @Test
    fun differentiatesCodeFragmentsWithoutExplicitLanguageInfo() {
        val blocks = parser.parse("""<code>foo</code>""")

        assert(blocks.first?.isTemperCode == false)
    }

    @Test
    fun multipleClasses() {
        val blocks = parser.parse("""<code class="language-temper bar">foo</code>""")

        assert(blocks.codeFragments.size == 1)
        assert(blocks.first?.sourceText == "foo")
    }

    @Test
    fun htmlEscapes() {
        val blocks = parser.parse(
            """<code class="language-temper">if (x &lt; y) { console.log(&quot;x is less&quot;); }</code>""",
        )

        assert(blocks.first != null)
        assertEquals("""if (x < y) { console.log("x is less"); }""", blocks.first?.sourceText)
    }

    @Test
    fun fragmentRewriting() {
        val blocks = parser.parse(
            """<code class="language-temper">foo</code><div>bar</div><code class="language-temper">baz</code>""",
        )

        blocks.supplant(blocks.first ?: fail("fragment was not parsed"), listOf(Code("qux", LanguageLabel("temper"))))

        val expected = parser.parse(
            """<code class="language-temper">qux</code><div>bar</div><code class="language-temper">baz</code>""",
        )

        assertEquals(expected.document.html, blocks.document.html)
    }

    @Test
    fun parsesBlockQuotes() {
        // Should there be a version of this for <q>?
        val md = """<blockquote>lorem ipsum</blockquote>"""

        val result = parser.parse(md)

        assert(result.fragments.first() is BlockQuoteFragment)
        assertEquals("lorem ipsum", result.fragments.first().sourceText)
    }

    @Test
    fun multiLineBlockQuote() {
        val md = """<blockquote>lorem<br/>ipsum</blockquote>""".trimMargin()

        val result = parser.parse(md)

        // Don't expect the content of a multiline block quote since that isn't a marker we need for replacement
        assert(result.fragments.isEmpty())
    }

    @Test
    fun nestedBlockQuote() {
        val md = """<blockquote><blockquote>lorem ipsum</blockquote></blockquote>"""

        val result = parser.parse(md)

        assert(result.fragments.first() is BlockQuoteFragment)
        assertEquals("lorem ipsum", result.fragments.first().sourceText)
    }

    @Test
    fun codeNestedInBlockQuote() {
        val md = """<blockquote><code>foo</code></blockquote>"""

        val result = parser.parse(md)

        assertFalse(result.fragments.any { it is BlockQuoteFragment })
        assert(result.fragments.first() is CodeFragment<*>)
    }

    @Test
    fun supplantingBlockQuote() {
        val input = """<blockquote>lorem ipsum</blockquote>"""

        val result = parser.parse(input)
        val blockQuoteFragment = result.fragments.first() as BlockQuoteFragment
        result.supplant(blockQuoteFragment, listOf(Prose("foo")))

        assert(result.fragments.isEmpty())
        val expected = parser.parse(
            """<html>
 <head></head>
 <body>
  foo
 </body>
</html>
            """,
        )
        assertEquals(expected.document.text, result.document.text)
    }

    @Test
    fun multipleSupplant() {
        val input = """<blockquote>lorem ipsum</blockquote>"""

        val result = parser.parse(input)
        val blockQuoteFragment = result.fragments.first() as BlockQuoteFragment
        result.supplant(blockQuoteFragment, listOf(Prose("foo"), Code("bar", LanguageLabel("js"))))

        assert(result.fragments.isEmpty())
        assertStringsEqual(
            """
            |<html>
            | <head></head>
            | <body>
            |  foo <code class="language-js">bar</code>
            | </body>
            |</html>
            """.trimMargin(),
            result.document.outerHtml,
        )
    }

    @Test
    fun prepend() {
        val doc = parser.parse("")

        doc.prepend("stuff")

        assertEquals(
            """
            |<html>
            | <head>
            |  stuff
            | </head>
            | <body></body>
            |</html>
            """.trimMargin(),
            doc.document.html,
        )
    }
}
