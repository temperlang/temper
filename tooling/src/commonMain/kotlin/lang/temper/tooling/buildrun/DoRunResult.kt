package lang.temper.tooling.buildrun

import lang.temper.be.Dependencies
import lang.temper.be.cli.ToolchainResult
import lang.temper.common.Log
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier

data class DoRunResultDetail(
    val ok: Boolean,
    val maxBuildLogLevel: Log.Level,
    val output: String,
    val backendId: BackendId,
    val libraryName: DashedIdentifier? = null,
    /** From name to message, allowing for (not recommended) duplicate names. */
    val testFailures: List<Pair<String, String>> = emptyList(),
    val testNames: List<String> = emptyList(),
)

data class DoRunResult(
    val ok: Boolean,
    val maxBuildLogLevel: Log.Level,
    val outputThunk: () -> String,
    val testTally: TestTally? = null,
    val details: List<DoRunResultDetail>,
) {
    /** End result was ok and no errors were logged in Temper compiler processing. */
    val errorFree get() = ok && errorFreeInTemperCompiler

    /** No errors were logged in Temper compiler processing. */
    val errorFreeInTemperCompiler get() = maxBuildLogLevel < Log.Error
}

data class BackendRunResult(
    val maxBuildLogLevel: Log.Level,
    val toolchainResult: ToolchainResult,
    val dependencies: Dependencies<*>?,
)
