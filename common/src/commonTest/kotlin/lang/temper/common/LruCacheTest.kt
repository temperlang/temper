package lang.temper.common

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LruCacheTest {
    @Test
    fun eviction() = withRandomForTest { prng ->
        val maxSize = 32
        val (lruCache, keys) = churnCache(prng, maxSize = maxSize, randUntil = Int.MAX_VALUE)
        val keysFromKeySet = lruCache.keys.toList()
        val pairsFromEntrySet = lruCache.entries.map { it.key to it.value }
        val valuesFromValueSet = lruCache.values.toList()

        val lastNKeys = keys.subList(keys.size - maxSize, keys.size)
        assertEquals(lastNKeys, keysFromKeySet)
        assertEquals(valuesFromValueSet, keysFromKeySet.map { "$it" })
        assertEquals(pairsFromEntrySet, keysFromKeySet.map { it to "$it" })

        for (key in keysFromKeySet) {
            assertEquals("$key", lruCache[key])
        }
    }

    @Test
    fun edginess() = withRandomForTest { prng ->
        // This case that increases repetition was just plain crashing before.
        churnCache(prng, maxSize = 3, randUntil = 10)
    }
}

private fun churnCache(
    prng: Random,
    maxSize: Int,
    randUntil: Int,
): Pair<MutableMap<Int, String>, MutableList<Int>> {
    val keys = mutableListOf<Int>()
    val lruCache = lruCacheWithSize<Int, String>(maxSize = maxSize)
    repeat(1000) {
        val key = prng.nextInt(randUntil)
        val value = "$key"
        lruCache[key] = value
        keys.add(key)
        assertTrue(lruCache.size <= maxSize, "#$it: ${lruCache.size} vs $maxSize")
    }
    assertEquals(lruCache.size, maxSize)
    return lruCache to keys
}
