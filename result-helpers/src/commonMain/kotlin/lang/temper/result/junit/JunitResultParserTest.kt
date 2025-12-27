package lang.temper.result.junit

import kotlin.test.Test
import kotlin.test.assertEquals

class JunitResultParserTest {

    @Test
    fun basicParsing() {
        val testResults = parseJunitResults(SAMPLE)

        assertEquals(2, testResults.testsRun)
        assertEquals(1, testResults.failures.size)
    }

    @Test
    fun extraElements() {
        val testResults = parseJunitResults(EXTRA_ELEMENTS)
        assertEquals(1, testResults.testsRun)
        assertEquals(0, testResults.failures.size)
    }

    @Test
    fun parsesNothingToEmpty() {
        val testResults = parseJunitResults("")
        assertEquals(0, testResults.testsRun)
        assert(testResults.failures.isEmpty())
    }

    @Test
    fun parsesNullToEmpty() {
        val testResults = parseJunitResults(null)
        assertEquals(0, testResults.testsRun)
        assert(testResults.failures.isEmpty())
    }

    @Suppress("MagicNumber") // Count of tests
    @Test
    fun combiningResults() {
        val combined = combineSurefireResults(listOf(SAMPLE_SUREFIRE_1, SAMPLE_SUREFIRE_2))
        val testResults = parseJunitResults(combined)
        assertEquals(3, testResults.testsRun)
        assertEquals(0, testResults.failures.size)
    }
}

// Generated from a mocha invocation of the testing functional test
private const val SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="Mocha Tests" time="0.0030" tests="2" failures="1">
<testsuite name="Root Suite" timestamp="2022-07-19T13:41:13" tests="2" time="0.0020" failures="1">
<testcase name="a test case" time="0.0000" classname="a test case">
</testcase>
<testcase name="a failing test" time="0.0000" classname="a failing test">
<failure message="function fn_60() {
let return_61;
return_61 = &quot;because it&apos;s wrong&quot;;
return return_61;
}" type="AssertionError"><![CDATA[AssertionError [ERR_ASSERTION]: function fn_60() {
let return_61;
return_61 = "because it's wrong";
return return_61;
}
at Context.fn_58 (file:///private/var/folders/cb/0b2q8wt50454rgd6k25mb98h0000gn/T/temper-test8419442703188501440/
at processImmediate (node:internal/timers:466:21)]]></failure>
</testcase>
</testsuite>
</testsuites>"""

private const val EXTRA_ELEMENTS = """<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="Mocha Tests" time="0.0030" tests="1" failures="0" bonus="3">
<testsuite name="Root Suite" timestamp="2022-07-19T13:41:13" tests="1" time="0.0020" failures="0">
<testcase name="a test case" time="0.0000" classname="a test case">
</testcase>
<something />
</testsuite>
</testsuites>"""

// Check that we can combine simple results.
private const val SAMPLE_SUREFIRE_1 = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Suite1" timestamp="2022-07-19T13:41:13" tests="1" time="0.0020" failures="1">
<testcase name="a test case" time="0.0000" classname="a test case">
</testcase>
</testsuite>
"""

private const val SAMPLE_SUREFIRE_2 = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Suite2" timestamp="2022-07-19T13:41:13" tests="2" time="0.0020" failures="1">
<testcase name="another test case" time="0.0000" classname="a test case">
</testcase>
<testcase name="and another test case" time="0.0000" classname="a test case">
</testcase>
</testsuite>
"""
