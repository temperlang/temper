package lang.temper.be.rust

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.CommandFailed
import lang.temper.be.cli.EXIT_UNAVAILABLE
import lang.temper.be.cli.Effort
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.maybeLogBeforeRunning
import lang.temper.common.RFailure
import lang.temper.common.json.JsonString
import lang.temper.common.json.JsonValue
import lang.temper.library.relativeOutputDirectoryForLibrary
import lang.temper.log.resolveFile
import lang.temper.name.DashedIdentifier
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml

internal fun runRust(
    cliEnv: CliEnv,
    dependencies: Dependencies<RustBackend>,
    request: ToolchainRequest,
): List<ToolchainResult> {
    return when (request) {
        is RunLibraryRequest -> cliEnv.runLibrary(request)
        is RunTestsRequest -> cliEnv.runTests(request, dependencies)
        is RunBackendSpecificCompilationStepRequest -> error(request)
        is ExecInteractiveRepl -> {
            return listOf(
                ToolchainResult(
                    result = RFailure(
                        CliFailure(
                            message = "Rust backend does not yet support interactive shell",
                            effort = Effort(exitCode = EXIT_UNAVAILABLE, cliEnv = cliEnv),
                        ),
                    ),
                ),
            )
        }
    }.also { results ->
        if (results.any { it.result is RFailure }) {
            cliEnv.maybeFreeze()
        }
    }
}

private fun CliEnv.runCargo(subcommand: String, libraryName: DashedIdentifier): ToolchainResult {
    val runDir = relativeOutputDirectoryForLibrary(RustBackend.Factory.backendId, libraryName)
    val cargo = this[CargoCommand]
    val command = Command(
        args = listOf(subcommand),
        aux = mapOf(Aux.Stderr to runDir.resolveFile("stderr.txt")),
        cwd = runDir,
    )
    command.maybeLogBeforeRunning(cargo, shellPreferences)
    val result = ToolchainResult(libraryName = libraryName, result = cargo.run(command))
    return result
}

private fun CliEnv.runLibrary(request: RunLibraryRequest): List<ToolchainResult> {
    return listOf(runCargo("run", request.libraryName))
}

private fun CliEnv.runTests(request: RunTestsRequest, dependencies: Dependencies<RustBackend>): List<ToolchainResult> {
    val libraryNames = when (val libraries = request.libraries) {
        // We could maybe also make a workspace, but that might not be ideal for unrelated dependencies.
        null -> dependencies.libraryConfigurations.byLibraryName.keys.map { it }
        else -> libraries.map { it }
    }
    return libraryNames.map { libraryName ->
        val result = runCargo("test", libraryName)
        val failure = result.result.failure
        when {
            failure != null && failure !is CommandFailed -> null
            else -> {
                // For either success or plain command failure, we expect test results that we can turn into xml.
                val effort = (result.result.result ?: result.result.failure!!.effort) as Effort
                cargoTestToJunitXml(libraryName = libraryName.text, stdout = effort.stdout)?.let { xml ->
                    val reportEffort = effort.copy(auxOut = effort.auxOut + mapOf(Aux.JunitXml to xml))
                    result.copy(result = reportEffort.asResult())
                }
            }
        } ?: result
    }
}

@Suppress("MagicNumber")
fun cargoTestToJunitXml(libraryName: String, stdout: String): String? {
    var failed = 0
    var passed = 0
    val messages = mutableMapOf<String, String?>()
    var nameForMessage: String? = null
    var time = 0.0
    var total = 0
    // There could be multiple rounds of test reports, and we usually only care about one of them, while the others
    // typically report zero tests for our usage. For simplicity, just treat all of it as unordered.
    lines@ for (line in stdout.split("\n")) {
        nameForMessage?.also {
            if (line.trim().isEmpty()) {
                // Any blank lines in the message means we miss whatever's left, but this is sloppy text formatting.
                nameForMessage = null
            } else {
                val text = regexTestMessageEntry.find(line)?.let { entryMatch ->
                    // Handle our custom messaging nicely.
                    val entry = entryMatch.groupValues[1]
                    when (val textMatch = regexTestMessageText.find(entry)) {
                        null -> entry
                        else -> textMatch.groupValues[1].let { raw ->
                            // Rust literals likely aren't fully compatible wth json, but this will do for now.
                            (JsonValue.parse(raw).result as? JsonString)?.content ?: raw
                        }
                    }
                } ?: line // but go with whatever if needed
                // We don't expect multiple lines very often, so just be sloppy about concatenation.
                messages.compute(nameForMessage) { _, oldValue ->
                    when {
                        (oldValue ?: "").isEmpty() -> ""
                        else -> "$oldValue\n"
                    } + text
                }
            }
        }
            ?: regexTestTotal.find(line)?.also { total += it.groupValues[1].toInt() }
            ?: regexTestItem.find(line)?.also { match ->
                messages[match.groupValues[1]] = when (val failure = match.groupValues[2]) {
                    "ok" -> null
                    else -> failure
                }
            }
            ?: when (val headingMatch = regexTestMessageHeading.find(line)) {
                null -> regexTestSummary.find(line)?.let { summaryMatch ->
                    passed += summaryMatch.groupValues[1].toInt()
                    failed += summaryMatch.groupValues[2].toInt()
                    time += summaryMatch.groupValues[3].toDouble()
                }
                else -> {
                    nameForMessage = headingMatch.groupValues[1]
                    messages[nameForMessage] = ""
                }
            }
    }
    // Check matching data.
    val good = passed + failed == total && failed == messages.count { it.value != null } && time >= 0.0
    if (!good) {
        return null
    }
    // Build xml.
    val xmlRoot = xml("testsuites") {
        "testsuite" {
            attribute("name", libraryName)
            attribute("tests", total)
            attribute("failures", failed)
            attribute("time", time)
            for ((name, failure) in messages) {
                val baseName = name.split("::").last()
                "testcase" {
                    attribute("classname", baseName)
                    attribute("name", baseName)
                    attribute("time", 0)
                    if (failure != null) {
                        "failure" {
                            attribute("message", failure)
                        }
                    }
                }
            }
        }
    }
    return xmlRoot.toString(PrintOptions(pretty = true, indent = "  "))
}

private val regexTestMessageEntry = Regex("""^Error: (.+)""") // relying on greed
private val regexTestMessageText = Regex("""MessageError\((".*")\)+""") // relying on greed
private val regexTestMessageHeading = Regex("""^---- (\S+) stdout ----""")
private val regexTestItem = Regex("""^test (\S+) \.\.\. (\w+)""")
private val regexTestSummary = Regex("""^test result: .* (\d+) passed; (\d+) failed;.* finished in ([\d.]+)s""")
private val regexTestTotal = Regex("""^running (\d+) test""")
