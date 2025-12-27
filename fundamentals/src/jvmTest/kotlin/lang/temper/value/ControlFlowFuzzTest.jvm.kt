package lang.temper.value

import java.nio.file.Files
import java.util.concurrent.TimeUnit

actual fun compileAndRunJavaAndGetStdout(javaSourceText: String): Pair<String, () -> Unit> {
    val tempDir = Files.createTempDirectory("javaTestRunRoot")
    val javaFile = tempDir.resolve("C.java")
    Files.createDirectories(tempDir)
    Files.writeString(
        javaFile,
        buildString {
            append("public class C {\n")
            append("  public static void main(String[] argv) {\n")
            append(javaSourceText)
            append('\n')
            append("  }\n")
            append("}\n")
        },
    )

    val javaOutFile = tempDir.resolve("java.out.txt")

    // Run javac
    run {
        val javacPb = ProcessBuilder()
        javacPb.directory(tempDir.toFile())
        javacPb.command("javac", "C.java")
        val javacProcess = javacPb.start()
        val javacExited = javacProcess.waitFor(20L, TimeUnit.SECONDS)
        check(javacExited) {
            javacProcess.destroy()
            "$tempDir: javac did not exit"
        }
        val javacExitCode = javacProcess.waitFor()
        check(javacExitCode == 0) {
            "$tempDir: javac exited with $javacExitCode"
        }
    }

    run {
        val javaPb = ProcessBuilder()
        javaPb.directory(tempDir.toFile())
        javaPb.command("java", "-cp", ".", "C")
        javaPb.redirectOutput(ProcessBuilder.Redirect.to(javaOutFile.toFile()))
        val javaProcess = javaPb.start()
        val javaExited = javaProcess.waitFor(20L, TimeUnit.SECONDS)
        check(javaExited) {
            javaProcess.destroy()
            "$tempDir: java did not exit"
        }
        val javaExitCode = javaProcess.waitFor()
        check(javaExitCode == 0) {
            "$tempDir: java exited with $javaExitCode"
        }
    }

    return Files.readString(javaOutFile) to {
        tempDir.toFile().deleteOnExit()
    }
}
