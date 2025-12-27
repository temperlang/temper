package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

class KBitSetTest {
    @Test
    fun bitIterators() {
        // Create a bunch of bitset indices.
        val bitIndices = setOf(0, 1, 2, 5, 7, 11, 12, 13, 23, 32)
        val bitSet = KBitSet()
        for (bi in bitIndices) {
            bitSet[bi] = true
        }

        // Try a bunch of windows over the bits and iterate in both directions and for both
        // possible bit values.
        for (bitValue in listOf(false, true)) {
            for (forward in listOf(false, true)) {
                for (left in -1 until 25) {
                    if (!bitValue && left < 0) { continue }
                    for (right in if (left < 0) 0..0 else left + 1 until 25) {
                        val range = if (left < 0) {
                            null
                        } else {
                            left..right
                        }
                        var iterable = if (bitValue) {
                            bitSet.bitIndices
                        } else {
                            bitSet.clearBitIndices(range!!)
                        }
                        if (range != null) {
                            iterable = iterable.limited(range)
                        }
                        if (!forward) {
                            iterable = iterable.reverse()
                        }
                        val got = iterable.toList()
                        val want = if (bitValue) {
                            bitIndices.filter { range == null || it in range }
                        } else {
                            range!!.filter { it !in bitIndices }
                        }.let {
                            if (forward) it else it.reversed()
                        }
                        assertEquals(
                            want,
                            got,
                            "bitValue=$bitValue, range=$range, forward=$forward",
                        )
                    }
                }
            }
        }
    }
}
