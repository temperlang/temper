package lang.temper.tooling.buildrun

import lang.temper.be.Dependencies
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.RFailure
import lang.temper.common.currents.CancelGroup
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.fs.RealWritableFileSystem
import lang.temper.name.BackendId
import lang.temper.supportedBackends.lookupFactory
import java.nio.file.Path

internal class Runner(
    val args: RunArgs,
    val request: ToolchainRequest,
    private val cancelGroup: CancelGroup,
) {
    val checkpoints = CliEnv.Checkpoints()

    fun doRun(
        harness: BuildHarness,
        built: BuildDoneResult,
    ): DoRunResult {
        val realResults = if (args.backendIds.isNotEmpty()) {
            runBackendsForModuleSync(
                backendIds = args.backendIds,
                outputFileSystem = harness.outputFileSystem,
                built = built,
            )
        } else {
            emptyMap()
        }

        var testTally: TestTally
        val results = buildList {
            testTally = tallyResults(realResults, args.cliConsole, resultDetails = this)
        }

        return DoRunResult(
            ok = results.all { it.ok },
            maxBuildLogLevel = results.maxOfOrNull { it.maxBuildLogLevel } ?: Log.levels.first(),
            outputThunk = if (results.size == 1) {
                { results.first().output }
            } else {
                {
                    results.joinToString { result ->
                        "Job: ${args.jobName} Backend: ${result.backendId}\n${result.output}"
                    }
                }
            },
            testTally = testTally,
            details = results,
        )
    }

    private fun runBackendsForModuleSync(
        backendIds: List<BackendId>,
        outputFileSystem: RealWritableFileSystem,
        built: BuildDoneResult,
    ): Map<BackendId, List<BackendRunResult>> {
        val outputRoot = OutputRoot(outputFileSystem)
        val runResultLists = backendIds.map { backendId ->
            runRequest(backendId, built, outputRoot)
        }
        // Each Effort result includes cwd, which implies library and backend.
        // TODO Add them explicitly?
        return backendIds.zip(runResultLists).associate { (backendId, runResults) ->
            backendId to runResults.map {
                BackendRunResult(built.maxLogLevel, it, built.dependencies[backendId])
            }
        }
    }

    private fun runRequest(
        backendId: BackendId,
        buildResult: BuildDoneResult,
        outputRoot: OutputRoot,
    ): List<ToolchainResult> {
        val dependencies = buildResult.dependencies[backendId]
        val specializedRequest =
            dependencies?.let {
                request.specializeForBackend(backendId, it)
            }
                ?: return listOf(
                    ToolchainResult(result = RFailure(CliFailure("Cannot specialize $request for $backendId"))),
                )
        // Currently, results only imply which library was being tested for multi tests.
        return runOnBackend(
            backendId = backendId,
            request = specializedRequest,
            files = outputRoot,
            dependencies = buildResult.dependencies,
        )
    }

    private fun lookupSpecifics(backendId: BackendId): RunnerSpecifics =
        lookupFactory(backendId)!!.specifics

    private fun <T> usingCliEnv(
        specifics: RunnerSpecifics,
        shellPreferences: ShellPreferences,
        outDir: OutDir,
        block: CliEnv.() -> T,
    ): T = CliEnv.using(specifics, shellPreferences, cancelGroup, outDir) {
        this@using.checkpoints.addAll(this@Runner.checkpoints)
        block()
    }

    /**
     * Find the interpreter for a backend and run files in the given OutDir.
     */
    private fun runOnBackend(
        backendId: BackendId,
        files: OutDir,
        request: ToolchainRequest,
        dependencies: Map<BackendId, Dependencies<*>>,
    ): List<ToolchainResult> =
        lookupSpecifics(backendId).let { specifics ->
            usingCliEnv(specifics, args.shellPreferences, files) {
                specifics.runBestEffort(
                    cliEnv = this,
                    request = request,
                    code = files,
                    dependencies = dependencies,
                )
            }
        }
}

internal data class RunArgs(
    val backendIds: List<BackendId>,
    val jobName: String,
    /** The root directory under which to find source files. */
    val workRoot: Path,
    /** Defaults to [workRoot] but actual current dir can be passed in. */
    val currentDirectory: Path,
    val shellPreferences: ShellPreferences,
) {
    val cliConsole: Console get() = shellPreferences.console
}

internal class SynchronizedPrintBuffer {
    private val buffer = StringBuilder()

    @Synchronized
    fun append(str: String) { buffer.append(str) }

    override fun toString(): String = "$buffer"
}
