package lang.temper.fs

import java.io.OutputStream
import java.lang.AutoCloseable

internal class OutputStreamByteSink(
    private val outputStream: OutputStream,
) : ByteSink(), AutoCloseable {
    override fun write(bytes: ByteArray, off: Int, len: Int) {
        outputStream.write(bytes, off, len)
    }

    override fun close() {
        outputStream.close()
    }

    override fun flush() {
        outputStream.flush()
    }
}
