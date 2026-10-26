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
        // A backend that runs tests it didn't declare mustn't give "33 of 22 (-11 not run)".
        val summary = TestTally(run = 33, failed = 0, defined = 22).summary()
        assertEquals(RunTestsMessageTemplate.TestSummaryAllRun, summary.template)
        assertEquals(listOf<Any>(33, 33), summary.values)
    }
}
