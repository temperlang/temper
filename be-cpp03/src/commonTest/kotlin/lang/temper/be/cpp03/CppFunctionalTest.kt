package lang.temper.be.cpp03

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

class Cpp03FunctionalTest : CppFunctionalTest(CppVersion.Cpp03) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }
}

class Cpp11FunctionalTest : CppFunctionalTest(CppVersion.Cpp11) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }
}

class Cpp23FunctionalTest : CppFunctionalTest(CppVersion.Cpp23) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }
}

abstract class CppFunctionalTest(val version: CppVersion) : FunctionalTestRunner<CppBackend>(CppBackend.Factory) {
    override fun runGeneratedCode(
        backend: CppBackend,
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
            val result = runCpp(
                cliEnv = this,
                dependencies = backend.getDependencies(),
                request = request,
                version = version,
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
