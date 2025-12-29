package lang.temper.fs

import lang.temper.common.Flushable
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.Charset

// Suppress because implicit constructors are not actual.
@Suppress("UnnecessaryAbstractClass", "EmptyDefaultConstructor")
actual abstract class ByteSink : OutputStream(), AutoCloseable, Flushable {
    actual override fun write(p0: Int) {
        write(byteArrayOf(p0.toByte()), 0, 1)
    }

    abstract override fun close()

    abstract override fun flush()
}

actual class CharSink(
    private val writer: Writer,
) : Appendable, AutoCloseable, Flushable {
    override fun append(c: Char): Appendable {
        writer.append(c)
        return this
    }

    override fun append(charSequence: CharSequence, start: Int, end: Int): Appendable {
        writer.append(charSequence, start, end)
        return this
    }

    override fun append(charSequence: CharSequence): Appendable {
        writer.append(charSequence)
        return this
    }

    override fun close() {
        writer.close()
    }

    override fun flush() {
        writer.flush()
    }
}

actual fun ByteSink.asAppendable(charset: Charset): CharSink =
    CharSink(OutputStreamWriter(this, charset))
