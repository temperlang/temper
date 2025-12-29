package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals

private data class HoldsAnInt(val i: Int)

class MutableIdentityMapTest {
    @Test
    fun twoEntryMap() {
        val a = HoldsAnInt(1)
        val b = HoldsAnInt(2)

        val m = mutableIdentityMapOf<HoldsAnInt, Int>()
        m[a] = 1
        m[b] = 2

        assertEquals(m.size, 2)
        assertEquals(1, m[a])
        assertEquals(2, m[b])
        assertEquals(null, m[HoldsAnInt(1)])
        assertEquals(null, m[HoldsAnInt(2)])

        assertEquals(
            listOf(a to 1, b to 2),
            m.toList().sortedBy { it.first.i },
        )

        m -= a
        assertEquals(m.size, 1)
        assertEquals(null, m[a])
        assertEquals(2, m[b])

        assertEquals(
            listOf(b to 2),
            m.toList(),
        )
    }

    @Test
    fun twoEntrySet() {
        val a = HoldsAnInt(1)
        val b = HoldsAnInt(2)

        val s = mutableIdentitySetOf<HoldsAnInt>()
        s += a
        s += b

        assertEquals(s.size, 2)
        assertEquals(true, a in s)
        assertEquals(true, b in s)
        assertEquals(false, HoldsAnInt(1) in s)
        assertEquals(false, HoldsAnInt(2) in s)

        assertEquals(
            listOf(a, b),
            s.toList().sortedBy { it.i },
        )

        s -= a
        assertEquals(s.size, 1)
        assertEquals(false, a in s)
        assertEquals(true, b in s)

        assertEquals(
            listOf(b),
            s.toList(),
        )
    }
}
