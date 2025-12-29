package lang.temper.be.cli

import lang.temper.common.RResult
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.fs.mkdir
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.log.NullConsole
import lang.temper.name.BackendId
import kotlin.io.path.absolutePathString
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LocalCliEnvTest {
    @Test
    fun dirsArentExes(): Unit = makeCancelGroupForTest().let { cancelGroup ->
        runWithTemporaryDirectory("dirsArentExes") { root ->
            val name = "hi"
            // Use both bare and ".exe" for cross-platform checks, even if Windows ".exe" makes collision unlikely.
            val variants = listOf(name, "$name.exe")
            val matchingDirs = variants.map { root.resolve(it).also { full -> full.mkdir() } }
            fun findCommand(): RResult<CliTool, CliFailure> {
                val specifics = TestSpecifics(cliNames = listOf(name))
                val cliEnv = LocalCliEnv(
                    specifics,
                    ShellPreferences(
                        console = NullConsole,
                        onFailure = ShellPreferences.OnFailure.Release,
                        pathElements = (listOf(root) + matchingDirs).map { it.absolutePathString() },
                        verbosity = ShellPreferences.Verbosity.Quiet,
                    ),
                    cancelGroup,
                )
                // This caches results, which is why we make a new env each time.
                return cliEnv.which(specifics.tools.first())
            }
            // Try with only the dir present.
            val result1 = findCommand()
            assertIs<CommandNotFound>(result1.failure)
            // Try with a matching file on the path now.
            val dir = matchingDirs.first()
            variants.forEach { dir.resolve(it).createFile() }
            val result2 = findCommand()
            assertNotNull(result2.result)
        }
    }
}

class TestSpecifics(cliNames: List<String>) : Specifics {
    override val backendId = BackendId("test")
    override val tools: List<ToolSpecifics> = listOf(TestToolSpecifics(cliNames))
}

class TestToolSpecifics(override val cliNames: List<String>) : ToolSpecifics
