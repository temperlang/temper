package lang.temper.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.coroutines.DelicateCoroutinesApi
import lang.temper.common.console
import lang.temper.fs.runWithTemporaryDirectory
import lang.temper.name.BackendId
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors.toSet
import kotlin.io.path.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bootstrap a test as close to what running the doc-gen through the CLI
 * is because I failed to find a way to attach a debugger to that directly.
 */
class DoDocGenTest {

    private val testedBackends = listOf(BackendId("js"), BackendId("py"))

    @Test
    @DelicateCoroutinesApi
    @ExperimentalCli
    fun test() {
        runWithTemporaryDirectory("DoDocGenTest") { output ->
            val main = DummyMain()

            val ok = main.doDocGen(
                workRoot = resourcePath("/doc/input"),
                outputDirectory = output,
                backends = testedBackends,
                cliConsole = console,
            )
            assertTrue(ok)

            // TODO(mikesamuel, integrated-doc-gen): fix name translation in PyBackend
            val expected = resourcePath("/doc/expected")

            assertEquals(readAllFilesInDir(expected), readAllFilesInDir(output))
        }
    }

    @Test
    @DelicateCoroutinesApi
    @ExperimentalCli
    fun testFailing() {
        runWithTemporaryDirectory("DoDocGenTestFailing") { output ->
            val main = DummyMain()

            val ok = main.doDocGen(
                workRoot = resourcePath("/doc/input").resolve("missing"),
                outputDirectory = output,
                backends = testedBackends,
                cliConsole = console,
            )

            assertFalse(ok)
        }
    }

    private fun readAllFilesInDir(path: Path): Set<String> {
        return Files.list(path).map { Files.readString(it).trim() }.collect(toSet())
    }

    private fun resourcePath(resource: String): Path { // TODO(bps): resources
        return DoDocGenTest::class.java.getResource(resource)!!.toURI().toPath()
    }
}
