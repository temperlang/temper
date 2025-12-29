package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

private val want = listOf(
    0x20, 0x1000, 0xD7FF, 0xD812, 0xFF, 0xDDEF, 0xD900, 0xE000, 0xFFFF,
    0x101234, 0x1ABCD, 0xD800,
)

// Representing this as a string does bad things on some backends since Kotlin silently converts
// orphaned surrogates in string constant expressions to '?' sometimes.  Filed a bug.
private val inpArr = charArrayOf(
    '\u0020', '\u1000', '\uD7FF', '\uD812', '\u00FF', '\uDDEF', '\uD900', '\uE000', '\uFFFF',
    '\uDBC4', '\uDE34', '\uD82A', '\uDFCD', '\uD800',
)

private object OpaqueCharSequence : CharSequence {
    override val length get() = inpArr.size
    override fun get(index: Int) = inpArr[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        SubSequence.of(this, startIndex, endIndex)
}

class UnicodeTest {
    @Test
    fun decodeUtf16GivenString() {
        val inpStr = inpArr.concatToString()
        val got = mutableListOf<Int>()
        var i = 0
        while (i < inpStr.length) {
            val cp = decodeUtf16(inpStr, i)
            got.add(cp)
            i += charCount(cp)
        }
        assertEquals(i, inpStr.length)

        assertEquals(want, got)
    }

    @Test
    fun decodeUtf16GivenCharSequence() {
        val got = mutableListOf<Int>()
        var i = 0
        while (i < OpaqueCharSequence.length) {
            val cp = decodeUtf16(OpaqueCharSequence, i)
            got.add(cp)
            i += charCount(cp)
        }
        assertEquals(i, OpaqueCharSequence.length)

        assertEquals(want, got)
    }

    @Test
    fun encodeUtf16OntoBuffer() {
        val got = toStringViaBuilder { sb ->
            for (cp in want) {
                encodeUtf16(cp, sb)
            }
        }
        assertEquals(inpArr.concatToString(), got, jsonEscaper.escape(got))
    }

    @Test
    fun codePointCharCategory() {
        assertEquals(CharCategory.UNASSIGNED, (-0x1).charCategory)
        assertEquals(CharCategory.CONTROL, (0x1).charCategory)
        assertEquals(CharCategory.CONTROL, (0x11).charCategory)
        assertEquals(CharCategory.LOWERCASE_LETTER, (0x111).charCategory)
        assertEquals(CharCategory.OTHER_LETTER, (0x1111).charCategory)
        assertEquals(CharCategory.OTHER_LETTER, (0x11111).charCategory)
        assertEquals(CharCategory.UNASSIGNED, (0x111111).charCategory)
    }
}
