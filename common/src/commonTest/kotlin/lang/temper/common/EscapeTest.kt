package lang.temper.common

import kotlin.test.Test

class EscapeTest {
    @Test
    fun temperEscapeOfEmpty() {
        assertStringsEqual(
            """
                |""
            """.trimMargin(),
            temperEscaper.escape(""),
        )
    }

    @Test
    fun temperEscapeOfMultiline() {
        assertStringsEqual(
            """
                |"123\n456\n789"
            """.trimMargin(),
            temperEscaper.escape("123\n456\n789"),
        )
    }

    @Test
    fun temperEscapeOfNestedQuotes() {
        val input = "a\"b\'c`d"
        assertStringsEqual(
            """
                |"a\"b'c`d"
            """.trimMargin(),
            temperEscaper.escape(input),
        )
        assertStringsEqual(
            """
                |'a"b\'c`d'
            """.trimMargin(),
            singleQuoteTemperEscaper.escape(input),
        )
        assertStringsEqual(
            """
                |`a"b'c\`d`
            """.trimMargin(),
            backtickTemperEscaper.escape(input),
        )
    }

    @Test
    fun temperEscapeOfAdjacentUnprintableChars() {
        // This string is not a well-formed Unicode string because of the orphaned surrogate.
        val str = invalidUnicodeString(
            """
                "a\uD800\u0000\u0001${'$'}b\u0002\u0003"
            """,
        ) // $ is a meta-character, \uD800 is an orphaned surrogate

        assertStringsEqual(
            """
                |"a\u{d800,0,1,24}b\u{2,3}"
            """.trimMargin(),
            temperEscaper.escape(str),
        )
    }

    @Test
    fun temperEscapeOfSupplementaryCodePoints() {
        assertStringsEqual(
            """
                |*\u{e0000,e0001,e0002}\*\u{e0003,e0004}*
            """.trimMargin(),
            temperEscaper.withQuote('*').escape(
                // We try to visibly escape non-graphical and unassigned code-points.
                // Code-points in the format (F*) and control (C*) categories are non-graphical.
                // www.unicode.org/L2/L1999/99191.htm
                // > the ranges U-000E0000..U-000E1000 are reserved for future format and
                // > control characters
                toStringViaBuilder { sb ->
                    sb.appendCodePoint(0xE0000)
                    sb.appendCodePoint(0xE0001)
                    sb.appendCodePoint(0xE0002)
                    sb.append('*') // Quote character needs escaping
                    sb.appendCodePoint(0xE0003)
                    sb.appendCodePoint(0xE0004)
                },
            ),
        )
    }

    @Test
    fun temperAscii() {
        @Suppress("SpellCheckingInspection")
        assertStringsEqual(
            listOf(
                "\"",
                """\u{0,1,2,3,4,5,6,7,8}\t\n\u{b,c}\r""",
                """\u{e,f,10,11,12,13,14,15,16,17,18,19,1a,1b,1c,1d,1e,1f}""",
                """ !\"#\u{24}%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ""",
                """[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~""",
                """\u{7f,80,81,82,83,84,85,86,87,88,89,8a,8b,8c,8d,8e,8f,""",
                """90,91,92,93,94,95,96,97,98,99,9a,9b,9c,9d,9e,9f,a0}""",
                """¡¢£¤¥¦§¨©ª«¬\u{ad}®¯°±²³´µ¶·¸¹º»¼½¾¿""",
                """ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ""",
                "\"",
            ).joinToString(""),
            temperEscaper.escape(
                toStringViaBuilder {
                    (0..MAX_ASCII).forEach { cp ->
                        it.append(cp.toChar())
                    }
                },
            ),
        )
    }

    @Test
    fun testUrlUnescape() {
        assertStringsEqual("", urlUnescape(""))
        assertStringsEqual("foo", urlUnescape("foo"))
        assertStringsEqual("foo bar baz", urlUnescape("foo+bar baz"))
        assertStringsEqual("%25/+\u1234", urlUnescape("%2525%2f%2b%e1%88%b4"))
        assertStringsEqual("%1", urlUnescape("%1"))
        assertStringsEqual("%", urlUnescape("%"))
    }
}
