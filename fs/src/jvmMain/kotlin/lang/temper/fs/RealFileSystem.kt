package lang.temper.fs

import com.sun.nio.file.ExtendedWatchEventModifier
import io.methvin.watchservice.MacOSXListeningWatchService
import io.methvin.watchservice.WatchablePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.withContext
import lang.temper.common.MimeType
import lang.temper.common.OpenOrClosed
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.nio.file.attribute.BasicFileAttributeView
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/** An implementation of a Temper file system backed by a [java.nio.file.FileSystem]. */
open class RealFileSystem(
    /** The root of the file tree exposed via the Temper file system abstraction. */
    val javaRoot: Path,
    /** The abstract path corresponding to [javaRoot].  Defaults to the empty path. */
    val basePath: FilePath = FilePath.emptyPath,
) : FileSystem {
    init {
        require(javaRoot.exists()) { "$javaRoot does not exist" }
        require(basePath.isDir)
    }

    @Volatile
    private var hasBeenClosed = false
    override val openOrClosed: OpenOrClosed
        get() = when {
            !javaRoot.fileSystem.isOpen -> OpenOrClosed.Closed
            hasBeenClosed -> OpenOrClosed.Closed
            else -> OpenOrClosed.Open
        }

    override fun close() {
        // We don't really own the underlying file system, just the part of it under javaRoot.
        hasBeenClosed = true
    }

    protected fun toJavaPath(filePath: FilePath): Path? {
        val segments = filePath.segments
        val baseSegments = basePath.segments
        val nSegments = segments.size
        val nBaseSegments = baseSegments.size
        if (nSegments < nBaseSegments) {
            return null
        }
        for (i in 0 until nBaseSegments) {
            if (segments[i] != baseSegments[i]) {
                return null
            }
        }

        var javaPath = javaRoot
        for (i in nBaseSegments until nSegments) {
            javaPath = javaPath.resolve(segments[i].fullName)
        }
        return javaPath
    }

    override fun classify(filePath: FilePath): FileClassification {
        val javaPath = toJavaPath(filePath) ?: return FileClassification.DoesNotExist
        return classify(javaPath)
    }

    override fun directoryListing(dirPath: FilePath): RResult<List<FilePath>, IOException> {
        if (!dirPath.isDir) {
            return RFailure(NotDirectoryException("$dirPath"))
        }
        val javaFile = toJavaPath(dirPath)
            ?: return RFailure(FileNotFoundException("$dirPath"))
        val fileStream = try {
            Files.list(javaFile)
        } catch (ex: IOException) {
            return RFailure(ex)
        }
        return RSuccess(
            buildList {
                for (entry in fileStream) {
                    add(
                        FilePath(
                            dirPath.segments + FilePathSegment(entry.fileName.toString()),
                            isDir = Files.isDirectory(entry),
                        ),
                    )
                }
            },
        )
    }

    override fun textualFileContent(filePath: FilePath): RResult<CharSequence, IOException> {
        val javaPath = toJavaPath(filePath)
            ?: return RFailure(FileNotFoundException("$filePath"))
        return RResult.of(IOException::class) {
            Files.readString(javaPath)
        }
    }

    private fun callBackWithBytes(
        filePath: FilePath,
        onNotReadable: () -> Unit,
        action: (WrappedByteArray) -> Unit,
    ) {
        val javaPath = toJavaPath(filePath)
            ?: return onNotReadable()
        val sizeLong = try {
            Files.size(javaPath)
        } catch (_: IOException) {
            onNotReadable()
            return
        }
        if (sizeLong > Int.MAX_VALUE) {
            onNotReadable()
            return
        }
        val size = sizeLong.toInt()
        val byteChannel = try {
            AsynchronousFileChannel.open(javaPath, StandardOpenOption.READ)
        } catch (_: IOException) {
            onNotReadable()
            return
        }
        val byteBuffer = ByteBuffer.allocate(size)
        val completionHandler = object : CompletionHandler<Int, ByteBuffer> {
            override fun completed(result: Int?, attachment: ByteBuffer?) {
                if (result == size && attachment != null) {
                    action(
                        WrappedByteArray.build(size) {
                            if (attachment.hasArray()) {
                                val offset = attachment.arrayOffset()
                                append(attachment.array(), offset, offset + size)
                            } else {
                                val bytes = ByteArray(size)
                                attachment.get(bytes)
                                append(bytes)
                            }
                        },
                    )
                } else {
                    onNotReadable()
                }
            }

            override fun failed(exc: Throwable?, attachment: ByteBuffer?) {
                onNotReadable()
            }
        }
        byteChannel.read(byteBuffer, 0L, byteBuffer, completionHandler)
    }

    override fun readBinaryFileContentSync(filePath: FilePath): RResult<WrappedByteArray, IOException> {
        val javaPath = toJavaPath(filePath)
            ?: return RFailure(FileNotFoundException("$filePath"))
        return RResult.of(IOException::class) {
            WrappedByteArray.build { append(Files.readAllBytes(javaPath)) }
        }
    }

    override fun readBinaryFileContent(filePath: FilePath): RFuture<WrappedByteArray, IOException> {
        val results = UnmanagedFuture.newCompletableFuture<WrappedByteArray, IOException>("Reading file $filePath")
        callBackWithBytes(
            filePath = filePath,
            onNotReadable = {
                results.completeFail(IOException("$filePath is not readable"))
            },
        ) {
            results.completeOk(it)
        }
        return results
    }

    // Let clients fall back to any kind of inference they want.
    override fun readMimeType(filePath: FilePath): MimeType? = null

    /** For [AsyncSystemAccess] convenience, when this information is handy. */
    fun envPath(pathToFileCreatorRoot: FilePath, path: FilePath): String {
        // If empty, a final slash could be seen as file system root.
        val sub = pathToFileCreatorRoot.resolve(path.segments, isDir = false)
        // We don't accept "..", but normalize anyway.
        return javaRoot.toAbsolutePath().resolve(sub).normalize().toString()
    }

    override fun createWatchService(root: FilePath): RResult<FileWatchService, IOException> =
        RResult.of<FileWatchService, IOException>(IOException::class) {
            FileWatchServiceImpl(root)
        }

    private inner class FileWatchServiceImpl(
        watched: FilePath,
    ) : FileWatchService {
        @Volatile
        private var hasBeenClosed = false

        override val openOrClosed: OpenOrClosed
            get() = when {
                hasBeenClosed -> OpenOrClosed.Closed
                else -> this@RealFileSystem.openOrClosed
            }

        private val watchService: WatchService
        private val toWatchable: (Path) -> Watchable
        private val canRecurse: Boolean

        init {
            val osName = System.getProperty("os.name")
            val isDefaultFs = javaRoot.fileSystem == FileSystems.getDefault()
            canRecurse = osName.startsWith("Windows") && isDefaultFs
            when {
                // On Mac OS X, the default WatchService implementation polls every 8 seconds or so.
                // Instead, we use an almost-drop-in replacement which uses the FSEvents API.
                osName == "Mac OS X" && isDefaultFs -> {
                    toWatchable = { WatchablePath(it) }
                    watchService = MacOSXListeningWatchService(
                        object : MacOSXListeningWatchService.Config {},
                    )
                }
                osName.startsWith("Linux") && isDefaultFs -> {
                    // On Linux, we're getting crashes inside the watcher thread such as from this bug report to Oracle,
                    // which is why we're using Gradle's file-events system on Linux.
                    // in 2013: https://forums.oracle.com/ords/apexds/post/java-nio-file-nosuchfileexception-on-watchservice-6352
                    // On usage, see also: https://github.com/gradle/gradle/blob/644751cdc2b67494bbf3612e1405a3757098a3bd/subprojects/file-watching/src/main/java/org/gradle/internal/watch/registry/impl/AbstractFileWatcherRegistryFactory.java#L53
                    watchService = FileEventsWatchService(LinuxFileEventFunctions::class.java)
                    toWatchable = { watchService.watchable(it) }
                }
                else -> {
                    // Presumably only Windows down here, but eh.
                    toWatchable = { it }
                    watchService = javaRoot.fileSystem.newWatchService()
                }
            }

            val javaPath = toJavaPath(watched)
            if (javaPath != null) {
                walkAndRegister(javaPath, atTop = true)
            }
        }

        private fun walkAndRegister(
            javaPath: Path,
            /** If we pass this in, also add [FileChange.Kind.Created] changes */
            changesOut: MutableList<FileChange>? = null,
            filePathSegments: List<FilePathSegment>? = null,
            atTop: Boolean = false,
        ) {
            val isDir = Files.isDirectory(javaPath)
            changesOut?.add(
                FileChange(FilePath(filePathSegments!!, isDir), FileChange.Kind.Created),
            )
            if (isDir) {
                if (atTop || !canRecurse) {
                    val options = when (canRecurse) {
                        true -> arrayOf(ExtendedWatchEventModifier.FILE_TREE)
                        false -> emptyArray()
                    }
                    safeFs({
                        @Suppress("SpreadOperator")
                        toWatchable(javaPath).register(watchService, watchedEventKinds, *options)
                    }) {
                        // Seen this under Linux file-events for ".deleting" files.
                        return@walkAndRegister
                    }
                }
                val kids = safeFs({ Files.list(javaPath) }) {
                    // This happens occasionally in Windows for ".deleting" files under temper.out.
                    // But we don't expect such problems to persist, so don't die over them.
                    return@walkAndRegister
                }
                for (javaFile in kids) {
                    walkAndRegister(
                        javaFile,
                        changesOut,
                        filePathSegments?.let {
                            filePathSegments + FilePathSegment(
                                javaFile.getName(javaFile.nameCount - 1).toString(),
                            )
                        },
                    )
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override val changes = CoroutineScope(Dispatchers.Default).produce {
            while (true) {
                val result = withContext(Dispatchers.IO) {
                    try {
                        // Run using a different dispatcher, so we're not actually blocking the
                        // current coroutine's thread per https://stackoverflow.com/a/63332658/20394
                        watchKeyToChanges(watchService.take())
                    } catch (_: ClosedWatchServiceException) {
                        null
                    } catch (_: InterruptedException) {
                        null
                    }
                }
                send(result ?: emptyList())
                if (hasBeenClosed) {
                    break
                }
            }
        }

        private fun watchKeyToChanges(watchKey: WatchKey): List<FileChange> {
            val changes = mutableListOf<FileChange>()
            for (watchEvent in watchKey.pollEvents()) {
                val context = watchEvent.context() ?: continue // been null on windows at least once
                val javaChangePath = when (val watchable = watchKey.watchable()) {
                    is Path -> watchable.resolve(context as Path)
                    // MacOSXWatchService and FileEventsWatchService just use the fully qualified path as the context.
                    else -> (context as? Path) ?: continue
                }
                val changePath = fromJavaPath(javaRoot.relativize(javaChangePath)) ?: continue
                when (watchEvent.kind()) {
                    StandardWatchEventKinds.ENTRY_CREATE -> {
                        walkAndRegister(javaChangePath, changes, changePath.segments)
                    }
                    StandardWatchEventKinds.ENTRY_MODIFY ->
                        changes.add(FileChange(changePath, FileChange.Kind.Edited))
                    StandardWatchEventKinds.ENTRY_DELETE ->
                        changes.add(FileChange(changePath, FileChange.Kind.Deleted))
                    else -> Unit
                }
            }
            watchKey.reset()
            return changes.toList()
        }

        override fun close() {
            hasBeenClosed = true
            watchService.close()
        }
    }
}

private val watchedEventKinds = arrayOf(
    StandardWatchEventKinds.ENTRY_CREATE,
    StandardWatchEventKinds.ENTRY_MODIFY,
    StandardWatchEventKinds.ENTRY_DELETE,
)

fun fromJavaPath(relative: Path): FilePath? {
    if (relative.nameCount != 0 && relative.getName(0).toString() == "..") {
        // Not under javaRoot
        return null
    }
    return FilePath(
        (0 until relative.nameCount).map {
            FilePathSegment(relative.getName(it).toString())
        },
        isDir = classify(relative) == FileClassification.Directory,
    )
}

fun fromJavaPathThrowing(relative: Path): FilePath =
    fromJavaPath(relative) ?: throw InvalidPath("$relative not under root")

class InvalidPath(message: String) : RuntimeException(message)

private fun classify(javaFile: Path): FileClassification {
    val view = Files.getFileAttributeView(
        javaFile,
        BasicFileAttributeView::class.java,
    )
    val attributes = safeFs({ view?.readAttributes() }) {
        // Symlinks are among problems here on Windows.
        return@classify FileClassification.DoesNotExist
    }
    return when {
        attributes == null -> FileClassification.DoesNotExist
        attributes.isDirectory -> FileClassification.Directory
        else -> FileClassification.File
    }
}

private inline fun<T> safeFs(action: () -> T, fallback: () -> T): T {
    return try {
        action()
    } catch (_: IOException) {
        // Catch broadly because fails are broad, such as (java.nio.file.) AccessDeniedException or NoSuchFileException.
        // TODO Keep track and log each failure message? Once?
        fallback()
    }
}

fun toFilePath(path: Path, relativeTo: Path? = null): FilePath {
    val relPath = when (relativeTo) {
        null -> path
        else -> path.relativeTo(relativeTo)
    }
    check(!relPath.isAbsolute)
    return FilePath(relPath.map { FilePathSegment(it.name) }, isDir = path.isDirectory())
}
