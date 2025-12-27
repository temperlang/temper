package lang.temper.kcodegen

import lang.temper.fs.temperRoot
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.name

internal fun getSubprojectRoot(subProject: String): File {
    return temperRoot.resolve(subProject).toFile()
}

private fun getSourceRoot(subProject: String): File {
    return temperRoot.resolve(subProject).resolve("src").toFile()
}

internal actual fun findExistingGeneratedSourcesBestEffort(
    codeGenerator: CodeGenerator,
): List<CodeGenerator.GeneratedSource>? {
    val fileExtensions = codeGenerator.fileExtensions
    val languageTags = codeGenerator.languageTags
    val subProject = codeGenerator.subProject
    val sourcePrefix = codeGenerator.sourcePrefix

    val generatedSources = mutableListOf<CodeGenerator.GeneratedSource>()

    val sourceRootPath = getSourceRoot(subProject).toPath()

    Files.walkFileTree(
        sourceRootPath,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes): FileVisitResult {
                require(file != null)
                run {
                    val name = file.fileName.toString()
                    val ext = fileExtensions.firstOrNull { name.endsWith(it) }
                        ?: return@run
                    val content = Files.readString(file, Charsets.UTF_8)
                    if (sourcePrefix !in content.firstLine) {
                        return@run
                    }
                    val baseName = name.substring(0, name.length - ext.length)
                    val directoryNameParts = mutableListOf<String>()

                    var p: Path? = file
                    while (p != null) {
                        p = p.parent
                        if (p == sourceRootPath) { break }
                        directoryNameParts.add(p.fileName.toString())
                    }

                    val group = directoryNameParts.removeLastOrNull() // Like commonMain
                    val secondToLast = directoryNameParts.lastOrNull() // Like kotlin
                    // Under src/commonMain/<languageTag>
                    directoryNameParts.reverse()
                    if (group == null || secondToLast !in languageTags) {
                        return@run
                    }

                    generatedSources.add(
                        CodeGenerator.GeneratedSource.create(
                            directoryNameParts = directoryNameParts,
                            baseName = baseName,
                            ext = ext,
                            group = group,
                            content = content,
                            contentHasErrors = false,
                        ),
                    )
                }
                return FileVisitResult.CONTINUE
            }
        },
    )

    return generatedSources.toList()
}

internal actual fun updateExistingGeneratedSourcesBestEffort(
    subProject: String,
    generatedSources: List<CodeGenerator.GeneratedSource>,
) {
    val sourceRootPath = getSourceRoot(subProject).toPath()

    for (generatedSource in generatedSources) {
        var p = sourceRootPath.resolve(generatedSource.group)
        for (packageNamePart in generatedSource.directoryNameParts) {
            p = p.resolve(packageNamePart)
        }
        p = p.resolve("${generatedSource.baseName}${generatedSource.ext}")

        val content = generatedSource.content

        if (content == null) {
            Runtime.getRuntime().exec(
                arrayOf("git", "rm", "-f", "--", p.toAbsolutePath().toString()),
            )
            Files.deleteIfExists(p)
        } else {
            Files.createDirectories(p.parent)
            Files.writeString(p, content, Charsets.UTF_8)
        }
    }
}

internal actual fun globScanBestEffort(
    subProject: String,
    startPath: List<String>,
    glob: String,
): Iterable<Pair<List<String>, () -> String>> {
    val sourceRootPath = getSourceRoot(subProject).toPath()
    val pathMatcher = sourceRootPath.fileSystem.getPathMatcher("glob:$glob")

    val out = mutableListOf<Pair<List<String>, () -> String>>()
    val finder = object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path?, attrs: BasicFileAttributes): FileVisitResult {
            if (file != null && pathMatcher.matches(file)) {
                out.add(
                    sourceRootPath.relativize(file).map { it.name } to
                        { Files.readString(file, Charsets.UTF_8) },
                )
            }
            return FileVisitResult.CONTINUE
        }
    }
    Files.walkFileTree(sourceRootPath, finder)

    return out.toList()
}

internal actual fun subProjectsMatchingBestEffort(pattern: Regex): Iterable<String> {
    return buildList {
        val superProjectRoot = getSourceRoot("kcodegen").parentFile.parentFile
        addAll(subProjectsMatchingBestEffort(superProjectRoot, pattern))
        val external = superProjectRoot.resolve("external")
        if (external.exists()) {
            for (externalSub in external.listFiles()!!) {
                val prefix = "${external.name}/${externalSub.name}/"
                addAll(subProjectsMatchingBestEffort(externalSub, pattern, prefix = prefix))
            }
        }
    }
}

internal fun subProjectsMatchingBestEffort(dir: File, pattern: Regex, prefix: String = ""): Iterable<String> {
    return dir.listFiles { file ->
        val name = file.name
        name != "." && name != ".." && pattern.matches(name) && file.isDirectory
    }?.map {
        "$prefix${it.name}"
    } ?: emptyList()
}
