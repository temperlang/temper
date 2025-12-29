package lang.temper.be.cli

import lang.temper.be.Dependencies
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.flatMap
import lang.temper.fs.KCharset
import lang.temper.fs.KCharsets
import lang.temper.fs.OutDir
import lang.temper.fs.utf8
import lang.temper.log.FilePath
import lang.temper.log.filePath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier

/**
 * Details of how to configure a temporary environment to run the given language.
 * We generally expect a specifics instance to be an `object` singleton.
 */
interface Specifics {
    /** identifies the individual tools expected to be present in the environment */
    val tools: List<ToolSpecifics>

    /** the name of the encoding all these tools use in common */
    val preferredEncoding: KCharset get() = KCharsets.utf8

    /** the name of the backend that this specifics object is designed for */
    val backendId: BackendId

    /** Check that all tools are present and have the correct version. */
    fun validate(cliEnv: CliEnv): RResult<Unit, CliFailure> {
        var result: RResult<Unit, CliFailure> = RSuccess(Unit)
        for (toolSpecifics in tools) {
            // Skip tools that don't have a version flag.
            if (toolSpecifics !is VersionedTool) continue
            result = result.flatMap {
                cliEnv.which(toolSpecifics).flatMap { tool ->
                    val stderr = filePath("version-stderr")
                    val cmd = Command(
                        toolSpecifics.versionCheckArgs,
                        cwd = FilePath.emptyPath,
                        aux = mapOf(Aux.Stderr to stderr),
                    )
                    tool.run(cmd).flatMap { run ->
                        toolSpecifics.checkVersion(run).also {
                            cliEnv.remove(stderr)
                        }
                    }
                }
            }
            if (result is RFailure) {
                break
            }
        }
        return result
    }
}

/** A runner has various methods to run . */
interface RunnerSpecifics : Specifics {
    /** This runs an interpreter with a single source file. If a cliEnv is not provided, one is created. */
    fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String> = mapOf(),
        aux: Map<Aux, FilePath> = mapOf(),
    ): RResult<EffortSuccess, CliFailure>

    /**
     * This corresponds to the "best effort" convention.
     * TODO replace with a cleaner system.
     */
    fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Map<BackendId, Dependencies<*>>,
    ): List<ToolchainResult> = run {
        val ownDependencies = dependencies.getValue(backendId)
        runBestEffort(cliEnv, request, code, ownDependencies)
    }

    /**
     * This corresponds to the "best effort" convention.
     * TODO replace with a cleaner system.
     */
    fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> {
        error("Dependencies expected for multiple backends")
    }
}

data class ToolchainResult(
    val libraryName: DashedIdentifier? = null,
    val result: RResult<EffortSuccess, CliFailure>,
)

/**
 * The specifics of a single tool used in a [CliEnv] to obtain a [CliTool].
 * [cliNames] should specify valid names for this tool; most specific to least. `.exe` is added for you on Windows.
 */
interface ToolSpecifics {
    val cliNames: List<String>
}

/**
 * Versioned tools are verified in the local environment.
 */
interface VersionedTool : ToolSpecifics {
    /** Arguments to check the version, e.g. `listOf("--version")` */
    val versionCheckArgs: List<String>

    /**
     * If a command is invoked with [versionCheckArgs] and produces stdout, this method should parse the result and
     * post a rejection if the version is incorrect.
     */
    fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure>
}
