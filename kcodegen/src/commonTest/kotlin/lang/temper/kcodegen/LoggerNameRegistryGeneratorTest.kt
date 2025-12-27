package lang.temper.kcodegen

import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.kcodegen.lognames.LoggerNameRegistryGenerator
import kotlin.test.Test

class LoggerNameRegistryGeneratorTest {
    @Test
    fun loggerNamesUpToDate() {
        var pass = false
        try {
            LoggerNameRegistryGenerator.assertUpToDate { want, got, desc ->
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
