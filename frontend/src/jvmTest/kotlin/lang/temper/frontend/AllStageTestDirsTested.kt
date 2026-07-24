package lang.temper.frontend

import lang.temper.fs.temperRoot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class AllStageTestDirsTested {
    @Test
    fun stageTestDirsUsed() {
        // Merge conflicts could cause dropping of test cases.
        // Scan the file system for the stage dirs and then check the Kotlin
        // sources mention them.
        val inFileSystem = buildSet {
            val root = stageTestDirFileRoot.toPath()
            // Look for directories that have a `work` and/or `expect` sub-directories.
            fun walk(p: Path) {
                val ls = Files.list(p).toList()
                val hasWorkOrExpectSubDir = ls.any {
                    when ("${it.fileName}") {
                        "work", "expect" -> true
                        else -> false
                    }
                }
                if (hasWorkOrExpectSubDir) {
                    add("${p.relativeTo(root)}")
                } else {
                    for (c in ls) {
                        if (Files.isDirectory(c)) {
                            walk(c)
                        }
                    }
                }
            }
            walk(root)
        }
        val used = buildSet {
            /** Scan for uses of [StageTestDir]'s constructor */
            val stageTestDirCtor = Regex(
                """StageTestDir\((?:\w+\s*=)?\s*"((?:[^"\\]|\\.)*)"\s*(?:,\s*)?\)""",
            )
            fun walk(p: Path) {
                when {
                    Files.isRegularFile(p) && p.fileName.extension == "kt" -> {
                        val content = Files.readString(p)
                        for (m in stageTestDirCtor.findAll(content)) {
                            add(m.groupValues[1])
                        }
                    }
                    Files.isDirectory(p) -> {
                        for (c in Files.list(p)) {
                            walk(c)
                        }
                    }
                }
            }
            walk(temperRoot.resolve("frontend/src/commonTest/kotlin/lang/temper"))
        }

        val unused = inFileSystem.toMutableSet()
        unused.removeAll(used)

        assertEquals(
            inFileSystem.sorted().joinToString("\n"),
            used.sorted().joinToString("\n"),
            message = "unused=$unused"
        )
    }
}
