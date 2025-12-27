package lang.temper.be

import kotlin.math.abs

private const val BASE64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

const val LEAST_FOUR_BITS = 0b1111
const val LEAST_FIVE_BITS = 0b11111
const val CONTINUATION_BIT = 0b100000

internal fun base64VlqEncode(integers: IntArray, stringBuilder: StringBuilder) {
    for (integer in integers) {
        val sextets = vlqEncode(integer)
        base64Encode(sextets, stringBuilder)
    }
}

private val justZero = listOf(0)

@Suppress("MagicNumber") // Bits were twiddled.
private fun vlqEncode(x: Int): List<Int> {
    if (x == 0) {
        return justZero
    }
    var absX = abs(x)
    val sextets = mutableListOf<Int>()
    while (absX > 0) {
        var sextet: Int
        if (sextets.isEmpty()) {
            sextet = if (x < 0) { 1 } else { 0 } // set the sign bit
            // shift one to make space for sign bit
            sextet = sextet or ((absX and LEAST_FOUR_BITS) shl 1)
            absX = absX ushr 4
        } else {
            sextet = absX and LEAST_FIVE_BITS
            absX = absX ushr 5
        }
        if (absX > 0) {
            sextet = sextet or CONTINUATION_BIT
        }
        sextets.add(sextet)
    }
    return sextets
}

private fun base64Encode(vlq: List<Int>, stringBuilder: StringBuilder) {
    for (i in vlq) {
        stringBuilder.append(BASE64_ALPHABET[i])
    }
}
