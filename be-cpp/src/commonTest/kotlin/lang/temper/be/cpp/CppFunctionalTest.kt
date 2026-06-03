package lang.temper.be.cpp

import lang.temper.be.FunctionalTestRunner
import lang.temper.be.assertRunOutput
import lang.temper.be.assertTestingTest
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.cliEnvImplemented
import lang.temper.be.cli.print
import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.log.FilePath
import lang.temper.name.ModuleName
import lang.temper.tests.FunctionalTestBase
import lang.temper.tests.FunctionalTests
import kotlin.test.Test

class CppFunctionalTest : FunctionalTestRunner<CppBackend>(CppBackend.Cpp11) {

    @Test
    override fun algosHelloWorld() {
        runFunctionalTest(FunctionalTests.AlgosHelloWorld)
    }

    override fun runGeneratedCode(
        backend: CppBackend,
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

        CliEnv.using(Cpp11Specifics, shellPreferences, backend.cancelGroup) {
            copyOutputDir(outputRoot, FilePath.emptyPath)
            copyCppTemperCore(factory)
            val specifics = specifics as Cpp11Specifics
            val result = specifics.runBestEffort(
                cliEnv = this,
                request = request,
                code = outputRoot,
                dependencies = backend.getDependencies(),
            ).first().result
            var pass = false
            try {
                if (test.runAsTest) {
                    assertTestingTest(test, result)
                } else {
                    test.assertRunOutput(result)
                }
                pass = true
            } finally {
                if (!pass) {
                    dumpModuleBodies(modules)
                    // Surface the C++ compiler's stderr, which is otherwise hidden and is usually
                    // what's needed to diagnose a generated-code compilation failure.
                    val effort = result.failure?.effort
                    if (effort is lang.temper.be.cli.EffortSuccess) {
                        val stderr = effort.auxOut[lang.temper.be.cli.Aux.Stderr]
                        if (stderr != null) {
                            System.err.println("C++ compiler stderr:\n$stderr")
                        }
                    }
                    result.print(console, asError = true)
                }
            }
        }
    }
}
