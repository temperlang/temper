package lang.temper.docgen.parsers

import lang.temper.docgen.LinkFragment
import lang.temper.docgen.Prose
import lang.temper.docgen.SupplantableFragment
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkProcessingTests {
    private val parser = MarkdownDocumentParser()

    @Test
    fun parsesLinks() {
        val doc = parser.parse("[Text](#not-code)")

        assertEquals(1, doc.fragments.size)
        assertEquals("[Text](#not-code)", doc.fragments[0].sourceText)
        assertEquals("Text", (doc.fragments[0] as LinkFragment).linkText)
        assertEquals("#not-code", (doc.fragments[0] as LinkFragment).linkTarget)
    }

    @Test
    fun parsesLinksWithName() {
        val doc = parser.parse("""[label](#not-code "Text")""")

        assertEquals(1, doc.fragments.size)
        assertEquals("""[label](#not-code "Text")""", doc.fragments[0].sourceText)
        assertEquals("label", (doc.fragments[0] as LinkFragment).linkText)
        assertEquals("#not-code", (doc.fragments[0] as LinkFragment).linkTarget)
        assertEquals("Text", (doc.fragments[0] as LinkFragment).altText)
    }

    @Test
    fun parsesReferenceStyleLinks() {
        val doc = parser.parse(
            """[label][1]
            |
            |[1]: #not-code
            """.trimMargin(),
        )

        assertEquals(1, doc.fragments.size)
        assertEquals(
            """[label][1]
            |
            |[1]: #not-code
            """.trimMargin(),
            doc.fragments[0].sourceText,
        )
        assertEquals("label", (doc.fragments[0] as LinkFragment).linkText)
        assertEquals("#not-code", (doc.fragments[0] as LinkFragment).linkTarget)
    }

    @Test
    fun parsesReferenceStyleLinksWithIntermediateContent() {
        val doc = parser.parse(
            """[label][1]
            | - foo
            | - bar
            | 
            |[1]: #not-code
            """.trimMargin(),
        )

        assertEquals(1, doc.fragments.size)
        assertEquals("label", (doc.fragments[0] as LinkFragment).linkText)
        assertEquals("#not-code", (doc.fragments[0] as LinkFragment).linkTarget)
        assertEquals(
            """[label][1]
            |
            |[1]: #not-code
            """.trimMargin(),
            doc.fragments[0].sourceText,
        )
    }

    @Ignore("Because https://github.com/commonmark/commonmark-java/issues/292")
    @Test
    fun parsesReferenceStyleLinksWithName() {
        val doc = parser.parse(
            """[label][1]
            |
            |[1]: #not-code "Text" """.trimMargin(),
        )

        assertEquals(1, doc.fragments.size)
        assertEquals("label", (doc.fragments[0] as LinkFragment).linkText)
        assertEquals("Text", (doc.fragments[0] as LinkFragment).altText)
        assertEquals("#not-code", (doc.fragments[0] as LinkFragment).linkTarget)
        assertEquals(
            """[label][1]
            |
            |[1]: #not-code "Text"
            """.trimMargin(),
            doc.fragments[0].sourceText,
        )
    }

    @Test
    fun replacesLinks() {
        val doc = parser.parse("[Text](#not-code)")
        val replacement = "[otherText](#elsewhere)"

        doc.supplant(doc.fragments[0] as SupplantableFragment, listOf(Prose(replacement)))

        assertEquals(replacement, doc.document)
        assert(doc.fragments.isEmpty())
    }

    @Test
    fun conflictingLinkDefinitions() {
        // This may be specific to the github flavor of markdown
        // see https://github.github.com/gfm/#link-labe
        val doc = parser.parse(
            """[label][1]
            |
            |[1]: #not-code
            |[1]: elsewhere
            """.trimMargin(),
        )

        assertEquals("#not-code", (doc.fragments[0] as LinkFragment).linkTarget)

        assertEquals(
            """[label][1]
            |
            |[1]: #not-code
            """.trimMargin(),
            doc.fragments[0].sourceText,
        )
    }
}
