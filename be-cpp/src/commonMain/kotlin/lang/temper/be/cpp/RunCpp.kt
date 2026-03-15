package lang.temper.be.cpp

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.Command
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.fs.escapeShellString
import lang.temper.library.relativeOutputDirectoryForLibrary
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile

fun runCpp11(
    cliEnv: CliEnv,
    dependencies: Dependencies<*>,
    request: ToolchainRequest,
): List<ToolchainResult> {
    return when (request) {
        is RunLibraryRequest -> cliEnv.runLibrary(request, dependencies)
        is RunTestsRequest -> cliEnv.runTests(request, dependencies)
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

object ShCommand : ToolSpecifics {
    override val cliNames = listOf("sh")
}

/** Virtual memory limit in KB for g++ compilation and binary execution (4 GB). */
private const val MEMORY_LIMIT_KB = 4_194_304

private fun CliEnv.runLibrary(
    request: RunLibraryRequest,
    dependencies: Dependencies<*>,
): List<ToolchainResult> {
    val libraryName = request.libraryName
    val backendId = CppLang.Cpp11.id
    val runDir = relativeOutputDirectoryForLibrary(backendId, libraryName)
    val buildDir = runDir.resolveDir("build")
    makeDir(buildDir)
    val gpp = this[GppCommand]
    val sh = this[ShCommand]
    // Read list of required dependency source files from the dep-sources file
    val depSourcesFile = runDir.resolveFile("dep-sources.txt")
    val requiredDepSources = try {
        readFile(depSourcesFile).lines().filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    val gppArgs = buildList {
        add("-I..")
        add("-std=c++11")
        add("-fwrapv") // Signed integer overflow wraps (Temper semantics)
        // Compile .cpp files from current library
        files@ for (file in dependencies.filesPerLibrary[libraryName]!!) {
            file.mimeType == MimeType.cppSource || continue@files
            val path = file.filePath.toString()
            if (path.endsWith(CPP_EXT)) {
                add(path)
            }
        }
        // Compile required dependency .cpp files
        for (depPath in requiredDepSources) {
            add("../$depPath")
        }
        add("-o")
        add("build/main")
    }

    // Wrap g++ in a shell with ulimit to prevent OOM
    val shellCmd = buildString {
        append("ulimit -v $MEMORY_LIMIT_KB && ")
        append(escapeShellString(gpp.command))
        for (arg in gppArgs) {
            append(' ')
            append(escapeShellString(arg))
        }
    }
    val buildCommand = Command(
        args = listOf("-c", shellCmd),
        aux = mapOf(Aux.Stderr to buildDir.resolveFile("stderr-build.txt")),
        cwd = runDir,
    )
    val buildResult = ToolchainResult(libraryName = libraryName, result = sh.run(buildCommand))
    buildResult.result.failure != null && return listOf(buildResult)
    // Now run the binary, also with a memory limit.
    val binaryPath = envPath(runDir.resolve(filePath("build", "main")))
    val runShellCmd = "ulimit -v $MEMORY_LIMIT_KB && ${escapeShellString(binaryPath)}"
    val runAux = buildMap {
        put(Aux.Stderr, runDir.resolveFile("stderr-run.txt"))
        put(Aux.JunitXml, runDir.resolveFile("test-results.xml"))
    }
    val runCommand = Command(
        args = listOf("-c", runShellCmd),
        aux = runAux,
        cwd = runDir,
    )
    val runResult = ToolchainResult(libraryName = request.libraryName, result = sh.run(runCommand))
    return listOf(runResult)
}

private fun CliEnv.runTests(
    request: RunTestsRequest,
    dependencies: Dependencies<*>,
): List<ToolchainResult> {
    // For C++, tests are compiled and run the same way as libraries
    val libraries = request.libraries ?: return emptyList()
    return libraries.flatMap { libraryName ->
        runLibrary(RunLibraryRequest(libraryName = libraryName, taskName = request.taskName), dependencies)
    }
}
