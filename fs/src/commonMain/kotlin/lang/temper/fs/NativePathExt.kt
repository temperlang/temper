package lang.temper.fs

import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath
import lang.temper.log.filePath

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect interface NativePath

/** This is the source distribution root when running tests. */
expect val temperRoot: NativePath

/** Find the root of a submodule, e.g. "$temperRoot/myModule" == "${temperSubRoot(object)}"  */
expect fun temperSubRoot(obj: Any): NativePath

/** the file name, without leading path, but with extension if present */
expect val NativePath.filePathSegment: FilePathSegment

/** find a file reference by resolving a relative FilePath */
expect fun NativePath.resolve(relative: FilePath): NativePath

/** find a file reference by resolving a relative FilePath */
expect fun NativePath.resolveEntry(entry: String): NativePath

fun NativePath.resolveFile(vararg parts: String): NativePath = resolve(filePath(*parts))

fun NativePath.resolveDir(vararg parts: String): NativePath = resolve(dirPath(*parts))

fun NativePath.resolveFile(vararg parts: FilePathSegment): NativePath =
    resolve(FilePath(parts.toList(), isDir = false))

fun NativePath.resolveDir(vararg parts: FilePathSegment): NativePath =
    resolve(FilePath(parts.toList(), isDir = true))

fun NativePath.resolveFile(parts: Iterable<FilePathSegment>): NativePath =
    resolve(FilePath(parts.toList(), isDir = false))

fun NativePath.resolveDir(parts: Iterable<FilePathSegment>): NativePath =
    resolve(FilePath(parts.toList(), isDir = true))

/** Reads from the given path and returns the contents. */
expect fun NativePath.read(): String

/** Lists the names of the immediate children of the path, which must be a directory. */
expect fun NativePath.list(): List<NativePath>

/** Deletes a tree; obviously use with caution. */
expect fun NativePath.rmrf()

/** Create a directory, and intermediate directories, as necessary. */
expect fun NativePath.mkdir()

/** Signals used by the called block to alter walk behavior. */
enum class WalkSignal {
    Continue,
    SkipSubtree,
    Stop,
}

/** Walk a file tree; the function is called with the real file reference and a relative path. */
expect fun NativePath.walk(block: (NativePath, FilePath) -> WalkSignal)

/** A helper to extract file attributes. */
interface NativeStat {
    val isDir: Boolean
    val isFile: Boolean
}

/** Get file attributes for this native path. */
expect fun NativePath.stat(): NativeStat

expect val nativeConvention: NativeConvention

enum class NativeConvention {
    Posix,
    Windows,
}
