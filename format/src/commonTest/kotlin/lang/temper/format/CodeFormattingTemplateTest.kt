package lang.temper.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodeFormattingTemplateTest {
    private fun assertTemplateFromString(
        want: CodeFormattingTemplate,
        formatString: String,
    ) {
        assertEquals(
            want,
            CodeFormattingTemplate.fromFormatString(formatString),
        )
    }

    @Test
    fun emptyTemplate() = assertTemplateFromString(
        CodeFormattingTemplate.empty,
        "",
    )

    @Test
    fun spaceIgnored() = assertTemplateFromString(
        CodeFormattingTemplate.empty,
        " ",
    )

    @Test
    fun escapedSpace() = assertTemplateFromString(
        CodeFormattingTemplate.Space,
        """\ """,
    )

    @Test
    fun newlinesNotIgnored() = assertTemplateFromString(
        CodeFormattingTemplate.NewLine,
        "\n",
    )

    @Test
    fun dashToken() = assertTemplateFromString(
        CodeFormattingTemplate.LiteralToken(
            OutputToken("-", OutputTokenType.Punctuation),
        ),
        " - ",
    )

    @Test
    fun idToken() = assertTemplateFromString(
        CodeFormattingTemplate.LiteralToken(
            OutputToken("hi", OutputTokenType.Word),
        ),
        " hi ",
    )

    @Test
    fun blockOfStuff() = assertTemplateFromString(
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(OutToks.leftCurly),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.OneSubstitution(1),
                CodeFormattingTemplate.NewLine,
                CodeFormattingTemplate.LiteralToken(OutToks.rightCurly),
            ),
        ),
        "{\n{{1}}\n}",
    )

    @Test
    fun abbreviatedEmptyParens() = assertTemplateFromString(
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(OutToks.leftParen),
                CodeFormattingTemplate.LiteralToken(OutToks.rightParen),
            ),
        ),
        " () ",
    )

    @Test
    fun anglesPaired() = assertTemplateFromString(
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.LiteralToken(OutToks.leftAngle),
                CodeFormattingTemplate.GroupSubstitution(
                    -1,
                    CodeFormattingTemplate.LiteralToken(OutToks.comma),
                ),
                CodeFormattingTemplate.LiteralToken(OutToks.rightAngle),
            ),
        ),
        "<{{-1*:,}}>",
    )

    @Test
    fun anglesUnpaired() = assertTemplateFromString(
        CodeFormattingTemplate.Concatenation(
            listOf(
                CodeFormattingTemplate.OneSubstitution(0),
                CodeFormattingTemplate.LiteralToken(
                    OutputToken("<", OutputTokenType.Punctuation),
                ),
                CodeFormattingTemplate.OneSubstitution(1),
            ),
        ),
        "{{0}}<{{1}}",
    )

    private fun assertFormatted(
        want: String,
        formatString: String,
        children: List<IndexableFormattableTreeElement>,
        skipOuterCurlies: Boolean = false,
    ) {
        val tree = object : FormattableTree {
            override val codeFormattingTemplate = CodeFormattingTemplate.fromFormatString(formatString)

            override val formatElementCount: Int get() = children.size

            override fun formatElement(index: Int): IndexableFormattableTreeElement =
                children[index]

            override val operatorDefinition: OperatorDefinition? get() = null
        }
        val got = toStringViaTokenSink {
            CodeFormatter(it).format(tree, skipOuterCurlies = skipOuterCurlies)
        }
        assertEquals(want, got)
    }

    @Test
    fun twoSubs() = assertFormatted(
        want = "000 001",
        formatString = "{{0}} {{1}}",
        children = listOf(paddedCountingNum(0), paddedCountingNum(1)),
    )

    @Test
    fun notAllSubstituted() = assertFormatted(
        want = "(001)",
        formatString = "( {{1}} )",
        children = listOf(paddedCountingNum(0), paddedCountingNum(1), paddedCountingNum(2)),
    )

    @Test
    fun twoSubsOutOfOrder() = assertFormatted(
        want = "001 000",
        formatString = "{{1}} {{0}}",
        children = listOf(paddedCountingNum(0), paddedCountingNum(1)),
    )

    @Test
    fun groupSub() = assertFormatted(
        want = "000 -(001, 002, 003) - 004",
        formatString = "{{0}}-( {{1*:,}} )-{{2}}",
        children = listOf(
            paddedCountingNum(0),
            FormattableTreeGroup((1..3).map(::paddedCountingNum)),
            paddedCountingNum(4),
        ),
    )

    private enum class WordsToRemember : FormattableEnum {
        Hello,
        World,
    }

    @Test
    fun formattableEnums() {
        assertFormatted(
            want = "hello, world !",
            formatString = "{{0}}, {{1}}!",
            children = WordsToRemember.entries,
        )
    }

    @Test
    fun excessCurlyElimination() = assertFormatted(
        want = "if (000) {\n 001 \n}",
        formatString = "if ({{0}}) {\n{{1}}\n}",
        children = listOf(
            paddedCountingNum(0),
            blockTree(paddedCountingNum(1)),
        ),
    )

    @Test
    fun needTemplateOrCustomRenderTo() {
        assertFailsWith<IllegalStateException> {
            toStringViaTokenSink {
                val cf = CodeFormatter(it)
                cf.format(
                    object : FormattableTree {
                        override val codeFormattingTemplate: CodeFormattingTemplate? = null

                        // renderTo intentionally not overridden here
                        override val formatElementCount: Int = 0
                        override fun formatElement(index: Int): IndexableFormattableTreeElement =
                            throw IndexOutOfBoundsException(index)

                        override val operatorDefinition: OperatorDefinition? = null
                    },
                )
            }
        }
    }

    companion object {
        fun paddedCountingNum(i: Int): FormattableTree =
            object : FormattableTree {
                override val formatElementCount: Int get() = 0
                override val codeFormattingTemplate: CodeFormattingTemplate?
                    get() = null
                override val operatorDefinition: OperatorDefinition?
                    get() = null

                override fun formatElement(index: Int) =
                    error("$index")

                override fun isCurlyBracketBlock(): Boolean = false

                override fun renderTo(tokenSink: TokenSink) {
                    tokenSink.number(
                        buildString {
                            append(i)
                            while (this.length < 3) {
                                insert(0, '0')
                            }
                        },
                    )
                }
            }

        fun blockTree(content: FormattableTree): FormattableTree =
            object : FormattableTree {
                override val formatElementCount: Int get() = 1
                override val codeFormattingTemplate: CodeFormattingTemplate = blockTemplate
                override val operatorDefinition: OperatorDefinition?
                    get() = null

                override fun formatElement(index: Int): IndexableFormattableTreeElement {
                    require(index == 0)
                    return FormattableTreeGroup(listOf(content))
                }
            }
    }
}

private val blockTemplate = CodeFormattingTemplate.fromFormatString("""{\n{{0*:;}}\n}""")
