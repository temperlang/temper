package lang.temper.kcodegen

import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests that Kotlin code generated from `*.out-grammar` files are up-to-date.
 */
class OutputGrammarCodeGeneratorTest {
    @Test
    fun outputGrammarsUpToDate() {
        var pass = false
        try {
            for (subProject in OutputGrammarCodeGenerator.subProjects) {
                OutputGrammarCodeGenerator(subProject).assertUpToDate { want, got, desc ->
                    assertStringsEqual(want = want, got = got, message = "$desc")
                    error("$desc")
                }
            }
            pass = true
        } finally {
            if (!pass) {
                console.error(
                    """
                    You can auto-update by running
                    $ gradle kcodegen:updateGeneratedCode
                    """.trimIndent(),
                )
            }
        }
    }

    @Test
    fun subProjectsNotEmpty() {
        val subProjects = OutputGrammarCodeGenerator.subProjects.toSet()
        assertTrue("be" in subProjects, "subProjects=$subProjects")
    }
}
