package lang.temper.be.cpp

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.EXIT_UNAVAILABLE
import lang.temper.be.cli.Effort
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.VersionedTool
import lang.temper.be.cli.checkMin
import lang.temper.be.cli.maybeLogBeforeRunning
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.library.relativeOutputDirectoryForLibrary
import lang.temper.log.FilePathAndMimeTypeOrNull
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile
import lang.temper.name.DashedIdentifier
import lang.temper.name.SemVer

/** A SemVer is `major.minor.patch` — three components, which [GppCommand.checkVersion] pads to. */
private const val SEM_VER_PART_COUNT = 3

fun runCpp(
    cliEnv: CliEnv,
    dependencies: Dependencies<*>,
    request: ToolchainRequest,
): List<ToolchainResult> {
    return when (request) {
        is RunLibraryRequest -> cliEnv.runLibrary(request, dependencies)
        is RunTestsRequest -> cliEnv.runTests(request, dependencies)
        is RunBackendSpecificCompilationStepRequest ->
            error("unsupported request: RunBackendSpecificCompilationStepRequest")
        // Return a failure result rather than throwing for request kinds the C++ backend does
        // not serve (e.g. an interactive REPL), matching how the Rust backend degrades.
        else -> listOf(
            ToolchainResult(
                result = RFailure(
                    CliFailure(
                        message = "C++ backend does not support ${request::class.simpleName}",
                        effort = Effort(exitCode = EXIT_UNAVAILABLE, cliEnv = cliEnv),
                    ),
                ),
            ),
        )
    }.also { results ->
        if (results.any { it.result is RFailure }) {
            cliEnv.maybeFreeze()
        }
    }
}

object GppCommand : VersionedTool {
    override val cliNames = listOf("g++")

    // `-dumpversion` prints just the version (e.g. "11.4.0"); older g++ may print only the
    // major number, so checkVersion normalizes to a full `major.minor.patch` before parsing.
    override val versionCheckArgs = listOf("-dumpversion")

    override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> {
        // Pull the leading numeric version out of stdout and pad missing components with zeros
        // so SemVer (which requires major.minor.patch) can parse it.
        val raw = Regex("""\d+(\.\d+)*""").find(run.stdout.trim())?.value ?: ""
        val parts = raw.split('.')
        val normalized = (parts + List(SEM_VER_PART_COUNT) { "0" })
            .take(SEM_VER_PART_COUNT)
            .joinToString(".")
        return SemVer(normalized).checkMin(run, minVersion)
    }

    /**
     * g++ 5 is a safe floor for the full C++14 feature set we compile with (`-std=c++14`);
     * C++14 support landed in g++ 5.
     */
    @Suppress("MagicNumber")
    val minVersion = SemVer(5, 0, 0)
}

private fun CliEnv.runLibrary(
    request: RunLibraryRequest,
    dependencies: Dependencies<*>,
): List<ToolchainResult> {
    val libraryName = request.libraryName
    val backendId = CppLang.Cpp.id
    val runDir = relativeOutputDirectoryForLibrary(backendId, libraryName)
    val buildDir = runDir.resolveDir("build")
    makeDir(buildDir)
    val gpp = this[GppCommand]
    // The set of .cpp files to compile comes straight from the dependency graph the
    // frontend already recorded — the current library's files plus those of every
    // transitive dependency — rather than re-deriving it by scanning #include lines.
    // This mirrors how the Rust backend hands its dependency set to Cargo.
    val depCppFiles = transitiveCppDepFiles(libraryName, dependencies)

    val gppArgs = buildList {
        add("-I..")
        add("-std=c++14")
        // Compile .cpp files from the current library.
        for (path in cppSourcePaths(dependencies.filesPerLibrary[libraryName].orEmpty())) {
            add(path)
        }
        // Compile dependency .cpp files, referenced through `..` (the backend output
        // root, added to the include path with `-I..`).
        for (depPath in depCppFiles) {
            add("../$depPath")
        }
        add("-o")
        add("build/main")
    }

    // Invoke g++ directly as a process, the same way the Rust backend invokes `cargo`,
    // rather than going through a shell wrapper.
    val buildCommand = Command(
        args = gppArgs,
        aux = mapOf(Aux.Stderr to buildDir.resolveFile("stderr-build.txt")),
        cwd = runDir,
    )
    buildCommand.maybeLogBeforeRunning(gpp, shellPreferences)
    val buildResult = ToolchainResult(libraryName = libraryName, result = gpp.run(buildCommand))
    if (buildResult.result.failure != null) {
        return listOf(buildResult)
    }
    // Now run the compiled binary directly (no shell), reusing the tool plumbing by pointing
    // it at the built executable's path.
    val binaryPath = envPath(runDir.resolve(filePath("build", "main")))
    val binary = gpp.withCommandPath(binaryPath)
    val runAux = buildMap {
        put(Aux.Stderr, runDir.resolveFile("stderr-run.txt"))
        put(Aux.JunitXml, runDir.resolveFile("test-results.xml"))
    }
    val runCommand = Command(
        args = emptyList(),
        aux = runAux,
        cwd = runDir,
    )
    runCommand.maybeLogBeforeRunning(binary, shellPreferences)
    val runResult = ToolchainResult(libraryName = request.libraryName, result = binary.run(runCommand))
    return listOf(runResult)
}

/** The `.cpp` source paths among [files], relative to their own library's output directory. */
private fun cppSourcePaths(files: Set<FilePathAndMimeTypeOrNull>): List<String> =
    files.asSequence()
        .filter { it.mimeType == MimeType.cppSource }
        .map { it.filePath.toString() }
        .filter { it.endsWith(CPP_EXT) }
        .sorted()
        .toList()

/**
 * The `.cpp` files of every transitive dependency of [libraryName], as paths relative to
 * the backend output root (`<library>/<file>.cpp`). Sourced from the dependency graph the
 * frontend recorded ([Dependencies.transitiveDependencies] over [Dependencies.filesPerLibrary])
 * rather than by scraping `#include` directives, which is the authoritative set and avoids
 * guessing `.hpp`→`.cpp` mappings or reading files off disk.
 */
private fun transitiveCppDepFiles(
    libraryName: DashedIdentifier,
    dependencies: Dependencies<*>,
): List<String> = buildList {
    for (depLib in dependencies.transitiveDependencies[libraryName].orEmpty()) {
        for (path in cppSourcePaths(dependencies.filesPerLibrary[depLib].orEmpty())) {
            // Each library generates its own `main.cpp` entry point; only the current
            // library's belongs in the link. Skip dependencies' to avoid a duplicate `main`.
            if (path == MAIN_CPP_FILE) continue
            add("${depLib.text}/$path")
        }
    }
}.sorted()

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
