package lang.temper.fs

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.select
import lang.temper.common.MimeType
import lang.temper.common.OpenOrClosed
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.commonPrefixLength
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.preComputedResultFuture
import lang.temper.common.putMulti
import lang.temper.common.subListToEnd
import lang.temper.log.FilePath
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePathSegment
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NotDirectoryException
import java.util.TreeSet
import kotlin.math.min

/**
 * Internally, we use a list of paths as a key so that we can iterate to find the longest matching
 * without copying lists.
 */
typealias FsKey = List<FilePathSegment>

/**
 * A file system view that combines multiple file systems.
 *
 * This is used, for example, to combine `~/.templandia/...` with `local/...` so that we can
 * provide short file paths in error logs to both and make it impossible for user code to mention
 * other files on the file system.
 */
class StitchedFileSystem private constructor(
    handler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> },
) : FileSystem {
    constructor(roots: Map<FilePath, FileSystem>) : this() {
        for ((filePath, fileSystem) in roots) {
            addRoot(filePath, fileSystem)
        }
    }

    /**
     * TODO Also need some `removeRoot` or something, presumably.
     */
    @Synchronized
    fun addRoot(filePath: FilePath, fileSystem: FileSystem) {
        val fsKey = filePath.segments
        require(filePath.isDir) { "Can only root a file system at a directory" }
        // Validate disjoint.
        for (otherKey in mutableRoots.keys) {
            val commonCount = commonPrefixLength(fsKey, otherKey)
            require(
                commonCount != fsKey.size && commonCount != otherKey.size,
            ) {
                "Cannot stitch non-disjoint file systems: ${
                    fsKey.join(isDir = true)
                } and ${
                    otherKey.join(isDir = true)
                }"
            }
        }
        // Store.
        mutableRoots[fsKey] = fileSystem
        // Phantomize parents.
        // If we've got an FsKey like foo/bar/
        // we need to pretend that foo/ exists.
        var prefix: FsKey = fsKey
        while (true) {
            if (prefix.isEmpty()) { break }
            val parent = prefix.subList(0, prefix.size - 1)
            phantomDirs.putMulti(parent, FilePath(prefix, isDir = true)) { TreeSet() }
            prefix = parent
        }
        // Update watches.
        for (watch in watches) {
            watch.addFsRoot(fsKey, fileSystem)
        }
    }

    private val mutableRoots = mutableMapOf<FsKey, FileSystem>()

    val roots: Map<FsKey, FileSystem> get() = mutableRoots

    companion object {
        private fun rejoin(fsKey: FsKey, relPath: FilePath) =
            relPath.copy(segments = fsKey + relPath.segments)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default + handler)

    private val phantomDirs = mutableMapOf<FsKey, MutableSet<FilePath>>()

    private val watches = mutableSetOf<StitchedWatchService>()

    @Synchronized
    private fun removeWatch(watch: StitchedWatchService) {
        watches.remove(watch)
    }

    /**
     * If the path matches any root, splits into
     * - the key for that root,
     * - the file system for the root, and
     * - the remaining path after the key.
     */
    private fun split(externalPath: FilePath): Triple<FsKey, FileSystem, FilePath>? {
        var possibleKey: FsKey = externalPath.segments
        while (true) {
            val fs = roots[possibleKey]
            if (fs != null) {
                return Triple(
                    possibleKey,
                    fs,
                    externalPath.copy(
                        segments = externalPath.segments.subListToEnd(possibleKey.size),
                    ),
                )
            }
            val lastIndex = possibleKey.lastIndex
            if (lastIndex < 0) { return null }
            possibleKey = possibleKey.subList(0, lastIndex)
        }
    }

    override val openOrClosed: OpenOrClosed
        get() = if (roots.values.any { it.openOrClosed == OpenOrClosed.Open }) {
            OpenOrClosed.Open
        } else {
            OpenOrClosed.Closed
        }

    override fun close() {
        roots.values.forEach { it.close() }
        coroutineScope.cancel("Closing file system")
    }

    override fun classify(filePath: FilePath): FileClassification {
        val known = split(filePath)
        return when {
            known != null -> {
                val (_, system, relPath) = known
                system.classify(relPath)
            }
            filePath.isDir && filePath.segments in phantomDirs ->
                FileClassification.Directory
            else -> FileClassification.DoesNotExist
        }
    }

    override fun directoryListing(dirPath: FilePath): RResult<List<FilePath>, IOException> {
        val owned = split(dirPath)
        val paths: List<FilePath>? = when {
            owned != null -> {
                val (fsKey, fs, relPath) = owned
                when (val fsResult = fs.directoryListing(relPath)) {
                    is RFailure -> return fsResult
                    is RSuccess -> fsResult.result.map { rejoin(fsKey, it) }
                }
            }
            // TODO: if the list contains a file system key and the root does not exist, should
            // we exclude it from the listing?
            // If it's been replaced by a file, should we adjust the isDir bit?
            dirPath.isDir -> phantomDirs[dirPath.segments]?.toList()
            else -> null
        }
        return RSuccess(paths ?: emptyList())
    }

    private fun noFileResult(filePath: FilePath) = RFailure(
        FileNotFoundException("$filePath"),
    )

    override fun textualFileContent(filePath: FilePath): RResult<CharSequence, IOException> {
        val (_, system, relPath) = split(filePath)
            ?: return noFileResult(filePath)
        return system.textualFileContent(relPath)
    }

    override fun readBinaryFileContentSync(filePath: FilePath): RResult<WrappedByteArray, IOException> {
        val (_, system, relPath) = split(filePath)
            ?: return noFileResult(filePath)
        return system.readBinaryFileContentSync(relPath)
    }

    override fun readBinaryFileContent(filePath: FilePath): RFuture<WrappedByteArray, IOException> {
        val (_, system, relPath) = split(filePath)
            ?: return preComputedResultFuture(noFileResult(filePath))
        return system.readBinaryFileContent(relPath)
    }

    override fun readMimeType(filePath: FilePath): MimeType? {
        val (_, system, relPath) = split(filePath)
            ?: return null
        return system.readMimeType(relPath)
    }

    override fun createWatchService(root: FilePath): RResult<FileWatchService, IOException> {
        if (!root.isDir) { return RFailure(NotDirectoryException("$root")) }
        // If roots were fixed, we could potentially simplify results, but they aren't so be flexible.
        return RSuccess(StitchedWatchService(root, this))
    }

    private class FilePathPrefixingWatchService(
        val pathPrefix: FsKey,
        val underlying: FileWatchService,
    ) : FileWatchService {
        override val openOrClosed: OpenOrClosed
            get() = underlying.openOrClosed

        @OptIn(ExperimentalCoroutinesApi::class)
        override val changes = CoroutineScope(Dispatchers.Default).produce {
            while (true) {
                send(underlying.changes.receive().map { rejoinChange(it) })
            }
        }

        private fun rejoinChange(fileChange: FileChange) = FileChange(
            filePath = rejoin(pathPrefix, fileChange.filePath),
            fileChangeKind = fileChange.fileChangeKind,
        )

        override fun close() {
            underlying.close()
        }
    }

    private inner class StitchedWatchService(
        private val watchRoot: FilePath,
        stitchedFileSystem: StitchedFileSystem,
    ) : FileWatchService {
        /** Map to support also removing stitch roots. */
        private val watchServices = mutableMapOf<FsKey, FileWatchService>()

        init {
            for ((fsKey, fileSystem) in stitchedFileSystem.roots) {
                addFsRoot(fsKey, fileSystem)
            }
        }

        @Synchronized
        fun addFsRoot(fsKey: FsKey, fileSystem: FileSystem) {
            val commonLength = commonPrefixLength(watchRoot.segments, fsKey)
            if (commonLength >= min(watchRoot.segments.size, fsKey.size)) {
                val subPath = FilePath(watchRoot.segments.subListToEnd(commonLength), isDir = watchRoot.isDir)
                val wsForSegmentResult = fileSystem.createWatchService(subPath)
                if (wsForSegmentResult is RSuccess) {
                    val watchAll = wsForSegmentResult.result
                    val watchService = FilePathPrefixingWatchService(pathPrefix = fsKey, watchAll)
                    watchServices[fsKey] = watchService
                }
            }
        }

        override var openOrClosed = OpenOrClosed.Open
            private set

        @OptIn(ExperimentalCoroutinesApi::class)
        override val changes = coroutineScope.produce {
            while (true) {
                val result = select {
                    for (watchService in watchServices.values) {
                        watchService.changes.onReceive { it }
                    }
                }
                send(result)
            }
        }

        @Synchronized
        override fun close() {
            watchServices.values.forEach { it.close() }
            watchServices.clear()
            openOrClosed = OpenOrClosed.Closed
            removeWatch(this)
        }
    }
}
