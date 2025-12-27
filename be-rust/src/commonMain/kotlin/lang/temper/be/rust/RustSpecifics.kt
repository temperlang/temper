package lang.temper.be.rust

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.VersionedTool
import lang.temper.be.cli.checkMin
import lang.temper.common.RResult
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.name.SemVer

object RustSpecifics : RunnerSpecifics {
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
    ): List<ToolchainResult> = runRust(
        cliEnv = cliEnv,
        dependencies = @Suppress("UNCHECKED_CAST") (dependencies as Dependencies<RustBackend>),
        request = request,
    )

    override val backendId get() = RustBackend.Factory.backendId

    /** For now just inspect rustc and presume they have an appropriate matching cargo. */
    override val tools: List<ToolSpecifics> = listOf(RustcCommand)
}

object CargoCommand : ToolSpecifics {
    override val cliNames = listOf("cargo")
}

object RustcCommand : VersionedTool {
    override val cliNames = listOf("rustc")
    override val versionCheckArgs = listOf("--version")

    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> {
        // Example: "rustc 1.74.1 (a28077b28 2023-12-04)"
        return SemVer(run.stdout.trim().split(' ')[1]).checkMin(run, minVersion)
    }

    /**
     * Rust users often use recent Rust, but we can try to be more conservative than that.
     * Might depend on whatever dependencies we need.
     *
     * See:
     * - https://lib.rs/stats#rustc
     * - https://releases.rs/docs/1.56.0/ - Oct 2021, Rust 2021
     * - https://releases.rs/docs/1.63.0/ - Aug 2022, Const RwLock
     * - https://releases.rs/docs/1.70.0/ - Jun 2023, OnceLock
     * - https://releases.rs/docs/1.71.1/ - Aug 2023, Required for ureq
     */
    @Suppress("MagicNumber")
    val minVersion = SemVer(1, 71, 1)
}
