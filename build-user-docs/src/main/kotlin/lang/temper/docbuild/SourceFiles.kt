package lang.temper.docbuild

import lang.temper.common.subListToEnd
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystems
import java.util.stream.Stream

/**
 * The set of Temper source files, not including those `.gitignore`d.
 */
internal object SourceFiles {
    val files: Set<FilePath> = UserDocFilesAndDirectories.run {
        val bytesCollected = ByteArrayOutputStream()
        for (
        gitFileListCommand in listOf(
            // Read the tracked source files
            listOf("git", "ls-files"),
            // Read the untracked & unignored files
            listOf("git", "ls-files", "-o", "-X", ".gitignore"),
        )
        ) {
            val process = ProcessBuilder(gitFileListCommand)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .directory(projectRoot.toAbsolutePath().toFile())
                .start()
            process.outputStream.close()
            BufferedInputStream(process.inputStream).use {
                val bytes = ByteArray(READ_BUFFER_SIZE)
                while (true) {
                    val n = it.read(bytes)
                    if (n <= 0) { break }
                    bytesCollected.write(bytes, 0, n)
                }
            }
            val exitResult = process.waitFor()
            check(exitResult == 0)
        }

        val fileListText = bytesCollected.toString(Charsets.UTF_8)
        val lines = fileListText.split(crLfOrLfPattern)
        lines.mapNotNull { line ->
            if (line.isEmpty()) {
                null
            } else {
                // Git uses consistent '/' paths, even on Windows.
                val segments = line.split("/").mapNotNull {
                    if (it.isEmpty()) { null } else { FilePathSegment(it) }
                }
                FilePath(segments, isDir = line.endsWith("/"))
            }
        }.sorted().toSet()
    }

    fun withExtension(extensionWithDot: String): Stream<FilePath> =
        files.stream().filter {
            it.segments.lastOrNull()?.extension == extensionWithDot
        }

    fun matching(glob: String): Stream<FilePath> {
        val fs = FileSystems.getDefault()
        val matcher = fs.getPathMatcher("glob:$glob")
        return files.stream().filter { file ->
            if (file.segments.isNotEmpty()) {
                @Suppress("SpreadOperator") // Need to create path from strings.
                val path = fs.getPath(
                    file.segments[0].fullName,
                    *file.segments.subListToEnd(1).map { it.fullName }.toTypedArray(),
                )
                matcher.matches(path)
            } else {
                false
            }
        }
    }
}

internal val crLfOrLfPattern = Regex("\n|\r\n?")
private const val READ_BUFFER_SIZE = 4096 // bytes
