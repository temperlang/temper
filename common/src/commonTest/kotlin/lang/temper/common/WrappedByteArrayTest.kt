package lang.temper.common

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class WrappedByteArrayTest {
    @Test
    fun builder() {
        val wrappedBytes = WrappedByteArray.build(initialBufferSize = 1) {
            append(byteArrayOf(0, 1, 2, 3, 4))
            append(byteArrayOf(3, 4, 5, 6, 7, 8), startIndex = 2) // Skips 3, 4
            append(byteArrayOf(9, 10, 11, 12), endIndex = 3) // Skips 12
        }
        assertEquals(
            "000102030405060708090A0B",
            wrappedBytes.hexEncode(),
        )
    }

    @Test
    fun decodeWithCharset() {
        val wrappedBytes = WrappedByteArray(ByteArray(5, { (it + 48).toByte() }))
        assertEquals(
            "01234",
            wrappedBytes.decodeToStringWithCharset(StandardCharsets.US_ASCII),
        )
    }
}
