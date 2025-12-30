package lang.temper.be.java

import lang.temper.be.Backend
import lang.temper.be.FunctionalTestRunner
import lang.temper.be.assertRunOutput
import lang.temper.be.assertTestingTest
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.cliEnvImplemented
import lang.temper.be.cli.print
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.plus
import lang.temper.name.ModuleName
import lang.temper.tests.FunctionalTestBase
import kotlin.test.Test

class Java8FunctionalTest : JavaFunctionalTest(JavaBackend.FunctionalTestJava8) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }
}

class Java17FunctionalTest : JavaFunctionalTest(JavaBackend.FunctionalTestJava17) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }
}

abstract class JavaFunctionalTest(factory: Backend.Factory<JavaBackend>) : FunctionalTestRunner<JavaBackend>(factory) {

    override fun runGeneratedCode(
        backend: JavaBackend,
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

        updateRegexIfPresent(outputDir)

        val mainModuleName: ModuleName = test.mainModuleName
        val moduleInfo = ModuleInfo(
            packageName = QualifiedName.fromTemperPath(mainModuleName.sourceFile),
            module = mainModuleName,
        )
        val runJava = if (test.runAsTest) RunAsTest else RunByExec(moduleInfo.entryQualifiedName, bundled = true)
        CliEnv.using(factory.specifics, ShellPreferences.functionalTests(console), cancelGroup) {
            val result = runJavaBestEffort(
                cliEnv = this,
                factory = factory as JavaBackend.JavaFactory,
                runJava = runJava,
                runLibrary = test.libraryName,
                files = outputDir,
                taskName = request.taskName,
                dependencies = backend.getDependencies(),
                bundled = true,
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
                    result.print(console)
                }
            }
        }
    }
}

private fun updateRegexIfPresent(outputDir: OutDir) {
    val regexPath = outputDir.path + dirPath("src", "main", "java", "work", "regex")
    val fs = outputDir.root.fs
    when (val regexFiles = fs.directoryListing(regexPath)) {
        is RSuccess -> {
            // Further hack redirect for bundled regex.Core references.
            // TODO Once we stop doing "bundled" mode, remove all this.
            val unbundledName = "temper.std.regex.Core"
            for (file in regexFiles.result) {
                when (val read = fs.readBinaryFileContentSync(file)) {
                    is RFailure -> console.error(read.failure)
                    is RSuccess -> {
                        val content = try {
                            read.result.decodeToString(throwOnInvalidSequence = true)
                        } catch (ex: CharacterCodingException) {
                            console.error(ex)
                            null
                        }
                        if (content != null && unbundledName in content) {
                            val updated = content
                                .replace("import $unbundledName;\n", "")
                                .replace(unbundledName, "work.regex.Core")
                            fs.write(file, updated.encodeToByteArray())
                        }
                    }
                }
            }
        }
        is RFailure -> console.error(regexFiles.failure)
    }
}
