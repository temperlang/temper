package lang.temper.tooling.buildrun

import lang.temper.fs.removeDirRecursive
import java.nio.file.Files
import java.nio.file.Path

fun <T> withTempDir(jobName: String, action: (Path) -> T): T {
    val tempDir = Files.createTempDirectory("temper$jobName")
    try {
        return action(tempDir)
    } finally {
        removeDirRecursive(tempDir)
    }
}
