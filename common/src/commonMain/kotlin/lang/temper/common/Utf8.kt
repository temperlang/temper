package lang.temper.common

/**
 * The size, in bytes, of the given code-point encoded using UTF-8.
 * Surrogate code-points are treated as having a length of 3 and
 * inputs out of the range of valid code-points as having a length of 4.
 */
@Suppress("MagicNumber") // Code-point range boundaries in range expressions and byte counts.
fun utf8ByteLength(codepoint: Int): Int = when (codepoint) {
    in 0..0x7F -> 1
    in 0x80..0x7FF -> 2
    in 0x800..0xFFFF -> 3
    else -> 4
}

/** A UTF-8 encoder that is tolerant of orphaned surrogates. */
@Suppress("MagicNumber") // Moving bit offsets and masks out just complicates the code.
fun toUtf8Tolerant(cs: CharSequence, offset: Int = 0, limit: Int = cs.length): ByteArray {
    var nBytes = 0
    var i = offset
    while (i < limit) {
        val c = cs[i]
        i += 1
        if (c < '\u0080') {
            nBytes += 1
        } else if (c < '\u0800') {
            nBytes += 2
        } else if (c < '\uD800') {
            nBytes += 3
        } else {
            if (c in '\uD800'..'\uDBFF' && i < limit) {
                val cNext = cs[i]
                if (cNext in '\uDC00'..'\uDFFF') {
                    nBytes += 4
                    i += 1
                    continue
                }
            }
            nBytes += 3
        }
    }

    val bytes = ByteArray(nBytes)

    i = offset
    var bytesIndex = 0
    while (i < limit) {
        val c = cs[i].code
        i += 1
        if (c < 0x0080) {
            bytes[bytesIndex] = c.toByte()
            bytesIndex += 1
        } else if (c < 0x0800) {
            bytes[bytesIndex] = (
                0b1100_0000 or
                    ((c shr 6) and 0b0001_1111)
                ).toByte()
            bytes[bytesIndex + 1] = (
                0b1000_0000 or
                    (c and 0b0011_1111)
                ).toByte()
            bytesIndex += 2
        } else {
            if (c in C_MIN_SURROGATE..0xDBFF && i < limit) {
                val cNext = cs[i].code
                if (cNext in 0xDC00..C_MAX_SURROGATE) {
                    i += 1

                    val y = c and 0x3ff
                    val x = cNext and 0x3ff
                    val cp = 0x10000 + ((y shl 10) or x)

                    bytes[bytesIndex] = (
                        0b1111_0000 or
                            ((cp shr 18) and 0b0000_0111)
                        ).toByte()
                    bytes[bytesIndex + 1] = (
                        0b1000_0000 or
                            ((cp shr 12) and 0b0011_1111)
                        ).toByte()
                    bytes[bytesIndex + 2] = (
                        0b1000_0000 or
                            ((cp shr 6) and 0b0011_1111)
                        ).toByte()
                    bytes[bytesIndex + 3] = (
                        0b1000_0000 or
                            (cp and 0b0011_1111)
                        ).toByte()
                    bytesIndex += 4
                    continue
                }
            }

            bytes[bytesIndex] = (
                0b1110_0000 or
                    ((c shr 12) and 0b0000_1111)
                ).toByte()
            bytes[bytesIndex + 1] = (
                0b1000_0000 or
                    ((c shr 6) and 0b0011_1111)
                ).toByte()
            bytes[bytesIndex + 2] = (
                0b1000_0000 or
                    (c and 0b0011_1111)
                ).toByte()
            bytesIndex += 3
        }
    }

    return bytes
}

/** A UTF-8 decoder that decodes code-points not just scalar values. */
fun fromUtf8Tolerant(
    bytes: ByteArray,
    out: Appendable,
    offset: Int = 0,
    limit: Int = bytes.size,
) {
    var i = offset
    while (i < limit) {
        val b = bytes[i]

        // 8 - 5: the number of leading bits that determine the octet count for a sequence
        @Suppress("MagicNumber")
        var nBytes = byteCountFromFirstUtf8Byte(b)

        if (i + nBytes > limit) {
            nBytes = -1
        }
        i += 1

        @Suppress("MagicNumber") // Lots of masks.
        when (nBytes) {
            1 -> out.append(Char(b.toInt()))
            2, 3, 4 -> {
                var cp = b.toInt() and firstByteMask[nBytes]
                var nCont = nBytes - 1
                do {
                    val cbi = bytes[i].toInt()
                    if ((cbi and 0b1100_0000) != 0b1000_0000) {
                        cp = 0xFFFD // Invalid character
                        break
                    }
                    cp = (cp shl 6) or (cbi and 0b0011_1111)
                    nCont -= 1
                    i += 1
                } while (nCont != 0)
                encodeUtf16(cp, out)
            }
            else -> out.append('\uFFFD')
        }
    }
}

/** Bits in the leading byte that are part of a codepoint indexed by whole-sequence byte count. */
@Suppress("MagicNumber") // Masks
private val firstByteMask = intArrayOf(
    0, // Indexed by byte count.  There is no zero byte count.
    0b1111_1111,
    0b0001_1111,
    0b0000_1111,
    0b0000_0111,
)

@Suppress("MagicNumber") // 8(b/B) - 5(leading bits determine a UTF-8 sequence's byte count)
fun byteCountFromFirstUtf8Byte(firstByte: Byte) =
    nBytesBasedOnTop5Bits[(firstByte.toInt() and UNSIGNED_BYTE_MASK) ushr (8 - 5)]

/**
 * Given the 5 high bits (after shifting right by 3) of the leading byte, the number of bytes in
 * the whole sequence, or -1 if we've got something invalid.
 */
@Suppress("MagicNumber") // Lookup table of byte counts contains 3 which is super-magical.
private val nBytesBasedOnTop5Bits = intArrayOf(
    1, // 00000
    1, // 00001
    1, // 00010
    1, // 00011
    1, // 00100
    1, // 00101
    1, // 00110
    1, // 00111
    1, // 01000
    1, // 01001
    1, // 01010
    1, // 01011
    1, // 01100
    1, // 01101
    1, // 01110
    1, // 01111
    -1, // 10000
    -1, // 10001
    -1, // 10010
    -1, // 10011
    -1, // 10100
    -1, // 10101
    -1, // 10110
    -1, // 10111
    2, // 11000
    2, // 11001
    2, // 11010
    2, // 11011
    3, // 11100
    3, // 11101
    4, // 11110
    -1, // 11111
)

private const val UNSIGNED_BYTE_MASK = 0xFF
