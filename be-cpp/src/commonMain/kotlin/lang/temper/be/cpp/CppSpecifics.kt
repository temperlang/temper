package lang.temper.be.cpp

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.composing
import lang.temper.common.RResult
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.name.BackendId

object CppSpecifics : RunnerSpecifics {
    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> =
        cliEnv.composing(this) {
            error("single-source C++ execution not yet supported")
        }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> {
        return runCpp(cliEnv, dependencies, request)
    }

    override val tools: List<ToolSpecifics>
        get() = listOf(GppCommand)
    override val backendId: BackendId
        get() = CppLang.Cpp.id
}
