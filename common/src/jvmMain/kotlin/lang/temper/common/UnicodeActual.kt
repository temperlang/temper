package lang.temper.common

import java.text.Normalizer

actual fun normalize(s: String, goal: UnicodeNormalForm) = Normalizer.normalize(
    s,
    when (goal) {
        UnicodeNormalForm.NFC -> Normalizer.Form.NFC
        UnicodeNormalForm.NFD -> Normalizer.Form.NFD
        UnicodeNormalForm.NFKC -> Normalizer.Form.NFKC
        UnicodeNormalForm.NFKD -> Normalizer.Form.NFKD
    },
).toString()

actual fun decodeUtf16(s: String, i: Int): Int = s.codePointAt(i)

actual fun decodeUtf16(cs: CharSequence, i: Int): Int = Character.codePointAt(cs, i)

/**
 * Try to iterate similarly to stepping through `.codePointAt`.
 */
actual fun decodeUtf16Iter(s: String): Iterable<Int> = object : Iterable<Int> {
    override fun iterator(): Iterator<Int> = s.codePoints().iterator()
}

actual val (Int).charCategory: CharCategory
    get() = CharCategory.valueOf(Character.getType(this))
