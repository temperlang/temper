package lang.temper.common

private val encodeTable = charArrayOf(
    'A', 'B', 'C', 'D', 'E', 'F',
    'G', 'H', 'I', 'J', 'K', 'L',
    'M', 'N', 'O', 'P', 'Q', 'R',
    'S', 'T', 'U', 'V', 'W', 'X',
    'Y', 'Z', 'a', 'b', 'c', 'd',
    'e', 'f', 'g', 'h', 'i', 'j',
    'k', 'l', 'm', 'n', 'o', 'p',
    'q', 'r', 's', 't', 'u', 'v',
    'w', 'x', 'y', 'z', '0', '1',
    '2', '3', '4', '5', '6', '7',
    '8', '9', '+', '/'
)

@Suppress("MagicNumber") // shifty, masked code ahead.
actual fun (ByteArray).base64EncodeTo(out: Appendable, off: Int, len: Int) {
    var packed = 0
    var packedByteCount = 0
    for (k in off until (off + len)) {
        packed = (packed shl 8) or (this[k].toInt() and 0xFF)
        if (++packedByteCount == 3) {
            val sextet0 = (packed and 0b1111_1100_0000_0000_0000_0000) ushr 18
            val sextet1 = (packed and 0b0000_0011_1111_0000_0000_0000) ushr 12
            val sextet2 = (packed and 0b0000_0000_0000_1111_1100_0000) ushr 6
            val sextet3 = (packed and 0b0000_0000_0000_0000_0011_1111)

            packedByteCount = 0
            packed = 0

            out.append(encodeTable[sextet0])
            out.append(encodeTable[sextet1])
            out.append(encodeTable[sextet2])
            out.append(encodeTable[sextet3])
        }
    }

    if (packedByteCount != 0) {
        // Shift as if there were zero bytes (represented by the '=' signs at the end)
        packed = packed shl (8 * (3 - packedByteCount))

        val sextet0 = (packed and 0b1111_1100_0000_0000_0000_0000) ushr 18
        val sextet1 = (packed and 0b0000_0011_1111_0000_0000_0000) ushr 12
        val sextet2 = (packed and 0b0000_0000_0000_1111_1100_0000) ushr 6
        // val sextet3 = (packed and 0b0000_0000_0000_0000_0011_1111)

        when (packedByteCount) {
            1 -> {
                out.append(encodeTable[sextet0])
                out.append(encodeTable[sextet1])
                out.append("==")
            }
            2 -> {
                out.append(encodeTable[sextet0])
                out.append(encodeTable[sextet1])
                out.append(encodeTable[sextet2])
                out.append('=')
            }
        }
    }
}
