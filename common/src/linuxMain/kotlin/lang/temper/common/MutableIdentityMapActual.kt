package lang.temper.common

private class MutableIdentityMap<K : Any, V> : AbstractMutableMap<K, V>() {
    private val map = mutableMapOf<K, MutableList<MutableIdentityEntry<K, V>>>()

    override var size: Int = 0
        private set

    override fun put(key: K, value: V): V? {
        val entries = map.getOrPut(key) { mutableListOf() }
        for (entry in entries) {
            if (entry.key === key) {
                val old = entry.value
                entry.value = value
                return old
            }
        }
        entries.add(MutableIdentityEntry(key, value, this))
        size += 1
        return null
    }

    override fun get(key: K): V? {
        val entries: List<MutableIdentityEntry<K, V>> = map[key] ?: return null
        for (entry in entries) {
            if (entry.key === key) {
                return entry.value
            }
        }
        return null
    }

    override fun remove(key: K): V? {
        val entries: MutableList<MutableIdentityEntry<K, V>> = map[key] ?: return null
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.key === key) {
                val last = entries.size - 1
                entries[i] = entries[last]
                entries.removeAt(last)
                size -= 1
                return entry.value
            }
        }
        return null
    }

    override fun clear() {
        map.clear()
        size = 0
    }

    override fun containsKey(key: K): Boolean {
        val entries: List<MutableIdentityEntry<K, V>> = map[key] ?: return false
        for (entry in entries) {
            if (entry.key === key) {
                return true
            }
        }
        return false
    }

    override val entries = MutableIdentityEntries()

    private inner class MutableIdentityEntries :
        AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int get() = map.size

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                private val keyIterator: Iterator<K> = map.keys.iterator()
                private var entryIterator: Iterator<MutableIdentityEntry<K, V>>? = null
                private var current: MutableIdentityEntry<K, V>? = null

                override fun hasNext(): Boolean {
                    while (entryIterator?.hasNext() != true) {
                        entryIterator = null
                        if (!keyIterator.hasNext()) {
                            return false
                        }
                        entryIterator = map[keyIterator.next()]?.iterator()
                    }
                    return true
                }

                override fun next(): MutableMap.MutableEntry<K, V> {
                    if (!hasNext()) {
                        throw NoSuchElementException("Iterator is done")
                    }
                    val entry = entryIterator!!.next()
                    this.current = entry
                    return entry
                }

                override fun remove() {
                    val entry = current ?: throw IllegalStateException("No item to remove")
                    map.remove(entry.key)
                    current = null
                }
            }
        }

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            val key = element.key
            val value = element.value
            val old = put(key, value)
            return old != value
        }
    }
}

private class MutableIdentityEntry<K : Any, V>(
    override val key: K,
    override var value: V,
    val backingMap: MutableIdentityMap<K, V>
) : MutableMap.MutableEntry<K, V> {
    override fun setValue(newValue: V): V {
        val old = value
        this.value = newValue
        return old
    }

    override fun equals(other: Any?) =
        other is MutableIdentityEntry<*, *> &&
                this.backingMap === other.backingMap &&
                this.key === other.key

    override fun hashCode() = key.hashCode()

    operator fun component1() = key
    operator fun component2() = value
}

actual fun <K : Any, V> mutableIdentityMapOf(): MutableMap<K, V> = MutableIdentityMap()
actual fun <V : Any> mutableIdentitySetOf(): MutableSet<V> = MutableIdentitySet()

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
            val element = last!!
            hasLast = false
            last = null
            this@MutableIdentitySet.remove(element)
        }
    }
}
