package lang.temper.log

import lang.temper.common.subListToEnd
import lang.temper.log.FilePath.Companion.join
import lang.temper.log.FilePath.Companion.joinAbsolute

/** Convert a file path to Posix form. */
fun FilePath.asUnixPath(): String {
    return join()
}

/** Convert a file path to Posix form. */
fun FilePath.asUnixPathAbsolute(): String {
    return joinAbsolute()
}

/** A resource path is simply a Posix path. */
fun resourcePath(path: String): FilePath =
    FilePath(
        path.split(UNIX_FILE_SEGMENT_SEPARATOR)
            .filter { it.isNotEmpty() }
            .map { FilePathSegment(it) },
        isDir = false,
    )

/** Remove the first segment of a file path. */
fun FilePath.chompFirst(): FilePath {
    require(segments.isNotEmpty()) { "This path can't be empty" }
    return FilePath(segments.subListToEnd(1), isDir = this.isDir)
}

/** Get the first segment as its own path. */
fun FilePath.first(): FilePath {
    require(segments.isNotEmpty()) { "This path can't be empty" }
    return if (segments.size == 1) {
        this
    } else {
        FilePath(segments.subList(0, 1), isDir = true)
    }
}

/** Like [FilePath.lastOrNull()] but asserts it's not empty. */
fun FilePath.last(): FilePathSegment = segments.last()

/** Split a path into the directory and name. */
fun FilePath.split(): Pair<FilePath, FilePathSegment> {
    return dirName() to segments.last()
}

/** Split a path into the directory and name, asserting that it is a file. */
fun FilePath.splitFile(): Pair<FilePath, FilePathSegment> {
    require(isFile) { "Expecting $this to be file, but is a directory" }
    return split()
}

/**
 * Create a new filepath by applying updating functions, e.g. to scrub filenames.
 * The dirUpdate function is applied to all directory segments, if the FilePath is a directory that means all of them.
 * The fileUpdate function is applied to the last segment if the file path is a file.
 */
fun FilePath.applyToSegments(
    dir: (FilePathSegment) -> FilePathSegment = { it },
    file: (FilePathSegment) -> FilePathSegment = { it },
): FilePath =
    FilePath(
        segments.mapIndexed { idx, seg ->
            if (isDir || idx != segments.lastIndex) {
                dir(seg)
            } else {
                file(seg)
            }
        },
        isDir = isDir,
    )

/** Given a file path to a directory, resolve a specific file. */
fun FilePath.resolveFile(name: String) = resolveFile(FilePathSegment(name))

/** Given a file path to a directory, resolve a specific file. */
fun FilePath.resolveFile(name: FilePathSegment) = resolve(name, isDir = false)

/** Given a file path to a directory, resolve a specific file. */
fun FilePath.resolveDir(name: String) = resolveDir(FilePathSegment(name))

/** Given a file path to a directory, resolve a specific file. */
fun FilePath.resolveDir(name: FilePathSegment) = resolve(name, isDir = true)

fun FilePathSegment.asFilePath() = FilePath(listOf(this), isDir = false)
fun FilePathSegment.asDirPath() = FilePath(listOf(this), isDir = true)
fun Iterable<FilePathSegment>.asFilePath() = FilePath(toList(), isDir = false)
fun Iterable<FilePathSegment>.asDirPath() = FilePath(toList(), isDir = true)
