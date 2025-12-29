// Can't rename this as it'll conflict with kotlin common
@file:Suppress("MatchingDeclarationName")

package lang.temper.fs

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

actual class KCharset(val realCharset: Charset)

actual val KCharset.name: String get() = realCharset.name()

actual fun KCharset.encodeToBytes(str: String): ByteArray {
    val buffer = realCharset.encode(CharBuffer.wrap(str))
    val remaining = buffer.remaining()
    if (buffer.hasArray() && buffer.arrayOffset() == 0) {
        val array = buffer.array()
        if (remaining == array.size) {
            return array
        }
    }
    return ByteArray(remaining).also { buffer.get(it) }
}
actual fun KCharset.decodeToString(bytes: ByteArray): String {
    val buffer: CharBuffer = realCharset.decode(ByteBuffer.wrap(bytes))
    val remaining = buffer.remaining()
    if (buffer.hasArray() && buffer.arrayOffset() == 0) {
        val array = buffer.array()
        if (remaining == array.size) {
            return String(array)
        }
    }
    return String(
        CharArray(remaining).also {
            buffer.get(it)
        },
    )
}

actual val KCharsets.utf8 get() = KCharset(Charsets.UTF_8)
