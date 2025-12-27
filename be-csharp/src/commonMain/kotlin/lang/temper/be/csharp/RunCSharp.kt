package lang.temper.be.csharp

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
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
import lang.temper.library.relativeOutputDirectoryForLibrary
import lang.temper.log.filePath
import lang.temper.log.resolveDir

internal fun runCSharp(
    cliEnv: CliEnv,
    dependencies: Dependencies<CSharpBackend>,
    request: ToolchainRequest,
): List<ToolchainResult> {
    return when (request) {
        is RunLibraryRequest -> runModule(cliEnv, request)
        is RunTestsRequest -> runTests(cliEnv, dependencies, request)
        is RunBackendSpecificCompilationStepRequest -> error(request)
        is ExecInteractiveRepl -> {
            return listOf(
                ToolchainResult(
                    result = RFailure( // TODO(mikesamuel, repl): implement this
                        CliFailure(
                            message = "C# backend does not yet support interactive shell",
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

private fun runModule(
    cliEnv: CliEnv,
    request: RunLibraryRequest,
): List<ToolchainResult> {
    val srcDir = relativeOutputDirectoryForLibrary(CSharpBackend.Factory.backendId, request.libraryName)
        .resolveDir(PROGRAM_PROJECT_DIR)
    val dotnet = cliEnv[DotnetCommand]
    val command = Command(
        // Ignore warnings by default because they add to gathered output.
        //
        // Configure Nuget to ignore audit warnings like:
        // > : warning NU1900: Error occurred while getting package vulnerability data: Unable to load
        // because I'm running functional tests on my laptop while sitting in a waiting room without wifi.
        // I'm not bitter.
        // See https://learn.microsoft.com/en-us/nuget/concepts/auditing-packages#configuring-nuget-audit
        args = listOf(
            "run",
            "--property", "WarningLevel=0",
            "--property", "NuGetAudit=false",
            // learn.microsoft.com/en-us/dotnet/core/compatibility/sdk/9.0/net70-warning#recommended-action
            "--property", "CheckEolTargetFramework=false",
        ),
        cwd = srcDir,
        env = DotnetCommand.defaultEnv,
    )
    command.maybeLogBeforeRunning(dotnet, cliEnv.shellPreferences)
    return listOf(ToolchainResult(libraryName = request.libraryName, result = dotnet.run(command)))
}

fun runTests(
    cliEnv: CliEnv,
    dependencies: Dependencies<CSharpBackend>,
    request: RunTestsRequest,
): List<ToolchainResult> {
    // Dotnet docs suggest Tests as part of the name for the tests project.
    // Tutorial: https://learn.microsoft.com/en-us/dotnet/core/testing/unit-testing-with-mstest
    // Reference app example: https://github.com/dotnet/eShop/blob/main/tests/Basket.UnitTests/BasketServiceTests.cs
    // TODO Sort libraries first like Java? Nicer even if not needed?
    // TODO Filter out std when running all?
    val libraries = when (val libraries = request.libraries) {
        null -> dependencies.libraryConfigurations.byLibraryName.keys.map { it }
        else -> libraries.map { it }
    }
    val dotnet = cliEnv[DotnetCommand]
    return libraries.mapNotNull libraries@{ library ->
        val testsDir = relativeOutputDirectoryForLibrary(CSharpBackend.Factory.backendId, library)
            .resolveDir("tests")
        cliEnv.fileExists(testsDir) || return@libraries null
        val command = Command(
            // Ignore warnings by default because they add to gathered output.
            // Also add `--framework net6.0` if we include net48 support in all our outputs.
            args = listOf("dotnet", "test", "--logger:junit"),
            aux = mapOf(Aux.JunitXml to testsDir.resolve(filePath("TestResults", "TestResults.xml"))),
            cwd = testsDir,
            env = DotnetCommand.defaultEnv,
        )
        command.maybeLogBeforeRunning(dotnet, cliEnv.shellPreferences)
        ToolchainResult(libraryName = library, result = dotnet.run(command))
    }
}
