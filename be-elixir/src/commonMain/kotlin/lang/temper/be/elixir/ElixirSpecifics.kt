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

object MixCommand : ToolSpecifics {
    override val cliNames = listOf("mix")
}

internal fun runElixir(cliEnv: CliEnv, request: ToolchainRequest): List<ToolchainResult> {
    return when (request) {
        is RunLibraryRequest -> listOf(cliEnv.runMain(request.libraryName))
        // `mix test` is not wired up yet; the translated program runs its
        // asserts inline, as be-blimp's did at this stage.
        is RunTestsRequest -> when (val libraryName = request.libraries?.firstOrNull()) {
            null -> unavailable(cliEnv, "Elixir backend needs an explicit library to test")
            else -> listOf(cliEnv.runMain(libraryName))
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

private fun CliEnv.runMain(libraryName: DashedIdentifier): ToolchainResult {
    val runDir = relativeOutputDirectoryForLibrary(ElixirBackend.Factory.backendId, libraryName)
    val mix = this[MixCommand]
    fun step(args: List<String>, stderr: String): RResult<EffortSuccess, CliFailure> {
        val command = Command(args = args, aux = mapOf(Aux.Stderr to runDir.resolveFile(stderr)), cwd = runDir)
        command.maybeLogBeforeRunning(mix, shellPreferences)
        return mix.run(command)
    }
    val compiled = step(listOf("compile"), "compile-stderr.txt")
    if (compiled is RFailure) {
        // the compile's own failure, with its diagnostics, is the result
        return ToolchainResult(libraryName = libraryName, result = compiled)
    }
    val call = "${ElixirBackend.MAIN_MODULE}.${ElixirBackend.MAIN_FUNCTION}()"
    return ToolchainResult(
        libraryName = libraryName,
        result = step(listOf("run", "--no-compile", "-e", call), "stderr.txt"),
    )
}
