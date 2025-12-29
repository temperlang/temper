package lang.temper.log

import lang.temper.common.TextOutput
import lang.temper.common.TtyCode
import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.common.temperEscaper
import lang.temper.common.testCodeLocation
import kotlin.test.Test
import kotlin.test.assertEquals

// Turn this to true if you want to see TTY output in the console.
private const val ALSO_LOG_TO_CONSOLE = false

class ExcerptTest {

    @Test
    fun smallExcerpt() = assertExcerpt(
        input = "123 + 234",
        //       0123456789
        left = 4,
        right = 5,
        want = listOf(
            TtyCode.FgGrey, "1: ", TtyCode.FgDefault,
            "123 ",
            TtyCode.Bold, "+", TtyCode.NormalColorIntensity,
            " 234",
            "\n",
            /*
            "1: 123 + 234",
             */
            "       ⇧",
            "\n",
        ),
    )

    @Test
    fun longerSingleLineExcerpt() = assertExcerpt(
        input = "longVariableName = foo + (bar * baz);",
        //       01234567890123456789012345678901234567
        //                 1         2         3
        left = 26,
        right = 35,
        want = listOf(
            TtyCode.FgGrey, "1: ", TtyCode.FgDefault,
            "riableName = foo + (",
            TtyCode.Bold, "bar * baz", TtyCode.NormalColorIntensity,
            ");",
            "\n",
            /*
            "1: riableName = foo + (bar * baz);" */
            "                       ┗━━━━━━━┛",
            "\n",
        ),
    )

    @Test
    fun longSnippetAbbreviatedInTheMiddle() {
        val input = (
            "This is a really long line which we're going to excerpt most of which is pretty " +
                "boring.  Sorry about this really, but don't feel compelled to read it.  " +
                "I could've just Lorem Ipsumed this; I regret my life decisions."
            )
        assertExcerpt(
            input = input,
            left = input.indexOf(" is") + 1,
            right = input.lastIndexOf("life") - 1,
            want = listOf(
                TtyCode.FgGrey, "1: ", TtyCode.FgDefault,
                "This ",
                TtyCode.Bold,
                "is a really long line which we're going to excerp",
                TtyCode.NormalColorIntensity,
                TtyCode.FgGrey, "⋯", TtyCode.FgDefault,
                TtyCode.Bold,
                ".  I could've just Lorem Ipsumed this; I regret my",
                TtyCode.NormalColorIntensity,
                " life decisions.",
                "\n",
                "        ┗" + "━".repeat(98) + "┛",
                "\n",
            ),
            maxExcerptLineLength = 124,
        )
    }

    @Test
    fun multiLineExcerpt() {
        val input = """
        // pad the line number
        // pad the line number
        // pad the line number
        // pad the line number
        // pad the line number
        // pad the line number
        // pad the line number
        // pad the line number
        // pad the line number
        // pad the line number.  This should be line 10 so the function starts on 12.
        {
            fun foo() {
                return bar * baz();
            } // end
        }
        """.trimIndent()
        assertExcerpt(
            input = input,
            left = input.indexOf("fun foo()"),
            right = input.lastIndexOf("} // end") + 1,
            want = listOf(

                "   ┏┓",
                "\n",

                TtyCode.FgGrey, "12:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "fun foo() {", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgGrey, "13:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "    return bar * baz();", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgGrey, "14:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "}", TtyCode.NormalColorIntensity, " // end",
                "\n",

                "   ┗┛",
                "\n",
            ),
        )
    }

    @Test
    fun testManyShortLinesInTheMiddle() {
        val input = "/*\n" + "*\n".repeat(45) + "*/"
        assertExcerpt(
            input = input,
            left = 0,
            right = input.length,
            want = listOf(

                "   ┏┓",
                "\n",

                TtyCode.FgGrey, "01:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "/*", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgGrey, "02:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "*", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgDefault,
                "   ┃", TtyCode.FgGrey, "⋮",
                "\n",

                TtyCode.FgGrey, "46:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "*", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgGrey, "47:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "*/", TtyCode.NormalColorIntensity,
                "\n",

                "   ┗━┛",
                "\n",
            ),
        )
    }

    @Test
    fun abbreviationDoesNotBreakLineNumbering() {
        @Suppress("SpellCheckingInspection")
        val input = "" +
            "        function fib(i) {\n" +
            "          let a = 0;\n" +
            "          let b = 1;\n" +
            "          while (i > 0) {\n" + // line 4
            "            let c = a + b; // This line has a comment that is " +
            "waaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaay " +
            "tooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo " +
            "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong\n" +
            "            a = b;\n" +
            "            b = c;\n" +
            "            i -= 1\n" +
            "          }\n" + // line 9
            "          a;\n" +
            "        }\n" +
            "        fib(8);\n"

        val left = input.indexOf("while")
        val right = input.lastIndexOf("}\n", input.lastIndexOf("}\n") - 1) + 1
        assertExcerpt(
            input = input,
            left = left,
            right = right,
            want = listOf(
                "  ┏┓",
                "\n",
                TtyCode.FgGrey,
                "4:", TtyCode.FgDefault, "┃",
                TtyCode.Bold,
                "while (i > 0) {",
                TtyCode.NormalColorIntensity,
                "\n",
                TtyCode.FgGrey,
                "5:", TtyCode.FgDefault, "┃",
                TtyCode.Bold,
                "  let c = a + b; // This line has a comment that",
                TtyCode.NormalColorIntensity,
                TtyCode.FgGrey,
                "⋯",
                TtyCode.FgDefault,
                TtyCode.Bold,
                "oooooooooooooooooooooooooooooooooooooooooooooong",
                TtyCode.NormalColorIntensity,
                "\n",
                TtyCode.FgDefault,
                "  ┃", TtyCode.FgGrey, "⋮",
                "\n",
                TtyCode.FgGrey,
                "8:", TtyCode.FgDefault, "┃",
                TtyCode.Bold,
                "  i -= 1",
                TtyCode.NormalColorIntensity,
                "\n",
                TtyCode.FgGrey,
                "9:", TtyCode.FgDefault, "┃",
                TtyCode.Bold,
                "}",
                TtyCode.NormalColorIntensity,
                "\n",
                "  ┗┛",
                "\n",
            ),
        )
    }

    @Test
    fun twoCharacterExcerpt() {
        assertExcerpt(
            input = "i ~= 1",
            left = 2,
            right = 4,
            //       0123456
            want = listOf(
                TtyCode.FgGrey,
                "1: ",
                TtyCode.FgDefault,
                "i ",
                TtyCode.Bold,
                "~=",
                TtyCode.NormalColorIntensity,
                " 1",
                "\n",
                "     ┗┛",
                "\n",
            ),
        )
    }

    @Test
    fun huggerThatNeitherStartsNorEndsAtLeft() {
        val input = """
            |if (foo) {
            |  thenClause();
            |  does(stuff);
            |/* comment before end */ } else {
            |  elseClause()
            |}
        """.trimMargin()
        assertExcerpt(
            input = input,
            left = input.indexOf(") {") + 2,
            right = input.lastIndexOf("} else {") + 1,
            want = listOf(

                "  ┏━━━━━━━━━┓",
                "\n",

                TtyCode.FgGrey, "1:", TtyCode.FgDefault, "┃if (foo) ",
                TtyCode.Bold, "{", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgGrey, "2:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "  thenClause();", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgGrey, "3:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "  does(stuff);", TtyCode.NormalColorIntensity,
                "\n",

                TtyCode.FgGrey, "4:", TtyCode.FgDefault, "┃",
                TtyCode.Bold, "/* comment before end */ }", TtyCode.NormalColorIntensity, " else {",
                "\n",

                "  ┗━━━━━━━━━━━━━━━━━━━━━━━━━┛",
                "\n",
            ),
        )
    }

    private fun assertExcerpt(
        input: String,
        left: Int,
        right: Int,
        want: List<Any>,
        contextLen: Int = DEFAULT_CONTEXT_LEN,
        lineNumberAtStartOfInput: Int = DEFAULT_LINE_NUMBER_AT_START_OF_INPUT,
        maxLinesPerExcerpt: Int = DEFAULT_MAX_LINES_PER_EXCERPT,
        maxExcerptLineLength: Int = DEFAULT_MAX_EXCERPT_LINE_LENGTH,
    ) {
        val textOutput = object : TextOutput() {
            val got = mutableListOf<Any>()
            override val isTtyLike = true

            override fun emitLineChunk(text: CharSequence) {
                if (text.isNotEmpty()) {
                    got.add("$text")
                }
            }

            override fun emitTty(ttyCode: TtyCode) {
                got.add(ttyCode) // but don't actually convert it to a string
            }

            override fun flush() {
                // already on text
            }
        }
        excerpt(
            p = Position(testCodeLocation, left, right),
            input = input,
            textOutput = textOutput,
            contextLen = contextLen,
            lineNumberAtStartOfInput = lineNumberAtStartOfInput,
            maxLinesPerExcerpt = maxLinesPerExcerpt,
            maxExcerptLineLength = maxExcerptLineLength,
        )

        @Suppress("ConstantConditionIf")
        if (ALSO_LOG_TO_CONSOLE) {
            val log = console.textOutput
            for (el in textOutput.got) {
                when (el) {
                    "\n" -> log.endLine()
                    is String -> log.emitLineChunk(el)
                    is TtyCode -> log.emitTty(el)
                    else -> error(el)
                }
            }
        }

        fun formatValue(x: Any) = when (x) {
            is String -> temperEscaper.escape(x)
            is TtyCode -> "TtyCode.${x.name}"
            else -> "?$x"
        }

        val got = textOutput.got
        fun undoes(before: TtyCode, after: TtyCode) =
            before == TtyCode.NormalColorIntensity && after == TtyCode.Bold
        // Collapsing adjacent text chunks makes the golden a bit less implementation dependent.
        run {
            var i = 0
            var lastTtyCode: TtyCode? = null
            while (i + 1 < got.size) {
                val current = got[i]
                val next = got[i + 1]
                if (current is String && next is String && current != "\n" && next != "\n") {
                    got[i] = current + next
                    got.removeAt(i + 1)
                } else if (
                    current is TtyCode && next is TtyCode && lastTtyCode == next &&
                    undoes(current, next)
                ) {
                    got.subList(i, i + 2).clear()
                    if (i != 0) {
                        i -= 1
                        lastTtyCode = null
                    }
                } else {
                    if (current is TtyCode) {
                        lastTtyCode = current
                    }
                    i += 1
                }
            }
        }

        assertStringsEqual(
            want.joinToString("\n") { formatValue(it) },
            textOutput.got.joinToString("\n") { formatValue(it) },
        )
        assertEquals(want, textOutput.got)
    }
}
