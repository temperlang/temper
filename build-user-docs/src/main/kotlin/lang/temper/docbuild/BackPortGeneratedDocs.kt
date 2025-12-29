@file:JvmName("BackPortGeneratedDocs") // The Gradle exec task expects this name.

package lang.temper.docbuild

import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.common.diff.Diff
import lang.temper.common.ignore
import lang.temper.common.putMultiList
import lang.temper.fs.resolve
import lang.temper.log.FilePath
import lang.temper.log.plus
import java.nio.file.Files
import kotlin.system.exitProcess

internal const val DRY_RUN_SYSTEM_PROPERTY = "backPort.dryRun"

fun main(argv: Array<String>) = UserDocFilesAndDirectories.inContext {
    ignore(argv)

    val problemTracker = ProblemTracker(console)

    val dryRun = System.getProperty(
        DRY_RUN_SYSTEM_PROPERTY,
        "true",
    ).toBooleanStrictOrNull()
        ?: run {
            problemTracker.error("$DRY_RUN_SYSTEM_PROPERTY property must be `true` or `false`")
            true
        }

    val logLevel = logLevelFromGradle(System.getProperty("org.gradle.logging.level"))
    console.setLogLevel(logLevel)

    val changes = UserDocsContent.reverseEngineer(problemTracker)

    if (problemTracker.problemCount == 0) {
        val newFileContent = deriveNewFileContent(changes, problemTracker)
        if (!dryRun && problemTracker.problemCount == 0) {
            // Commit changes to file system.
            for ((filePath, beforeAndAfter) in newFileContent) {
                val outPath = projectRoot.resolve(filePath)
                console.log("Writing changes to $outPath")
                Files.writeString(outPath, beforeAndAfter.second.text)
            }
        } else {
            // Show diffs
            console.setLogLevel(Log.Info)

            if (dryRun) {
                console.log("Showing diffs instead of committing changes because this is a dry run.")
                console.group("To commit changes try") {
                    console.log("gradle build-user-docs:backportAndCommit")
                }
            } else { // Diffing because of problems
                console.warn("There were errors.  Showing diffs but not over-writing files.")
            }

            for ((filePath, beforeAndAfter) in newFileContent) {
                val outPath = projectRoot.resolve(filePath)
                val patch = Diff.differencesBetween(
                    crLfOrLfPattern.split(beforeAndAfter.first.text),
                    crLfOrLfPattern.split(beforeAndAfter.second.text),
                )
                console.group("$outPath") {
                    console.log(Diff.formatPatch(patch, context = 3))
                }
            }
        }
    }

    val problemCount = problemTracker.problemCount
    exitProcess(
        if (problemCount == 0) {
            0
        } else {
            console.error(
                when (problemCount) {
                    1 -> "There was 1 problem back-porting docs"
                    else -> "There were $problemCount problems back-porting docs"
                },
            )
            -1
        },
    )
}

private data class FileBeforeAndAfter(
    val before: String,
) {
    val after = StringBuilder(before)

    operator fun component2() = after
}

private fun deriveNewFileContent(
    changes: UserDocChanges,
    problemTracker: ProblemTracker,
): Map<FilePath, Pair<TextDocContent, TextDocContent>> {
    val fileContent = mutableMapOf<FilePath, FileBeforeAndAfter>()
    fun getFileBeforeAndAfter(filePath: FilePath): FileBeforeAndAfter =
        fileContent.getOrPut(filePath) {
            FileBeforeAndAfter(
                Files.readString(UserDocFilesAndDirectories.projectRoot.resolve(filePath)),
            )
        }

    // Do changes to template files first, so that, in the unlikely event that there are snippets
    // in them, the snippet updates below will not get changed.
    for (fileChange in changes.fileChanges) {
        val (_, buffer) = getFileBeforeAndAfter(
            UserDocFilesAndDirectories.userDocRootPathPrefix + fileChange.relFilePath,
        )
        buffer.clear()
        buffer.append(fileChange.newContent.fileContent)
    }

    // Group the snippet changes by file they need to back port them into.
    val snippetChangesBySource = mutableMapOf<FilePath, MutableList<UserDocChanges.SnippetChange>>()
    changes.snippetChanges.forEach { snippetChangesBySource.putMultiList(it.snippet.source, it) }
    // Sort snippets so that we can apply changes right-to-left.
    // Doing that minimizes the differences that each call out to a SnippetExtractor
    // needs to work around.
    for ((_, changeList) in snippetChangesBySource) {
        changeList.sortBy { -it.snippet.sourceStartOffset }
    }

    // Now that we've got lists of snippet changes, in the order we want to process them,
    // go ahead and pass control off to the extractors to back-port the changes in.
    for ((sourceFilePath, changeList) in snippetChangesBySource) {
        problemTracker.console.groupSoft("Back-porting changes for $sourceFilePath") {
            var fileBeforeAndAfter: FileBeforeAndAfter? = null
            for (change in changeList) {
                when (val d = change.snippet.derivation) {
                    is ExtractedAndReplacedBack -> Unit // Already incorporated.
                    is ExtractedBy -> {
                        if (fileBeforeAndAfter == null) {
                            fileBeforeAndAfter = getFileBeforeAndAfter(sourceFilePath)
                        }
                        val problemCountBefore = problemTracker.problemCount
                        val changed = d.extractor.backPortSnippetChange(
                            snippet = change.snippet,
                            newContent = change.newContent,
                            into = fileBeforeAndAfter.after,
                            problemTracker = problemTracker,
                        )
                        if (problemCountBefore != problemTracker.problemCount && !changed) {
                            val contentBefore = when (val content = change.snippet.content) {
                                is TextDocContent -> content.text
                                is ByteDocContent -> "<Binary>"
                                is ShellCommandDocContent -> "<Computed by shell command>"
                            }
                            problemTracker.errorGroup(
                                "Discarded changes to snippet ${
                                    change.snippet.id.shortCanonString(false)
                                } from ${change.from}",
                            ) { errConsole ->
                                errConsole.error(
                                    Diff.formatPatch(
                                        Diff.differencesBetween(
                                            contentBefore.split(crLfOrLfPattern),
                                            change.newContent.fileContent.split(crLfOrLfPattern),
                                        ),
                                        context = 2,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    return fileContent.mapValues { (_, f: FileBeforeAndAfter) ->
        TextDocContent(f.before) to TextDocContent("${f.after}")
    }
}
