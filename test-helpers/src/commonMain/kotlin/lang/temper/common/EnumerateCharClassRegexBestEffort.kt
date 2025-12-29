package lang.temper.common

import kotlin.test.assertEquals

fun assertRangeSetsEqualBestEffort(
    want: IntRangeSet?,
    input: IntRangeSet,
) {
    if (want != null) {
        @Suppress("ConstantConditionIf") // Allow dumping when upgrading ICU4J
        if (false) {
            println("IntRangeSet.new(")
            println("        sortedUniqEvenLengthArray=intArrayOf(")
            for (r in want) {
                println("            ${r.first}, ${r.last + 1},")
            }
            println("        )")
            println("    )")
        }

        assertEquals(want, input)
    }
}

expect fun enumerateCharClassRegexBestEffort(regex: String): IntRangeSet?
