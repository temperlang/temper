package lang.temper.common

import com.ibm.icu.text.UnicodeSet

actual fun enumerateCharClassRegexBestEffort(regex: String): IntRangeSet? {
    val intRanges = mutableSetOf<IntRange>()
    val uset = UnicodeSet(regex)
    for (i in 0 until uset.rangeCount) {
        val start = uset.getRangeStart(i)
        val end = uset.getRangeEnd(i)
        intRanges.add(start..end)
    }
    return IntRangeSet.unionRanges(intRanges)
}
