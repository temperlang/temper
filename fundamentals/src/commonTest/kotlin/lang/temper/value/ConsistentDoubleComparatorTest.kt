package lang.temper.value

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsistentDoubleComparatorTest {
    private fun assertDoubleCmp(want: Int, a: Double, b: Double) {
        val got = ConsistentDoubleComparator.compare(a, b)
        assertEquals(want, got, "$a <=> $b")
    }

    private val doubles = listOf(
        Double.NEGATIVE_INFINITY,
        -1e6,
        -1.0,
        -0.5,
        -0.0,
        0.0,
        0.5,
        1.0,
        1e6,
        Double.POSITIVE_INFINITY,
        Double.NaN,
    )

    @Test
    fun eq() {
        for (d in doubles) {
            assertDoubleCmp(0, d, d)
        }
    }

    @Test
    fun lt() {
        for (i in 0 until (doubles.size - 1)) {
            assertDoubleCmp(-1, doubles[i], doubles[i + 1])
        }
    }

    @Test
    fun gt() {
        for (i in 0 until (doubles.size - 1)) {
            assertDoubleCmp(1, doubles[i + 1], doubles[i])
        }
    }
}
