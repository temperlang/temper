package lang.temper.common

private val mapCtor = js("(function () { return new Map })")
private val setCtor = js("(function () { return new Set })")

private class MutableIdentityMap<K : Any, V> : AbstractMutableMap<K, V>() {
    private val jsMap = mapCtor()

    override val size: Int get() = jsMap.size as Int

    override fun put(key: K, value: V): V? {
        val old = jsMap.get(key)
        jsMap.set(key, value)
        return if (old == undefined) {
            null
        } else {
            old as V
        }
    }

    override fun get(key: K): V? = when (val v = jsMap.get(key)) {
        undefined -> null
        else -> v as V
    }

    override fun remove(key: K): V? {
        val old = jsMap.get(key)
        jsMap.delete(key)
        return if (old == undefined) null else old as V
    }

    override fun containsKey(key: K): Boolean = jsMap.has(key) as Boolean

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = MutableIdentityEntries(jsMap)
}

private class MutableIdentityEntries<K : Any, V>(
    val jsMap: dynamic,
) : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
    override val size: Int get() = jsMap.size as Int

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        val jsIterator = jsMap.keys()
        return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
            private var next = jsIterator.next()
            private var current: MutableMap.MutableEntry<K, V>? = null

            override fun hasNext(): Boolean = next.value != undefined

            override fun next(): MutableMap.MutableEntry<K, V> {
                val nextValue = next.value
                if (nextValue == undefined) {
                    throw NoSuchElementException("Iterator is done")
                }
                val key = nextValue as K
                val entry = Entry(key)
                current = entry
                next = jsIterator.next()
                return entry
            }

            override fun remove() {
                val entry = current ?: throw IllegalStateException("No item to remove")
                jsMap.delete(entry.key)
                current = null
            }
        }
    }

    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
        val (key, value) = element
        val old = jsMap.get(key)
        jsMap.set(key, value)
        return old != value
    }

    private inner class Entry(
        override val key: K,
    ) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val old = jsMap.get(key)
            require(old != undefined)
            jsMap.set(key, newValue)
            return old as V
        }

        override val value: V get() = jsMap.get(key) as V

        override fun equals(other: Any?) =
            other is MutableIdentityEntries<*, *>.Entry &&
                this.backingMap() === other.backingMap() &&
                this.key === other.key

        override fun hashCode() = key.hashCode()

        private fun backingMap() = jsMap
    }
}

actual fun <K : Any, V> mutableIdentityMapOf(): MutableMap<K, V> = MutableIdentityMap()

private class MutableIdentitySet<V : Any> : AbstractMutableSet<V>() {
    private val jsSet = setCtor()

    override val size: Int get() = jsSet.size as Int

    override fun add(element: V): Boolean {
        val had = jsSet.has(element) as Boolean
        jsSet.add(element)
        return !had
    }

    override fun contains(element: V): Boolean = jsSet.has(element) as Boolean

    override fun remove(element: V): Boolean = jsSet.delete(element) as Boolean

    override fun clear() {
        jsSet.clear()
    }

    override fun iterator(): MutableIterator<V> = IdentitySetIterator()

    private inner class IdentitySetIterator : MutableIterator<V> {
        private var hasLast = false
        private var last: V? = null
        private val jsIterator = jsSet.values()
        private var pending: dynamic = null

        private fun fetch(): dynamic {
            if (pending == null) {
                pending = jsIterator.next()
                if (pending.value == undefined) {
                    pending = null
                }
            }
            return pending
        }

        override fun hasNext(): Boolean = fetch() != null

        override fun next(): V {
            val entry = fetch() ?: throw NoSuchElementException()
            pending = null

            val element = entry.value as V
            this.last = element
            this.hasLast = true
            return element
        }

        override fun remove() {
            require(hasLast)
            val element = last
            hasLast = false
            last = null
            this@MutableIdentitySet.remove(element)
        }
    }
}

actual fun <V : Any> mutableIdentitySetOf(): MutableSet<V> = MutableIdentitySet()
