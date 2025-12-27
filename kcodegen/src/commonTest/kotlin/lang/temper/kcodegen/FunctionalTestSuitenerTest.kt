package lang.temper.kcodegen

import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.kcodegen.functests.FunctionalTestSuitener
import kotlin.test.Test

class FunctionalTestSuitenerTest {
    @Test
    fun functionalTestsUpToDate() {
        var pass = false
        try {
            FunctionalTestSuitener.assertUpToDate { want, got, desc ->
                assertStringsEqual(want = want, got = got, message = desc.toString())
                error("$desc")
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
}
