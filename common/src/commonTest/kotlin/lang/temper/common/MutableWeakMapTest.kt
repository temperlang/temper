package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableWeakMapTest {
    /** A simple class that has identity based equals/hashCode */
    private class O(val string: String) {
        override fun toString(): String = string
    }

    @Test
    fun heldKeysAvailable() {
        val oa = O("a")
        val ob = O("b")
        val oc = O("c")
        val od = O("d")

        val m = mutableWeakMapOf<O, String>()
        m[oa] = "A"
        m[ob] = "B"
        m[oc] = "C"

        // `in` is by key
        assertTrue(oa in m)
        assertFalse(od in m)

        assertEquals("A", m[oa])
        assertEquals("B", m[ob])
        assertEquals("C", m[oc])
        assertNull(m[od])

        // Old value returned on remove
        assertEquals("A", m.remove(oa))
        assertNull(m[oa])
        // Other keys still work
        assertEquals("B", m[ob])

        // Old value returned on set
        val old = m.set(oc, "CC")
        assertEquals("C", old)
        assertEquals("CC", m[oc])
    }
}
