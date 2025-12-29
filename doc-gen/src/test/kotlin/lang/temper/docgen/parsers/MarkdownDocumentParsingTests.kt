package lang.temper.docgen.parsers

import lang.temper.docgen.BlockQuoteFragment
import lang.temper.docgen.Code
import lang.temper.docgen.CodeFragment
import lang.temper.docgen.Prose
import lang.temper.name.LanguageLabel
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * This only has tests for the basic code block parsing see other files for tests of other areas
 */
class MarkdownDocumentParsingTests {
    private val parser = MarkdownDocumentParser()

    @Test
    fun parsesSampleWithNoCodeBlocks() {
        val blocks = parser.parse("""* foo""")

        assert(blocks.codeFragments.isEmpty())
        assertEquals(null, blocks.first)
    }

    @Test
    fun extractsCodeFence() {
        val blocks = parser.parse(
            """
            |```
            |foo
            |```
            """.trimMargin(),
        )

        assert(blocks.codeFragments.size == 1)
        assertEquals("foo", blocks.first?.sourceText)
    }

    @Test
    fun extractsMultiLineCodeFence() {
        val blocks = parser.parse(
            """
            |```
            |foo
            |bar
            |```
            """.trimMargin(),
        )

        assert(blocks.codeFragments.size == 1)
        assertEquals(
            """foo
            |bar
            """.trimMargin(),
            blocks.first?.sourceText,
        )
    }

    @Test
    fun extractsCodeSpan() {
        val blocks = parser.parse("""`foo`""")

        assert(blocks.codeFragments.size == 1)
        assertEquals("foo", blocks.first?.sourceText)
        assert(blocks.first?.isTemperCode == false)
    }

    @Test
    fun extractsCodeBlockSpaces() {
        val blocks = parser.parse("""    foo""")

        assert(blocks.codeFragments.size == 1)
        assertEquals("foo", blocks.first?.sourceText)
    }

    @Test
    fun multiLineCodeBlockTrims() {
        val blocks = parser.parse(
            """    foo
            |    bar
            """.trimMargin(),
        )

        assert(blocks.codeFragments.size == 1)
        assertEquals(
            """foo
            |bar
            """.trimMargin(),
            blocks.first?.sourceText,
        )
    }

    @Ignore("The underlying parser doesn't seem to support this not working around it right now")
    @Test
    fun extractsCodeBlockTab() {
        val blocks = parser.parse("""   foo""")

        assertEquals(1, blocks.codeFragments.size)
        assertEquals("foo", blocks.first?.sourceText)
    }

    @Test
    fun extractsCodeBlocks() {
        val firstBlock = "foo"
        val secondBlock = "baz"
        val str = """`$firstBlock`bar`$secondBlock`"""
        val blocks = parser.parse(str)

        assert(blocks.codeFragments.size == 2)
        assertEquals(firstBlock, blocks.first?.sourceText)
        assertEquals(secondBlock, blocks.first?.nextCodeFragment?.sourceText)
    }

    @Test
    fun fragmentRewriting() {
        val firstBlock = "foo"
        val secondBlock = "baz"
        val str = """`$firstBlock`bar`$secondBlock`"""
        val blocks = parser.parse(str)

        blocks.supplant(blocks.first ?: fail("fragment was not parsed"), listOf(Prose("qux")))

        assertEquals("""quxbar`$secondBlock`""", blocks.document)
    }

    @Test
    fun fragmentRewritingCodeBlocks() {
        val firstBlock = "foo"
        val secondBlock = "baz"
        val str = """```
            |$firstBlock
            |```
            |bar```$secondBlock```
        """.trimMargin()
        val blocks = parser.parse(str)

        blocks.supplant(blocks.first ?: fail("fragment was not parsed"), listOf(Code("qux", LanguageLabel("js"))))

        assertEquals(
            """```js
            |qux
            |```
            |bar```$secondBlock```
            """.trimMargin(),
            blocks.document,
        )
    }

    @Test
    fun associatesCodeFragments() {
        val blocks = parser.parse("""`foo`bar`baz`""")

        assertEquals("baz", blocks.first?.nextCodeFragment?.sourceText)
    }

    @Test
    fun excludesOtherLanguages() {
        // AFAICT this is only expressible in the code fence syntax
        val blocks = parser.parse(
            """
            |```js
            |foo
            |bar
            |```
            """.trimMargin(),
        )

        assert(blocks.codeFragments.isEmpty())
    }

    @Test
    fun differentiatesCodeFragmentsWithoutExplicitLanguageInfo() {
        val blocks = parser.parse(
            """
            |```temper
            |foo
            |```
            """.trimMargin(),
        )

        assert(blocks.first?.isTemperCode == true)
    }

    @Test
    fun codeFenceInfoStringExtra() {
        val blocks = parser.parse(
            """
            |```temper hello
            |foo
            |```
            """.trimMargin(),
        )

        assert(blocks.first?.isTemperCode == true)
    }

    @Test
    fun multipleReplacements() {
        val firstBlock = "foo"
        val secondBlock = "baz"
        val str = """`$firstBlock`bar`$secondBlock`"""
        val blocks = parser.parse(str)

        blocks.supplant(blocks.first ?: fail("fragment was not parsed"), listOf(Prose("foofoo")))
        // The first call to supplant changed first to keep it in sync
        blocks.supplant(blocks.first ?: fail("fragment was not parsed"), listOf(Code("bust", LanguageLabel(""))))

        assertEquals(
            """
            |foofoobar```
            |bust
            |```
            """.trimMargin(),
            blocks.document,
        )
    }

    @Test
    fun parsesBlockQuotes() {
        val md = """> lorem ipsum"""

        val result = parser.parse(md)

        assert(result.fragments.first() is BlockQuoteFragment)
        assertEquals("lorem ipsum", result.fragments.first().sourceText)
    }

    @Test
    fun multiLineBlockQuote() {
        val md = """> lorem
            |> ipsum
        """.trimMargin()

        val result = parser.parse(md)

        // Don't expect the content of a multiline block quote since that isn't a marker we need
        // for replacement
        assert(result.fragments.isEmpty())
    }

    @Test
    fun nestedBlockQuote() {
        val md = """> > lorem ipsum"""

        val result = parser.parse(md)

        assert(result.fragments.first() is BlockQuoteFragment)
        assertEquals("lorem ipsum", result.fragments.first().sourceText)
    }

    @Test
    fun multiLineBlockQuoteVariety() {
        val md = """> lorem
            |> > ipsum
        """.trimMargin()

        val result = parser.parse(md)

        // TODO I don't understand why we want this result. Just matching earlier behavior.
        assertContentEquals(listOf("ipsum", "lorem"), result.fragments.map { it.sourceText })
    }

    @Test
    fun codeNestedInBlockQuote() {
        val md = """> `foo`"""

        val result = parser.parse(md)

        assertFalse(result.fragments.any { it is BlockQuoteFragment })
        assert(result.fragments.first() is CodeFragment<*>)
    }

    @Test
    fun supplantingBlockQuote() {
        val md = """> lorem ipsum"""

        val result = parser.parse(md)
        val blockQuoteFragment = result.fragments.first() as BlockQuoteFragment
        result.supplant(blockQuoteFragment, listOf(Prose("foo")))

        assert(result.fragments.isEmpty())
        assertEquals("foo", result.document)
    }

    @Test
    fun multipleSupplant() {
        val md = """> lorem ipsum"""

        val result = parser.parse(md)
        val blockQuoteFragment = result.fragments.first() as BlockQuoteFragment
        result.supplant(blockQuoteFragment, listOf(Prose("foo"), Code("bar", LanguageLabel("js"))))

        assert(result.fragments.isEmpty())
        assertEquals(
            """foo
            |```js
            |bar
            |```
            """.trimMargin(),
            result.document,
        )
    }

    @Test
    fun supplantingCodeWithBackticks() {
        val md = """> lorem ipsum"""

        val result = parser.parse(md)
        val blockQuoteFragment = result.fragments.first() as BlockQuoteFragment
        result.supplant(blockQuoteFragment, listOf(Code("```", LanguageLabel("stuff"))))

        assert(result.fragments.isEmpty())
        assertEquals(
            """````stuff
            |```
            |````
            """.trimMargin(),
            result.document,
        )
    }

    @Test
    fun prepend() {
        val doc = parser.parse("`foo`")

        doc.prepend("stuff")

        assertEquals(
            """stuff
            |`foo`
            """.trimMargin(),
            doc.document,
        )
        // ensure the fragments moved correctly
        doc.supplant(doc.first ?: fail(), listOf(Prose("bar")))
        assertEquals(
            """stuff
            |bar
            """.trimMargin(),
            doc.document,
        )
    }
}
