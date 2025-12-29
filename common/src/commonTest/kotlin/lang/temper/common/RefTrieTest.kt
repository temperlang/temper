package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RefTrieTest {
    private val trie = RefTrie(
        listOf(
            listOf(2) to "2",
            listOf(5) to "5",
            listOf(1, 2, 3) to "123",
            listOf(1, 2) to "12",
            listOf(1, 3) to "13",
            listOf(2, 5) to "25",
            listOf(1, 2, 3, 4, 5, 6) to "123456",
            listOf(1, 2, 3, 4) to "1234",
        ),
    )

    @Test
    fun get12() {
        assertEquals(
            "12",
            trie[listOf(1, 2)]?.value,
        )
    }

    @Test
    fun get123() {
        assertEquals(
            "123",
            trie[listOf(1, 2, 3)]?.value,
        )
    }

    @Test
    fun get1234() {
        assertEquals(
            "1234",
            trie[listOf(1, 2, 3, 4)]?.value,
        )
    }

    @Test
    fun get12345() {
        assertEquals(
            null,
            trie[listOf(1, 2, 3, 4, 5)]?.value,
        )
    }

    @Test
    fun get123456() {
        assertEquals(
            "123456",
            trie[listOf(1, 2, 3, 4, 5, 6)]?.value,
        )
    }

    @Test
    fun longestPrefix1357() {
        assertEquals(
            2 to "13",
            trie.longestPrefix(listOf(1, 3, 5, 7))?.let { (n, t) ->
                n to t.value
            },
        )
    }

    @Test
    fun get5() {
        assertEquals(
            "5",
            trie[listOf(5)]?.value,
        )
    }

    @Test
    fun get124IsAbsent() {
        assertEquals(
            null,
            trie[listOf(1, 2, 4)]?.value,
        )
        assertFalse(listOf(1, 2, 4) in trie)
    }
}
