package lang.temper.be.py

import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.cliEnvImplemented
import lang.temper.be.cli.print
import lang.temper.common.Log
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertIs

class PyRuntimeTest {
    private fun runModule(module: String) {
        if (!cliEnvImplemented) {
            return
        }
        val cancelGroup = makeCancelGroupForTest()
        val result = runPySingleModule(
            module,
            libraries = listOf(
                filePath(
                    "be-py", "src", "commonMain", "resources",
                    "lang", "temper", "be", "py", "temper-core",
                ),
                filePath("be-py", "src", "commonTest", "py"),
            ),
            shellPreferences = ShellPreferences.default(console),
            cancelGroup = cancelGroup,
        )
        result.print(console, successLevel = Log.Error, failureLevel = Log.Error)
        assertIs<RSuccess<*, *>>(result, "Test process failed.")
    }

    @Test
    fun collections() {
        runModule("test_collections")
    }

    @Test
    fun stringSlice() {
        runModule("test_string_slice")
    }

    @Test
    fun toStringMethods() {
        runModule("test_to_string")
    }
}
