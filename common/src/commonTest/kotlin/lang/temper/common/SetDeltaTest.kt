package lang.temper.common

import lang.temper.common.SetDelta.Companion.buildSetDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SetDeltaTest {
    @Test
    fun empty() {
        val s = setOf(1, 2, 3)
        val delta = s.buildSetDelta {
            // do nothing
        }
        assertFalse(delta.isChanged)
        val t = delta.modified(s)
        assertEquals(s, t)
    }

    @Test
    fun addSomeRemoveSome() {
        val s = setOf(1, 2, 3)
        val delta = s.buildSetDelta {
            removeAll(listOf(0, 2, 4))
            addAll(listOf(1, 3, 5))
        }
        assertEquals("SetDelta(+[5], -[2])", "$delta")
        val t = delta.modified(s)
        assertEquals(setOf(1, 3, 5), t)
    }

    @Test
    fun lastActionClobbers() {
        val s = setOf(1, 2, 3)
        val delta = s.buildSetDelta {
            remove(2)
            add(4)
            add(5)
            add(2) // Clobbers remove(2)
            remove(4) // Clobbers add(4)
        }
        assertEquals("SetDelta(+[5], -[])", "$delta")
        val t = delta.modified(s)
        assertEquals(setOf(1, 2, 3, 5), t)
    }

    @Test
    fun equality() {
        val delta1 = setOf(1, 2, 3).buildSetDelta {
            remove(2)
        }
        val delta2 = setOf(2, 4, 6).buildSetDelta {
            remove(2)
        }
        assertEquals(delta1, delta2)
        assertEquals(delta1.hashCode(), delta2.hashCode())
    }
}
