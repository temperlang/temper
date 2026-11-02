package lang.temper.regex

import lang.temper.common.IntRangeSet
import lang.temper.common.codePointString
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.minus
import lang.temper.common.printErr
import lang.temper.common.withRandomForTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegexMatchGenTest {
    private var cancelGroup: CancelGroup? = null

    @BeforeTest
    fun setupCancelGroup() {
        cancelGroup = makeCancelGroupForTest()
    }

    @AfterTest
    fun tearDownCancelGroup() {
        cancelGroup = null
    }

    private fun genMany(
        checkMatch: (Iterable<RegexCheck>) -> Unit,
        formatter: RegexFormatter,
    ) = withRandomForTest { random ->
        var fails = 0
        var reports = 0
        var total = 0
        val checks = (1..50).flatMap {
            val pattern = random.nextRegex()
            val patternText = pattern.formatToString()
            var failsForPattern = 0
            (1..5).mapNotNull match@{
                total += 1
                val matchText = runCatching {
                    random.nextMatch(pattern)
                }.getOrElse { err ->
                    if (failsForPattern == 0 && reports < 20) {
                        // The idea is that limited match generation reporting could help us improve things.
                        // But don't say "failed" because that makes it harder to search logs for actual test failures.
                        printErr("Bailed on /$patternText/ with: ${err.message}")
                        reports += 1
                    }
                    fails += 1
                    failsForPattern += 1
                    return@match null
                }
                RegexCheck(true, RegexMatch(pattern.formatToString(formatter), matchText))
            }
        }
        printErr("Fail fraction: ${fails / total.toDouble()}")
        // Run all actual checks at the end in a batch for subprocess speed.
        // Also lets us get full stats for generation things above if we care.
        checkMatch(checks)
    }

    @Test
    fun genManyDotnet() = genMany(::checkMatchDotnet, DotnetRegexFormatter)

    @Test
    fun genManyJs() = genMany(::checkMatchJs, JsRegexFormatter)

    @Test
    fun genManyKotlin() = genMany(::checkMatchKotlin, KotlinRegexFormatter)

    @Test
    fun genManyPython() = genMany(::checkMatchPython, PythonRegexFormatter)

    @Test
    fun genMatch() {
        val random = Random(3333)
        val actual = (1..10).joinToString("\n") {
            random.nextMatch(messyPattern)
        }
        val expected =
            """
            abc󰄨q
            abc񸘻cbacbaa򢜻a󧓦a񀞓
            abc򜨠cbaa󫍚
            abc􌆔cbacbaa񦙣cbacbacba
            abc򥨬cbacba
            abc񨈣cbaa񮼤cbacba
            abc⃛f
            abc񼦰H
            abc𡍧N
            abc񇬊5
            """.trimIndent()
        assertEquals(expected, actual)
    }

    @Test
    fun randomIntFromRangeSet() = withRandomForTest { random ->
        val rangeSet = asciiWordIntRangeSet
        repeat(100) {
            val code = random.nextInt(rangeSet)
            assertTrue(code in rangeSet, "Not in set: ${code.codePointString()}")
        }
    }

    @Test
    fun rangesMerge() {
        val ranges = listOf(
            -10..-8,
            -6..-6,
            1..5,
            1..1,
            4..5,
            2..4,
            4..6,
            7..7,
            9..10,
            9..10,
        ).shuffled() // just to prove order doesn't matter
        val actual = IntRangeSet.unionRanges(ranges).toList()
        val expected = listOf(
            -10..-8,
            -6..-6,
            1..7,
            9..10,
        )
        assertEquals(expected, actual)
    }

    /**
     * This is just a test of IntRangeSet features at the moment, but this also gets used for RegexMatchGen, so it's
     * nice to verify that it works for needs here.
     */
    @Test
    fun rangesNegate() {
        val ranges = IntRangeSet.new(
            listOf(
                -10..-8,
                -6..-6,
                1..7,
                9..10,
            ).flatMap { listOf(it.first, it.last + 1) }.toIntArray(),
        )
        val expected = listOf(
            -15..-11,
            -7..-7,
            -5..0,
            8..8,
            11..15,
        )
        // One check with universe beyond the ranges.
        assertEquals(expected, (IntRangeSet.new(-15..15) - ranges).toList())
        // One check at the edges of the ranges.
        assertEquals(expected.slice(1 until expected.size - 1), (IntRangeSet.new(-10..10) - ranges).toList())
    }

    @Test
    fun surrogateRoundTrip() = withRandomForTest { random ->
        val pair = "🌊"
        assertEquals("\uD83C\uDF0A", pair)
        val pattern = Repeat(CodePoints(pair), min = 1, max = null, reluctant = false)
        repeat(10) {
            val match = random.nextMatch(pattern)
            assertTrue(match.isNotEmpty())
            match.slice(match.indices step 2).forEach { assertEquals(pair[0], it) }
            match.slice(1 until match.length step 2).forEach { assertEquals(pair[1], it) }
        }
    }

    @Test
    fun wordBoundarySimple() = withRandomForTest { random ->
        val wordAndNon = CodeRange(' '.code, 'z'.code)
        val pattern = Seq(listOf(wordAndNon, WordBoundary, wordAndNon))
        // Can fail because we don't choose "or" branches carefully.
        // Maybe not worth fixing separate from a more general regex engine.
        // val pattern = Seq(listOf(wordAndNon, WordBoundary, Or(listOf(wordAndNon, Word))))
        // Can also fail on forced code points, which would require planning from before the word boundary.
        // val pattern = Seq(listOf(wordAndNon, WordBoundary, CodePoints("hi")))
        repeat(20) {
            random.nextMatch(pattern)
        }
    }
}
