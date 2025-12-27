package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LawfulEvilTest {
    @Test
    fun testLawfulEvilListMutationWhileStableFails() {
        val ls = lawfulEvilListOf<String>()
        ls.add("foo")
        ls.doWhileStable {
            assertFailsWith(AttemptToMutateWhileStable::class) {
                ls.add("bar")
            }
            assertEquals(ls[0], "foo")

            assertFailsWith(AttemptToMutateWhileStable::class) {
                val iterator = ls.iterator()
                assertTrue(iterator.hasNext())
                assertEquals("foo", iterator.next())
                iterator.remove()
            }

            assertFailsWith(AttemptToMutateWhileStable::class) {
                ls.subList(0, 1).clear()
            }
        }
        ls.add("baz")

        assertEquals(
            listOf("foo", "baz"),
            ls,
        )
    }

    @Test
    fun testLawfulEvilListMutationWhileUnstableSucceeds() {
        val ls = lawfulEvilListOf<String>()
        ls.add("foo")

        ls.add("bar")
        assertEquals(ls[0], "foo")

        val iterator = ls.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("foo", iterator.next())
        iterator.remove()

        ls.subList(0, 1).clear()

        ls.add("baz")

        assertEquals(
            listOf("baz"),
            ls,
        )
    }

    @Test
    fun testLawfulEvilSetMutationWhileStableFails() {
        val s = lawfulEvilSetOf<String>()
        s.add("foo")

        s.doWhileStable {
            assertFailsWith(AttemptToMutateWhileStable::class) {
                s.add("bar")
            }

            val iterator = s.iterator()
            assertTrue(iterator.hasNext())
            assertEquals("foo", iterator.next())

            assertFailsWith(AttemptToMutateWhileStable::class) {
                iterator.remove()
            }

            assertFailsWith(AttemptToMutateWhileStable::class) {
                s.clear()
            }

            assertFailsWith(AttemptToMutateWhileStable::class) {
                s.retainAll(setOf())
            }
        }

        s.add("baz")

        assertEquals(
            setOf("foo", "baz"),
            s,
        )
    }

    @Test
    fun testLawfulEvilMapMutationWhileStableFails() {
        val m = lawfulEvilMapOf<String, String>()
        m["foo"] = "FOO"

        m.doWhileStable {
            assertFailsWith(AttemptToMutateWhileStable::class) {
                m["bar"] = "BAR"
            }

            assertFailsWith(AttemptToMutateWhileStable::class) {
                m["foo"] = "FOO2"
            }

            val iterator = m.iterator()
            assertTrue(iterator.hasNext())
            iterator.next()

            assertFailsWith(AttemptToMutateWhileStable::class) {
                iterator.remove()
            }

            assertFailsWith(AttemptToMutateWhileStable::class) {
                m.clear()
            }

            // TODO: entries

            // TODO: values
        }

        m["baz"] = "BAZ"

        assertEquals(
            mapOf(
                "foo" to "FOO",
                "baz" to "BAZ",
            ),
            m as Map<String, String>,
        )
    }
}
