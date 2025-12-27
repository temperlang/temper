package lang.temper.common

// TODO: this must exist somewhere in the Kotlin common libs.  Where?
/** Cross-platform UTF-16 decode.  This is equivalent to `.codePointAt`. */
expect fun decodeUtf16(cs: CharSequence, i: Int): Int

/** Cross-platform UTF-16 decode.  This is equivalent to `.codePointAt`. */
expect fun decodeUtf16(s: String, i: Int): Int

/**
 * Get an iterator of codepoints.
 */
expect fun decodeUtf16Iter(s: String): Iterable<Int>

/** Cross-platform `Appendable.appendCodePoint` */
@Suppress("MagicNumber")
fun encodeUtf16(codePoint: Int, out: Appendable) {
    val excess = codePoint - MIN_SUPPLEMENTAL_CP
    if (excess < 0) {
        out.append(codePoint.toChar())
    } else {
        val leading = C_MIN_SURROGATE + ((excess ushr 10) and 0x3FF)
        out.append(leading.toChar())
        val trailing = 0xDC00 + (excess and 0x3FF)
        out.append(trailing.toChar())
    }
}

/** Extension function for convenience. */
fun Appendable.appendCodePoint(codePoint: Int) {
    encodeUtf16(codePoint, this)
}

/** Make ints know how to convert themselves into a String. */
fun Int.codePointString(): String = StringBuilder(2).let {
    it.appendCodePoint(this)
    "$it"
}

/**
 * True iff the length in Java's modified UTF-8 length is >= limit.
 *
 * The modified UTF-8 length is the UTF-8 length plus 1 for each NUL character.
 */
@Suppress("MagicNumber") // max bytes per UTF-16 code units
fun modifiedUtf8LenExceeds(s: String, limit: Int): Boolean {
    val utf16Len = s.length
    if (utf16Len * 3 < limit) { return false }
    if (utf16Len >= limit) { return true }
    var n = 0
    for (cp in decodeUtf16Iter(s)) {
        n += modifiedUtf8ByteCount(cp)
        if (n >= limit) { return true }
    }
    return false
}

@Suppress("MagicNumber") // code point ranges and byte counts
fun modifiedUtf8ByteCount(codepoint: Int) = when (codepoint) {
    0 -> 2
    in 1..0x7F -> 1
    in 0x80..0x7ff -> 2
    in 0x800..0xFFFF -> 3
    else -> 4
}

/** Iterates code points in a *CharSequence* */
data class CodePoints(val charSequence: CharSequence) : Iterable<Int> {
    override fun iterator(): Iterator<Int> = object : Iterator<Int> {
        private var i = 0
        private val n = charSequence.length

        override fun hasNext(): Boolean = i < n

        override fun next(): Int {
            if (i >= n) { throw NoSuchElementException() }
            val cp = decodeUtf16(charSequence, i)
            i += charCount(cp)
            return cp
        }
    }
}

@Suppress("MagicNumber")
fun decodeCharacterReferences(encoded: String): String {
    return encoded.replace(Regex("&(?:#(\\d+)|#[xX]([a-fA-F0-9]+)|(\\w+));")) { matches ->
        val index = matches.groupValues.indexOfFirst { it != "" }
        val text = matches.groups[index]?.value!!
        when (index) {
            1 -> {
                toStringViaBuilder { encodeUtf16(text.toInt(), it) }
            }
            2 -> {
                toStringViaBuilder { encodeUtf16(text.toInt(16), it) }
            }
            3 -> {
                when (text) {
                    "lt" -> "<"
                    "gt" -> ">"
                    "amp" -> "&"
                    "quot" -> "\""
                    "apos" -> "'"
                    else -> throw IllegalArgumentException(text)
                }
            }
            else -> throw IllegalArgumentException(text)
        }
    }
}

fun charCount(cp: Int): Int = if (cp < MIN_SUPPLEMENTAL_CP) { 1 } else { 2 }

expect val (Int).charCategory: CharCategory

const val MIN_SUPPLEMENTAL_CP = 0x10000
