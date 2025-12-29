package lang.temper.lexer

import lang.temper.common.assertStringsEqual
import kotlin.test.Test

class LineCommentContentTest {
    private fun assertCommentContent(
        want: String,
        input: String,
    ) = assertStringsEqual(want, lineCommentContent(input))

    @Test
    fun simpleLineComment() = assertCommentContent(
        want = "foo bar",
        input = "// foo bar\n",
    )

    @Test
    fun moreThanTwoSlashesAtStart() = assertCommentContent(
        want = "foo bar",
        input = "// foo bar\n",
    )

    @Test
    fun noLeadingSpaces() = assertCommentContent(
        want = "foo bar",
        input = "//foo bar",
    )

    @Test
    fun trailingSpaces() = assertCommentContent(
        want = "foo bar",
        input = "// foo bar   \n",
    )

    @Test
    fun horizontalRule() = assertCommentContent(
        want = "",
        input = "/////////////////////////////\n",
    )

    @Test
    fun slashesInContent() = assertCommentContent(
        want = "/regex/i is case-insensitive.",
        input = "// /regex/i is case-insensitive.\n",
    )

    @Test
    fun multilineCommentContent() = assertCommentContent(
        want = """
            |Line 1
            |Line 2
            |
            |Line 4
        """.trimMargin(),
        input = """
            |// Line 1
            |// Line 2  ${
            "" // trailing spaces on this line
        }
            |//
            |// Line 4
        """.trimMargin(),
    )

    @Test
    fun italicizedBox() = assertCommentContent(
        want = """
            |Line 1
            |Line 2
            |
            |Line 4
        """.trimMargin(),
        input = """
            |/////////////
            |// Line 1  //
            |// Line 2  //
            |//         //
            |// Line 4  //
            |/////////////
        """.trimMargin(),
    )
}
