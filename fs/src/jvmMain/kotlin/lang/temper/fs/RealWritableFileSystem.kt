package lang.temper.fs

import lang.temper.common.Either
import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.RThrowable
import lang.temper.common.WrappedByteArray
import lang.temper.common.console
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.common.currents.newCompletableFuture
import lang.temper.common.currents.newComputedResultFuture
import lang.temper.common.currents.preComputedFuture
import lang.temper.common.currents.preComputedResultFuture
import lang.temper.common.ignore
import lang.temper.log.FilePath
import lang.temper.log.plus
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

class RealWritableFileSystem(
    javaRoot: Path,
    basePath: FilePath = FilePath.emptyPath,
    /**
     * Some batch operations like recursive deletion need to make a "best effort"
     * by continuing despite some failures.
     * They report any failures to delete a child via this callback.
     *
     * This callback should be thread-safe.
     *
     * @see deleteDirRecursively
     * @see ensureCleanDir
     */
    private val onIoException: (IOException) -> Unit,
) : RealFileSystem(javaRoot = javaRoot, basePath = basePath), WritableFileSystem {

    private fun failFuture(ex: IOException) = preComputedResultFuture(RFailure(ex))

    override fun ensureDir(path: FilePath): RFuture<Unit, IOException> {
        val javaPath = toJavaPath(path)
            ?: return preComputedResultFuture(RFailure(FileNotFoundException("$path")))
        return try {
            Files.createDirectories(javaPath)
            preComputedResultFuture(RSuccess(Unit))
        } catch (e: IOException) {
            preComputedResultFuture(RFailure(e))
        }
    }

    override fun ensureCleanDir(path: FilePath): RFuture<Unit, IOException> {
        check(path.isDir)
        val javaPath = toJavaPath(path)
        val parent = javaPath?.parent
            ?: return failFuture(FileNotFoundException("$path/.."))

        val result = UnmanagedFuture.newCompletableFuture<Unit, IOException>("rm -rf $path")
        val toDelete = try {
            Files.createDirectories(parent)

            // Relink the path under a temporary name so that we can return control
            // while unlinking a large directory structure proceeds separately.
            val tempDir = Files.createTempDirectory(parent, DOT_DELETING_PREFIX)
            Files.deleteIfExists(tempDir)
            try {
                Files.move(javaPath, tempDir)
            } catch (e: IOException) {
                // It's ok if the directory didn't exist to move.
                ignore(e)
            }
            tempDir
        } catch (e: IOException) {
            result.completeFail(e)
            return result
        }

        UnmanagedFuture.runLater("deleting directory $toDelete moved from $javaPath") {
            deleteDirRecursively(toDelete)
        }

        try {
            Files.createDirectories(javaPath)
            result.completeOk(Unit)
        } catch (e: IOException) {
            result.completeFail(e)
        }

        return result
    }

    override fun deleteDirRecursively(path: FilePath) {
        val javaPath = toJavaPath(path)
        if (javaPath != null) {
            deleteDirRecursively(javaPath)
        }
    }

    private fun deleteDirRecursively(toDelete: Path) {
        try {
            Files.walk(toDelete)
                .sorted(Comparator.reverseOrder())
                .forEach {
                    try {
                        Files.deleteIfExists(it)
                    } catch (e: NoSuchFileException) {
                        ignore(e) // Cool
                    } catch (e: IOException) {
                        onIoException(e)
                    }
                }
        } catch (e: NoSuchFileException) {
            ignore(e) // Cool
        } catch (e: IOException) {
            onIoException(e)
        }
    }

    override fun write(path: FilePath, bytes: ByteArray) {
        val javaPath = toJavaPath(path)
            ?: throw IOException("$path")
        Files.createDirectories(javaPath.parent)
        Files.write(javaPath, bytes)
    }

    override fun writeAsync(
        path: FilePath,
        mimeType: MimeType?,
    ): ByteSink {
        val javaPath = toJavaPath(path)
            ?: throw IOException("$path")

        Files.createDirectories(javaPath.parent)
        val stream = BufferedOutputStream(
            Files.newOutputStream(
                javaPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ),
        )
        return OutputStreamByteSink(stream)
    }

    override fun systemAccess(
        defaultCwd: FilePath,
        cancelGroup: CancelGroup,
    ): AsyncSystemAccess =
        SystemAccessImpl(defaultCwd, cancelGroup)
    override fun systemReadAccess(
        defaultCwd: FilePath,
        cancelGroup: CancelGroup,
    ): AsyncSystemReadAccess =
        SystemReadAccessImpl(defaultCwd, cancelGroup)

    override fun reportInternalIoException(ex: IOException) {
        onIoException(ex)
    }

    private open inner class SystemAccessImpl(
        override val pathToFileCreatorRoot: FilePath,
        override val cancelGroup: CancelGroup,
    ) : AsyncSystemAccess {
        override fun buildFile(filePath: FilePath, mimeType: MimeType?): FileBuilder = object : FileBuilder {
            private val fullFilePath = pathToFileCreatorRoot + filePath
            private var isExecutable = false

            override fun executable(): FileBuilder {
                isExecutable = true
                return this
            }

            override fun write(doWrite: (ByteSink) -> Unit): RFuture<Unit, IOException> {
                return cancelGroup.newComputedResultFuture("writing $fullFilePath") result@{
                    val streamOrException: Either<OutputStream, IOException> = try {
                        val javaPath = toJavaPath(fullFilePath)
                            ?: throw NoSuchFileException("$fullFilePath")
                        Files.createDirectories(javaPath.parent)
                        val outputStream = BufferedOutputStream(
                            Files.newOutputStream(
                                javaPath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE,
                            ),
                        )
                        if (isExecutable) {
                            Files.setPosixFilePermissions(
                                javaPath,
                                @Suppress("SpellCheckingInspection")
                                PosixFilePermissions.fromString("rwxrwxrwx"), // Still governed by umask
                            )
                        }
                        Either.Left(outputStream)
                    } catch (e: IOException) {
                        Either.Right(e)
                    }

                    when (streamOrException) {
                        is Either.Right -> RFailure(streamOrException.item)
                        is Either.Left -> {
                            try {
                                streamOrException.item.use { outputStream ->
                                    OutputStreamByteSink(outputStream).use {
                                        doWrite(it)
                                    }
                                }
                                RSuccess(Unit)
                            } catch (ioEx: IOException) {
                                RFailure(ioEx)
                            }
                        }
                    }
                }
            }
        }

        override fun buildChildProcess(
            command: String,
            build: ChildProcessBuilder.() -> Unit,
        ): PendingChildProcess {
            val cpb = ChildProcessBuilderImpl(cwd = pathToFileCreatorRoot, command = command)
            cpb.build()
            val javaCwd = toJavaPath(cpb.cwd)
            return ProcessBuilderBasedPendingChildProcess(cancelGroup, pathToFileCreatorRoot, cpb, javaCwd)
        }

        override fun envPath(path: FilePath) = envPath(pathToFileCreatorRoot, path)
    }

    private inner class SystemReadAccessImpl(
        pathToFileCreatorRoot: FilePath,
        cancelGroup: CancelGroup,
    ) : SystemAccessImpl(pathToFileCreatorRoot, cancelGroup), AsyncSystemReadAccess {
        override fun fileReader(filePath: FilePath): FileReader = object : FileReader {
            private val fullFilePath = pathToFileCreatorRoot + filePath

            override fun binaryContent(): RFuture<WrappedByteArray, IOException> =
                cancelGroup.newComputedResultFuture("reading $filePath") {
                    RResult.of(IOException::class) {
                        val javaPath = toJavaPath(fullFilePath)
                            ?: throw NoSuchFileException("$fullFilePath")
                        WrappedByteArray(Files.readAllBytes(javaPath))
                    }
                }
        }
    }
}

private class ChildProcessBuilderImpl(
    cwd: FilePath,
    command: String,
) : ChildProcessBuilder(cwd = cwd, command = command)

/** Prefix for temporary directory names that a background process is deleting. */
const val DOT_DELETING_PREFIX = ".deleting"

class ProcessBuilderBasedPendingChildProcess(
    val cancelGroup: CancelGroup,
    defaultCwd: FilePath,
    cwd: FilePath,
    /** The java path corresponding to [cwd] */
    private val javaCwd: Path?,
    env: Map<String, String>,
    command: String,
    args: List<String>,
) : PendingChildProcess(
    defaultCwd = defaultCwd,
    cwd = cwd,
    env = env,
    command = command,
    args = args,
) {
    constructor(
        cancelGroup: CancelGroup,
        defaultCwd: FilePath,
        b: ChildProcessBuilder,
        javaCwd: Path?,
    ) : this(cancelGroup, defaultCwd, b.cwd, javaCwd, b.env, b.command, b.args)

    override fun execute(): RunningChildProcess<Int> {
        val processBuilder =
            when (val r = getJvmProcessBuilder()) {
                is RSuccess -> r.result
                is RFailure -> return UnrunnableChildProcessImpl(
                    preComputedResultFuture(RThrowable(r.failure)),
                )
            }
        val process: Process = processBuilder.start()
        return startAndWrap(process) { it.exitValue() }
    }

    override fun executeCapturing(): RunningChildProcess<Pair<Int, String>> {
        var processBuilder =
            when (val r = getJvmProcessBuilder()) {
                is RSuccess -> r.result
                is RFailure -> return UnrunnableChildProcessImpl(
                    preComputedResultFuture(RThrowable(r.failure)),
                )
            }
        val capturedStdout = temporaryFile()
        processBuilder = processBuilder.redirectOutput(ProcessBuilder.Redirect.to(capturedStdout.toFile()))
        return startAndWrap(processBuilder.start()!!) {
            it.exitValue() to Files.readString(capturedStdout, Charsets.UTF_8)
        }
    }

    private fun getJvmProcessBuilder(): RResult<ProcessBuilder, IOException> {
        if (javaCwd == null) {
            return RFailure(NoSuchFileException("$cwd"))
        }
        javaCwd.mkdir()
        val b = ProcessBuilder(listOf(command) + args)
        b.directory(javaCwd.toFile())
        b.environment().putAll(env)
        return RSuccess(b)
    }

    private fun <T : Any> startAndWrap(
        process: Process,
        resultFromProcess: (Process) -> T,
    ): RunningChildProcess<T> {
        val future = cancelGroup.newCompletableFuture<T, Nothing>(
            description = processDescription(),
        )
        future.onCancellation {
            process.destroy()
            // TODO: Maybe forcibly terminate if it takes to long to shut down.
        }
        process.onExit().whenCompleteAsync(
            { _, err: Throwable? ->
                if (err != null) {
                    console.warn(
                        "Command execution failed, ${
                            this@ProcessBuilderBasedPendingChildProcess
                        }: $err",
                    )
                    future.completeError(err)
                } else {
                    future.completeOk(resultFromProcess(process))
                }
            },
            cancelGroup.executorService,
        )

        class RunningChildProcessImpl : RunningChildProcess<T>(future) {
            override fun cancelSignalling(mayInterruptIfRunning: Boolean): SignalRFuture {
                process.destroy()
                val destroyed = cancelGroup.newCompletableFuture<Unit, Nothing>(
                    "Process terminated: ${processDescription()}",
                )
                process.onExit().whenCompleteAsync(
                    { _, err ->
                        if (err != null) {
                            destroyed.completeError(err)
                        } else {
                            destroyed.completeOk(Unit)
                        }
                    },
                    cancelGroup.executorService,
                )
                return destroyed
            }
        }
        return RunningChildProcessImpl()
    }

    private fun processDescription() = "exec $command"

    private fun temporaryFile(): Path = Files.createTempFile(null, null)
}

private class UnrunnableChildProcessImpl<R : Any>(
    exitFuture: RFuture<R, Nothing>,
) : RunningChildProcess<R>(exitFuture) {
    override fun cancelSignalling(mayInterruptIfRunning: Boolean): SignalRFuture =
        preComputedFuture(Unit)
}
