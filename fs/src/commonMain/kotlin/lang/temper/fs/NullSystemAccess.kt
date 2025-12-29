package lang.temper.fs

import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.WrappedByteArray
import lang.temper.common.currents.CancelGroup
import lang.temper.common.currents.RFuture
import lang.temper.common.currents.newComputedResultFuture
import lang.temper.common.currents.preComputedResultFuture
import lang.temper.log.FilePath
import java.io.IOException
import java.nio.file.NoSuchFileException

/**
 * A file creator that does not actually create any files.
 * This can be useful during testing.
 */
class NullSystemAccess(
    override val pathToFileCreatorRoot: FilePath,
    override val cancelGroup: CancelGroup,
) : AsyncSystemReadAccess {
    override fun buildFile(filePath: FilePath, mimeType: MimeType?): FileBuilder =
        NullFileBuilder(filePath, mimeType)

    private inner class NullFileBuilder(
        val filePath: FilePath,
        val mimeType: MimeType?,
    ) : FileBuilder {
        override fun executable(): FileBuilder = this

        override fun write(doWrite: (ByteSink) -> Unit): RFuture<Unit, IOException> =
            cancelGroup.newComputedResultFuture(
                "Create $filePath:${mimeType ?: "?"} but not really",
            ) {
                try {
                    doWrite(DevNullByteSink)
                    RSuccess(Unit)
                } catch (ioEx: IOException) {
                    RFailure(ioEx)
                }
            }
    }

    override fun buildChildProcess(
        command: String,
        build: ChildProcessBuilder.() -> Unit,
    ): PendingChildProcess? = null

    override fun fileReader(filePath: FilePath): FileReader = object : FileReader {
        override fun binaryContent(): RFuture<WrappedByteArray, IOException> =
            preComputedResultFuture(
                RResult.of(IOException::class) {
                    throw NoSuchFileException("/dev/null")
                },
            )
    }
}

object DevNullByteSink : ByteSink() {
    override fun write(bytes: ByteArray, off: Int, len: Int) {
        // Do nothing
    }

    override fun close() {
        // Do nothing
    }

    override fun flush() {
        // Do nothing
    }
}
