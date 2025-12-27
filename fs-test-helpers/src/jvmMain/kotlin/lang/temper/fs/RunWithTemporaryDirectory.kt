package lang.temper.fs

import lang.temper.common.console
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

actual fun <T> runWithTemporaryOutputRoot(
    testName: String,
    testAction: (OutputRoot, String) -> T,
): T {
    return runWithTemporaryDirectory(testName) { path ->
        val ioExceptions = mutableListOf<IOException>()
        val outputRoot = OutputRoot(
            RealWritableFileSystem(path) {
                synchronized(ioExceptions) {
                    ioExceptions.add(it)
                }
            },
        )
        val result = try {
            testAction(outputRoot, "$path")
        } finally {
            outputRoot.fs.close()
        }
        synchronized(ioExceptions) {
            ioExceptions.firstOrNull()
        }?.let {
            throw it
        }
        result
    }
}

/**
 * Testing file operations is a PITA so this lets us create a temporary directory
 * muck around in it.
 *
 * If the test passes, then it cleans up the directory.
 * If it doesn't, it prints a message to STDERR so the user can go and look at the files.
 */
fun <T> runWithTemporaryDirectory(
    testName: String,
    testAction: (Path) -> T,
): T {
    val tmpDir = Files.createTempDirectory("temper$testName")
    var passed = false
    val result: T
    try {
        result = testAction(tmpDir)
        passed = true
    } finally {
        if (passed) {
            removeDirRecursive(tmpDir)
        } else {
            console.error(
                """
                File system test failed.  To see the state of the files under test do

                    cd $tmpDir
                """.trimIndent(),
            )
        }
    }
    return result
}

fun <T> runWithTemporaryDirCopyOf(testName: String, source: Path, testAction: (Path) -> T): T {
    check(source.isDirectory())
    return runWithTemporaryDirectory(testName) { tempDir ->
        copyRecursive(source, tempDir)
        testAction(tempDir)
    }
}
