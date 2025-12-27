package lang.temper.docbuild

import lang.temper.common.asciiLowerCase
import lang.temper.common.assertStringsEqual
import kotlin.test.Test
import kotlin.test.assertEquals

class RewriteLinkTargetsTest {
    private val contentWithLinks = MarkdownContent(
        """
        |A [link][FOO] and another
        |- [FOO]
        |- and a
        |- [BAR]
        |- and not a reference link:
        |- [FOO](FOO)
        |
        |> [BAZ]
        |
        |Some links have meta-characters: [stars/**/_foo_]
        |
        |[BAR]: bar.gif
        """.trimMargin(),
    )

    @Test
    fun findLocallyDefinedTest() {
        val locallyDefined = findLocallyDefinedLinkTargets(contentWithLinks)
        assertEquals(
            setOf("[BAR]"),
            locallyDefined.keys,
        )
    }

    @Test
    fun findLinksTest() {
        val src = contentWithLinks.fileContent
        val links = findLinks(contentWithLinks)
        assertEquals(
            listOf(
                "[link][FOO], linked=[link], kind=SquareBracketed, target=FOO",
                "[FOO], linked=null, kind=SquareBracketed, target=FOO",
                "[BAR], linked=null, kind=SquareBracketed, target=BAR",
                "[FOO](FOO), linked=[FOO], kind=Parenthetical, target=FOO",
                "[BAZ], linked=null, kind=SquareBracketed, target=BAZ",
                "[stars/**/_foo_], linked=null, kind=SquareBracketed, target=stars/**/_foo_",
            ),
            links.map { it.stringifyRanges(src) }.map { link ->
                "${
                    link.whole
                }, linked=${
                    link.linked?.let { "[$it]" }
                }, kind=${
                    link.kind
                }, target=${
                    link.target
                }"
            },
        )
    }

    @Test
    fun rewriteTest() {
        val rewritten = rewriteLinkTargets(contentWithLinks) { _, target ->
            val lowerTarget = target.asciiLowerCase()
            "suggested $lowerTarget" to "(./$lowerTarget.txt)"
        }
        assertStringsEqual(
            """
                |A [link](./foo.txt) and another
                |- [suggested foo](./foo.txt)
                |- and a
                |- [BAR]
                |- and not a reference link:
                |- [FOO](FOO)
                |
                |> [suggested baz](./baz.txt)
                |
                |Some links have meta-characters: [suggested stars/**/_foo_](./stars/**/_foo_.txt)
                |
                |[BAR]: bar.gif
            """.trimMargin(),
            rewritten,
        )
    }
}
