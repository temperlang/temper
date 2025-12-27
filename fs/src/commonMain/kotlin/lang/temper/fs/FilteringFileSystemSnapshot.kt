package lang.temper.fs

import lang.temper.common.ContentHash
import lang.temper.common.ContentHashResult
import lang.temper.common.MimeType
import lang.temper.common.MutableWeakMap
import lang.temper.common.NotHashableException
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.mutableWeakMapOf
import lang.temper.log.FilePath
import java.io.IOException

class FilteringFileSystemSnapshot(
    val fileSystem: FileSystem,
    fileFilterRules: FileFilterRules,
    root: FilePath = FilePath.emptyPath,
) : FileSystemSnapshot {
    private data class DirRecord(
        val children: List<FilePath>,
    )

    private data class FileRecord(
        val mimeType: MimeType?,
        val hash: ContentHash,
    )

    private val dirs: Map<FilePath, DirRecord>
    private val files: Map<FilePath, FileRecord>
    private val contentCache: MutableWeakMap<FilePath, Pair<WrappedByteArray, ContentHashResult>> =
        mutableWeakMapOf()
    var error: IOException?
        private set

    override operator fun get(filePath: FilePath): FileSnapshot {
        if (filePath.isDir) {
            val dirRecord = dirs[filePath]
                ?: return FileSnapshot.NoSuchFile(filePath)
            return FileSnapshot.Dir(filePath, dirRecord.children)
        }
        val fileRecord = files[filePath]
            ?: return FileSnapshot.NoSuchFile(filePath)
        val snapshotHash = fileRecord.hash
        // Read file, possibly populating cache
        val (content, contentHashResult) = readFile(filePath)
        if (content == null || contentHashResult !is RSuccess) {
            return FileSnapshot.UnavailableFile(filePath, snapshotHash = snapshotHash)
        }
        val contentHash = contentHashResult.result
        return if (snapshotHash == contentHash) {
            FileSnapshot.UpToDateFile(filePath, fileRecord.mimeType, content, snapshotHash)
        } else {
            FileSnapshot.SkewedFile(
                path = filePath,
                mimeType = fileRecord.mimeType,
                content = content,
                contentHash = contentHash,
                snapshotHash = snapshotHash,
            )
        }
    }

    internal fun clearCacheForUnitTest() {
        synchronized(this) {
            contentCache.clear()
        }
    }

    init {
        var error: IOException? = null
        val dirs = mutableMapOf<FilePath, DirRecord>()
        val files = mutableMapOf<FilePath, FileRecord>()
        try {
            fun walk(p: FilePath) {
                if (p.isDir) {
                    when (val listing = fileSystem.directoryListing(p)) {
                        is RFailure -> if (error != null) { error = listing.failure }
                        is RSuccess -> {
                            val filtered = listing.result.filter {
                                !fileFilterRules.isIgnored(it)
                            }
                            dirs[p] = DirRecord(filtered)
                            filtered.forEach {
                                walk(it)
                            }
                        }
                    }
                } else {
                    val mimeType = fileSystem.readMimeType(p)
                    val (_, contentHashResult) = readFile(p)
                    if (contentHashResult is RSuccess) {
                        files[p] = FileRecord(mimeType, contentHashResult.result)
                    }
                }
            }
            if (!fileFilterRules.isIgnored(root)) {
                walk(root)
            }
        } catch (ex: IOException) {
            error = ex
        }
        // Make sure that we can enumerate files starting at the root.
        var rootAncestor = root
        while (rootAncestor.segments.isNotEmpty()) {
            val parent = rootAncestor.dirName()
            if (parent !in dirs) {
                dirs[parent] = DirRecord(listOf(rootAncestor))
            }
            rootAncestor = parent
        }

        this.error = error
        this.dirs = dirs.toMap()
        this.files = files.toMap()
    }

    private fun readFile(p: FilePath): Pair<WrappedByteArray?, ContentHashResult> {
        val cached = synchronized(this) {
            contentCache[p]
        }
        if (cached != null) {
            return cached
        }
        return when (val read = fileSystem.readBinaryFileContentSync(p)) {
            is RFailure -> {
                if (error != null) { error = read.failure }
                null to notHashableBecauseUnread
            }
            is RSuccess -> {
                val content = read.result
                val hash = ContentHash.fromBytes(CONTENT_HASH_ALGORITHM, read.result.copyOf())
                var result = content to hash
                synchronized(this) {
                    // If there are multiple, concurrent calls to this method, then we might
                    // run into a case where the cache is filled.
                    // We don't want to do a synchronous read inside a critical section, but the below
                    // makes us consistent with the view that the first writer is read by both.
                    val alreadyThere = contentCache[p]
                    if (alreadyThere == null) {
                        contentCache[p] = result
                    } else {
                        result = alreadyThere
                    }
                }
                return result
            }
        }
    }
}

const val CONTENT_HASH_ALGORITHM = "SHA-256"
private val notHashableBecauseUnread =
    RFailure(NotHashableException(IOException("File not readable")))
