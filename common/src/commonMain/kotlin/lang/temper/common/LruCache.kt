package lang.temper.common

fun <K, V> lruCacheWithSize(maxSize: Int = 1024): MutableMap<K, V> {
    require(maxSize > 2)
    return LruCache(maxSize)
}

private class LruCache<K, V>(private val maxSize: Int) : MutableMap<K, V> {
    private val m = mutableMapOf<K, LruEntry>()
    private var first: LruEntry? = null
    private var last: LruEntry? = null

    private fun moveToEnd(e: LruEntry) {
        if (last !== e) {
            val oldLast = last!!
            val oldPrev = e.prev
            val oldNext = e.next
            if (oldPrev != null) {
                oldPrev.next = oldNext
            } else {
                first = oldNext
            }
            if (oldNext != null) {
                oldNext.prev = oldPrev
            } else {
                last = oldPrev
            }
            e.next = null
            e.prev = oldLast
            last = e
            if (first == null) {
                first = e
            }
        }
    }

    private fun maybeEvict() {
        if (m.size > maxSize) {
            val oldFirst = first!!
            val newFirst = oldFirst.next
            first = newFirst
            if (newFirst == null) {
                last = null
            } else {
                newFirst.prev = null
            }
            oldFirst.next = null
            m.remove(oldFirst.key)
        }
    }

    override fun get(key: K): V? {
        val entry = m[key] ?: return null
        moveToEnd(entry)
        return entry.value
    }

    override val size: Int get() = m.size

    override fun put(key: K, value: V): V? {
        var entry = m[key]
        return if (entry == null) {
            entry = LruEntry(key, value)
            entry.prev = last
            val oldLast = last
            if (oldLast == null) {
                first = entry
            } else {
                oldLast.next = entry
            }
            last = entry
            m[key] = entry
            maybeEvict()
            null
        } else {
            moveToEnd(entry)
            val oldValue = entry.value
            entry.value = value
            oldValue
        }
    }

    override fun remove(key: K): V? {
        val entry = m[key] ?: return null
        val oldPrev = entry.prev
        val oldNext = entry.next
        if (oldPrev == null) {
            first = oldNext
        } else {
            oldPrev.next = oldNext
        }
        if (oldNext == null) {
            last = oldPrev
        } else {
            oldNext.prev = oldPrev
        }
        entry.next = null
        entry.prev = null
        m.remove(key)
        return entry.value
    }

    override fun clear() {
        m.clear()
        // We need to null out next/prev pointers so that entry.attached is consistent
        while (true) {
            val oldFirst = first ?: break
            first = oldFirst.next
            oldFirst.prev = null
            oldFirst.next = null
        }
        last = null
    }

    override fun containsKey(key: K): Boolean = m.containsKey(key)

    override fun containsValue(value: V): Boolean {
        for (entry in m.values) {
            if (entry.value == value) {
                return true
            }
        }
        return false
    }

    override fun isEmpty(): Boolean = m.isEmpty()

    override fun putAll(from: Map<out K, V>) {
        for (e in from.entries) {
            this[e.key] = e.value
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = LruEntrySet()

    override val keys: MutableSet<K> get() = LruKeySet()

    override val values: MutableCollection<V> get() = LruValues()

    private inner class LruEntry(
        override val key: K,
        override var value: V,
    ) : MutableMap.MutableEntry<K, V> {
        var prev: LruCache<K, V>.LruEntry? = null
        var next: LruCache<K, V>.LruEntry? = null

        override fun setValue(newValue: V): V {
            val oldValue = this.value
            this@LruCache[key] = newValue
            return oldValue
        }

        val attached get() = prev != null || next != null || first === this || last === this

        val owner get() = this@LruCache

        override fun toString() = "($key, $value)"
    }

    private inner class LruEntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int get() = m.size
        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            if (element is LruEntry && element.owner === this@LruCache && element.attached) {
                return false
            }
            this@LruCache[element.key] = element.value
            return true
        }

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            if (element !is LruEntry || element.owner !== this@LruCache || !element.attached) {
                return false
            }
            this@LruCache.remove(element.key)
            return true
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = LruEntryIterator()
    }

    private inner class LruKeySet : AbstractMutableSet<K>() {
        override val size: Int get() = m.size
        override fun add(element: K): Boolean {
            throw UnsupportedOperationException()
        }

        override fun remove(element: K): Boolean {
            if (element !in m) { return false }
            this@LruCache.remove(element)
            return true
        }

        override fun iterator(): MutableIterator<K> = LruKeyIterator()
    }

    private inner class LruValues : AbstractMutableCollection<V>() {
        override val size: Int get() = m.size

        override fun contains(element: V): Boolean = containsValue(element)

        override fun iterator(): MutableIterator<V> = LruValueIterator()

        override fun add(element: V): Boolean {
            throw UnsupportedOperationException()
        }
    }

    private abstract inner class AbstractLruIterator<O> : MutableIterator<O> {
        private val it = m.iterator()
        private var last: LruCache<K, V>.LruEntry? = null

        override fun hasNext(): Boolean = it.hasNext()

        override fun next(): O {
            val e = it.next().value
            last = e
            return adapt(e)
        }

        override fun remove() {
            val key = (last ?: throw IllegalStateException("Nothing to remove")).key
            last = null
            this@LruCache.remove(key)
        }

        abstract fun adapt(e: LruEntry): O
    }

    private inner class LruKeyIterator : AbstractLruIterator<K>() {
        override fun adapt(e: LruEntry) = e.key
    }

    private inner class LruValueIterator : AbstractLruIterator<V>() {
        override fun adapt(e: LruEntry) = e.value
    }

    private inner class LruEntryIterator : AbstractLruIterator<MutableMap.MutableEntry<K, V>>() {
        override fun adapt(e: LruEntry) = e
    }
}
