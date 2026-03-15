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
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile
import lang.temper.name.DashedIdentifier

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
    // Discover all transitively-needed .cpp files by scanning #include directives.
    // backendDir is the parent of runDir (e.g., "cpp/" when runDir is "cpp/work/").
    val backendDir = dirPath(backendId.uniqueId)
    val depCppFiles = discoverTransitiveCppDeps(backendDir, libraryName, dependencies)

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
        // Compile transitively-needed dependency .cpp files
        for (depPath in depCppFiles) {
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

/**
 * Discover needed .cpp files from dependency libraries by scanning
 * #include directives in the current library's .hpp and .cpp files.
 * [backendDir] is the parent directory containing all library output directories.
 */
private fun CliEnv.discoverTransitiveCppDeps(
    backendDir: FilePath,
    libraryName: DashedIdentifier,
    dependencies: Dependencies<*>,
): Set<String> {
    val libPrefix = "$libraryName/"
    val result = mutableSetOf<String>()
    val scannedHpp = mutableSetOf<String>()
    val includeRegex = Regex("""#include\s+[<"]([^>"]+\.hpp)[>"]""")

    fun scanFileForIncludes(content: String) {
        for (match in includeRegex.findAll(content)) {
            val includePath = match.groupValues[1]
            if ('/' !in includePath || includePath.startsWith("temper-core/")) continue
            if (includePath.startsWith(libPrefix)) continue // same library
            if (includePath in scannedHpp) continue // already scanned
            scannedHpp.add(includePath)

            // Add the corresponding .cpp for this dependency include
            val cppPath = includePath.replace(HPP_EXT, CPP_EXT)
            val cppParts = cppPath.split('/')
            var cppFilePath = backendDir
            for (part in cppParts.dropLast(1)) {
                cppFilePath = cppFilePath.resolveDir(part)
            }
            cppFilePath = cppFilePath.resolveFile(cppParts.last())
            if (fileExists(cppFilePath)) {
                result.add(cppPath)
            }

            // Transitively scan the included header for its own dependencies
            val hppParts = includePath.split('/')
            var hppFilePath = backendDir
            for (part in hppParts.dropLast(1)) {
                hppFilePath = hppFilePath.resolveDir(part)
            }
            hppFilePath = hppFilePath.resolveFile(hppParts.last())
            val hppContent = try { readFile(hppFilePath) } catch (_: Exception) { continue }
            scanFileForIncludes(hppContent)
        }
    }

    // Scan all files from the current library for dependency includes
    val currentFiles = dependencies.filesPerLibrary[libraryName] ?: return emptySet()
    for (file in currentFiles) {
        val path = file.filePath.toString()
        if (!path.endsWith(HPP_EXT) && !path.endsWith(CPP_EXT)) continue

        val filePath = backendDir.resolveDir(libraryName.text).resolveFile(path)
        val content = try { readFile(filePath) } catch (_: Exception) { continue }
        scanFileForIncludes(content)
    }

    return result
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
