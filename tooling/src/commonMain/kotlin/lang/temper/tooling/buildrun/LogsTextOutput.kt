package lang.temper.tooling.buildrun

import lang.temper.common.OpenOrClosed
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.RThrowable
import lang.temper.common.TextOutput
import lang.temper.fs.RealWritableFileSystem
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import java.io.IOException
import java.io.Writer
import java.nio.file.Files

internal val logsDirName = FilePathSegment("-logs")

internal class LogsTextOutput(
    logsDir: FilePath,
    fs: RealWritableFileSystem,
    private val onIoException: (Throwable) -> Unit,
) : TextOutput(), AutoCloseable {
    override val isTtyLike: Boolean get() = false
    private var openOrClosed = OpenOrClosed.Open

    private val currentLogsWriter: Writer
    init {
        when (val logsDirResult = fs.ensureCleanDir(logsDir).await()) {
            is RSuccess -> Unit
            is RFailure -> onIoException(logsDirResult.failure)
            is RThrowable -> onIoException(logsDirResult.throwable)
        }
        currentLogsWriter = Files.newBufferedWriter(
            // TODO: implement log file rotation
            fs.javaRoot.resolve(logsDirName.fullName).resolve("0000.log"),
            Charsets.UTF_8,
        )
    }

    override fun emitLineChunk(text: CharSequence) {
        var shouldLogSafely = false
        try {
            synchronized(this) {
                shouldLogSafely = openOrClosed == OpenOrClosed.Open
                currentLogsWriter.write(text.toString())
            }
        } catch (e: IOException) {
            if (shouldLogSafely) {
                onIoException(e)
            }
        }
    }

    override fun flush() {
        try {
            currentLogsWriter.flush()
        } catch (e: IOException) {
            onIoException(e)
        }
    }

    override fun close() {
        try {
            currentLogsWriter.close()
        } catch (e: IOException) {
            onIoException(e)
        } finally {
            synchronized(this) {
                openOrClosed = OpenOrClosed.Closed
            }
        }
    }
}
