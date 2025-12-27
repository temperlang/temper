@file:JvmName("UpdateGeneratedDocs") // The Gradle exec task expects this name.

package lang.temper.docbuild

import lang.temper.common.Log
import lang.temper.common.asciiUpperCase
import lang.temper.common.console
import lang.temper.common.ignore
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonString
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.fs.NativePath
import lang.temper.fs.resolve
import lang.temper.fs.temperRoot
import lang.temper.log.dirPath
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.system.exitProcess

fun main(argv: Array<String>) = UserDocFilesAndDirectories.inContext {
    ignore(argv)

    val logLevel = logLevelFromGradle(System.getProperty("org.gradle.logging.level"))
    console.setLogLevel(logLevel)

    val snippetsJson = FormattingStructureSink.toJsonString(Snippets, indent = true)

    val problemTracker = ProblemTracker(console)

    // Blow away existing files
    // but need to preserve submodules like the temperlang-blog
    console.groupSoft("Clearing $userDocRoot") {
        Files.walk(userDocRoot).use { pathStream ->
            pathStream
                .filter { path: NativePath? ->
                    path != null && Files.isRegularFile(path) &&
                        // Not in any of the must-preserve subdirs
                        preserveUnderDocRoot.none {
                            isPathAncestor(it, path)
                        }
                }
                .forEach { path: NativePath ->
                    Files.delete(path)
                }
        }
    }

    // Regenerate snippets JSON
    console.groupSoft("Regenerating $snippetsJsonFile") {
        Files.writeString(
            snippetsJsonFile,
            // Make sure that file ends with a newline which minimizes diffs on `git merge`.
            "${snippetsJson.trimEnd()}\n",
        )
    }

    // Copy files from skeletal
    val skeletalDocRoot = temperRoot.resolve(dirPath("build-user-docs", "skeletal-docs"))
    console.groupSoft("Copying from $skeletalDocRoot to $userDocRoot") {
        recursiveCopyIf(
            src = skeletalDocRoot,
            dest = userDocRoot,
        ) { src, _ ->
            if (src.isDirectory()) {
                true
            } else {
                // Copy regular files if they're not git-ignored
                projectRoot.relativize(src).asFilePath in SourceFiles.files
            }
        }
    }

    // Emit warning file
    Files.writeString(
        userDocRoot.resolve("README.md"),
        """
            |# GENERATED CODE / DO NOT EDIT
            |
            |`docs/for-users` is generated from files under `build-user-docs/skeletal-docs`.
            |
            |See its README for more instructions.
            |
        """.trimMargin(),
    )

    // Log any errors with snippets
    run {
        val snippetProblems = Snippets.snippetList
            .flatMap { snippet -> snippet.problems.map { snippet to it } }
            .groupBy { it.first.source }
        for ((fileWithProblemSnippets, snippetsAndProblems) in snippetProblems) {
            console.groupSoft("Errors with snippets in $fileWithProblemSnippets", Log.Error) {
                snippetsAndProblems.groupBy { it.first }
                    .entries.sortedBy { it.key.sourceStartOffset }
                    .forEach { (snippet, andProblems) ->
                        console.groupSoft(
                            "Snippet ${snippet.id.shortCanonString(withExtension = true)}",
                            Log.Error,
                        ) {
                            andProblems.forEach { (_, problem) ->
                                problemTracker.error("Error: $problem")
                            }
                        }
                    }
            }
        }
    }

    // Generate snippets files.
    console.groupSoft("Generating snippets files under $snippetsRoot") {
        rmrf(snippetsRoot)
        Files.createDirectories(snippetsRoot)
        val commands = mutableListOf<DelayedCommand>()
        for (snippet in Snippets.snippetList) {
            val snippetPath = projectRoot.resolve(snippet.id.filePath)
            Files.createDirectories(snippetPath.parent)
            when (val content = snippet.content) {
                is ByteDocContent -> Files.write(snippetPath, content.bytes.copyOf())
                is TextDocContent -> Files.writeString(snippetPath, content.text)
                is ShellCommandDocContent -> {
                    commands.add(DelayedCommand(snippet, snippetPath, content))
                    // Create a stub file.
                    // If we somehow have a command that fails to produce an output, then it'll
                    // be easier if there is a file & snippet content referencing that.
                    Files.writeString(
                        snippetPath,
                        "< Snippet construction in progress: ${
                            content.command
                        } ${content.args} >",
                    )
                }
            }
        }
        // Run commands to generate dynamic content.
        console.groupSoft("Generating snippets with command content") {
            val commandsGrouped = commands.groupBy {
                it.content.command to it.content.groupTogether
            }
            for ((groupKey, commandGroup) in commandsGrouped) {
                var command = groupKey.first
                val groupTogether = groupKey.second
                // Resolve command against script directory if present
                val commandPath = Path.of(command)
                if (commandPath.nameCount == 1) {
                    val variants = mutableListOf(scriptsDir.resolve(commandPath))
                    // Look for .bat variant on Windows?
                    if (isWindows) {
                        variants.add(0, scriptsDir.resolve("$command.bat"))
                    }
                    for (variant in variants) {
                        if (variant.isExecutable()) {
                            command = "$variant"
                            break
                        }
                    }
                }
                val jobs: List<Pair<List<String>, Path?>> =
                    if (groupTogether) {
                        check(commandGroup.isNotEmpty())
                        // Create combined jobs where the output file is in the argument list
                        // See the documentation for ShellCommandDocContent for why these arguments
                        val argBundles = commandGroup.map { delayedCommand ->
                            listOf(
                                "--args",
                                JsonArray(delayedCommand.content.args.map { JsonString(it) })
                                    .toJsonString(indent = false),
                                "--out",
                                delayedCommand.outputPath.toString(),
                            )
                        }
                        if (isWindows) {
                            // Windows can't fit everything in a command line, so break things into subgroups.
                            @Suppress("MagicNumber")
                            argBundles.withIndex().groupBy { it.index / 10 }.map { group ->
                                // Strip index, flatten each group, and escape quotes, since ProcessBuilder doesn't.
                                val args = group.value.map { it.value }.flatten().map { it.replace("\"", "\\\"") }
                                args to null
                            }
                        } else {
                            listOf(argBundles.flatten() to null)
                        }
                    } else {
                        // Create many jobs
                        commandGroup.map {
                            it.content.args to it.outputPath
                        }
                    }
                for ((args, outputPath) in jobs) {
                    val pb = ProcessBuilder(listOf(command) + args)
                    pb.directory(projectRoot.toFile())
                    if (outputPath != null) {
                        pb.redirectOutput(outputPath.toFile())
                    }

                    console.log(
                        "Running ${pb.command().joinToString(" ")}${
                            if (outputPath != null) { " > $outputPath" } else { "" }
                        }",
                    )
                    val process: Process = pb.start()
                    process.outputStream.close()
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        val errorText = process.errorStream.use { stream ->
                            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                                reader.readText()
                            }
                        }
                        console.error("Command failed")
                        problemTracker.error(errorText)
                    }
                }
            }
        }
    }

    UserDocsContent.generate(problemTracker)

    val problemCount = problemTracker.problemCount
    exitProcess(
        if (problemCount == 0) {
            0
        } else {
            console.error("There were $problemCount problems regenerating docs")
            -1
        },
    )
}

private fun rmrf(toBeDeleted: Path) {
    if (toBeDeleted.isDirectory()) {
        val allContents = toBeDeleted.listDirectoryEntries()
        for (file in allContents) {
            rmrf(file)
        }
    }
    toBeDeleted.deleteIfExists()
}

private fun recursiveCopyIf(
    src: Path,
    dest: Path,
    predicate: (Path, Path) -> Boolean,
) {
    if (predicate(src, dest)) {
        if (src.isDirectory()) {
            if (!dest.isDirectory()) {
                Files.createDirectories(dest)
            }
            for (file in src.listDirectoryEntries()) {
                recursiveCopyIf(file, dest.resolve(file.name), predicate)
            }
        } else if (src.isRegularFile()) {
            Files.copy(src, dest)
        } else {
            TODO("Unexpected symlink in copy: $src $dest")
        }
    }
}

/** A command that we might need to eventually run. */
private data class DelayedCommand(
    val snippet: Snippet,
    val outputPath: Path,
    val content: ShellCommandDocContent,
)

internal fun logLevelFromGradle(gradleLogLevelName: String): Log.LevelFilter =
    when (gradleLogLevelName.asciiUpperCase()) {
        // See docs.gradle.org/current/userguide/logging.html#sec:choosing_a_log_level
        "QUIET" -> Log.Fatal
        "WARN" -> Log.Warn
        "LIFECYCLE" -> Log.Error
        "INFO" -> Log.Info
        "DEBUG" -> Log.All
        else -> Log.Warn
    }
