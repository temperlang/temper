package lang.temper.docbuild

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownIndentationLevelTest {
    private val content = MarkdownContent(
        // Capital letters mark interesting offsets
        """
            |# header (A) blah
            |
            |here is a list
            |1. list item (B)
            |2. (C)ontains nested list:
            |   - (D)odo
            |   - (E)gret
            |
            |     <!-- Another (H)TML Comment -->
            |
            |some code
            |
            |${'\t'}tabs_not_spaces(F)enceless
            |
            |more code
            |
            |    // no fences here
            |    (J)ust.(I)ndentation
            |
            |
            |```
            |(N)ot indented
            |```
            |
            |> ```
            |> (O)nce indented
            |> ```
            |
            |> (P)refixed (Q)uotation
            |> (R)epetitive nonsense
            |  (S)ubsequent line without prefix
            |> > (T)wice quoted
            |
            |  (Z)ero indentation for this line.
            |You can indent a paragraph if you want
            |but it won't do anything.
        """.trimMargin(),
    )

    @Test
    fun indentationLevels() {
        val levels = ('A'..'Z').mapNotNull { letter ->
            val marker = "($letter)"
            val offset = content.fileContent.indexOf(marker)
            if (offset >= 0) {
                letter to markdownIndentationLevel(content, offset = offset)
            } else {
                null
            }
        }.toMap()
        assertEquals(
            mapOf(
                'A' to "",
                'B' to "   ",
                'C' to "   ",
                'D' to "     ",
                'E' to "     ",
                'F' to "\t",
                'H' to "     ",
                'I' to "    ",
                'J' to "    ",
                'N' to "",
                'O' to "> ",
                'P' to "> ",
                'Q' to "> ",
                'R' to "> ",
                'S' to "> ",
                'T' to "> > ",
                'Z' to "",
            ),
            levels,
        )
    }
}
