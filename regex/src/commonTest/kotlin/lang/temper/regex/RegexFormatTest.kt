package lang.temper.regex

import lang.temper.common.decodeUtf16Iter
import kotlin.test.Test
import kotlin.test.assertEquals

class RegexFormatTest {
    @Test
    fun dashRange() {
        val pattern = CodeRange('-'.code, '}'.code)
        assertEquals("""[\--\}]""", pattern.formatToString())
    }

    @Test
    fun dashSetDotnet() {
        val range = CodeRange('-'.code, '}'.code)
        assertEquals("""[\-\.-\}]""", range.formatToString(DotnetRegexFormatter))
        val set = CodeSet(listOf(range, CodePoints("ab-")), false)
        assertEquals("""[\-\.-\}ab]""", set.formatToString(DotnetRegexFormatter))
    }

    @Test
    fun dotnetSupplementaryCodePoints() {
        val pattern = Seq(
            listOf(
                Dot,
                CodePoints("ab🌊"),
                // Include a supplementary code range outside of a code set.
                CodeRange(0x20000, 0x40001),
                CodeSet(
                    listOf(
                        // [65]∪[67]∪[97-98]∪[512-768]∪[4660-127755]∪[127757]∪[131072-262145]
                        // [A]∪[C]∪[a-b]∪[Ȁ-300]∪[ሴ-🌋]∪[🌍]∪[20000-40001]
                        // [A]∪[AC]∪[a-b]∪[Ȁ-300]∪[1234-{D83C DF0B}]∪[{D83C DF0D}]∪[{D840 DC00}-{D8C0 DC01}]
                        CodePoints("ACab🌊🌍"),
                        CodeRange(0x200, 0x300),
                        CodeRange(0x1234, decodeUtf16Iter("🌋").first()),
                        CodeRange(0x20000, 0x40001),
                    ),
                    negated = false,
                ),
                CodeSet(listOf(CodePoints("a")), negated = true),
            ),
        )
        val start = """(?:.|[\uD800-\uDBFF][\uDC00-\uDFFF])ab${"\uD83C\uDF0A"}"""
        val codeRange = listOf(
            """\uD840[\uDC00-\uFFFF]""",
            """[\uD841-\uD8BF][\x00-\uFFFF]""",
            """\uD8C0[\x00-\uDC01]""",
        ).joinToString("|")
        val codeSet = listOf(
            """[ACa-bȀ-̀]""",
            """🌍""",
            """[ሴ-\uFFFF]""",
            """\uD800[\uDC00-\uFFFF]""",
            """[\uD801-\uD83B][\x00-\uFFFF]""",
            """\uD83C[\x00-\uDF0B]""",
            """\uD840[\uDC00-\uFFFF]""",
            """[\uD841-\uD8BF][\x00-\uFFFF]""",
            """\uD8C0[\x00-\uDC01]""",
        ).joinToString("|")
        val negated = listOf(
            """[\x00-`]""",
            """[b-\uFFFF]""",
            """[\uD800-\uDFFF][\uD800-\uDFFF]""",
        ).joinToString("|")
        val expected = "$start(?:$codeRange)(?:$codeSet)(?:$negated)"
        assertEquals(expected, pattern.formatToString(DotnetRegexFormatter))
    }

    @Test
    fun messy() {
        val expected = """abc[^\s0-9_\-=]\b(?:\w|(?:(?:a.|cba))*)"""
        val actual = messyPattern.formatToString()
        assertEquals(expected, actual)
    }
}

val messyPattern = Seq(
    listOf(
        CodePoints("abc"),
        CodeSet(listOf(Space, CodeRange('0'.code, '9'.code), CodePoints("_-=")), negated = true),
        WordBoundary,
        Or(
            listOf(
                Word,
                Repeat(
                    Or(listOf(Seq(listOf(CodePoints("a"), Dot)), CodePoints("cba"))),
                    min = 0,
                    max = null,
                    reluctant = false,
                ),
            ),
        ),
    ),
)
