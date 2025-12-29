package lang.temper.be.py

import lang.temper.be.Backend
import lang.temper.be.FunctionalTestRunner
import lang.temper.be.assertRunOutput
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.cliEnvImplemented
import lang.temper.be.cli.effort
import lang.temper.be.cli.print
import lang.temper.be.py.helper.PythonSpecifics
import lang.temper.be.py.helper.VenvBuilt
import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.name.ModuleName
import lang.temper.result.junit.parseJunitResults
import lang.temper.tests.FunctionalTestBase
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PyFunctionalTest : AbstractPyFunctionalTest(PyBackend.Python3) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }
}

@Ignore("Runs too slow to run everything every time")
class MypyFunctionalTest : AbstractPyFunctionalTest(PyBackend.MypyC) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }
}

abstract class AbstractPyFunctionalTest(
    factory: Backend.Factory<PyBackend>,
) : FunctionalTestRunner<PyBackend>(factory) {

    private object TrackVenvBuilt : VenvBuilt {
        val libs: MutableSet<FilePath> = mutableSetOf()

        @Synchronized
        override fun alreadyBuilt(path: FilePath): Boolean = path in libs

        @Synchronized
        override fun built(path: Collection<FilePath>) {
            libs.addAll(path)
        }
    }

    override fun runGeneratedCode(
        backend: PyBackend,
        modules: List<Module>,
        outputRoot: OutputRoot,
        outputDir: OutDir,
        outputPaths: Map<ModuleName, FilePath>,
        test: FunctionalTestBase,
        request: ToolchainRequest,
    ) {
        if (!cliEnvImplemented) {
            return
        }

        check(outputDir.hasAncestor(outputRoot))
        val shellPreferences = ShellPreferences.functionalTests(console)

        CliEnv.using(PythonSpecifics, shellPreferences, cancelGroup) {
            // Copy libs into cli env.
            copyOutputDir(outputRoot, FilePath.emptyPath)
            copyResources(
                factory.coreLibraryResources,
                FilePath(
                    listOf(factory.backendId.uniqueId, "temper-core").map { FilePathSegment(it) },
                    isDir = true,
                ),
            )
            factory.processCoreLibraryResources(this, console)
            // Run.
            val result = runPyBestEffort(
                pythonVersion = backend.pythonVersion,
                cliEnv = this,
                request = request,
                files = outputDir,
                dependencies = backend.getDependencies(),
                backendId = backend.backendId,
                built = TrackVenvBuilt,
            ).first().result
            // Check.
            var pass = false
            try {
                if (test.runAsTest) {
                    // We allow test runs to fail.
                    val effort = result.effort() as EffortSuccess
                    val junitOutput = effort.auxOut[Aux.JunitXml]
                    val parsedResults = parseJunitResults(junitOutput)

                    // Check expected failures.
                    val expected = test.expectedTestFailures.map { it.key.lowercase() }.sorted()
                    val actual = parsedResults.failures.map { it.name.lowercase() }.sorted()
                    assertEquals(expected, actual, "Raw aux output:$junitOutput\n\nstd:${effort.stdout}")
                    // N^2 check for output, but expected small for these tests. Allows some sloppiness on our part.
                    for (value in test.expectedTestFailures.values) {
                        assertTrue(parsedResults.failures.any { value in it.cause }, "no matching cause found")
                    }
                    // Assert the JUnit output to enable testing test failure and the test code itself
                    assertTrue(!junitOutput.isNullOrBlank())
                    // Also assert no warnings.
                    assertFalse("warning" in effort.stdout, "Warnings found")
                } else {
                    test.assertRunOutput(result)
                }
                pass = true
            } finally {
                if (!pass) {
                    dumpModuleBodies(modules)
                    result.print(console)
                }
            }
        }
    }
}
