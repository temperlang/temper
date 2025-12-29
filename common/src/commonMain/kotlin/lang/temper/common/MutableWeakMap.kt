package lang.temper.common

/**
 * A map that keys by identity and which may discard entries when the key is no longer strongly
 * referenced.
 */
interface MutableWeakMap<K, V> {
    /** Prone to race conditions. */
    operator fun contains(key: K): Boolean
    operator fun get(key: K): V?
    operator fun set(key: K, value: V): V?
    fun remove(key: K): V?
    fun clear()
}

/**
 * A new instance of [MutableWeakMap].
 */
expect fun <K, V> mutableWeakMapOf(): MutableWeakMap<K, V>

expect fun <K, V> mutableWeakIdentityMapOf(): MutableWeakMap<K, V>
