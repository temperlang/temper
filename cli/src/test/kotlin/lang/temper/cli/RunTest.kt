package lang.temper.cli

import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.csharp.CSharpBackend
import lang.temper.be.java.JavaBackend
import lang.temper.be.js.JsBackend
import lang.temper.be.lua.LuaBackend
import lang.temper.be.py.PyBackend
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.fs.TEMPER_OUT_NAME
import lang.temper.fs.removeDirRecursive
import lang.temper.fs.runWithTemporaryDirCopyOf
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.interpBackendId
import lang.temper.supportedBackends.defaultBackend
import lang.temper.tooling.buildrun.BuildDoneResult
import lang.temper.tooling.buildrun.BuildInitFailed
import lang.temper.tooling.buildrun.BuildNotNeededResult
import lang.temper.tooling.buildrun.DoRunResult
import lang.temper.tooling.buildrun.RunTask
import lang.temper.tooling.buildrun.doOneBuild
import lang.temper.tooling.buildrun.prepareBuild
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import kotlin.io.path.toPath
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunTest {

    @Test
    fun runInterpreter() {
        val path = resourcePath("/build/input")
        // Clean up in case someone ran a build in the source tree.
        removeDirRecursive(path.resolve(TEMPER_OUT_NAME))
        runWithTemporaryDirCopyOf("RunInterpreter", path) { tempDir ->
            doRun(
                RunTask(
                    backends = setOf(interpBackendId),
                    request = RunLibraryRequest(
                        DashedIdentifier.from("a")!!,
                        taskName = "RunInterpreter",
                    ),
                ),
                workRoot = tempDir,
            )
        }
    }

    @Test
    fun runInterpreterTestIntermediate() {
        val path = resourcePath("/build/input/testIntermediate")
        val jobName = "RunInterpreterTestIntermediate"
        // Clean up in case someone ran a build in the source tree.
        removeDirRecursive(path.resolve(TEMPER_OUT_NAME))
        val result = runWithTemporaryDirCopyOf(jobName, path) { tempDir ->
            doRun(
                RunTask(
                    backends = setOf(interpBackendId),
                    request = RunLibraryRequest(
                        DashedIdentifier.from("test")!!,
                        taskName = "RunInterpreterTestIntermediate",
                    ),
                ),
                workRoot = tempDir,
            )
        }
        val logs = result.outputThunk()
        assertTrue(result.errorFree, "wanted errorFree run:\n$logs")
        assertContains(
            logs,
            "<failure message='Failed as expected' />",
            message = "Expected some log output",
        )
    }

    @Test
    fun runCSharpBackendTop() = runRunTest("runTopLevel", CSharpBackend.Factory.backendId)

    @Test
    fun runWithImportCSharpBackend() = runRunTest("runWithImport", CSharpBackend.Factory.backendId)

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun runJavaBackend() = runRunTest("run", JavaBackend.Java17.backendId)

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun runJavaBackendTop() = runRunTest("runTopLevel", JavaBackend.Java17.backendId)

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun runWithImportJavaBackend() = runRunTest("runWithImport", JavaBackend.Java17.backendId)

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun runJava8Backend() = runRunTest("run", JavaBackend.Java8.backendId)

    @Test
    fun runJsBackend() = runRunTest("run", JsBackend.Factory.backendId)

    @Ignore("Reexamine how import strategy and run tasks affect running only cherry-picked modules")
    @Test
    fun runJsBackendTop() = runRunTest("runTopLevel", JsBackend.Factory.backendId)

    @Test
    fun runWithImportJsBackend() = runRunTest("runWithImport", JsBackend.Factory.backendId)

    @Test
    fun runWithStdImportDefaultBackend() {
        val defaultBackend = defaultBackend.value
        val path = resourcePath("/runWithStdImport/input")
        // Clean up in case someone ran a build in the source tree.
        removeDirRecursive(path.resolve(TEMPER_OUT_NAME))
        runWithTemporaryDirCopyOf("RunWithStdImportDefaultBackend", path) { tempDir ->
            val result = doRun(
                RunTask(
                    // default == js
                    backends = setOf(defaultBackend.backendId),
                    request = RunLibraryRequest(
                        DashedIdentifier.from("a")!!,
                        taskName = "RunWithStdImportDefaultBackend",
                    ),
                ),
                workRoot = tempDir,
            )
            assert(result.errorFree)
        }
    }

    @Test
    fun runLuaBackend() = runRunTest("run", LuaBackend.Lua51.backendId)

    @Test
    fun runLuaBackendTop() = runRunTest("runTopLevel", LuaBackend.Lua51.backendId)

    @Test
    fun runLuaBackendWithImport() = runRunTest("runWithImport", LuaBackend.Lua51.backendId)

    @Test
    fun runWithPathedModuleDefaultBackend() {
        val defaultBackend = defaultBackend.value
        val path = resourcePath("/pathedModuleRun")
        // Clean up in case someone ran a build in the source tree.
        removeDirRecursive(path.resolve(TEMPER_OUT_NAME))
        runWithTemporaryDirCopyOf("RunWithPathedModuleDefaultBackend", path) { tempDir ->
            val result = doRun(
                RunTask(
                    // default == js
                    backends = setOf(defaultBackend.backendId),
                    request = RunLibraryRequest(
                        DashedIdentifier.from("a")!!,
                        taskName = "RunWithPathedModuleDefaultBackend",
                    ),
                ),
                workRoot = tempDir,
            )
            assert(result.errorFree)
        }
    }

    @Test
    fun runPyBackend() = runRunTest("run", PyBackend.Python3.backendId)

    @Ignore("Need to reexamine imports to allow importing top level without importing other modules")
    @Test
    fun runPyBackendTop() = runRunTest("runTopLevel", PyBackend.Python3.backendId)

    @Test
    fun runWithImportPyBackend() = runRunTest("runWithImport", PyBackend.Python3.backendId)
}

internal fun shellPreferencesForTest(cliConsole: Console = lang.temper.common.console) =
    ShellPreferences.default(cliConsole)
        .copy(onFailure = ShellPreferences.OnFailure.Freeze)

internal fun resourcePath(resource: String): Path {
    return RunTest::class.java.getResource(resource)!!.toURI().toPath()
}

private fun runRunTest(testName: String, backendId: BackendId) {
    val jobName = "RunTest-$testName-${backendId.uniqueId}"
    val path = resourcePath("/$testName/input")
    // Clean up in case someone ran a build in the source tree.
    removeDirRecursive(path.resolve(TEMPER_OUT_NAME))
    runWithTemporaryDirCopyOf(jobName, path) { tempDir ->
        val result = doRun(
            RunTask(
                backends = setOf(backendId),
                request = RunLibraryRequest(DashedIdentifier.from("a")!!, jobName),
            ),
            workRoot = tempDir,
        )
        val actualOutput = result.outputThunk()
        expectedOutput[testName]?.let { assertEquals(it, actualOutput) }
        assert(result.errorFree)
    }
}

private val expectedOutput = mapOf(
    "run" to "I ran\n",
    "runTopLevel" to "top\n",
    "runWithImport" to "bar\n",
)

private fun doRun(
    runTask: RunTask,
    workRoot: Path,
    shellPreferences: ShellPreferences = shellPreferencesForTest(),
): DoRunResult {
    val backendIds = runTask.backends.toList()
    val build = prepareBuild(
        executorService = ForkJoinPool.commonPool(),
        backends = backendIds - interpBackendId,
        workRoot = workRoot,
        ignoreFile = null,
        shellPreferences = shellPreferences,
        moduleConfig = ModuleConfig(mayRun = interpBackendId in backendIds),
        runTask = runTask,
    )
    val buildResult = build?.let { doOneBuild(it) }
        ?: BuildInitFailed(false, Log.Error)
    return when (buildResult) {
        is BuildDoneResult -> buildResult.taskResults!!
        is BuildInitFailed,
        is BuildNotNeededResult,
        -> DoRunResult(
            ok = false,
            maxBuildLogLevel = buildResult.maxLogLevel,
            outputThunk = { "" },
            details = emptyList(),
        )
    }
}
