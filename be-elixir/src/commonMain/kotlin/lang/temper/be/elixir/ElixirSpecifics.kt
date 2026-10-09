package lang.temper.be.elixir

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.EXIT_UNAVAILABLE
import lang.temper.be.cli.Effort
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.maybeLogBeforeRunning
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.fs.OutDir
import lang.temper.library.relativeOutputDirectoryForLibrary
import lang.temper.log.FilePath
import lang.temper.log.resolveFile
import lang.temper.name.DashedIdentifier

/**
 * How to run translated Elixir: `mix compile`, then `mix run --no-compile`.
 *
 * Two commands, not one, because `mix run` prints `Compiling 1 file (.ex)`
 * and `Generated temper_main app` on stdout before the program's own output,
 * and stdout is what a run is judged by. `mix run --no-compile` after a
 * separate compile prints only what the program prints.
 */
object ElixirSpecifics : RunnerSpecifics {
    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> {
        TODO("Not yet implemented")
    }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> = runElixir(cliEnv, request)

    override val backendId get() = ElixirBackend.Factory.backendId

    override val tools: List<ToolSpecifics> = listOf(MixCommand)
}

/** How long a translated program may run before the watchdog halts it. */
internal const val RUN_TIMEOUT_MS = 60_000

object MixCommand : ToolSpecifics {
    override val cliNames = listOf("mix")
}

internal fun runElixir(cliEnv: CliEnv, request: ToolchainRequest): List<ToolchainResult> {
    return when (request) {
        is RunLibraryRequest -> listOf(cliEnv.runMain(request.libraryName))
        // every library asked for, as js and py do: taking the first, a
        // workspace of two libraries ran one's tests and said nothing of the other's
        is RunTestsRequest -> when (val libraries = request.libraries) {
            null -> unavailable(cliEnv, "Elixir backend needs an explicit library to test")
            else -> libraries.map { cliEnv.runMain(it, tests = true) }
        }
        is RunBackendSpecificCompilationStepRequest -> error(request)
        is ExecInteractiveRepl -> unavailable(cliEnv, "Elixir backend does not yet drive `iex -S mix`")
    }
}

private fun unavailable(cliEnv: CliEnv, message: String) =
    listOf(
        ToolchainResult(
            result = RFailure(
                CliFailure(message = message, effort = Effort(exitCode = EXIT_UNAVAILABLE, cliEnv = cliEnv)),
            ),
        ),
    )

private fun CliEnv.runMain(libraryName: DashedIdentifier, tests: Boolean = false): ToolchainResult {
    val runDir = relativeOutputDirectoryForLibrary(ElixirBackend.Factory.backendId, libraryName)
    // `this[MixCommand]` force-unwraps the lookup, so a machine without
    // Elixir failed every run with a NullPointerException that named no tool
    val found = which(MixCommand)
    val mix = found.result ?: return ToolchainResult(
        libraryName = libraryName,
        result = RFailure(
            CliFailure(
                message = "be-elixir needs `mix` (Elixir 1.15 or later) on the PATH" +
                    (found.failure?.message?.let { ": $it" } ?: ""),
                effort = Effort(exitCode = EXIT_UNAVAILABLE, cliEnv = this),
            ),
        ),
    )
    fun step(args: List<String>, stderr: String): RResult<EffortSuccess, CliFailure> {
        val aux = mapOf(Aux.Stderr to runDir.resolveFile(stderr)) +
            if (tests) mapOf(Aux.JunitXml to runDir.resolveFile(ElixirBackend.TEST_RESULTS_FILE)) else mapOf()
        // tests live in test/support/, which Mix compiles only for :test
        val env = if (tests) mapOf("MIX_ENV" to "test") else mapOf()
        val command = Command(args = args, aux = aux, cwd = runDir, env = env)
        command.maybeLogBeforeRunning(mix, shellPreferences)
        return mix.run(command)
    }
    val compiled = step(listOf("compile"), "compile-stderr.txt")
    if (compiled is RFailure) {
        // the compile's own failure, with its diagnostics, is the result
        return ToolchainResult(libraryName = libraryName, result = compiled)
    }
    // A watchdog, so a program that never finishes halts with a message
    // instead of hanging whatever ran it. The first translated loop that
    // forgot to carry a variable spun for twenty minutes before this.
    val root = ElixirBackend.libraryModule(libraryName).joinToString(".")
    val call =
        "spawn(fn -> Process.sleep($RUN_TIMEOUT_MS); IO.puts(:stderr, \"timed out after $RUN_TIMEOUT_MS ms\"); " +
            "System.halt(124) end); $root.${ElixirBackend.MAIN_FUNCTION}()" +
            // the module's top level runs first: tests read the values it sets
            if (tests) {
                // a library with no tests has no test module; its report is empty
                val tests = "$root.${ElixirBackend.TEST_MODULE}"
                "; File.write!(\"${ElixirBackend.TEST_RESULTS_FILE}\", " +
                    "if(Code.ensure_loaded?($tests), do: $tests.${ElixirBackend.TESTS_FUNCTION}(), " +
                    "else: TemperCore.Test.run_cases([])))"
            } else {
                ""
            }
    return ToolchainResult(
        libraryName = libraryName,
        result = step(listOf("run", "--no-compile", "-e", call), "stderr.txt"),
    )
}
