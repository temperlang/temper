package lang.temper.docbuild

import lang.temper.fs.resolve
import lang.temper.fs.temperRoot
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath
import lang.temper.log.filePath
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Relates paths under `build-user-docs/skeletal-docs` to markdown files to markdown content so
 * that we can process them to produce markdown files under `docs/for-users`.
 */
internal object SkeletalDocsFiles {
    val markdownContent: Map<FilePath, MarkdownContent>

    val root = dirPath("build-user-docs", "skeletal-docs")

    init {
        // The README file is not a user documentation file.  It describes the conventions used
        // in them.
        val toIgnore = setOf(filePath("README.md"))

        val markdownContent = mutableMapOf<FilePath, MarkdownContent>()

        val root = temperRoot.resolve(root)
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    if (file?.isRegularFile() == true && file.extension == "md") {
                        val relPath = root.relativize(file)
                        val relFilePath = relPath.asFilePath
                        if (relFilePath !in toIgnore) {
                            markdownContent[relFilePath] = MarkdownContent(Files.readString(file))
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        this.markdownContent = markdownContent.toMap()
    }
}

internal val (Path).asFilePath: FilePath
    get() = FilePath(
        segments = (0 until nameCount).map { i ->
            FilePathSegment(getName(i).name)
        },
        isDir = isDirectory(),
    )
