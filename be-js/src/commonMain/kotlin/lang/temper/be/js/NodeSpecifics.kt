package lang.temper.be.js

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.VersionedTool
import lang.temper.be.cli.check
import lang.temper.be.cli.composing
import lang.temper.be.cli.noCheck
import lang.temper.common.RResult
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.name.BackendId
import lang.temper.name.SemVer

val leadingV = Regex("^\\s*v")

object NodeSpecifics : RunnerSpecifics {
    override val tools = listOf(NodeCommand, NpmCommand)
    override val backendId: BackendId = BackendId("js")

    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> =
        cliEnv.composing(this) {
            this[NodeCommand].run(Command(args = listOf("-e", code), env = env, aux = aux))
        }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> = runJsBestEffort(
        cliEnv = cliEnv,
        request = request,
        files = code,
        dependencies = dependencies,
    )
}

// According to https://github.com/nodejs/Release#readme
// v14 remains maintained through 30 April 2023.
// v16 is the earliest that supports RegExp.hasIndices
// v18 was released on 2022-04-19 and entered EOL in 2025.
// v20 enters active support on 2023-10-24.
// v25 was released on 2025-10-15.
@Suppress("MagicNumber")
val nodeVersions = SemVer(18, 0, 0) until SemVer(25, 99, 99)

object NodeCommand : VersionedTool {
    override val cliNames = listOf("node")
    override val versionCheckArgs = listOf("--version")

    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> =
        SemVer(run.stdout.replace(leadingV, "").trim()).check(run, nodeVersions)
}

object NpmCommand : ToolSpecifics, VersionedTool {
    override val cliNames = listOf("npm")
    override val versionCheckArgs = listOf("--version")

    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> =
        SemVer(run.stdout.trim()).noCheck(run)
}
