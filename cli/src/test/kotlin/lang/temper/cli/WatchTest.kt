package lang.temper.cli

import lang.temper.be.csharp.CSharpBackend
import lang.temper.be.java.JavaBackend
import lang.temper.be.js.JsBackend
import lang.temper.be.lua.LuaBackend
import lang.temper.be.py.PyBackend
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.common.withCapturingConsole
import lang.temper.fs.mkdir
import lang.temper.fs.runWithTemporaryDirCopyOf
import lang.temper.name.BackendId
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ForkJoinPool
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchTest {
    @Test
    fun csharpWatchTest() {
        runTest("csharpWatchTest", listOf(CSharpBackend.Factory.backendId))
    }

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun javaWatchTest() {
        runTest("javaWatchTest", listOf(JavaBackend.Java17.backendId))
    }

    @Test
    fun jsWatchTest() {
        runTest("jsWatchTest", listOf(JsBackend.Factory.backendId))
    }

    @Test
    fun luaWatchTest() {
        runTest("luaWatchTest", listOf(LuaBackend.Lua51.backendId))
    }

    @Test
    fun pyWatchTest() {
        runTest("pyWatchTest", listOf(PyBackend.Python3.backendId))
    }

    @Test
    fun repeatWatchTest() {
        // Build twice to make sure that works, and don't bother to run tests.
        runTest("repeatWatchTest", listOf(JsBackend.Factory.backendId), buildLimit = 2, testBackendIds = listOf())
    }

    // None here for rust, but there's a DoTestTest case for Rust, which is good enough for now.
}

private fun runTest(
    testName: String,
    backendIds: List<BackendId>,
    buildLimit: Int = 1, // end immediately by default
    testBackendIds: List<BackendId> = backendIds,
) {
    val userSignalledDone = UnmanagedFuture.newCompletableFuture<Unit, Nothing>(
        "User signalled done",
    )
    runWithTemporaryDirCopyOf(testName, resourcePath("/testing/passing"), subPath = Path("src")) { dir ->
        // Add a extra files for possible ignoring.
        addExtraFiles(dir)
        // Now to the main test.
        // TODO Make these atomic, or just figure we have big gaps?
        var buildCount = 0
        var timer: Timer? = null
        val (ok, output) = withCapturingConsole { capturingConsole ->
            doWatch(
                executorService = ForkJoinPool.commonPool(),
                backends = backendIds,
                testBackends = testBackendIds,
                buildLimit = buildLimit,
                shellPreferences = shellPreferencesForTest(capturingConsole),
                workRoot = dir,
                ignoreFile = dir.resolve(".gitignore"),
                userSignalledDone = userSignalledDone,
            ) {
                buildCount += 1
                timer = Timer()
                @Suppress("MagicNumber")
                timer!!.schedule(
                    object : TimerTask() {
                        override fun run() {
                            timer = null
                            val file = dir.resolve("src/test.temper")
                            Files.writeString(file, Files.readString(file) + "\n// Keep on changing!")
                        }
                    },
                    500,
                )
            }
        }
        timer?.cancel()
        if (testBackendIds.size == 1) {
            assertContains(output, "Tests passed: 2 of 2")
        }
        assertTrue(ok, "expected ok.  output follows:\n\n$output")
        assertEquals(buildLimit, buildCount, "Build count wrong")
    }
}

private fun addExtraFiles(dir: Path) {
    // Fake git content.
    dir.resolve(".git").also { git ->
        git.mkdir()
        git.resolve("whatever.txt").writeText("I'm a git data file maybe!")
    }
    // Git ignored content.
    dir.resolve(".gitignore").writeText(
        """
            |/target
            |ignore/
        """.trimMargin()
    )
    dir.resolve("target").let { target ->
        target.mkdir()
        target.resolve("generated.txt").writeText("Did some other compiler make me?")
    }
    // Extra file outside of temper module space.
    dir.resolve("extra").also { extra ->
        extra.mkdir()
        extra.resolve("unrelated.txt").writeText("I have nothing to do with temper!")
        // With an explicitly ignored thing inside it but below top level.
        extra.resolve("ignore").let { ignore ->
            ignore.mkdir()
            ignore.resolve("unwanted.txt").writeText("I'm inside a sublevel gitignored dir.")
        }
    }
}
