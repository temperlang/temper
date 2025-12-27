package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LruCacheTest {
    @Test
    fun eviction() = withRandomForTest { prng ->
        val maxSize = 32
        val keys = mutableListOf<Int>()
        val lruCache = lruCacheWithSize<Int, String>(maxSize = maxSize)
        repeat(1000) {
            val key = prng.nextInt()
            val value = "$key"
            lruCache[key] = value
            keys.add(key)
            assertTrue(lruCache.size <= maxSize, "#$it: ${lruCache.size} vs $maxSize")
        }
        assertEquals(lruCache.size, maxSize)
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
}
