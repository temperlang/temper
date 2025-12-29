package lang.temper.common

/**
 * @return null if not well-formed.
 */
fun urlDecode(urlEncoded: String): String? {
    @Suppress("MagicNumber") // codec code has to count chunks
    return toStringViaBuilder { out ->
        val bytes = ByteArray(MAX_UTF8_SEQUENCE_LENGTH)
        var i = 0
        val n = urlEncoded.length
        while (i < n) {
            when (val cp = decodeUtf16(urlEncoded, i)) {
                C_PCT -> {
                    if (!pctDecodeInto(urlEncoded, i, bytes, 0)) { return null }
                    i += 3
                    val b0 = bytes[0].toInt()
                    val nExtraBytesNeeded = when ((b0 ushr 4) and 0b1111) {
                        0b1100, 0b1101 -> 1
                        0b1110 -> 2
                        0b1111 -> 3
                        else -> 0
                    }
                    for (byteIndex in 1..nExtraBytesNeeded) {
                        if (!pctDecodeInto(urlEncoded, i, bytes, byteIndex)) { return null }
                        i += 3
                    }
                    out.append(
                        try {
                            bytes.decodeToString(
                                0,
                                nExtraBytesNeeded + 1,
                                throwOnInvalidSequence = true,
                            )
                        } catch (_: CharacterCodingException) {
                            return null
                        },
                    )
                }
                C_PLUS -> { // URL compatibility.
                    out.append(' ')
                    i += 1
                }
                else -> {
                    encodeUtf16(cp, out)
                    i += charCount(cp)
                }
            }
        }
    }
}

private fun pctDecodeInto(
    urlEncoded: String,
    offset: Int,
    out: ByteArray,
    outIndex: Int,
): Boolean {
    if (offset + 2 < urlEncoded.length && urlEncoded[offset] == '%') {
        val hex0 = valueOfHexDigitOrNegOne(urlEncoded[offset + 1])
        val hex1 = valueOfHexDigitOrNegOne(urlEncoded[offset + 2])
        if (hex0 >= 0 && hex1 >= 0) {
            out[outIndex] = ((hex0 shl BITS_PER_HEX_DIGIT) or hex1).toByte()
            return true
        }
    }
    return false
}

private fun valueOfHexDigitOrNegOne(c: Char): Int = when (c) {
    in '0'..'9' -> c.code - '0'.code
    in 'a'..'f' -> c.code - 'a'.code + VALUE_HEX_NUMERAL_A
    in 'A'..'F' -> c.code - 'A'.code + VALUE_HEX_NUMERAL_A
    else -> -1
}

private const val MAX_UTF8_SEQUENCE_LENGTH = 4
