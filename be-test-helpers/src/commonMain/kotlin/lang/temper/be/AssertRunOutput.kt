package lang.temper.be

import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.EffortSuccess
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.assertStringsEqual
import lang.temper.tests.FunctionalTestBase
import kotlin.test.assertIs
import kotlin.test.fail

/** For convenience in checkout output when running for simple output. */
fun FunctionalTestBase.assertRunOutput(result: RResult<EffortSuccess, CliFailure>) {
    check(!runAsTest)
    when {
        expectRunFailure -> assertIs<RFailure<*>>(result)
        else -> when (result) {
            is RSuccess -> {
                val effort = result.result
                assertStringsEqual(
                    expectedOutput,
                    effort.stdout,
                    message = this::class.simpleName,
                )
            }
            is RFailure -> {
                val cliFailure = result.failure
                fail(cliFailure.message ?: "command line failed")
            }
        }
    }
}
