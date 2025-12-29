package lang.temper.be.csharp

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
import lang.temper.name.BackendId
import lang.temper.name.SemVer

object CSharpSpecifics : RunnerSpecifics {
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
    ): List<ToolchainResult> = runCSharp(
        cliEnv = cliEnv,
        dependencies = @Suppress("UNCHECKED_CAST") (dependencies as Dependencies<CSharpBackend>),
        request = request,
    )

    override val tools: List<ToolSpecifics>
        get() = listOf(DotnetCommand)

    override val backendId: BackendId
        get() = CSharpBackend.Factory.backendId
}

object DotnetCommand : VersionedTool {
    override val cliNames: List<String> = listOf("dotnet")

    override val versionCheckArgs = listOf("--version")

    @Suppress("MagicNumber")
    private val minVersion = SemVer(6, 0, 0)

    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> {
        return SemVer(run.stdout.trim()).checkMin(run, minVersion)
    }

    val defaultEnv = mapOf(
        "DOTNET_CLI_TELEMETRY_OPTOUT" to "true",
        "DOTNET_NOLOGO" to "true",
        // https://learn.microsoft.com/en-gb/dotnet/core/tools/dotnet#rollforward
        // This allows using `dotnet run` when there is no SDK available
        // with the <TargetFramework>'s major version but there is one
        // with a greater major version.
        // Without this, expect to see:
        // > You must install or update .NET to run this application.
        "DOTNET_ROLL_FORWARD" to "Major",
    )
}
