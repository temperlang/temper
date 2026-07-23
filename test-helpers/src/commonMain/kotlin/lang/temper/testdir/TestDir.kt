package lang.temper.testdir

import lang.temper.common.Either
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath

typealias Url = URI

/** Builder for a list of files to regenerate */
typealias RegeneratedFilesList = MutableList<Pair<Url, Either<String, ByteArray>>>

fun regenerateFiles(testDirRoot: Url, files: List<Pair<Url, Either<String, ByteArray>>>) {
    for ((relUrl, content) in files) {
        val path = testDirRoot.resolve(relUrl).toPath()
        Files.createDirectories(path.parent)
        val bytes = when (content) {
            is Either.Left<String> -> content.item.encodeToByteArray()
            is Either.Right<ByteArray> -> content.item
        }
        Files.newOutputStream(path).use { outputStream ->
            outputStream.write(bytes)
        }
    }
}

val defaultSkipFilePattern = Regex("""~$|README.*[.]md$|^[.]""", RegexOption.DOT_MATCHES_ALL)

fun readTestDir(testDirRoot: Url, skipFilePattern: Regex? = defaultSkipFilePattern): TestDir {
    // We assume that, when running tests, all the resources are in the
    // same source root which is on the file system.
    // This is not always the case Urls derived via Class.getResource on the JVM,
    // but it is true when running tests via Maven or Gradle.
    check(testDirRoot.scheme == "file") { "$testDirRoot" }

    val rootPath = testDirRoot.toPath()
    return TestDir(
        testDirRoot,
        buildMap {
            fun recursivelyReadRegularFilesIntoMap(path: Path) {
                when {
                    path.isRegularFile() && path.isReadable() -> {
                        val name = "${path.fileName}"
                        if (skipFilePattern?.find(name) == null) {
                            val url = path.relativeTo(rootPath).toUri()
                            // Let race conditions with isReadable check just bubble up as IOExceptions
                            this[url] = Files.readString(path, Charsets.UTF_8)
                        }
                    }
                    path.isDirectory() -> {
                        for (child in Files.list(path)) {
                            recursivelyReadRegularFilesIntoMap(child)
                        }
                    }
                }
            }
            recursivelyReadRegularFilesIntoMap(rootPath)
        },
    )
}

data class TestDir(
    val testDirRoot: Url,
    val files: Map<Url, String>,
) {
    fun isEmpty() = files.isEmpty()
    fun isNotEmpty() = files.isNotEmpty()
}
