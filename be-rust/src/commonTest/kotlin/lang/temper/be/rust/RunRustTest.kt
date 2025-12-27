package lang.temper.be.rust

import kotlin.test.Test
import kotlin.test.assertEquals

class RunRustTest {
    @Test
    fun parseCargoTest() {
        val report = """
            |running 3 tests
            |test r#mod::tests::exported__66 ... ok
            |test r#mod::tests::aFailingTest__65 ... FAILED
            |test r#mod::tests::aTestCase__54 ... ok
            |
            |failures:
            |
            |---- r#mod::tests::aFailingTest__65 stdout ----
            |Error: Error(Some(MessageError("because it's wrong, expected helper.i == (2) not (3), expected s == (hi) not (bye)")))
            |
            |
            |failures:
            |    r#mod::tests::aFailingTest__65
            |
            |test result: FAILED. 2 passed; 1 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
        """.trimMargin()
        val translated = cargoTestToJunitXml("whatever", report)!!.lines().joinToString("\n")
        val expected = """
            |<testsuites>
            |  <testsuite name="whatever" tests="3" failures="1" time="0.0">
            |    <testcase classname="exported__66" name="exported__66" time="0"/>
            |    <testcase classname="aFailingTest__65" name="aFailingTest__65" time="0">
            |      <failure message="because it&apos;s wrong, expected helper.i == (2) not (3), expected s == (hi) not (bye)"/>
            |    </testcase>
            |    <testcase classname="aTestCase__54" name="aTestCase__54" time="0"/>
            |  </testsuite>
            |</testsuites>
        """.trimMargin()
        assertEquals(expected, translated)
    }

    @Test
    fun parseCargoTestMultipart() {
        // This is also a test of all tests passing, in addition to the multipart test report.
        val report = """
            |running 7 tests
            |test averages::arithmetic_mean::example::tests::meanOfThreeNums__11 ... ok
            |test binary_search::binary_search::tests::recursive__28 ... ok
            |test averages::arithmetic_mean::example::tests::meanNotMedian__14 ... ok
            |test matrix_multiplication::dense::tests::multiplyDense__75 ... ok
            |test matrix_multiplication::matrix::tests::multiply__69 ... ok
            |test greatest_common_divisor::gcd::tests::positiveAndNegativeValues__16 ... ok
            |test fizz_buzz::fizz_buzz::tests::someFizzAndOrBuzz__19 ... ok
            |
            |test result: ok. 7 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
            |
            |
            |running 0 tests
            |
            |test result: ok. 0 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
            |
            |
            |running 0 tests
            |
            |test result: ok. 0 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
        """.trimMargin()
        val translated = cargoTestToJunitXml("whatever", report)!!.lines().joinToString("\n")
        val expected = """
            |<testsuites>
            |  <testsuite name="whatever" tests="7" failures="0" time="0.0">
            |    <testcase classname="meanOfThreeNums__11" name="meanOfThreeNums__11" time="0"/>
            |    <testcase classname="recursive__28" name="recursive__28" time="0"/>
            |    <testcase classname="meanNotMedian__14" name="meanNotMedian__14" time="0"/>
            |    <testcase classname="multiplyDense__75" name="multiplyDense__75" time="0"/>
            |    <testcase classname="multiply__69" name="multiply__69" time="0"/>
            |    <testcase classname="positiveAndNegativeValues__16" name="positiveAndNegativeValues__16" time="0"/>
            |    <testcase classname="someFizzAndOrBuzz__19" name="someFizzAndOrBuzz__19" time="0"/>
            |  </testsuite>
            |</testsuites>
        """.trimMargin()
        assertEquals(expected, translated)
    }

    @Test
    fun parseCargoTestUgly() {
        // Multi-part with some failures, including with multi-line and non-custom formatting.
        val report = """
            |
            |running 5 tests
            |test tests::tests::badRepeat__156 ... ok
            |test tests::tests::escapedDot__131 ... FAILED
            |test tests::tests::capture__124 ... FAILED
            |test tests::tests::unclosedNamedCaptureName__130 ... ok
            |test tests::tests::sub__177 ... FAILED
            |
            |failures:
            |
            |---- tests::tests::escapedDot__131 stdout ----
            |thread 'tests::tests::escapedDot__131' panicked at 'called `Option::unwrap()` on a `None` value', std\src\regex\mod.rs:173:18
            |
            |---- tests::tests::capture__124 stdout ----
            |thread 'tests::tests::capture__124' panicked at 'called `Option::unwrap()` on a `None` value', std\src\regex\mod.rs:165:19
            |note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace
            |
            |---- tests::tests::sub__177 stdout ----
            |thread 'tests::tests::sub__177' panicked at 'called `Option::unwrap()` on a `None` value', C:\Users\work2\Documents\projects\temper-regex-parser\temper.out\rust\std\src\regex\mod.rs:1322:39
            |
            |
            |failures:
            |    tests::tests::capture__124
            |    tests::tests::escapedDot__131
            |    tests::tests::sub__177
            |
            |test result: FAILED. 2 passed; 3 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
            |
            |
        """.trimMargin()
        val translated = cargoTestToJunitXml("whatever", report)!!.lines().joinToString("\n")
        val expected = """
            |<testsuites>
            |  <testsuite name="whatever" tests="5" failures="3" time="0.0">
            |    <testcase classname="badRepeat__156" name="badRepeat__156" time="0"/>
            |    <testcase classname="escapedDot__131" name="escapedDot__131" time="0">
            |      <failure message="thread &apos;tests::tests::escapedDot__131&apos; panicked at &apos;called `Option::unwrap()` on a `None` value&apos;, std\src\regex\mod.rs:173:18"/>
            |    </testcase>
            |    <testcase classname="capture__124" name="capture__124" time="0">
            |      <failure message="thread &apos;tests::tests::capture__124&apos; panicked at &apos;called `Option::unwrap()` on a `None` value&apos;, std\src\regex\mod.rs:165:19
            |note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace"/>
            |    </testcase>
            |    <testcase classname="unclosedNamedCaptureName__130" name="unclosedNamedCaptureName__130" time="0"/>
            |    <testcase classname="sub__177" name="sub__177" time="0">
            |      <failure message="thread &apos;tests::tests::sub__177&apos; panicked at &apos;called `Option::unwrap()` on a `None` value&apos;, C:\Users\work2\Documents\projects\temper-regex-parser\temper.out\rust\std\src\regex\mod.rs:1322:39"/>
            |    </testcase>
            |  </testsuite>
            |</testsuites>
        """.trimMargin()
        assertEquals(expected, translated)
    }
}
