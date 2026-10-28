package lang.temper.be.lua

import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CommandNotFound
import lang.temper.be.cli.ShellPreferences
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.log.NullConsole
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Lua51SpecificsTest {
    @Test
    fun missingLuaIsNamed(): Unit = makeCancelGroupForTest().let { cancelGroup ->
        runWithTemporaryDirectory("missingLuaIsNamed") { emptyDir ->
            val shellPreferences = ShellPreferences(
                console = NullConsole,
                onFailure = ShellPreferences.OnFailure.Release,
                pathElements = listOf(emptyDir.absolutePathString()),
                verbosity = ShellPreferences.Verbosity.Quiet,
            )
            // With no tools listed, validation passed and the run later hit a
            // NullPointerException looking up `lua`.
            val failure = assertFailsWith<CommandNotFound> {
                CliEnv.using(Lua51Specifics, shellPreferences, cancelGroup) {}
            }
            assertEquals(listOf("lua"), failure.names)
        }
    }
}
