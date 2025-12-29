package lang.temper.be.js

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
import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.name.ModuleName
import lang.temper.result.junit.parseJunitResults
import lang.temper.tests.FunctionalTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsFunctionalTest : FunctionalTestRunner<JsBackend>(JsBackend.Factory) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }

    override fun runGeneratedCode(
        backend: JsBackend,
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

        val shellPreferences = ShellPreferences.functionalTests(console)

        CliEnv.using(NodeSpecifics, shellPreferences, cancelGroup) {
            // Copy libs into cli env.
            copyOutputDir(outputRoot, FilePath.emptyPath)
            copyResources(
                JsBackend.Factory.coreLibraryResources,
                FilePath(
                    listOf(JsBackend.BACKEND_ID, "temper-core").map { FilePathSegment(it) },
                    isDir = true,
                ),
            )
            // Run.
            val result = runJsBestEffort(
                cliEnv = this,
                request = request,
                files = outputRoot,
                dependencies = backend.getDependencies(),
            ).first().result
            // Check.
            var pass = false
            try {
                if (test.runAsTest) {
                    val effort = result.effort() as EffortSuccess
                    val junitXml = effort.auxOut[Aux.JunitXml]
                    val parsedResults = parseJunitResults(junitXml)

                    // lowercase both side to remove any inconsistencies between platforms and naming strategies
                    assertEquals(
                        test.expectedTestFailures.map { it.key.lowercase() }.sorted(),
                        parsedResults.failures.map { it.name.lowercase() }.sorted(),
                    )
                    // N^2 check for output, but expected small for these tests. Allows some sloppiness on our part.
                    for (value in test.expectedTestFailures.values) {
                        assertTrue(parsedResults.failures.any { value in it.cause }, "no matching cause found")
                    }
                    // assertTrue(ok) doesn't validate failing tests and prevents testing the test code.
                    assertTrue(!junitXml.isNullOrBlank())
                } else {
                    test.assertRunOutput(result)
                }
                pass = true
            } finally {
                if (!pass) {
                    dumpModuleBodies(modules)
                    result.print(console, asError = true)
                }
            }
        }
    }
}

internal expect val runJsWorks: Boolean
