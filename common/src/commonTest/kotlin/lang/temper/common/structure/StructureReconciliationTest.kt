package lang.temper.common.structure

import lang.temper.common.assertStringsEqual
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonValueBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Implement this differently since Kotlin's JS backend does not yet support reflection.
expect class SampleDate(year: Int, month: Int, dayOfMonth: Int) : Structured

class StructureReconciliationTest {
    @Test
    fun testReflectiveImplementationOfToSink() {
        val aDate = SampleDate(2020, 4, 27)
        assertStringsEqual(
            """
            {
                "year": 2020,
                "month": 4,
                "dayOfMonth": 27,
                "isoString": "2020-04-27"
            }
            """.trimIndent(),
            FormattingStructureSink.toJsonString { value(aDate) },
        )
        val tree = JsonValueBuilder.build(emptyMap()) { value(aDate) }
        assertTrue(tree is JsonObject)
        assertEquals(
            setOf(StructureHint.NaturallyOrdered),
            tree.properties.first { it.key == "month" }.hints,
        )
        assertEquals(
            setOf(StructureHint.Unnecessary, StructureHint.Sufficient),
            tree.properties.first { it.key == "isoString" }.hints,
        )
    }

    private fun assertReconciled(
        testCaseJson: String,
        computedValue: Any?,
        reconciledJson: String,
    ) {
        val (_, got) = reconcileStructure(
            sloppy = StructureParser.parseJson(testCaseJson, true),
            pedantic = JsonValueBuilder.build(emptyMap()) {
                value(computedValue)
            },
        )
        val wantCanonical = FormattingStructureSink.toJsonString {
            value(StructureParser.parseJson(reconciledJson, true))
        }
        val gotCanonical = FormattingStructureSink.toJsonString {
            value(got)
        }
        assertStringsEqual(
            wantCanonical,
            gotCanonical,
        )
    }

    @Test
    fun testReconciliationToSufficient() {
        assertReconciled(
            testCaseJson = """ "2020-04-26" """,
            computedValue = SampleDate(2020, 4, 27),
            reconciledJson = """ "2020-04-27" """,
        )
    }

    @Test
    fun testReconciliationToNecessary() {
        assertReconciled(
            testCaseJson = """ { "year": 2020 } """,
            computedValue = SampleDate(2020, 4, 27),
            reconciledJson = """ { "year": 2020, "month": 4, "dayOfMonth": 27 } """,
        )
    }

    @Test
    fun testReconciliationToOrdered() {
        assertReconciled(
            testCaseJson = """ [ 2020, 4, 26 ] """,
            computedValue = SampleDate(2020, 4, 27),
            reconciledJson = """ [ 2020, 4, 27 ] """,
        )
    }

    @Test
    fun pedanticIsShorterFromLeft() {
        assertReconciled(
            testCaseJson = """ [ "2020-04-26", "2020-04-27", "2020-04-28" ]""",
            computedValue = listOf(
                SampleDate(2020, 4, 27),
                SampleDate(2020, 4, 28),
            ),
            reconciledJson = """
            [
                "2020-04-27",
                "2020-04-28"
            ]
            """,
        )
    }

    @Test
    fun pedanticIsShorterFromRight() {
        assertReconciled(
            testCaseJson = """ [ "2020-04-26", "2020-04-27", "2020-04-28" ]""",
            computedValue = listOf(
                SampleDate(2020, 4, 26),
                SampleDate(2020, 4, 27),
            ),
            reconciledJson = """
            [
                "2020-04-26",
                "2020-04-27"
            ]
            """,
        )
    }

    @Test
    fun pedanticIsLongerFromLeft() {
        assertReconciled(
            testCaseJson = """ [ "2020-04-27", "2020-04-28" ]""",
            computedValue = listOf(
                SampleDate(2020, 4, 26),
                SampleDate(2020, 4, 27),
                SampleDate(2020, 4, 28),
            ),
            reconciledJson = """
            [
                { "year": 2020, "month": 4, "dayOfMonth": 26, "isoString": "2020-04-26" },
                "2020-04-27",
                "2020-04-28"
            ]
            """,
        )
    }

    @Test
    fun pedanticIsLongerFromRight() {
        assertReconciled(
            testCaseJson = """ [ "2020-04-26", "2020-04-27" ]""",
            computedValue = listOf(
                SampleDate(2020, 4, 26),
                SampleDate(2020, 4, 27),
                SampleDate(2020, 4, 28),
            ),
            reconciledJson = """
            [
                "2020-04-26",
                "2020-04-27",
                { "year": 2020, "month": 4, "dayOfMonth": 28, "isoString": "2020-04-28" }
            ]
            """,
        )
    }

    @Test
    fun pedanticIsLongerFromMiddle() {
        assertReconciled(
            testCaseJson = """ [ "2020-04-26", "2020-04-28" ]""",
            computedValue = listOf(
                SampleDate(2020, 4, 26),
                SampleDate(2020, 4, 27),
                SampleDate(2020, 4, 28),
            ),
            reconciledJson = """
            [
                "2020-04-26",
                { "year": 2020, "month": 4, "dayOfMonth": 27, "isoString": "2020-04-27" },
                "2020-04-28"
            ]
            """,
        )
    }

    @Test
    fun pedanticMismatchInMiddle() {
        assertReconciled(
            testCaseJson = """ [ "2020-04-26", "2020-04-27", "2020-04-28" ]""",
            computedValue = listOf(
                SampleDate(2020, 4, 26),
                SampleDate(2020, 4, 29),
                SampleDate(2020, 4, 28),
            ),
            reconciledJson = """
            [
                "2020-04-26",
                "2020-04-29",
                "2020-04-28"
            ]
            """,
        )
    }
}

internal fun pad(n: Int, nDigitsNeeded: Int): CharSequence {
    val s = n.toString()
    val isNegative = s[0] == '-'
    val numLeft = if (isNegative) { 1 } else { 0 }
    val nDigitsPresent = s.length - numLeft
    val zeroesNeeded = nDigitsNeeded - nDigitsPresent
    return if (zeroesNeeded <= 0) {
        s
    } else {
        val sb = StringBuilder()
        sb.append(s, 0, numLeft)
        repeat(zeroesNeeded) { sb.append('0') }
        sb.append(s, numLeft, s.length)
        sb
    }
}
