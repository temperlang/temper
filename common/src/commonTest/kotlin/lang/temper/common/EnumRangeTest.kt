package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal enum class Vowel {
    A,
    E,
    I,
    O,
    U,
    Y, // Sometimes
}

class EnumRangeTest {
    @Test
    fun inRange() {
        val range = EnumRange(Vowel.E, Vowel.O)
        assertTrue(!range.isEmpty())
        assertTrue(Vowel.A !in range)
        assertTrue(Vowel.E in range)
        assertTrue(Vowel.I in range)
        assertTrue(Vowel.O in range)
        assertTrue(Vowel.U !in range)
        assertTrue(Vowel.Y !in range)
    }

    @Test
    fun emptyRange() {
        val range = EnumRange(Vowel.O, Vowel.I)
        assertTrue(range.isEmpty())
        assertTrue(Vowel.A !in range)
        assertTrue(Vowel.E !in range)
        assertTrue(Vowel.I !in range)
        assertTrue(Vowel.O !in range)
        assertTrue(Vowel.U !in range)
        assertTrue(Vowel.Y !in range)
    }

    @Test
    fun asSequence() {
        val range = EnumRange(Vowel.E, Vowel.U)
        assertEquals(
            listOf(Vowel.E, Vowel.I, Vowel.O, Vowel.U),
            range.asSequence().toList(),
        )
    }
}
