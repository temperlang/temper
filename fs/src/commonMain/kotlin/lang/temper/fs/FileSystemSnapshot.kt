package lang.temper.fs

import lang.temper.common.ContentHash
import lang.temper.common.MimeType
import lang.temper.common.WrappedByteArray
import lang.temper.log.FilePath

/**
 * Remembers the files available at a particular point in time so we can do a build
 * based on that conception.
 *
 * May store file contents and/or hashes.
 * Getting a file's contents may fail for several reasons:
 *
 * - the file is no longer readable and its contents is not cached,
 *   (this is also the case when a file is deleted and a directory created in its place)
 * - the file is readable and its content no longer matches the cache,
 *   (also see [FileSnapshot.SkewedFile])
 * - the underlying file system is needed to fetch content into an in-memory cache,
 *   but it is [closed][FileSystem.openOrClosed].
 *
 * In the normal build flow, once a file system watcher notifies us of material changes,
 * we create a file snapshot and then use that to decide what modules are needed, and
 * then provision the modules with the concatenation of parts of one or more files.
 *
 * Having synchronous access to the file-structure and file content hashes allows us to
 * quickly make good, tentative (assuming imports are stable) decisions about re-using module
 * objects from previous builds.
 */
interface FileSystemSnapshot {
    /**
     * @return [FileSnapshot.NoSuchFile] if the file path is unknown.
     *   Otherwise, if the file path [is a directory][FilePath.isDir] then a
     *   [FileSnapshot.Dir].
     *   If the file path [is a file][FilePath.isFile] and known,
     *   then returns a [FileSnapshot.RegularFileSnapshot].
     *   May return a [FileSnapshot.UnavailableFile] if the file content
     *   is unavailable, perhaps because the underlying file system is closed.
     *   A variety of [FileSnapshot.AvailableFile] is returned when file path
     *   references a file whose content is available.
     */
    operator fun get(filePath: FilePath): FileSnapshot
}

/** A view of a file (regular file or directory) at a moment in time. */
sealed class FileSnapshot {
    abstract val path: FilePath

    /** Indicates that the source is unaware of any file with the path: [path]. */
    data class NoSuchFile(
        override val path: FilePath,
    ) : FileSnapshot()

    /** A directory listing */
    data class Dir(
        override val path: FilePath,
        /** Names of child files.  Each entry has [path] as a prefix. */
        val names: List<FilePath>,
    ) : FileSnapshot()

    /** A regular file. */
    sealed class RegularFileSnapshot : FileSnapshot() {
        abstract val mimeType: MimeType?

        /** The hash of the content at the time the file was snapshotted. */
        abstract val snapshotHash: ContentHash
    }

    /**
     * A regular file whose content is not available.  Perhaps due to [FileSystem.close]
     * It may also be unavailable due to a transient I/O error, a change in read permissions,
     * or an inability to derive a [hash of the file's content][AvailableFile.contentHash].
     */
    data class UnavailableFile(
        override val path: FilePath,
        override val snapshotHash: ContentHash,
    ) : RegularFileSnapshot() {
        override val mimeType: MimeType? get() = null
    }

    /** A regular file whose content is available. */
    sealed class AvailableFile : RegularFileSnapshot() {
        abstract val content: WrappedByteArray

        /** The hash of [content] */
        abstract val contentHash: ContentHash
    }

    /** An available, regular file whose content is out of date. [content] may not match the expected hash. */
    data class SkewedFile(
        override val path: FilePath,
        override val mimeType: MimeType?,
        override val content: WrappedByteArray,
        override val contentHash: ContentHash,
        override val snapshotHash: ContentHash,
    ) : AvailableFile()

    /** An available, regular file whose content is up to date. */
    data class UpToDateFile(
        override val path: FilePath,
        override val mimeType: MimeType?,
        override val content: WrappedByteArray,
        override val contentHash: ContentHash,
    ) : AvailableFile() {
        override val snapshotHash: ContentHash get() = contentHash
    }
}
