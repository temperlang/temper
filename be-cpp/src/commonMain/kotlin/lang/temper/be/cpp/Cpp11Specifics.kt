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
import lang.temper.be.cli.VersionedTool
import lang.temper.be.cli.composing
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.subListToEnd
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.name.BackendId

class CompilerTool(val name: String) : VersionedTool {
    override val cliNames: List<String>
        get() = listOf(name)

    override val versionCheckArgs: List<String>
        get() = listOf("-v")

    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> = RSuccess(Unit)
}

/** The path segments but skipping over the initial `cpp` path segment. */
val FilePath.goodFileSegments: List<FilePathSegment>
    get() {
        var skipped = 0
        if (segments.getOrNull(skipped)?.fullName == CppLang.Cpp11.id.uniqueId) { skipped += 1 }
        return segments.subListToEnd(skipped)
    }
val FilePath.goodFilePath: FilePath
    get() = FilePath(segments = goodFileSegments, isDir = isDir)

object Cpp11Specifics : RunnerSpecifics {
    @Suppress("unused")
    private val Compiler = CompilerTool("cc")

    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> =
        cliEnv.composing(this) {
            TODO("Run single C++")
        }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> {
        TODO("Run best effort C++")
    }

    override val tools: List<ToolSpecifics>
        get() = listOf()
    override val backendId: BackendId
        get() = CppLang.Cpp11.id
}
