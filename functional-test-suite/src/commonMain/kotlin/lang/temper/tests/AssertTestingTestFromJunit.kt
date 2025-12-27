package lang.temper.tests

import lang.temper.name.identifiers.IdentStyle
import lang.temper.result.junit.parseJunitResults
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun assertTestingTestFromJunit(
    test: FunctionalTestBase,
    junitOutput: String?,
    stdout: String?,
    /** To reverse the test naming and normalize the human case. */
    testPattern: Regex = Regex("""(.*?)(?:__\d+)?"""),
) {
    val parsedResults = parseJunitResults(junitOutput)
    assertEquals(
        test.expectedTestFailures.map { it.key.lowercase() }.sorted(),
        parsedResults.failures.map {
            val stripped = testPattern.matchEntire(it.name)?.groupValues?.getOrNull(1) ?: ""
            IdentStyle.Camel.convertTo(IdentStyle.Human, stripped).lowercase()
        }.sorted(),
        "Raw aux output:$junitOutput\n\nstd:${stdout}",
    )
    // N^2 check for output, but expected small for these tests. Allows some sloppiness on our part.
    for (value in test.expectedTestFailures.values) {
        assertTrue(parsedResults.failures.any { value in it.cause }, "no matching cause found")
    }
}
