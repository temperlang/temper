package lang.temper.docgen.parsers

import lang.temper.docgen.BlockQuoteFragment
import lang.temper.docgen.LinkFragment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Suppress("MagicNumber")
class ParserAcceptanceTests {
    private val parser = MarkdownDocumentParser()

    @Test
    fun parsesDiffUtilsOverview() {
        val fileContent = ParserAcceptanceTests::class.java.getResource("/examples/Overview.md").readText()

        val result = parser.parse(fileContent)

        val linkFragments = result.fragments.filterIsInstance<LinkFragment>()

        assertEquals(fileContent, result.document)
        assertEquals(1, result.codeFragments.size)
        assertEquals(2, linkFragments.size)
        assertEquals(1, result.fragments.filterIsInstance<BlockQuoteFragment>().size)
        assertEquals(4, result.fragments.size)
    }

    @Test
    fun parsesDiffUtilsExamples() {
        val fileContent = ParserAcceptanceTests::class.java.getResource("/examples/Examples.md").readText()

        val result = parser.parse(fileContent)

        val linkFragments = result.fragments.filterIsInstance<LinkFragment>()

        assertEquals(fileContent, result.document)
        assertEquals(7, result.codeFragments.size)
        assertFalse {
            result.codeFragments.last().isTemperCode
        }
        assertEquals(0, linkFragments.size)
        assertEquals(7, result.fragments.size)
    }
}
