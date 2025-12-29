package lang.temper.be.cli

import com.sun.akuma.CLibrary
import com.sun.jna.StringArray
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.Cancellable
import lang.temper.common.currents.JoiningCancellable
import lang.temper.common.currents.SignalRFuture
import lang.temper.common.currents.newCompletableFuture
import lang.temper.common.flatMap
import lang.temper.common.ignore
import lang.temper.common.toStringViaBuilder
import lang.temper.fs.NativePath
import lang.temper.fs.WalkSignal
import lang.temper.fs.escapeShellString
import lang.temper.fs.escapeWindowsEnvValue
import lang.temper.fs.escapeWindowsString
import lang.temper.fs.filePathSegment
import lang.temper.fs.getDirectories
import lang.temper.fs.list
import lang.temper.fs.resolve
import lang.temper.fs.rmrf
import lang.temper.fs.walk
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

/**
 * An environment that uses temporary directories in the local filesystem and launches binaries
 */
open class LocalCliEnv(
    specifics: Specifics,
    shellPreferences: ShellPreferences,
    private val cancelGroup: CancelGroup,
    private val tempPrefix: String = "temper-test",
) : CliEnv(specifics, shellPreferences) {
    override val implementation: EnvImpl get() = EnvImpl.Local
    override val isWindows: Boolean get() = Companion.isWindows

    init {
        val releaseOnCancel = object : Cancellable {
            override fun cancel(mayInterruptIfRunning: Boolean) {
                release()
            }
        }
        cancelGroup.add(releaseOnCancel)
    }

    private var localRootCanonical: Path? = null

    /** A temporary directory that the command runs in. Automatically canonicalized. */
    protected var localRootBacking: Path?
        get() = localRootCanonical
        set(value) {
            localRootCanonical = value?.toFile()?.canonicalFile?.toPath()
        }

    private var advice = mapOf<Advice, String>()

    /** When invoked via [CliEnv.using] this is guaranteed to be set correctly. */
    private val localRoot: NativePath get() = localRootBacking!!

    private val pathElements = shellPreferences.pathElements?.map { Path.of(it) as NativePath } ?: envPathElements

    override fun init() {
        require(localRootBacking == null) { "Can't init twice without release" }
        localRootBacking = Files.createTempDirectory(tempPrefix)
    }

    override fun release() {
        localRootBacking?.rmrf()
        localRootBacking = null
    }

    override fun subRoot(sub: FilePath): CliEnv = run {
        PredefinedLocalCliEnv(
            specifics = specifics,
            shellPreferences = shellPreferences,
            cancelGroup = cancelGroup,
            outDir = envPathObj(sub),
        ).also { it.init() }
    }

    override fun maybeFreeze(): Map<Advice, String> {
        when (this.shellPreferences.onFailure) {
            ShellPreferences.OnFailure.Release -> {}
            ShellPreferences.OnFailure.Freeze -> {
                val root = localRootBacking
                if (root != null) {
                    localRootBacking = null
                    advice = mapOf(
                        Advice.CliEnv to """
                            |The environment is in a temporary directory:
                            |cd ${escapeShellString("$root")}
                        """.trimMargin(),
                    )
                }
            }
        }
        return advice
    }

    override fun which(tool: ToolSpecifics): RResult<CliTool, CliFailure> {
        // Cache all the lookups
        val command = localTools.computeIfAbsent(tool) {
            val names = tool.cliNames
            val extensions = if (isWindows) {
                // Using PATHEXT technically is more correct, but we don't care about most of these.
                // For example, could be: ".COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC"
                // (System.getenv("PATHEXT") ?: "").lowercase().split(';')
                listOf(".cmd", ".exe")
            } else {
                listOf("")
            }
            for (pathElement in pathElements) {
                for (name in names) {
                    for (extension in extensions) {
                        val exec = "$name$extension"
                        val candidate = pathElement.resolve(exec)
                        // TODO Also check `Files.isExecutable(candidate)`?
                        if (Files.exists(candidate) && !Files.isDirectory(candidate)) {
                            return@computeIfAbsent RSuccess(candidate)
                        }
                    }
                }
            }

            RFailure(CommandNotFound(tool.cliNames, pathElements.map(Path::toString)))
        }
        // All we need is the File indicating where the local tool is
        return command.mapResult { file -> LocalCliTool(file, tool) }
    }

    /** Convert a file path to an environment path object. */
    private fun envPathObj(path: FilePath): NativePath = localRoot.resolve(path)
    override fun envPath(path: FilePath): String = envPathObj(path).toString()
    override fun relativePath(path: FilePath): String = path.join(File.separator)
    override fun userCachePath(path: FilePath): String = getDirectories().userCacheDir.resolve(path).toString()

    override val pathSeparator: String
        get() = File.pathSeparator

    @Suppress("SwallowedException")
    override fun symlink(source: FilePath, destination: FilePath): Boolean =
        try {
            Files.createSymbolicLink(envPathObj(destination), envPathObj(source))
            true
        } catch (exc: FileSystemException) {
            // Windows easily denies symlinks while officially supporting them, which gives a vague:
            // FileSystemException with message "A required privilege is not held by the client"
            false
        } catch (exc: UnsupportedOperationException) {
            false
        }

    override fun fileExists(path: FilePath): Boolean = Files.exists(envPathObj(path))
    override fun fileExistsInUserCache(path: FilePath): Boolean =
        Files.exists(getDirectories().userCacheDir.resolve(path))

    override fun readDir(path: FilePath, recursively: Boolean): List<FilePath> {
        check(path.isDir)
        val dir: NativePath = envPathObj(path)
        return try {
            if (recursively) {
                buildList {
                    dir.walk { _, filePath: FilePath ->
                        add(filePath)
                        WalkSignal.Continue
                    }
                }
            } else {
                dir.list().map {
                    path.resolve(FilePathSegment("${it.fileName}"), isDir = it.isDirectory())
                }
            }
        } catch (e: FileSystemException) {
            ignore(e)
            emptyList()
        }
    }

    override fun readFile(path: FilePath): String {
        check(path.isFile)
        return Files.readString(envPathObj(path), specifics.preferredEncoding.realCharset)
    }

    override fun readBinary(path: FilePath): ByteArray {
        check(path.isFile)
        return Files.readAllBytes(envPathObj(path))
    }

    override fun remove(path: FilePath, errIfMissing: Boolean) {
        // Note that none of this is atomic, but that's already the case for other operations here.
        val native = envPathObj(path)
        if (!Files.exists(native)) {
            check(!errIfMissing)
            return
        }
        check(path.isFile)
        Files.delete(native)
    }

    override fun makeDir(target: FilePath) {
        require(target.isDir) { "Target must be a directory: $target" }
        if (target != FilePath.emptyPath) {
            Files.createDirectories(envPathObj(target))
        }
    }

    override fun writeLocalFile(sourceFile: NativePath, destination: FilePath) {
        val target = if (destination.isFile) {
            destination
        } else {
            destination.resolve(sourceFile.filePathSegment, isDir = false)
        }
        target.dirName().let {
            if (it != FilePath.emptyPath) {
                Files.createDirectories(envPathObj(it))
            }
        }
        Files.copy(sourceFile, envPathObj(target))
    }

    override fun write(source: ByteArray, destination: FilePath) {
        destination.dirName().let {
            if (it != FilePath.emptyPath) {
                Files.createDirectories(envPathObj(it))
            }
        }
        Files.write(envPathObj(destination), source)
    }

    companion object {
        private val envPathElements by lazy {
            System.getenv("PATH").split(File.pathSeparator).map { Path.of(it) as NativePath }
        }
        private val isWindows by lazy { "windows" in System.getProperty("os.name").lowercase() }

        // Avoid having to escape things whenever possible, but this is handy for diagnostics.
        private fun platformEscape(str: String) = if (isWindows) {
            escapeWindowsString(str)
        } else {
            escapeShellString(str)
        }

        private val localTools = ConcurrentHashMap<ToolSpecifics, RResult<NativePath, CliFailure>>()
    }

    inner class LocalCliTool(command: NativePath, specifics: ToolSpecifics) : CliTool(specifics) {
        override val name: String = command.fileName.toString()
        override val command = "$command"
        override val cliEnv get() = this@LocalCliEnv

        override fun specify(cmd: Command): CommandDetail = CommandDetail(
            command = command,
            args = cmd.args,
            cwd = envPath(cmd.cwd),
            env = cmd.env,
            auxPaths = cmd.aux.mapValues { (_, path) -> envPathObj(path).toString() },
        )

        override fun withCommandPath(newCommand: String): CliTool =
            LocalCliTool(Path.of(newCommand), specifics)

        private fun localFile(path: FilePath): File = envPathObj(path).toFile()

        override fun run(cmd: Command): RResult<EffortSuccess, CliFailure> = runEx(cmd)

        private fun runEx(cmd: Command, inheritStreamsOverride: Boolean = false): RResult<EffortSuccess, CliFailure> {
            val cmdDetail = specify(cmd)
            var effort = Effort(
                command = cmdDetail,
                reproduceAdvice = cmd.reproduce + mapOf(
                    Advice.CliTool to runSteps(cmdDetail),
                ),
                cliEnv = this@LocalCliEnv,
            )
            return RResult.of {
                val pr = ProcessBuilder()
                // Set the working directory
                pr.directory(envPathObj(cmd.cwd).toFile())
                pr.environment().putAll(cmd.env)
                if (inheritStreamsOverride) {
                    pr.redirectError(ProcessBuilder.Redirect.INHERIT)
                    pr.redirectInput(ProcessBuilder.Redirect.INHERIT)
                    pr.redirectOutput(ProcessBuilder.Redirect.INHERIT)
                } else {
                    // Always redirecting error stream; maybe this belongs in specifics?
                    when (val path = cmd.aux[Aux.Stderr]) {
                        null -> pr.redirectErrorStream(true)
                        else -> pr.redirectError(localFile(path))
                    }
                }

                val args = (listOf(command) + cmd.args).let { args ->
                    when {
                        isWindows -> args.map { it.replace("\"", "\\\"") }
                        else -> args
                    }
                }
                pr.command(args)
                cancelGroup.requireNotCancelled()
                val process: Process = pr.start()
                val processFuture = cancelGroup.newCompletableFuture<Unit, Nothing>(cmdDetail.verbose())
                process.onExit().handle { _, err: Throwable? ->
                    if (err != null) {
                        processFuture.completeError(err)
                    } else {
                        processFuture.completeOk(Unit)
                    }
                }
                val processCancellable = object : JoiningCancellable {
                    override fun toString(): String = "$process"
                    override fun cancelSignalling(mayInterruptIfRunning: Boolean): SignalRFuture {
                        process.destroy()
                        return processFuture
                    }
                }
                cancelGroup.add(processCancellable)
                try {
                    val (out, truncated) =
                        Read.truncating(InputStreamReader(process.inputStream, charset.realCharset))
                    effort = effort.copy(stdout = out)
                    if (truncated) {
                        return@runEx RFailure(TruncatedOutput(effort))
                    }
                    val exitCode = process.waitFor()
                    // Read what we can, and store any errors.
                    val auxOut = mutableMapOf<Aux, String>()
                    val auxErr = mutableMapOf<Aux, IOException>()
                    for ((key, path) in cmd.aux) {
                        val nativePath = envPathObj(path)
                        if (nativePath.exists()) {
                            try {
                                auxOut[key] = Files.readString(nativePath, charset.realCharset)
                            } catch (exc: IOException) {
                                auxErr[key] = exc
                            }
                        }
                    }
                    // Update result effort.
                    effort = effort.copy(
                        auxOut = auxOut,
                        auxErr = auxErr,
                        exitCode = exitCode,
                    )
                } finally {
                    process.destroyForcibly()
                }
                effort
            }.mapFailure {
                CliFailure(
                    "Unexpected exception",
                    cause = it,
                    effort = effort,
                )
            }.flatMap { eff ->
                eff.asResult()
            }
        }

        override fun runAsLast(cmd: Command) {
            val cmdDetail = specify(cmd)
            try {
                val libc = CLibrary.LIBC
                libc.chdir(cmdDetail.cwd)
                for ((k, v) in cmdDetail.env) {
                    libc.setenv(k, v)
                }
                CLibrary.LIBC.execv(
                    cmdDetail.command,
                    StringArray(
                        (listOf(cmdDetail.command) + cmdDetail.args).toTypedArray(),
                    ),
                )
            } catch (_: UnsatisfiedLinkError) {
                // Fall back to simple process running.
                var exitCode = -1
                try {
                    exitCode = runEx(cmd, inheritStreamsOverride = true).getOrElse(
                        mapResult = { it as? Effort },
                        mapFailure = { it.effort as? Effort },
                    )?.exitCode ?: -1
                } finally {
                    // We need to pretend we didn't exist anymore, so just exit with same code as the child.
                    exitProcess(exitCode)
                }
            }
        }

        private fun runSteps(command: CommandDetail): String =
            buildString {
                append("cd ")
                append(command.cwd)
                append("\n")
                if (isWindows) {
                    command.env.forEach { (k, v) ->
                        append("set $k=${escapeWindowsEnvValue(v)}\n")
                    }
                } else {
                    command.env.forEach { (k, v) -> append("$k=${escapeShellString(v)} ") }
                }
                append(command.command)
                command.args.forEach { arg -> append(' ').append(platformEscape(arg)) }
            }
    }
}

/**
 * Convenience class to read an input stream and abort if there's too much data.
 */
data class Read(
    val data: String,
    val truncated: Boolean,
) {
    companion object {
        private const val LARGE_FILE = 1024 * 1024 * 16
        private const val BUFFER = 1024 * 4
        private val lineSep = System.lineSeparator() ?: "\n"

        /** Reads a large input and returns true if the read was truncated. */
        fun truncating(reader: Reader, limit: Int = LARGE_FILE): Read {
            var trunc = false
            val unixLineSep = lineSep == "\n"
            return Read(
                toStringViaBuilder { sb ->
                    val cb = CharArray(BUFFER)
                    var totalRead = 0
                    while (totalRead < limit) {
                        val read = reader.read(cb, 0, BUFFER)
                        if (read < 0) return@toStringViaBuilder
                        if (unixLineSep) {
                            sb.appendRange(cb, 0, read)
                        } else {
                            sb.append(String(cb, 0, read).replace(lineSep, "\n"))
                        }
                        totalRead += read
                    }
                    trunc = true
                },
                trunc,
            )
        }
    }
}
