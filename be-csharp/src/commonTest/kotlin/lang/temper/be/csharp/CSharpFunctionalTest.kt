package lang.temper.be.csharp

import lang.temper.be.FunctionalTestRunner
import lang.temper.be.assertRunOutput
import lang.temper.be.assertTestingTest
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.print
import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.name.ModuleName
import lang.temper.tests.FunctionalTestBase
import kotlin.test.Test

class CSharpFunctionalTest : FunctionalTestRunner<CSharpBackend>(CSharpBackend.Factory) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }

    override fun runGeneratedCode(
        backend: CSharpBackend,
        modules: List<Module>,
        outputRoot: OutputRoot,
        outputDir: OutDir,
        outputPaths: Map<ModuleName, FilePath>,
        test: FunctionalTestBase,
        request: ToolchainRequest,
    ) {
        CliEnv.using(factory.specifics, ShellPreferences.functionalTests(console), cancelGroup) {
            // Copy
            copyOutputDir(outputRoot, FilePath.emptyPath)
            copyResources(
                factory.coreLibraryResources,
                FilePath(
                    listOf(factory.backendId.uniqueId, "temper-core").map { FilePathSegment(it) },
                    isDir = true,
                ),
            )
            // Run
            val result = runCSharp(
                cliEnv = this,
                dependencies = backend.getDependencies(),
                request = request,
            ).first().result
            // Check
            var pass = false
            try {
                when {
                    test.runAsTest -> assertTestingTest(test, result)
                    else -> test.assertRunOutput(result)
                }
                pass = true
            } finally {
                if (!pass) {
                    result.print(console)
                }
            }
        }
    }
}
