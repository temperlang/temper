package lang.temper.fs

import lang.temper.common.MimeType
import lang.temper.common.OpenOrClosed
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.RFuture
import lang.temper.common.invoke
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException

/**
 * A file system abstraction to enable compiler support on a JS backend and to ease unit testing
 * by avoiding a dependency on actual files.
 */
interface FileSystem : AutoCloseable {
    val openOrClosed: OpenOrClosed

    fun classify(filePath: FilePath): FileClassification

    fun isFile(filePath: FilePath) = classify(filePath) == FileClassification.File

    fun directoryListing(dirPath: FilePath): RResult<List<FilePath>, IOException>

    /**
     * Blocking equivalent of useTextualFileContent
     */
    fun textualFileContent(filePath: FilePath): RResult<CharSequence, IOException>

    /**
     * Synchronous access to a file.
     * Do not use this if you can use [readBinaryFileContent] instead.
     * It is provided to allow error message formatting code to fetch snippets
     * of code without having to make everything that logs async.
     *
     * @return null if there is no readable file at the given path.
     */
    fun readBinaryFileContentSync(
        filePath: FilePath,
    ): RResult<WrappedByteArray, IOException>

    fun readBinaryFileContent(filePath: FilePath): RFuture<WrappedByteArray, IOException>

    fun readMimeType(filePath: FilePath): MimeType?

    fun createWatchService(root: FilePath): RResult<FileWatchService, IOException>

    /**
     * Meant only for calling by wrappers around FileSystem to report [IOException]s that
     * could not be handled by the wrapper during a batch operation.
     *
     * If the implementation does not expose [IOException]s then this could raise
     * [UnsupportedOperationException] so do not call otherwise.
     *
     * See [lang.temper.fs.RealWritableFileSystem.onIoException]
     */
    fun reportInternalIoException(ex: IOException) {
        throw UnsupportedOperationException(ex)
    }
}

enum class FileClassification {
    DoesNotExist,
    File,
    Directory,
}

// TODO(tjp, files): Unify with OutputRoot in some fashion.
interface WritableFileSystem : FileSystem {
    /** Similar to `mkdir -p`, creates intermediate as needed. Throws on failure. */
    fun ensureDir(path: FilePath): RFuture<Unit, IOException>

    /**
     * Make sure that path exists and has no file content.
     * This is roughly equivalent to
     *
     *     mv "$path" "$someTempName"
     *     (rm -rf "$someTempName") &
     *     mkdir -p "$path"
     */
    fun ensureCleanDir(path: FilePath): RFuture<Unit, IOException>

    fun deleteDirRecursively(path: FilePath)

    fun write(path: FilePath, bytes: ByteArray)

    /**
     * Writes to a file asynchronously.
     *
     * The returned byte sink must be closed before changed are committed and
     * not closing it is a resource exhaustion hazard.
     */
    @Throws(IOException::class)
    fun writeAsync(
        /** The path to write to. */
        path: FilePath,
        /** The mime-type for the file if it's created. */
        mimeType: MimeType?,
    ): ByteSink

    fun systemAccess(defaultCwd: FilePath, cancelGroup: CancelGroup): AsyncSystemAccess
    fun systemReadAccess(defaultCwd: FilePath, cancelGroup: CancelGroup): AsyncSystemReadAccess
}

fun copyRecursive(from: FileSystem, to: WritableFileSystem) =
    copyRecursive(from = from, fromPath = dirPath(), to = to, toPath = dirPath())

fun copyRecursive(
    from: FileSystem,
    fromPath: FilePath,
    to: WritableFileSystem,
    toPath: FilePath,
): RResult<Unit, IOException> {
    check(fromPath.isDir) { "$fromPath" }
    when (to.classify(toPath)) {
        FileClassification.DoesNotExist -> to.ensureDir(toPath)
        else -> if (!toPath.isDir) {
            return RFailure(NotDirectoryException("$toPath"))
        }
    }
    val files = from.directoryListing(fromPath)
    when (files) {
        is RFailure -> return files
        is RSuccess -> {}
    }
    var error: IOException? = null
    files.result.forEach { fromKid ->
        when (from.classify(fromKid)) {
            FileClassification.DoesNotExist -> error = NoSuchFileException("$fromKid")
            FileClassification.File -> from.readBinaryFileContentSync(fromKid).invoke {
                when (it) {
                    is RFailure -> error = it.failure
                    is RSuccess -> {
                        to.write(
                            path = toPath.resolve(fromKid.segments.last(), isDir = false),
                            bytes = it.result.copyOf(),
                        )
                    }
                }
            }
            FileClassification.Directory -> copyRecursive(
                from = from,
                fromPath = fromKid,
                to = to,
                toPath = toPath.resolve(fromKid.segments.last(), isDir = true),
            )
        }
    }
    return error?.let { RFailure(it) } ?: RSuccess(Unit)
}

/**
 * Reads a resource from the jar where the type of [root] is defined.
 */
expect fun loadResource(root: Any, resource: String): String

/**
 * Reads a resource from the jar where the type of [root] is defined.
 */
expect fun loadResourceBinary(root: Any, resource: String): ByteArray

/**
 * Copy file trees from resources to a location where generated code can use it.
 * Creates subdirectories underneath any common prefix.
 */
expect fun copyResources(
    /**
     * Resources to move
     */
    resources: List<ResourceDescriptor>,
    /**
     * Path to a directory to contain the contents of source directory.
     */
    destinationDir: NativePath,
)
