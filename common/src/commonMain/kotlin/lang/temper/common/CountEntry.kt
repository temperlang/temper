package lang.temper.common

/**
 * Represents an entry in a map of counts to allow adjusting the count by a delta using `+=` syntax.
 *
 * ```kt
 * val fooCounts: MutableMap<Foo, Int> = ...
 *
 * // Increment
 * fooCounts.countFor(myFoo) += 1
 * // Decrement
 * fooCounts.countFor(myOtherFoo) -= 1
 * ```
 *
 * Any adjustment will insert an entry if none exists for [key] while assuming that the absence of
 * an entry for [key] is equivalent to an entry with a value of zero.
 */
data class CountEntry<K>(
    val map: MutableMap<K, Int>,
    val key: K,
) {
    operator fun plusAssign(delta: Int) {
        map[key] = (map[key] ?: 0) + delta
    }

    operator fun inc(): CountEntry<K> {
        plusAssign(1)
        return this
    }

    operator fun minusAssign(delta: Int) {
        plusAssign(-delta)
    }

    operator fun dec(): CountEntry<K> {
        plusAssign(-1)
        return this
    }

    override fun toString() = "($key => $value)"

    val value get() = map[key] ?: 0
}

/** Gets something that when incremented or decremented, affects the mutable map. */
fun <K> MutableMap<K, Int>.countFor(key: K) = CountEntry(this, key)
