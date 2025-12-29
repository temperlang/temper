package lang.temper.log

import lang.temper.common.commonPrefixBy
import lang.temper.common.commonPrefixLength
import lang.temper.common.compatRemoveLast
import lang.temper.common.subListToEnd
import lang.temper.common.toHexPadded
import lang.temper.common.toStringViaBuilder
import lang.temper.common.urlEscapeTo
import kotlin.math.max
import kotlin.math.min

/**
 * A full path starting from the root.  This is like a resolved file path; it may not contain
 * `.` or `..` segments.
 */
data class FilePath(
    val segments: List<FilePathSegment>,
    /**
     * Whether the path represents a directory.
     *
     * Storing this bit lets us have uniform resolution rules whether this is representing a file
     * path or the file portion of a hierarchical URL.
     *
     * Hierarchical URL resolution rules use the presence of a `/` at the end so that
     *
     * - resolving `a/b` with `c` yields `a/c` since `a/b` is presumed to be a file, but
     * - resolving `a/b/` with `c` yields `a/b/c` since `/a/b` is clearly marked as a directory.
     *
     * This property comes in useful because the way module specifiers may be interpreted depends
     * very much on context provided elsewhere.  When a local file might be a mirror of content
     * fetched from the web it'd be handy to be able to map relative specifiers back to that
     * origin address without worrying about corner cases.
     */
    val isDir: Boolean,
) : FileRelatedCodeLocation, Comparable<FilePath> {

    val isFile get() = !isDir

    fun lastOrNull() = segments.lastOrNull()

    /** `this`'s parent directory or the blank file path. */
    fun dirName(): FilePath = when (val n = segments.size) {
        0, 1 -> emptyPath
        else -> FilePath(segments.subList(0, n - 1), true)
    }

    /**
     * Iterates over ancestor file paths from deepest to shallowest.
     *
     * If this is a path like `foo/bar/baz.ext`, iteration proceeds as below, in order.
     *
     * When [skipThis] is false:
     *
     * 1. `foo/bar/baz.ext`
     * 2. `foo/bar/`
     * 3. `foo/`
     * 4. [emptyPath]
     *
     * When [skipThis] is true, iteration produces skips the first, but returns
     * the second and subsequent elements.
     */
    fun ancestors(skipThis: Boolean): Iterable<FilePath> = AncestorIterable(
        if (skipThis) dirName() else this,
    )

    /**
     * Given `this` and `dest`, find a relative path `rel` such that
     * `this / rel` points to `dest`.
     *
     * Exception: if `this` is a file, consider the directory `this` is in.
     * @return a sequence of (file names or `..` or `.`)
     */
    fun relativePathTo(dest: FilePath): List<FilePathSegmentOrPseudoSegment> {
        val sourceSegments = if (isDir) {
            segments
        } else {
            segments.subList(0, max(0, segments.size - 1))
        }
        val destSegments = dest.segments
        val commonPrefixLen = commonPrefixBy(listOf(sourceSegments, destSegments)) { it }.size
        val parts = mutableListOf<FilePathSegmentOrPseudoSegment>()
        repeat(sourceSegments.size - commonPrefixLen) { parts.add(ParentPseudoFilePathSegment) }
        for (i in commonPrefixLen until destSegments.size) {
            parts.add(destSegments[i])
        }
        if (parts.isEmpty()) {
            parts.add(SameDirPseudoFilePathSegment)
        }
        return parts.toList()
    }

    /**
     * If [this] starts with [other], returns a path with that prefix removed, else null.
     * Somewhat similar to `other.relativePathTo(this)` except no pseudo segments.
     */
    fun unprefix(other: FilePath): FilePath? {
        return when (commonPrefixLength(segments, other.segments)) {
            other.segments.size -> FilePath(segments.subListToEnd(other.segments.size), isDir = isDir)
            else -> null
        }
    }

    /**
     * If this is a directory, then [nextSegments] starts with the first as a child of
     * this, otherwise as a sibling per normal URL resolution rules. Further elements
     * of [nextSegments] continue from there.
     */
    fun resolve(nextSegments: Iterable<FilePathSegment>, isDir: Boolean): FilePath {
        val baseSegments = when {
            this.isDir -> segments
            segments.isNotEmpty() -> segments.subList(0, segments.size - 1)
            else -> error("File with empty segment list")
        }
        return FilePath(baseSegments + nextSegments, isDir = isDir)
    }

    fun resolve(relative: FilePath): FilePath {
        return resolve(relative.segments, isDir = relative.isDir)
    }

    /**
     * Resolves one segment against this path.  If this is a directory, then [nextSegment] is
     * treated as a child file, otherwise as a sibling per normal URL resolution rules.
     */
    fun resolve(nextSegment: FilePathSegment, isDir: Boolean): FilePath {
        return resolve(listOf(nextSegment), isDir = isDir)
    }

    fun resolvePseudo(relative: Iterable<FilePathSegmentOrPseudoSegment>, isDir: Boolean): FilePath? {
        val newSegments = segments.toMutableList()
        if (!this.isDir && newSegments.isNotEmpty()) {
            newSegments.compatRemoveLast()
        }
        for (segment in relative) {
            when (segment) {
                is FilePathSegment -> newSegments.add(segment)
                ParentPseudoFilePathSegment -> if (newSegments.isNotEmpty()) {
                    newSegments.compatRemoveLast()
                } else {
                    return null
                }
                SameDirPseudoFilePathSegment -> {}
            }
        }
        return FilePath(newSegments.toList(), isDir = isDir)
    }

    override fun toString(): String = segments.join(isDir = isDir)

    override val diagnostic: String get() = toString()

    override fun compareTo(other: FilePath): Int {
        val aSegments = this.segments
        val bSegments = other.segments
        val n = min(aSegments.size, bSegments.size)
        for (i in 0 until n) {
            // TODO: Should this compare as by UTF-8 order?
            val delta = aSegments[i].fullName.compareTo(bSegments[i].fullName)
            if (delta != 0) {
                return delta
            }
        }
        val delta = aSegments.size - bSegments.size
        if (delta != 0) {
            return delta
        }
        return when {
            this.isDir -> (if (other.isDir) 0 else 1)
            other.isDir -> -1
            else -> 0
        }
    }

    /**
     * True when this path's segments are a prefix of [possibleDescendant]'s.
     * This is non-strict so `x.isAncestorOf(x) == true`.
     */
    fun isAncestorOf(possibleDescendant: FilePath): Boolean {
        val commonPrefixLen = commonPrefixLength(this.segments, possibleDescendant.segments)
        return commonPrefixLen == this.segments.size
    }

    fun withExtension(extension: String?) = lastOrNull()?.let { dirName() + it.withExtension(extension) }

    companion object {
        fun (FilePath).join(
            separator: String = UNIX_FILE_SEGMENT_SEPARATOR,
        ) = segments.join(separator = separator, isDir = isDir)

        fun (FilePath).joinAbsolute(
            separator: String = UNIX_FILE_SEGMENT_SEPARATOR,
        ) = segments.joinAbsolute(separator = separator, isDir = isDir)

        fun (Iterable<FilePathSegmentOrPseudoSegment>).join(
            separator: String = UNIX_FILE_SEGMENT_SEPARATOR,
            isDir: Boolean,
        ) = toStringViaBuilder {
            this.joinPathTo(isDir = isDir, separator = separator, sb = it)
        }

        private fun (Iterable<FilePathSegmentOrPseudoSegment>).joinAbsolute(
            separator: String = UNIX_FILE_SEGMENT_SEPARATOR,
            isDir: Boolean,
        ) = toStringViaBuilder {
            this.joinPathTo(isDir = isDir, separator = separator, absolute = true, sb = it)
        }

        fun (FilePath).joinPathTo(
            sb: StringBuilder,
            separator: String = UNIX_FILE_SEGMENT_SEPARATOR,
        ) = segments.joinPathTo(isDir = isDir, sb = sb, separator = separator)

        fun (Iterable<FilePathSegmentOrPseudoSegment>).joinPathTo(
            isDir: Boolean,
            sb: StringBuilder,
            separator: String = UNIX_FILE_SEGMENT_SEPARATOR,
            absolute: Boolean = false,
        ) {
            var sawOne = absolute
            for (segment in this) {
                if (sawOne) {
                    sb.append(separator)
                } else {
                    sawOne = true
                }
                segment.appendSegmentTo(sb)
            }
            if (isDir) {
                sb.append(separator)
            }
        }

        fun FilePathSegmentOrPseudoSegment.appendSegmentTo(sb: StringBuilder) {
            urlEscapeTo(fullName, sb)
        }

        fun Iterable<String>.toPseudoPath(): List<FilePathSegmentOrPseudoSegment> = map { name ->
            when (name) {
                "." -> SameDirPseudoFilePathSegment
                ".." -> ParentPseudoFilePathSegment
                else -> FilePathSegment(name)
            }
        }

        fun FilePath.isWithin(prefix: FilePath): Boolean {
            val segments = this.segments
            val prefixSegments = prefix.segments
            val nSegments = segments.size
            val nPrefixSegments = prefixSegments.size
            if (nSegments == nPrefixSegments) { return this == prefix }
            if (nSegments < nPrefixSegments) { return false }
            if (!prefix.isDir) { return false }
            return prefixSegments == segments.subList(0, nPrefixSegments)
        }

        /** An empty path; useful for relative paths. */
        val emptyPath = dirPath()
    }

    override val sourceFile: FilePath get() = this
}

operator fun (FilePath).plus(basename: FilePathSegment) = FilePath(
    this.segments + basename,
    isDir = false,
)

operator fun (FilePath).plus(rel: FilePath): FilePath = FilePath(this.segments + rel.segments, isDir = rel.isDir)

val bannedPathSegmentNames = setOf("", ".", "..")

sealed interface FilePathSegmentOrPseudoSegment : Comparable<FilePathSegmentOrPseudoSegment> {
    val fullName: String
    override fun compareTo(other: FilePathSegmentOrPseudoSegment): Int = fullName.compareTo(other.fullName)
}

object ParentPseudoFilePathSegment : FilePathSegmentOrPseudoSegment {
    override val fullName = ".."

    override fun toString(): String = fullName
}

object SameDirPseudoFilePathSegment : FilePathSegmentOrPseudoSegment {
    override val fullName = "."

    override fun toString(): String = fullName
}

/**
 * A segment in a file path.  Segments are separated by path separators like `/`.
 */
data class FilePathSegment(
    override val fullName: String,
) : FilePathSegmentOrPseudoSegment {
    init {
        require(fullName !in bannedPathSegmentNames) {
            "\"$fullName\" is not a valid file path segment"
        }
    }

    private val dotIndex get() = fullName.lastIndexOf('.')

    /** [fullName] without any [extension] or the dot preceding the [extension] */
    val baseName: String
        get() {
            val dotIndex = this.dotIndex
            return if (dotIndex >= 0) {
                fullName.substring(0, dotIndex)
            } else {
                fullName
            }
        }

    /** The [fullName] text starting at the last dot or `null` if there is no dot. */
    val extension: String?
        get() {
            val dotIndex = this.dotIndex
            // Below we use <= instead of < because `.ignored` is a hidden file on UNIX file systems
            // and is not a file with the extension ".ignored".
            return if (dotIndex <= 0) { null } else { fullName.substring(dotIndex) }
        }

    fun withExtension(extension: String?): FilePathSegment = FilePathSegment(
        when (extension) {
            null, "" -> baseName
            else -> {
                require(extension[0] == '.') { extension }
                "$baseName$extension"
            }
        },
    )

    override fun toString(): String = fullName
}

const val UNIX_FILE_SEGMENT_SEPARATOR = "/"

/** True if the character may cause problems when used in a file path segments. */
fun isProblematicInFilePathSegment(c: Char) = when (c) {
    // https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file#naming-conventions
    // > The following reserved characters:
    '<', '>', ':', '"', '/', '\\', '|', '?', '*' -> true
    // > Integer value zero, sometimes referred to as the ASCII NUL character.
    // > Characters whose integer representations are in the range from 1 through 31
    '\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006', '\u0007',
    '\u0008', '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', '\u000E', '\u000F',
    '\u0010', '\u0011', '\u0012', '\u0013', '\u0014', '\u0015', '\u0016', '\u0017',
    '\u0018', '\u0019', '\u001A', '\u001B', '\u001C', '\u001D', '\u001E', '\u001F',
    -> true
    else -> false
}

fun escapeMaybeProblematicPathSegment(segment: String): String {
    val escapeChar = '$'
    if (!segment.any { it == escapeChar || isProblematicInFilePathSegment(it) }) {
        return segment
    }
    return segment.map { char ->
        if (char == escapeChar || isProblematicInFilePathSegment(char)) {
            @Suppress("MagicNumber")
            "${'$'}${char.code.toHexPadded(2)}"
        } else {
            char
        }
    }.joinToString("")
}

fun FilePathSegment.escapeMaybeProblematicPathSegment(): FilePathSegment =
    FilePathSegment(escapeMaybeProblematicPathSegment(fullName))

/**
 * General constructor for a [FilePath]; but see [filePath] and [dirPath].
 * Having this and the following overloads confused JS.
 */
private fun filePath(isDir: Boolean, seg: Iterable<String>) = FilePath(
    seg.map { FilePathSegment(it) },
    isDir,
)

/** Construct a [FilePath] to a file from segments. */
fun filePath(vararg seg: String) = filePath(isDir = false, seg.toList())

/** Construct a [FilePath] to a directory from segments. */
fun dirPath(vararg seg: String) = filePath(isDir = true, seg.toList())

/** Construct a [FilePath] to a file from segments. */
fun filePath(seg: Iterable<String>) = filePath(isDir = false, seg)

/** Construct a [FilePath] to a directory from segments. */
fun dirPath(seg: Iterable<String>) = filePath(isDir = true, seg)

/** Insert a segment before this [FilePath]. */
fun FilePath.prepend(segment: String) = FilePath(listOf(FilePathSegment(segment)) + this.segments, this.isDir)

private class AncestorIterable(
    private val start: FilePath,
) : Iterable<FilePath> {
    override fun iterator(): Iterator<FilePath> = AncestorIterator(start)
}

private class AncestorIterator(
    private var path: FilePath?,
) : Iterator<FilePath> {
    override fun hasNext(): Boolean = path != null
    override fun next(): FilePath {
        val result = path ?: throw NoSuchElementException()
        path = if (result.segments.isNotEmpty()) { result.dirName() } else { null }
        return result
    }
}
