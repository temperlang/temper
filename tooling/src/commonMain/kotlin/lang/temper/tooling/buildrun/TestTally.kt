package lang.temper.tooling.buildrun

import lang.temper.be.cli.Aux
import lang.temper.be.cli.Effort
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.cleanup
import lang.temper.be.cli.effort
import lang.temper.be.cli.print
import lang.temper.common.Console
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.frontend.Module
import lang.temper.library.LibraryConfigurationLocationKey
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.result.junit.parseJunitResults
import lang.temper.value.DependencyCategory

/** True if the module tests any of the libraries in request */
internal fun tests(request: RunTestsRequest, module: Module): Boolean {
    if (module.dependencyCategory != DependencyCategory.Test) { return false }
    val libraries = request.libraries
    return if (libraries != null) {
        val config = module.sharedLocationContext[module.loc, LibraryConfigurationLocationKey]
        config?.libraryName in libraries
    } else {
        true // We're testing all libraries.
    }
}

data class TestTally(
    val run: Int,
    val failed: Int,
    /**
     * When named `total`, I get java.lang.NoSuchMethodError: 'int lang.temper.cli.TestTally.getTotal()'.
     * That's when trying to reference it from DoTestTest. I'm clueless. So call it `defined`.
     * It's the total number of defined tests, whether they ran or not.
     */
    val defined: Int?,
) {
    operator fun plus(other: TestTally): TestTally {
        val sumDefined = when {
            defined != null && other.defined != null -> defined + other.defined
            else -> null
        }
        return TestTally(run = run + other.run, failed = failed + other.failed, defined = sumDefined)
    }

    fun summary(): String {
        val notRun = when (defined) {
            null -> ""
            else -> when (val notRun = defined - run) {
                0 -> ""
                else -> " ($notRun not run)"
            }
        }
        return "Tests passed: ${run - failed} of ${defined ?: run}$notRun"
    }
}

/** Also prints failure causes and errors. */
fun tallyResults(
    resultsByBackend: Map<BackendId, List<BackendRunResult>>,
    cliConsole: Console,
    resultDetails: MutableList<DoRunResultDetail>? = null,
): TestTally {
    var testsDefined: Int? = null
    var testsRun = 0
    var testsFailed = 0
    val testsNamesByRun = mutableMapOf<Pair<BackendId, DashedIdentifier?>, Map<String, String>>()
    for ((backendId, results) in resultsByBackend) {
        for (result in results) {
            val libraryName = result.toolchainResult.libraryName
            val testNames = result.dependencies?.tests?.let { tests ->
                testsNamesByRun.getOrPut(backendId to libraryName) {
                    // Backends shouldn't build duplicate test names into a library, but start with a list anyway.
                    val testNames = tests[libraryName]?.map { it.backendName to it.temperName } ?: listOf()
                    // This only runs on first definition, to prevent duplicate counting.
                    testsDefined = (testsDefined ?: 0) + testNames.size
                    // TODO Report if any duplicate backend test names?
                    testNames.toMap()
                }
            } ?: mapOf()
            val runResult = result.toolchainResult.result
            // We only expect junit output for test requests, but easier not to worry that here.
            val effort = when (runResult) {
                is RFailure ->
                    runResult.failure.effort as? Effort
                is RSuccess -> {
                    runResult.cleanup()
                    runResult.result
                }
            }
            val testFailures = mutableListOf<Pair<String, String>>()
            val ok = when (val junitOutput = effort?.auxOut?.get(Aux.JunitXml)) {
                null -> {
                    if (runResult is RFailure) {
                        // Not explainable by junit xml, whether run or test request, so report fully.
                        runResult.print(cliConsole)
                        (effort as? Effort)?.auxErr?.get(Aux.JunitXml)?.let { cliConsole.error(it) }
                    }
                    runResult is RSuccess
                }

                else -> {
                    val parsedResults = runCatching {
                        parseJunitResults(junitOutput)
                    }.getOrElse { err ->
                        // Typically javax.xml.stream.XMLStreamException, but report in any case.
                        // Any case that gets here is presumably a case we should address, but
                        // reporting bad output makes dealing with such cases easier.
                        cliConsole.error("Invalid junit xml:\n$junitOutput")
                        throw err
                    }
                    testsRun += parsedResults.testsRun
                    testsFailed += parsedResults.failures.size
                    for (failure in parsedResults.failures) {
                        val testName = testNames.getOrDefault(failure.name, failure.name)
                        testFailures.add(testName to failure.cause)
                        cliConsole.error("Test failed ($backendId): $testName - ${failure.cause}")
                        // TODO Stack traces when and how?
                    }
                    testsFailed == 0
                }
            }
            resultDetails?.add(
                DoRunResultDetail(
                    ok = ok,
                    output = (runResult.effort() as? EffortSuccess)?.stdout ?: "<no stdout>",
                    backendId = backendId,
                    libraryName = result.toolchainResult.libraryName,
                    maxBuildLogLevel = result.maxBuildLogLevel,
                    testFailures = testFailures,
                    testNames = testNames.values.toList(),
                ),
            )
        }
    }
    return TestTally(run = testsRun, failed = testsFailed, defined = testsDefined)
}
