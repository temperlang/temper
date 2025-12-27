package lang.temper.common

import java.util.IdentityHashMap

actual fun <K : Any, V> mutableIdentityMapOf() = IdentityHashMap<K, V>() as MutableMap<K, V>
actual fun <V : Any> mutableIdentitySetOf(): MutableSet<V> = MutableIdentitySet()

fun <K : Any, V> IdentityHashMap<K, V>.getOrPut(key: K, computeValue: () -> V): V {
    val value = this[key]
    if (value == null && key !in this) {
        val computed = computeValue()
        this[key] = computed
        return computed
    }
    @Suppress("UNCHECKED_CAST") // By contract, value == null && key in this -> V is nullable
    return value as V
}

private class MutableIdentitySet<V : Any> : AbstractMutableSet<V>() {
    private val identityMap = mutableIdentityMapOf<V, Boolean>()

    override val size: Int get() = identityMap.size

    override fun add(element: V): Boolean {
        val had = identityMap.containsKey(element)
        identityMap[element] = true
        return !had
    }

    override fun addAll(elements: Collection<V>): Boolean {
        val sizeBefore = size
        for (element in elements) {
            add(element)
        }
        return size != sizeBefore
    }

    override fun remove(element: V): Boolean {
        return identityMap.remove(element) == true
    }

    override fun clear() {
        identityMap.clear()
    }

    override fun contains(element: V): Boolean = identityMap.containsKey(element)

    override fun iterator(): MutableIterator<V> = IdentitySetIterator()

    private inner class IdentitySetIterator : MutableIterator<V> {
        private var hasLast = false
        private var last: V? = null
        private var keyIterator = identityMap.keys.iterator()

        override fun hasNext(): Boolean = keyIterator.hasNext()

        override fun next(): V {
            val element = keyIterator.next()
            this.last = element
            this.hasLast = true
            return element
        }

        override fun remove() {
            require(hasLast)
            val element = last ?: throw NoSuchElementException()
            hasLast = false
            last = null
            this@MutableIdentitySet.remove(element)
        }
    }
}
