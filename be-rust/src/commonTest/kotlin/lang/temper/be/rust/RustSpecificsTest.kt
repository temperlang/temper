package lang.temper.be.rust

import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CommandNotFound
import lang.temper.be.cli.ShellPreferences
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.log.NullConsole
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.absolutePathString
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RustSpecificsTest {
    @Test
    fun missingCargoIsNamed(): Unit = makeCancelGroupForTest().let { cancelGroup ->
        if (System.getProperty("os.name").startsWith("Windows")) {
            return@let // The stand-in rustc below is a shell script.
        }
        runWithTemporaryDirectory("missingCargoIsNamed") { binDir ->
            // A rustc that passes the version check, and no cargo beside it.
            binDir.resolve("rustc").apply {
                writeText("#!/bin/sh\necho 'rustc ${RustcCommand.minVersion} (0000000 2023-08-03)'\n")
                setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
            }
            val shellPreferences = ShellPreferences(
                console = NullConsole,
                onFailure = ShellPreferences.OnFailure.Release,
                pathElements = listOf(binDir.absolutePathString()),
                verbosity = ShellPreferences.Verbosity.Quiet,
            )
            // Only rustc was checked, so the run later hit a NullPointerException looking up `cargo`.
            val failure = assertFailsWith<CommandNotFound> {
                CliEnv.using(RustSpecifics, shellPreferences, cancelGroup) {}
            }
            assertEquals(listOf("cargo"), failure.names)
        }
    }
}
