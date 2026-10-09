package lang.temper.builtin

import temper.regex_parser.RegexParserGlobal
import temper.std.regex.Capture
import temper.std.regex.CodePoints
import temper.std.regex.CodeRange
import temper.std.regex.CodeSet
import temper.std.regex.Or
import temper.std.regex.RegexNode
import temper.std.regex.Repeat
import temper.std.regex.Sequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegexCountedRepetitionTest {
    private fun parse(pattern: String, slots: MutableMap<String, RegexNode> = mutableMapOf()): String {
        val text = rewriteCountedRepetition(pattern, slots)
        return show(RegexParserGlobal.parseWith(text, slots)!!)
    }

    private fun show(node: RegexNode): String = when (node) {
        is Capture -> "Capture(${node.name}, ${show(node.item)})"
        is CodePoints -> "\"${node.value}\""
        is CodeRange -> "${node.min}-${node.max}"
        is CodeSet -> "[${if (node.negated) "^" else ""}${node.items.joinToString(" ") { show(it) }}]"
        is Or -> "Or(${node.items.joinToString(", ") { show(it) }})"
        is Repeat -> "Repeat(${show(node.item)}, ${node.min}, ${node.max}${if (node.reluctant) ", reluctant" else ""})"
        is Sequence -> "Seq(${node.items.joinToString(", ") { show(it) }})"
        else -> node::class.simpleName!!
    }

    private fun assertProblem(pattern: String, message: String) {
        val problem = assertFailsWith<RegexSyntaxProblem> {
            rewriteCountedRepetition(pattern, mutableMapOf())
        }
        assertEquals(message, problem.message)
    }

    @Test
    fun exactCountOfCodeSet() = assertEquals(
        "Repeat([48-57], 3, 3)",
        parse("[0-9]{3}"),
    )

    @Test
    fun rangeAndOpenEndedCounts() = assertEquals(
        "Seq(Repeat(\"a\", 2, 3), Repeat(\"b\", 2, null))",
        parse("a{2,3}b{2,}"),
    )

    @Test
    fun countAppliesToLastCodePointOnly() = assertEquals(
        "Seq(\"ab\", Repeat(\"c\", 2, 2), \"d\")",
        parse("abc{2}d"),
    )

    @Test
    fun reluctantCount() = assertEquals(
        "Seq(Repeat(\"a\", 2, 3, reluctant), \"b\")",
        parse("a{2,3}?b"),
    )

    @Test
    fun countOfGroupsNestedAndCaptured() = assertEquals(
        "Seq(Repeat(Seq(Repeat(\"a\", 2, 2), \"b\"), 1, 2), Repeat(Capture(x, \"cd\"), 0, 1))",
        parse("(a{2}b){1,2}(?x=cd){0,1}"),
    )

    @Test
    fun countOfEscapeAndSupplementaryCodePoint() = assertEquals(
        "Seq(Repeat(Digit, 4, 4), Repeat(\"\uD83D\uDE00\", 2, 2))",
        parse("\\d{4}\uD83D\uDE00{2}"),
    )

    @Test
    fun countInEachAlternative() = assertEquals(
        "Or(Repeat(\"a\", 1, 1), Repeat(\"b\", 2, 2))",
        parse("a{1}|b{2}"),
    )

    @Test
    fun countOfInterpolatedSlot() = assertEquals(
        "Repeat(\"x.y\", 2, 2)",
        parse("(?\$arg0){2}", mutableMapOf("arg0" to CodePoints("x.y"))),
    )

    @Test
    fun bracesStayLiteralInSetsAndWhenEscaped() = assertEquals(
        "Seq([\"{\" \"}\"], \"{3}\")",
        parse("[{}]\\{3\\}"),
    )

    @Test
    fun braceThatIsNotACount() = assertProblem(
        "x{a}",
        "`{` at offset 1 starts `{a}`, which is not a count like {3}, {2,} or {2,5};" +
            " write `\\{` to match a literal brace",
    )

    @Test
    fun braceWithNoMinimum() = assertProblem(
        "x{,3}",
        "`{` at offset 1 starts `{,3}`, which is not a count like {3}, {2,} or {2,5};" +
            " write `\\{` to match a literal brace",
    )

    @Test
    fun countWithNothingToRepeat() {
        assertProblem("{2}", "{2} at offset 0 has nothing to repeat")
        assertProblem("a|{2}", "{2} at offset 2 has nothing to repeat")
        assertProblem("^{2}", "{2} at offset 1 has nothing to repeat")
        assertProblem("a*{2}", "{2} at offset 2 has nothing to repeat")
    }

    @Test
    fun countFollowedByAnotherQuantifier() =
        assertProblem("a{2}+", "{2} at offset 1 is followed by another quantifier, `+`")

    @Test
    fun minimumAboveMaximum() =
        assertProblem("a{3,2}", "{3,2} at offset 1 has a minimum greater than its maximum")

    @Test
    fun unmatchedCloseParen() =
        assertProblem("a)b", "unmatched `)` at offset 1")
}
