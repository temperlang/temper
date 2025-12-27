package lang.temper.cli

import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.csharp.CSharpBackend
import lang.temper.be.java.JavaBackend
import lang.temper.be.js.JsBackend
import lang.temper.be.lua.LuaBackend
import lang.temper.be.py.PyBackend
import lang.temper.be.py.helper.PythonSpecifics
import lang.temper.be.rust.RustBackend
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.fs.read
import lang.temper.fs.runWithTemporaryDirCopyOf
import lang.temper.log.dirPath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.interpBackendId
import lang.temper.tooling.buildrun.Build
import lang.temper.tooling.buildrun.BuildDoneResult
import lang.temper.tooling.buildrun.BuildHarness
import lang.temper.tooling.buildrun.BuildInitFailed
import lang.temper.tooling.buildrun.BuildNotNeededResult
import lang.temper.tooling.buildrun.DoRunResult
import lang.temper.tooling.buildrun.RunTask
import lang.temper.tooling.buildrun.doOneBuild
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import kotlin.io.path.exists
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DoTestTest {

    @Test
    fun testPassingInterpreter() {
        checkPassing("TestPassingInterpreter", "/testing/passing", listOf(interpBackendId))
    }

    @Test
    fun testFailingInterpreter() = runWithCopyOfTestingDir(
        "TestFailingInterpreter",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(interpBackendId), jobName, output, libraryName)
        checkFailureResults(result)
    }

    @Test
    fun testFailingCSharpAndInterp() = runWithCopyOfTestingDir(
        "TestFailingCSharpInterpreter",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val backends = listOf(CSharpBackend.Factory.backendId, interpBackendId)
        val result = doTestResult(backends, jobName, output, libraryName)
        checkFailureResults(result)
    }

    @Test
    fun testPassingCSharpBackend() {
        checkPassing(
            "TestPassingCSharpBackend",
            "/testing/passing",
            listOf(CSharpBackend.Factory.backendId),
        )
    }

    @Ignore("Slow, so we have something in integration tests instead")
    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun testPassingCSharpAndLuaBackend() {
        val backends = listOf(CSharpBackend.Factory.backendId, LuaBackend.Lua51.backendId)
        checkPassing("TestPassingLuaAndCSharpBackend", "/testing/passing", backends) { _, result ->
            val testTally = result.testTally!!
            val expected = 2 * backends.size
            assertEquals(expected, testTally.defined!!)
            assertEquals(expected, testTally.run)
            assertEquals(0, testTally.failed)
        }
    }

    @Test
    fun testActualErrorCSharpBackend() = runWithCopyOfTestingDir(
        "TestActualErrorCSharpBackend",
        "/testing/actual-error",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(CSharpBackend.Factory.backendId), jobName, output, libraryName)
        checkErrorResults(result)
    }

    @Test
    fun testFailingCSharpBackend() = runWithCopyOfTestingDir(
        "TestFailingCSharpBackend",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(CSharpBackend.Factory.backendId), jobName, output, libraryName)
        checkFailureResults(result)
    }

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun testActualErrorJava17Backend() = runWithCopyOfTestingDir(
        "TestActualErrorJava17Backend",
        "/testing/actual-error",
    ) { output, libraryName, jobName ->
        // In Java, this causes a compiler error, so the test can't run at all.
        val result = doTestResult(listOf(JavaBackend.Java17.backendId), jobName, output, libraryName)
        checkErrorResults(result)
    }

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun testFailingJava17Backend() = runWithCopyOfTestingDir(
        "TestFailingJava17Backend",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(JavaBackend.Java17.backendId), jobName, output, libraryName)
        checkErrorResults(result)
    }

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun testPassingJava17Backend() {
        checkPassing("TestPassingJava17Backend", "/testing/passing", listOf(JavaBackend.Java17.backendId))
    }

    @Test
    fun testPassingJsBackend() {
        checkPassing("TestPassingJsBackend", "/testing/passing", listOf(JsBackend.Factory.backendId))
    }

    @Test
    fun testFailingJsBackend() = runWithCopyOfTestingDir(
        "TestFailingJsBackend",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(JsBackend.Factory.backendId), jobName, output, libraryName)
        checkFailureResults(result)
    }

    @Test
    fun testPassingJsBackendAndCompilerError() = runWithCopyOfTestingDir(
        "TestPassingJsBackendAndCompilerError",
        "/testing/error",
    ) { output, libraryName, jobName ->
        // We could test this on other backends as well, but js should do well enough.
        val result = doTestResult(listOf(JsBackend.Factory.backendId), jobName, output, libraryName)
        val tally = result.testTally
        assertNotNull(tally, "Missing tally from running tests")
        assertEquals(1, tally.run, "Tests should have been run")
        assertEquals(0, tally.failed, "Tests should have passed")
        assertFalse(result.errorFree, "Tests should have had an error")
    }

    @Test
    fun testPassingLuaBackend() {
        checkPassing("TestPassingLuaBackend", "/testing/passing", listOf(LuaBackend.Lua51.backendId))
    }

    @Test
    fun testFailingLuaBackend() = runWithCopyOfTestingDir(
        "TestFailingLuaBackend",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(LuaBackend.Lua51.backendId), jobName, output, libraryName)
        checkFailureResults(result)
    }

    @Ignore // TODO: Is this a pyproject problem: 'OSError: Readme file does not exist: README.md'
    @Test
    @Timeout(MYPYC_TIMEOUT_SECONDS) // Slower than ordinary python.
    fun testPassingMypycBackend() {
        checkPassing("TestPassingMypycBackend", "/testing/passing", listOf(PyBackend.MypyC.backendId))
    }

    @Test
    @Timeout(PY_TIMEOUT_SECONDS)
    fun testPassingPyBackend() {
        checkPassing("TestPassingPyBackend", "/testing/passing", listOf(PyBackend.Python3.backendId))
    }

    @Test
    @Timeout(PY_TIMEOUT_SECONDS)
    fun testActualErrorPyBackend() = runWithCopyOfTestingDir(
        "TestActualErrorPyBackend",
        "/testing/actual-error",
    ) { output, libraryName, jobName ->
        // In Python, this causes an error other than AssertionError, which causes an "error"
        // in the report rather than a "failure".
        val result = doTestResult(listOf(PyBackend.Python3.backendId), jobName, output, libraryName)
        checkErrorResults(result)
    }

    @Test
    @Timeout(PY_TIMEOUT_SECONDS)
    fun testFailingPyBackend() = runWithCopyOfTestingDir(
        "TestFailingPyBackend",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(PyBackend.Python3.backendId), jobName, output, libraryName)
        checkFailureResults(result)
    }

    @Ignore // TODO: jgit TransportException
    @Test
    @Timeout(PY_TIMEOUT_SECONDS)
    fun testRemoteImportPyBackend() = checkPassing(
        "TestRemoteImportPyBackend",
        "/testing/remote",
        listOf(PyBackend.Python3.backendId),
        libraryName = null,
    ) { _, result ->
        val mainResult = result.details.find { it.libraryName!!.text == "use-remote" }!!
        assertEquals(1, mainResult.testNames.size)
        // While we do test everything, check a total of 22 tests for temper-regex-parser v0.3.0.
        // Current temper-regex-parser main has one more than that for a total of 23.
        // TODO Instead test something more stable. Also, this is no good if we stop testing imports.
        @Suppress("MagicNumber")
        assertEquals(23, result.testTally?.run)
        // TODO Enable this if we stop testing remote imports.
        // assertEquals(1, result.details.size)
    }

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun testFailingRustBackend() = runWithCopyOfTestingDir(
        "TestFailingRustBackend",
        "/testing/failing",
    ) { output, libraryName, jobName ->
        val result = doTestResult(listOf(RustBackend.Factory.backendId), jobName, output, libraryName)
        checkFailureResults(result)
    }

    @Test
    fun multiInterpAll() = multiInterpNamed(null)

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun multiCSharpAll() =
        checkPassing("MultiCSharp", "/testing/multi", listOf(CSharpBackend.Factory.backendId), null) { _, result ->
            checkMultiResult(null, result)
        }

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun multiJava8All() = multiJava8Named(null)

    @Test
    @Timeout(JAVA_TIMEOUT_SECONDS)
    fun multiJava17All() = multiJava17Named(null)

    @Ignore // Need to limit running to select libraries
    @Test
    fun multiJs() = multiJsNamed(testLibraryName)

    @Test
    fun multiJsAll() = multiJsNamed(null)

    @Test
    fun multiLuaAll() = multiLuaNamed(null)

    @Ignore // Need to limit running to select libraries
    @Test
    @Timeout(PY_TIMEOUT_SECONDS)
    fun multiPy() = multiPyNamed(testLibraryName)

    @Test
    @Timeout(PY_TIMEOUT_SECONDS)
    fun multiPyAll() = multiPyNamed(null)
}

private fun multiInterpNamed(libraryName: DashedIdentifier?) {
    // TODO Check tallies once we have those for interp testing.
    checkPassing("MultiInterp", "/testing/multi", listOf(interpBackendId), libraryName)
}

private fun multiJava8Named(libraryName: DashedIdentifier?) {
    checkPassing("MultiJava8", "/testing/multi", listOf(JavaBackend.Java8.backendId), libraryName) { _, result ->
        checkMultiResult(libraryName, result)
    }
}

private fun multiJava17Named(libraryName: DashedIdentifier?) {
    checkPassing("MultiJava17", "/testing/multi", listOf(JavaBackend.Java17.backendId), libraryName) { dir, result ->
        checkMultiResult(libraryName, result)
        // Make sure global is in main, and test is in test.
        val testMeSrc = dir.resolve("temper.out/java/test-me/src")
        val subdir = "java/test_me/test"
        val testMeGlobal = testMeSrc.resolve("main/$subdir/TestGlobal.java")
        val testMeTest = testMeSrc.resolve("test/$subdir/TestTest.java")
        assertTrue(testMeGlobal.exists())
        assertTrue(testMeTest.exists())
        // Check that we get test helper code in test. BuildTest has additional checks of this style.
        assertContains(testMeTest.read(), "static boolean eq")
    }
}

private fun multiJsNamed(libraryName: DashedIdentifier?) {
    checkPassing("MultiJs", "/testing/multi", listOf(JsBackend.Factory.backendId), libraryName) { _, result ->
        checkMultiResult(libraryName, result)
    }
}

private fun multiLuaNamed(libraryName: DashedIdentifier?) {
    checkPassing("MultiLua", "/testing/multi", listOf(LuaBackend.Lua51.backendId), libraryName) { _, result ->
        checkMultiResult(libraryName, result)
    }
}

private fun multiPyNamed(libraryName: DashedIdentifier?) {
    checkPassing("MultiPy", "/testing/multi", listOf(PyBackend.Python3.backendId), libraryName) { _, result ->
        checkMultiResult(libraryName, result)
    }
}

/** Check expected common results for a case with a compile-time error. */
private fun checkErrorResults(result: DoRunResult) {
    // We expect the correct total, whether the tests actually ran or not, which varies by backend.
    val tally = result.testTally
    assertNotNull(tally, "Missing test tally")
    assertEquals(1, tally.run, "Run count of $tally should be 1")
    assertEquals(1, tally.failed, "Failed count of $tally should be 1")
    assertFalse(result.ok, "Tests shouldn't have passed")
    assertFalse(result.errorFree, "Tests should have had an error")
}

/** Check expected common results for a case with a specific test failure. */
private fun checkFailureResults(
    result: DoRunResult,
    allowedFailureName: (String) -> Boolean = { it == A_TEST_CASE_TEST_NAME },
    skipDetails: Boolean = false,
) {
    assertTrue(!result.ok, "Unexpected claim of success")
    assertTrue(result.errorFreeInTemperCompiler, "Unexpected errors in Temper code")
    skipDetails && return
    // We're currently checking only one backend at a time, but might as well code for multiple.
    for (detail in result.details) {
        assertEquals(detail.testFailures.size, 1)
        val (failureName, failureMessage) = detail.testFailures.first()
        assertTrue(allowedFailureName(failureName), "failing method name: $failureName not allowed")
        assertEquals("Failed as expected", failureMessage)
    }
}

/** Check expected common results for our multi-library cases. */
private fun checkMultiResult(libraryName: DashedIdentifier?, result: DoRunResult) {
    val tally = result.testTally
    assertNotNull(tally, "Missing test tally")
    // We have differing numbers of tests depending on how many of our expected libraries we're running.
    @Suppress("MagicNumber")
    val expectedTests = when (libraryName) {
        null -> 3
        else -> 1
    }
    assertEquals(expectedTests, tally.run, "Bad tally $tally")
    // And we also expect to run all defined tests for these cases.
    assertEquals(tally.defined, tally.run, "Bad tally $tally")
}

private fun checkPassing(
    name: String,
    path: String,
    backends: List<BackendId>,
    libraryName: DashedIdentifier? = testLibraryName,
    verbose: Boolean = false,
    extraChecks: (dir: Path, result: DoRunResult) -> Unit = { _, _ -> },
) {
    runWithTemporaryDirCopyOf(name, resourcePath(path)) { dir ->
        val result = doTestResult(backends, name, dir, libraryName, verbose = verbose)
        assertTrue(result.errorFree, "Tests should have passed")
        extraChecks(dir, result)
    }
}

private fun doTestResult(
    backendIds: List<BackendId>,
    jobName: String,
    workRoot: Path,
    libraryName: DashedIdentifier?,
    verbose: Boolean = false,
): DoRunResult {
    val cliConsole = Console(console.textOutput)
    if (verbose) { cliConsole.setLogLevel(Log.Fine) }

    val shellPreferences = ShellPreferences.default(cliConsole).let { default ->
        default.copy(
            onFailure = ShellPreferences.OnFailure.Freeze,
            verbosity = if (verbose) {
                ShellPreferences.Verbosity.Verbose
            } else {
                default.verbosity
            },
        )
    }

    val realBackends = backendIds.filter { it != interpBackendId }
    val harness = BuildHarness(
        executorService = ForkJoinPool.commonPool(),
        backends = realBackends,
        workRoot = workRoot,
        ignoreFile = null,
        outDir = null,
        keepDir = null,
        shellPreferences = shellPreferences,
    )
    val libraries: Set<DashedIdentifier>? = libraryName?.let { setOf(it) }
    val build = Build(
        harness = harness,
        runTask = RunTask(
            request = RunTestsRequest(
                libraries = libraries,
                taskName = jobName,
            ),
            backends = backendIds.toSet(),
        ),
    )
    build.checkpoints.on(CliEnv.Checkpoint.postInstall) { cliEnv, _ ->
        checkInstall(cliEnv)
    }
    val buildResult = doOneBuild(build)
    val testResults = when (buildResult) {
        is BuildDoneResult -> buildResult.taskResults
        is BuildInitFailed,
        is BuildNotNeededResult,
        -> null
    }
    return DoRunResult(
        ok = buildResult.ok,
        maxBuildLogLevel = buildResult.maxLogLevel,
        outputThunk = testResults?.outputThunk ?: { "OUTPUT UNAVAILABLE" },
        testTally = testResults?.testTally,
        details = testResults?.details ?: emptyList(),
    )
}

/** Verify that we got the local version of a local dependency rather than one on an external repo. */
private fun checkInstall(cliEnv: CliEnv) {
    when (cliEnv.specifics) {
        is PythonSpecifics -> {
            // If we don't have a temper_core dir, we didn't get it from pypi. And local is good, not pypi.
            // And we actually just control pythonpath these days, by the way.
            assertFalse(cliEnv.fileExists(dirPath("py", "__pypackages__", "temper_core")))
        }
        // TODO Support other backends.
        else -> return
    }
}

/** Seen it over 90 sometimes or more when uncached. Cached still over 30. */
internal const val JAVA_TIMEOUT_SECONDS = 270L

/** Compilation can take a while. */
internal const val MYPYC_TIMEOUT_SECONDS = 300L

/** We've seen it over 30 sometimes. */
internal const val PY_TIMEOUT_SECONDS = 60L

private fun runWithCopyOfTestingDir(
    jobName: String,
    resourceDir: String,
    test: (Path, DashedIdentifier, String) -> Unit,
) {
    val path = resourcePath(resourceDir)
    runWithTemporaryDirCopyOf(jobName, path) { output ->
        test(output, testLibraryName, jobName)
    }
}

/** For convenience, use the same name for all DoTestTest libraries. */
private val testLibraryName = DashedIdentifier("test-me")

private const val A_TEST_CASE_TEST_NAME = "--== a test case ==--"
