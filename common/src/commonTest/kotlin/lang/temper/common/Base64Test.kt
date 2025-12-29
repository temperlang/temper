package lang.temper.common

import kotlin.test.Test

class Base64Test {
    @Test
    fun base64EncodeTestVectors() {
        // https://tools.ietf.org/html/rfc4648 section 10 test vectors
        val testVectors = listOf(
            bytesOf("") to "",
            bytesOf("f") to "Zg==",
            bytesOf("fo") to "Zm8=",
            bytesOf("foo") to "Zm9v",
            bytesOf("foob") to "Zm9vYg==",
            bytesOf("fooba") to "Zm9vYmE=",
            bytesOf("foobar") to "Zm9vYmFy",
        )

        for ((input, want) in testVectors) {
            assertStringsEqual(want, input.base64Encode(), "${input.asList()}")

            val longerBytes = ByteArray(input.size + 2)
            input.copyInto(longerBytes, 1, 0, input.size)
            longerBytes[0] = 'x'.code.toByte()
            longerBytes[longerBytes.size - 1] = 'y'.code.toByte()
            assertStringsEqual(
                want,
                longerBytes.base64Encode(1, input.size),
                "lengthened ${input.asList()}",
            )
        }
    }
}

private fun bytesOf(s: String) = s.map {
    require(it < '\u0080')
    it.code.toByte()
}.toByteArray()
