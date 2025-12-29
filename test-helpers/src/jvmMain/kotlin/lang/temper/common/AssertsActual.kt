package lang.temper.common

import org.opentest4j.AssertionFailedError
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.fail

actual fun stringsNotEqual(messageStr: String, wantStr: String, gotStr: String): Nothing {
    // Even though the outer portion asserted it already reassert it to trigger the intellij integration and then throw
    // to match the signature
    assertEquals(wantStr, gotStr, messageStr)
    throw AssertionFailedError(messageStr, wantStr, gotStr)
}

actual fun failWithCause(messageStr: String, cause: Throwable?): Nothing {
    fail(message = messageStr, cause = cause)
    @Suppress("UNREACHABLE_CODE")
    throw AssertionFailedError(messageStr).initCause(cause)
}

internal actual fun tryRunDiff(left: String, right: String) {
    val fileA = File.createTempFile("aaa", ".txt")
    fileA.deleteOnExit()
    val fileB = File.createTempFile("bbb", ".txt")
    fileB.deleteOnExit()

    Files.writeString(fileA.toPath(), left, Charsets.UTF_8)
    Files.writeString(fileB.toPath(), right, Charsets.UTF_8)

    val command = when {
        System.getProperty("os.name").startsWith("Windows") -> arrayListOf("fc", "/a")
        else -> arrayListOf("diff", "-u")
    }
    val diffCmdBuilder = ProcessBuilder(command + arrayOf(fileA.absolutePath, fileB.absolutePath))
    diffCmdBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
    diffCmdBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    val process = diffCmdBuilder.start()
    process.outputStream.close()
    process.waitFor()
}
