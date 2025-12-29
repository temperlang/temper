package lang.temper.common

/**
 * Zips entries for two maps.  This is tolerant when [block] modifies [leftMap]'s entry for the
 * keys it receives without adding or otherwise deleting entries.
 */
fun <K, V> forTwoMapsMutatingLeft(
    leftMap: Map<K, V>,
    rightMap: Map<K, V>,
    block: (ZippedEntry<K, V>) -> Unit,
) {
    // First iterate through keys in the map we might mutate
    // so that we don't accidentally revisit based on keys inserted
    // that are only in the right.
    val iterated = mutableSetOf<K>()
    for ((key, leftValue) in leftMap) {
        val rightValueOrNull = rightMap[key]
        val zipped = when {
            // In the common case, where the value is not null, don't bother with a
            // .contains check.
            rightValueOrNull != null -> ZippedEntry.Both(key, leftValue, rightValueOrNull)
            // Extra check needed in case V is a super-type of Null.
            key in rightMap -> {
                @Suppress("UNCHECKED_CAST") // if rightMap is stable V must be a super-type of Null
                val rightValue = null as V
                ZippedEntry.Both(key, leftValue, rightValue)
            }
            else -> ZippedEntry.LeftOnly(key, leftValue)
        }
        iterated.add(key)
        block(zipped)
    }
    for ((key, rightValue) in rightMap) {
        if (key !in iterated) {
            block(ZippedEntry.RightOnly(key, rightValue))
        }
    }
}

sealed class ZippedEntry<K, V> {
    abstract val key: K
    abstract val left: V?
    abstract val right: V?

    data class LeftOnly<K, V>(
        override val key: K,
        override val left: V,
    ) : ZippedEntry<K, V>() {
        override val right: V? get() = null
    }

    data class RightOnly<K, V>(
        override val key: K,
        override val right: V,
    ) : ZippedEntry<K, V>() {
        override val left: V? get() = null
    }

    data class Both<K, V>(
        override val key: K,
        override val left: V,
        override val right: V,
    ) : ZippedEntry<K, V>()
}
