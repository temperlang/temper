package lang.temper.be.cpp03

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.Command
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.common.RFailure
import lang.temper.library.relativeOutputDirectoryForLibrary
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile

internal fun runCpp(
    cliEnv: CliEnv,
    dependencies: Dependencies<CppBackend>,
    request: ToolchainRequest,
    version: CppVersion,
): List<ToolchainResult> {
    return when (request) {
        is RunLibraryRequest -> cliEnv.runLibrary(request, dependencies, version)
        is RunBackendSpecificCompilationStepRequest -> error(request)
        else -> error(request)
    }.also { results ->
        if (results.any { it.result is RFailure }) {
            cliEnv.maybeFreeze()
        }
    }
}

object GppCommand : ToolSpecifics {
    override val cliNames = listOf("g++")
}

private fun CliEnv.runLibrary(
    request: RunLibraryRequest,
    dependencies: Dependencies<CppBackend>,
    version: CppVersion,
): List<ToolchainResult> {
    val libraryName = request.libraryName
    val runDir = relativeOutputDirectoryForLibrary(CppBackend.Factory.backendId, libraryName)
    val buildDir = runDir.resolveDir("build")
    // For now, just manually build with g++.
    // TODO Use cmake or something, and ensure we work on msvc as well as gcc and clang.
    makeDir(buildDir)
    val gpp = this[GppCommand]
    val buildCommand = Command(
        args = buildList {
            add("-I..")
            add("-std=${version.nameForArg()}")
            files@ for (file in dependencies.filesPerLibrary[libraryName]!!) {
                file.mimeType == CppBackend.mimeType || continue@files
                add(file.filePath.toString())
            }
            add("-o")
            add("build/main")
        },
        aux = mapOf(Aux.Stderr to buildDir.resolveFile("stderr-build.txt")),
        cwd = runDir,
    )
    val buildResult = ToolchainResult(libraryName = libraryName, result = gpp.run(buildCommand))
    buildResult.result.failure != null && return listOf(buildResult)
    // Now run the binary.
    val runTool = gpp.withCommandPath(envPath(runDir.resolve(filePath("build", "main"))))
    val runCommand = Command(
        args = listOf(),
        aux = mapOf(Aux.Stderr to runDir.resolveFile("stderr-run.txt")),
        cwd = runDir,
    )
    val runResult = ToolchainResult(libraryName = request.libraryName, result = runTool.run(runCommand))
    return listOf(runResult)
}
