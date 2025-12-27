package lang.temper.common

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.WeakHashMap

private class MutableWeakMapImpl<K, V> : MutableWeakMap<K, V> {
    private val javaWeakMap = WeakHashMap<K, V>()

    override fun get(key: K): V? = javaWeakMap[key]

    override fun contains(key: K) = javaWeakMap.containsKey(key)

    override fun set(key: K, value: V): V? = javaWeakMap.put(key, value)

    override fun remove(key: K): V? = javaWeakMap.remove(key)

    override fun clear() { javaWeakMap.clear() }

    override fun toString(): String = "$javaWeakMap"
}

actual fun <K, V> mutableWeakMapOf(): MutableWeakMap<K, V> = MutableWeakMapImpl()

private class MutableWeakIdentityMapImpl<K, V> : MutableWeakMap<K, V> {
    // Instead of using a Java weak map, we use a regular map, but with weak references
    // that queue themselves.
    private val javaWeakMap = mutableMapOf<KeyWrapper<K>, V>()

    private val referenceQueue = ReferenceQueue<K>()

    private fun turnUndead() { // Amortize cleanup over access operations below
        while (true) {
            @Suppress("UNCHECKED_CAST")
            val undead = (referenceQueue.poll() ?: break)
                as NonNullKeyWrapper<K>
            javaWeakMap.remove(undead)
        }
    }

    private fun wrap(key: K?): KeyWrapper<K> = if (key == null) {
        // We don't try to weakly reference null
        @Suppress("UNCHECKED_CAST") // Content-less singleton
        NullKeyWrapper as KeyWrapper<K>
    } else {
        NonNullKeyWrapper(key, referenceQueue)
    }

    override fun get(key: K): V? {
        turnUndead()
        return javaWeakMap[wrap(key)]
    }

    override fun contains(key: K): Boolean {
        turnUndead()
        return wrap(key) in javaWeakMap
    }

    override fun set(key: K, value: V): V? {
        turnUndead()
        return javaWeakMap.put(wrap(key), value)
    }

    override fun remove(key: K): V? {
        turnUndead()
        return javaWeakMap.remove(wrap(key))
    }

    override fun clear() {
        turnUndead()
        javaWeakMap.clear()
    }

    override fun toString(): String = "$javaWeakMap"

    private sealed interface KeyWrapper<in K>

    private data object NullKeyWrapper : KeyWrapper<Nothing>

    private class NonNullKeyWrapper<K>(
        key: K,
        referenceQueue: ReferenceQueue<in K>?,
    ) : WeakReference<K>(key, referenceQueue), KeyWrapper<K> {
        // Cached the hash code while we're strongly referencing it
        private val hashCode = System.identityHashCode(key)

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is NonNullKeyWrapper<*> && this.get() === other.get()

        override fun hashCode() = hashCode
    }
}

actual fun <K, V> mutableWeakIdentityMapOf(): MutableWeakMap<K, V> = MutableWeakIdentityMapImpl()
