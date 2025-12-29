package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BagTest {
    @Test
    fun emptyBagIsEmpty() {
        assertTrue(bagOf<Nothing>().isEmpty())
        assertFalse(bagOf<Nothing>().isNotEmpty())
    }

    @Test
    fun equalBagsAreEqualAndUnequalBagsAreUnequal() {
        val a = bagOf(1 to 2, 2 to 1) // 1 1 2
        val b = bagOf(2 to 1, 1 to 2)
        val c = mutableBagOf(1 to 2, 2 to 1)
        val d = mutableBagOf<Int>()
        d[1] += 1
        d[2] += 1
        d[1] += 1
        @Suppress("RemoveExplicitTypeArguments")
        val e = buildBag<Int> {
            this[2] = 1
            this[1] = 1
            this[1] += 1
        }
        val equivalents = listOf(a, b, c, e, e.toBag())
        val distinct = bagOf(1 to 1, 2 to 2)
        for (equivalent in equivalents) {
            for (equivalentOther in equivalents) {
                assertEquals(equivalent, equivalentOther)
            }
            assertNotEquals(equivalent, distinct)
            assertNotEquals<Any?>(equivalent, null)
            assertNotEquals<Any?>(equivalent, equivalents.toList())
        }
        assertEquals(
            1,
            equivalents.toSet().size,
        )
    }

    @Test
    fun zeroCounts() {
        val mutableBagWithZero = mutableBagOf<String>()
        mutableBagWithZero["a"] += 2
        mutableBagWithZero["b"] += 2
        mutableBagWithZero["a"] -= 2
        val immutableBag = mutableBagWithZero.toBag()

        for (bag in listOf(mutableBagWithZero, immutableBag)) {
            assertTrue("b" in bag)
            assertFalse("a" in bag)
            assertEquals("[(b \u00d7 2)]", "$bag")
            assertTrue(bag.none { it.key == "a" })
            assertTrue(bag.any { it.key == "b" })
        }
    }

    @Test
    fun explosionsAndIterations() {
        val bagOfWords = bagOf("nary" to 0, "once" to 1, "twice" to 2, "thrice" to 3)
        assertEquals(
            listOf("once", "twice", "twice", "thrice", "thrice", "thrice"),
            bagOfWords.exploded.toList(),
        )
        assertEquals(
            listOf("once" to 1, "twice" to 2, "thrice" to 3),
            bagOfWords.map { it.key to it.count },
        )
    }
}
