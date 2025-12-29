package lang.temper.value

import kotlin.math.min

internal object Utf8EquivalentStringComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val alen = a.length
        val blen = b.length
        val minlen = min(alen, blen)
        for (i in 0 until minlen) {
            val ca = a[i]
            val cb = b[i]
            val d = ca.compareTo(cb)
            if (d != 0) {
                // If neither is a surrogate, or they're the same kind of surrogate then we can
                // compare lexicographically by utf-16 without diverging from utf8-based comparison.
                // If they're different kinds of surrogates, then
                val aIsSurrogate = ca in '\uD800'..'\uDFFF'
                val bIsSurrogate = cb in '\uD800'..'\uDFFF'
                if (!(aIsSurrogate or bIsSurrogate)) {
                    return d
                }

                // Let's classify!
                val aClassification = classifySurrogate(ca, a, i)
                val bClassification = classifySurrogate(cb, b, i)

                // ⇩
                // a   ⇨b| NOT   | LEAD        | TRAIL
                // ------+-------+-------------+------------
                // NOT   | d     | -1 (note A) | -1 (note B)
                // LEAD  | 1     | d           | -1 (note C)
                // TRAIL | 1     | 1           | d

                // note A: b starts a supplemental codepoint which falls after a
                // note B: a[i-1] was an orphaned lead surrogate which falls before the supplemental
                //         codepoint specified by (b[i-1], b[i])
                // note C: The strings around this part look like
                //              i-1  i    i+1
                //           a: ???? D8__ DC__
                //           b: D8__ DC__ ????
                //         so we know that (b[i-1], b[i]) follow a[i - 1]
                val dClassificiation = aClassification.compareTo(bClassification)
                return if (dClassificiation == 0) d else dClassificiation
            }
        }
        // If both a and b end in a leading surrogate, the one that is longer is sorted after.
        // If they don't, the one that is longer is sorted after.
        return alen.compareTo(blen)
    }
}

const val NOT_A_SURROGATE = 0 // or an orphaned surrogate
const val LEADING_SURROGATE = 1
const val TRAILING_SURROGATE = 2
private fun classifySurrogate(c: Char, s: String, i: Int): Int {
    if (c < '\uDC00') {
        if (c >= '\uD800') {
            if (i + 1 < s.length && s[i + 1] in '\uDC00'..'\uDFFF') {
                return LEADING_SURROGATE
            }
        }
    } else if (c <= '\uDFFF') {
        if (i != 0 && s[i - 1] in '\uD800'..'\uDBFF') {
            return TRAILING_SURROGATE
        }
    }
    return NOT_A_SURROGATE
}
