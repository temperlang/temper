package lang.temper.tooling.buildrun

import kotlin.test.Test
import kotlin.test.assertEquals

class TestTallyTest {
    @Test
    fun allRun() {
        val summary = TestTally(run = 22, failed = 0, defined = 22).summary()
        assertEquals(RunTestsMessageTemplate.TestSummaryAllRun, summary.template)
        assertEquals(listOf<Any>(22, 22), summary.values)
    }

    @Test
    fun someNotRun() {
        val summary = TestTally(run = 20, failed = 1, defined = 22).summary()
        assertEquals(RunTestsMessageTemplate.TestSummaryNotAllRun, summary.template)
        assertEquals(listOf<Any>(19, 22, 2), summary.values)
    }

    @Test
    fun moreRunThanDefined() {
        // A backend that runs tests it didn't declare gives "33 of 22 (11 unexpected)",
        // not "33 of 22 (-11 not run)".
        val summary = TestTally(run = 33, failed = 0, defined = 22).summary()
        assertEquals(RunTestsMessageTemplate.TestSummaryUnexpected, summary.template)
        assertEquals(listOf<Any>(33, 22, 11), summary.values)
    }

    @Test
    fun notRunAndUnexpected() {
        // One backend didn't run its 11, another ran 11 it didn't declare: show both, not "22 of 22".
        val summary = TestTally(run = 22, failed = 0, defined = 22, notRun = 11, unexpected = 11).summary()
        assertEquals(RunTestsMessageTemplate.TestSummaryNotRunAndUnexpected, summary.template)
        assertEquals(listOf<Any>(22, 22, 11, 11), summary.values)
    }

    @Test
    fun sumsKeepBoth() {
        val sum = TestTally(run = 0, failed = 0, defined = 11) + TestTally(run = 11, failed = 0, defined = 0)
        assertEquals(11, sum.notRun)
        assertEquals(11, sum.unexpected)
    }
}
