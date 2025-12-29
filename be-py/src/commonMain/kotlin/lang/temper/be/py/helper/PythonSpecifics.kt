package lang.temper.be.py.helper

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.Specifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.VersionedTool
import lang.temper.be.cli.check
import lang.temper.be.cli.composing
import lang.temper.be.py.PythonVersion
import lang.temper.be.py.runPyBestEffort
import lang.temper.common.RResult
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.name.BackendId
import lang.temper.name.SemVer

abstract class AbstractPythonSpecifics(private val pythonVersion: PythonVersion) :
    Specifics, RunnerSpecifics {

    override val backendId: BackendId get() = pythonVersion.backendId

    override val tools = listOf(PythonCommand)

    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> =
        cliEnv.composing(this) {
            this[PythonCommand].run(Command(args = listOf("-c", code), env = env, aux = aux))
        }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> {
        return runPyBestEffort(
            pythonVersion = pythonVersion,
            cliEnv = cliEnv,
            request = request,
            files = code,
            dependencies = dependencies,
            backendId = backendId,
        )
    }
}

object PythonSpecifics : AbstractPythonSpecifics(PythonVersion.Python311)
object MypySpecifics : AbstractPythonSpecifics(PythonVersion.MypyC)

object PythonCommand : VersionedTool {
    private val leadingPython = Regex("^\\s*Python", RegexOption.IGNORE_CASE)

    @Suppress("MagicNumber")
    private val validVersions = SemVer(3, 11, 0) until SemVer(4, 0, 0)

    override val cliNames = listOf("python3", "python")
    override val versionCheckArgs = listOf("--version")
    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> =
        SemVer(run.stdout.replace(leadingPython, "").trim())
            .check(run, validVersions)
}

/**
 * This is an optimization for the PyFunctionalTest.
 * It skips rebuilding a dependency that's already
 * in the virtualenv. This mostly affects the MyPyC backend.
 */
interface VenvBuilt {
    fun alreadyBuilt(path: FilePath): Boolean
    fun built(path: Collection<FilePath>)
}

/**
 * In normal operation, never assume a library is built.
 */
object NilVenvBuilt : VenvBuilt {
    override fun alreadyBuilt(path: FilePath): Boolean = false
    override fun built(path: Collection<FilePath>) = Unit
}
