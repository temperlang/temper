package lang.temper.tooling.buildrun

import lang.temper.be.cli.ToolchainRequest
import lang.temper.name.BackendId
import lang.temper.name.interpBackendId
import java.nio.file.Path

data class RunTask(
    /** The request to run */
    val request: ToolchainRequest,
    /** The backend / backends to run request on */
    val backends: Set<BackendId>,
    /** The directory to run in.  Defaults to the work root */
    val currentDirectory: Path? = null,
) {
    val jobName get() = request.taskName
    val needsInterpreter get() = interpBackendId in backends
    val nonInterpBackends get() = backends - interpBackendId
}
