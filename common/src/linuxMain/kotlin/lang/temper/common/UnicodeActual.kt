package lang.temper.common

@Suppress("MagicNumber")
actual fun decodeUtf16(s: String, i: Int): Int = decodeUtf16(s as CharSequence, i)

@Suppress("MagicNumber")
actual fun decodeUtf16(cs: CharSequence, i: Int): Int {
    val c = cs[i]
    if (c in '\uD800'..'\uDBFF' && i + 1 < cs.length) {
        val d = cs[i + 1]
        if (d in '\uDC00'..'\uDFFF') {
            val y = c.toInt() and 0x3ff
            val x = d.toInt() and 0x3ff
            return 0x10000 + ((y shl 10) or x)
        }
    }
    return c.toInt()
}

actual fun normalize(
    s: String,
    @Suppress("UnusedPrivateMember") // Legit.  Goal not used.
    goal: UnicodeNormalForm
): String {
    // TODO: need to link in ICU I guess.
    // TODO: Issue #11
    return s
}
