package lang.temper.tooling.buildrun

import lang.temper.be.Backend
import lang.temper.be.cli.ShellPreferences
import lang.temper.common.Console
import lang.temper.common.ForkingTextOutput
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.TextOutput
import lang.temper.compile.BuildListener
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FileSystem
import lang.temper.fs.RealFileSystem
import lang.temper.fs.RealWritableFileSystem
import lang.temper.fs.TEMPER_KEEP_NAME
import lang.temper.fs.TEMPER_OUT_NAME
import lang.temper.fs.fileFilterRulesFromIgnoreFile
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.bannedPathSegmentNames
import lang.temper.log.resolveDir
import lang.temper.name.BackendId
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.name

/**
 * State that comes in handy for one or more builds.
 */
class BuildHarness(
    val executorService: ExecutorService,
    /** Controls logging */
    shellPreferences: ShellPreferences,
    /** The native path to the work root.  Used to populate [BuildHarness.workFileSystem] */
    val workRoot: Path,
    /** A file system rooted at [workRoot] */
    workFileSystem: FileSystem? = null,
    /** A .gitignore formatted description of files under [workRoot] to ignore. */
    ignoreFile: Path?,
    val backends: List<BackendId>,
    /** The output directory.  If null, [BuildHarness.outputFileSystem] lives at `temper.out` under [workRoot] */
    outDir: Path? = null,
    /** The output directory.  If null, [BuildHarness.keepFileSystem] lives at `temper.keep` under [workRoot] */
    keepDir: Path? = null,
    /** Backends to build.  Must include any needed by [Build.runTask]. */
    val backendConfig: Backend.Config = Backend.Config.production,
    /** Receives notifications when a build starts and completes */
    val buildListener: BuildListener? = null,
) : AutoCloseable {
    val hadIoExceptions = AtomicBoolean(false)
    val onIoException = { it: Throwable ->
        hadIoExceptions.set(true)
        cliConsole.error(it)
    }

    val outputFileSystemRealRoot: Path = run {
        val path = outDir ?: workRoot.resolve(TEMPER_OUT_NAME)
        Files.createDirectories(path)
        path
    }

    val outputFileSystem = RealWritableFileSystem(
        javaRoot = outputFileSystemRealRoot,
        onIoException = onIoException,
    )

    val keepFileSystemRealRoot: Path = run {
        val path = keepDir ?: workRoot.resolve(TEMPER_KEEP_NAME)
        Files.createDirectories(path)
        path
    }

    val keepFileSystem = RealWritableFileSystem(
        javaRoot = keepFileSystemRealRoot,
        onIoException = onIoException,
    )

    val shellPreferences: ShellPreferences
    val logsDir: FilePath = outputFileSystem.basePath.resolveDir(logsDirName)
    private val logTextOutput: TextOutput

    init {
        // Output logs to temper.out/-logs/<job-name>
        val cliConsole = shellPreferences.console
        val cliTextOutput = cliConsole.rawTextOutput
        val logsTextOutput = LogsTextOutput(logsDir, outputFileSystem, onIoException)
        this.logTextOutput = logsTextOutput
        val loggingConsole = Console(
            textOutput = ForkingTextOutput(listOf(cliTextOutput, logsTextOutput)),
            logLevel = cliConsole.level,
            snapshotter = cliConsole.snapshotter,
        )
        this.shellPreferences = shellPreferences.copy(console = loggingConsole)
    }

    val cliConsole: Console get() = shellPreferences.console

    val workFileSystem = workFileSystem ?: RealFileSystem(workRoot, workDir)

    val filterRules: FileFilterRules = run {
        val ignoreFileNorm = ignoreFile?.normalize()
        val filterRulesFromFile = if (ignoreFileNorm != null) {
            val fileContent = Files.readString(ignoreFileNorm, Charsets.UTF_8)
            when (val fromFile = fileFilterRulesFromIgnoreFile(fileContent)) {
                is RSuccess -> {
                    val filter = fromFile.result
                    val relPath = ignoreFileNorm.parent.relativize(workRoot).normalize()

                    val parts = (0 until relPath.nameCount).map { i ->
                        relPath.getName(i).name
                    }
                    when {
                        parts.isEmpty() -> filter
                        parts.all { it == ".." } -> {
                            // ../../.. means the ignore file is three directories deep.
                            // So if the work root is
                            //    path/to/workroot
                            // and the ignore file is in
                            //    path/to/workroot/src/subdir/
                            // then we get 2 parts: ../..
                            // and we grab 2 segments from the end of dirname($ignoreFile)
                            //     src/subdir
                            FileFilterRules.StripSegments(
                                filter,
                                pathToStrip = workDir.resolve(
                                    parts.indices.reversed().map {
                                        FilePathSegment(
                                            ignoreFileNorm.getName(ignoreFileNorm.nameCount - 1 - it).name,
                                        )
                                    },
                                    isDir = true,
                                ),
                            )
                        }

                        parts.none { it in bannedPathSegmentNames } -> {
                            val pathToPrepend = FilePath(parts.map { FilePathSegment(it) }, isDir = true)
                            FileFilterRules.Prepending(filter, pathToPrepend = pathToPrepend)
                        }

                        else -> {
                            // If your workroot is
                            //     /foo/bar
                            // It's ok to have an ignore file at
                            //     /foo/baz/.ignore
                            // but since that would only apply to files under /foo/baz, we
                            // just allow all files.
                            FileFilterRules.Allow
                        }
                    }
                }

                is RFailure -> {
                    cliConsole.error(
                        "Build aborted. Could not read ignore file patterns from `$ignoreFile`!",
                        fromFile.failure,
                    )
                    throw BuildAbortedDueToInitFailure()
                }
            }
        } else {
            FileFilterRules.Allow
        }
        FileFilterRules.eitherIgnores(filterRulesFromFile, ExcludeOutputDirectories)
    }

    override fun close() {
        outputFileSystem.use { _ ->
            (logTextOutput as? AutoCloseable).use {
                keepFileSystem.use { _ ->
                    workFileSystem.use { _ ->
                        // Just using for exception-safe closing.
                    }
                }
            }
        }
    }

    companion object {
        val workDir = FilePath(listOf(FilePathSegment("-work")), isDir = true)
    }
}

internal class BuildAbortedDueToInitFailure : RuntimeException()

/**
 * Hard exclusion of `-work/temper.out` and `-work/temper.keep`
 * from source snapshots.
 */
private data object ExcludeOutputDirectories : FileFilterRules {
    override fun isIgnored(path: FilePath): Boolean {
        if (BuildHarness.workDir.isAncestorOf(path)) {
            val next = path.segments.getOrNull(BuildHarness.workDir.segments.size)
            when (next?.fullName) {
                TEMPER_KEEP_NAME, TEMPER_OUT_NAME -> return true
            }
        }
        return false
    }
}
