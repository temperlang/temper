package lang.temper.be.elixir

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
import lang.temper.log.dirPath
import lang.temper.name.ModuleName
import lang.temper.tests.FunctionalTestBase
import lang.temper.tests.MarkdownFileBasedFunctionalTestBase
import kotlin.test.Test

/**
 * Runs the shared functional test suite against the Elixir backend.
 *
 * The build writes `elixir/<library>/` into the output root and temper-core
 * is copied to `elixir/temper-core/`, where the library's `mix.exs` looks
 * for it. [runElixir] runs `mix deps.get`, `mix compile`, then `mix run` with
 * `elixir/<library>` as its working directory.
 *
 * Which tests run is decided by `onlyPasses(elixir(), ...)` in
 * `FunctionalTestStatus.kt`; everything outside that list is skipped, so
 * widening the list is how this backend records progress.
 */
class ElixirFunctionalTest : FunctionalTestRunner<ElixirBackend>(ElixirBackend.Factory) {
    @Test
    override fun algosHelloWorld() {
        super.algosHelloWorld()
    }

    /**
     * A class inheriting getters, a setter and methods with bodies from
     * interfaces declared in two other libraries, built as three libraries
     * and run together, the way `temper build` lays them out.
     */
    @Test
    fun classesInheritFromOtherLibrariesInterfaces() {
        runFunctionalTest(CrossLibraryInterfaces)
    }

    override fun runGeneratedCode(
        backend: ElixirBackend,
        modules: List<Module>,
        outputRoot: OutputRoot,
        outputDir: OutDir,
        outputPaths: Map<ModuleName, FilePath>,
        test: FunctionalTestBase,
        request: ToolchainRequest,
    ) {
        CliEnv.using(factory.specifics, ShellPreferences.functionalTests(console), cancelGroup) {
            copyOutputDir(outputRoot, FilePath.emptyPath)
            // `temper build` lays temper-core down beside the library, but the
            // output root here holds only what translate() returned, so the
            // core library is copied in the way be-rust copies its crate
            copyResources(
                factory.coreLibraryResources,
                FilePath(
                    listOf(factory.backendId.uniqueId, ElixirBackend.CORE_DIR).map { FilePathSegment(it) },
                    isDir = true,
                ),
            )
            val result = runElixir(cliEnv = this, request = request).first().result
            var pass = false
            try {
                when {
                    test.runAsTest -> assertTestingTest(test, result)
                    else -> test.assertRunOutput(result)
                }
                pass = true
            } finally {
                if (!pass) {
                    // while the backend is young, the TmpL is usually the
                    // thing worth reading when the output does not match
                    dumpModuleBodies(modules)
                    result.print(console)
                }
            }
        }
    }
}

/**
 * `cross-library-interfaces/`: `main.temper.md` and the libraries beside it
 * that it imports, each a directory with its own `config.temper.md`.
 */
private object CrossLibraryInterfaces : MarkdownFileBasedFunctionalTestBase() {
    override val testName = "crossLibraryInterfaces"
    override val projectPath = dirPath(
        "be-elixir", "src", "commonTest", "resources", "lang", "temper", "be", "elixir", "cross-library-interfaces",
    )
    override val sourcePath = projectPath
}
