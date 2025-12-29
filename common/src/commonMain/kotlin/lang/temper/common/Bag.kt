package lang.temper.common

/**
 * Set-like but allows duplicates.
 * Iteration produces pairs of the element and counts.
 * Iteration order is insertion order but where like elements are grouped together.
 *
 * It's possible to have negative counts which count against [size], and a bag
 * with a negative number of items [is not empty][Bag.isNotEmpty].
 */
sealed class Bag<out E> : Iterable<Bag.Entry<E>> {
    /** A bag entry bundles an element with a count. */
    sealed class Entry<out E> {
        abstract val key: E
        abstract val count: Int

        operator fun component1() = key
        operator fun component2() = count

        // This implementation of hashCode is necessary for
        // equal BagImpls and MutableBagImpls to have equal hashCodes.
        final override fun hashCode(): Int = count

        final override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Entry<*> && count == other.count && key == other.key)

        final override fun toString(): String = "($key $TIMES count)"
    }

    /** An alternate iterable method that returns each element multiple times. */
    @Suppress("LeakingThis") // To a private helper class
    val exploded: Iterable<E> = BagExplosion(this)

    /** The [count][Entry.count] for the entry with the given [key]. */
    abstract operator fun get(key: Any?): Int

    operator fun contains(key: Any?) = get(key) != 0

    /** The sum of [counts][Entry.count]. */
    abstract val size: Int

    /**
     * True if there are no non-zero count entries.
     * When counts are non-negative this is equivalent to [size] being 0.
     */
    abstract fun isEmpty(): Boolean

    override fun toString(): String = buildString {
        append('[')
        this@Bag.forEachIndexed { index, (key, count) ->
            if (index != 0) { append(", ") }
            append('(')
            append(key)
            append(" $TIMES ")
            append(count)
            append(')')
        }
        append(']')
    }
}

fun <E> Bag<E>.toMap(): Map<E, Int> = buildMap {
    for ((key, count) in this@toMap) {
        if (count != 0) { put(key, count) }
    }
}
fun <E> Bag<E>.isNotEmpty(): Boolean = !isEmpty()

sealed class MutableBag<E> : Bag<E>() {
    abstract override fun iterator(): Iterator<MutableEntry<E>>

    abstract operator fun set(key: E, count: Int)

    sealed class MutableEntry<E> : Entry<E>() {
        abstract override var count: Int

        operator fun plusAssign(countDelta: Int) {
            count += countDelta
        }
    }
}

fun <E> Bag<E>.toBag(): Bag<E> = if (isEmpty()) {
    bagOf()
} else {
    when (this) {
        is BagImpl<E> -> this
        is MutableBagImpl<E> -> this.toImmutableBag()
    }
}

/**
 * Allows building a bag by mutating a `this` value that is
 * a mutable bag.
 *
 * @param <E> sometimes explicitly specify it or kotlinc dies horribly with
 * `e: Could not load module <Error module>`
 * because internally it infers an Error type for the type argument.
 */
fun <E> buildBag(block: (MutableBag<E>).() -> Unit): Bag<E> {
    val mutableBag = MutableBagImpl<E>()
    block.invoke(mutableBag)
    return mutableBag.toImmutableBag()
}

fun <E> bagOf(): Bag<E> = BagImpl.empty
fun <E> bagOf(vararg counts: Pair<E, Int>): Bag<E> {
    var size = 0
    val map = mutableMapOf<E, Int>()
    for ((key, count) in counts) {
        if (count != 0) {
            size += count
        }
        val totalCount = (map.remove(key) ?: 0) + count
        if (totalCount != 0) {
            map[key] = totalCount
        }
    }
    return if (map.isEmpty()) {
        bagOf()
    } else {
        BagImpl(map.mapValues { BagImpl.EntryImpl(it.key, it.value) }, size)
    }
}

fun <E> mutableBagOf(): MutableBag<E> = MutableBagImpl()
fun <E> mutableBagOf(vararg counts: Pair<E, Int>): MutableBag<E> {
    val mutableBag = MutableBagImpl<E>()
    for ((key, count) in counts) {
        mutableBag[key] = mutableBag[key] + count
    }
    return mutableBag
}

private class BagImpl<E>(
    val map: Map<E, EntryImpl<E>>,
    override val size: Int,
) : Bag<E>() {
    override fun get(key: Any?): Int = map[key]?.count ?: 0

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun iterator(): Iterator<Entry<E>> = map.values.iterator()

    class EntryImpl<E>(
        override val key: E,
        override val count: Int,
    ) : Entry<E>()

    override fun equals(other: Any?): Boolean {
        if (other !is Bag<*>) { return false }
        return when (other) {
            is BagImpl<*> ->
                this === other ||
                    (this.size == other.size && this.map == other.map)
            // This is immutable so safer to let MutableBagImpl poke
            // at BagImpl's internals.
            is MutableBagImpl<*> -> other == this
        }
    }

    // This is consistent with MutableBagImpl's hashCode because
    // EntryImpl's hashCode is count which is the value stored in
    // MutableBagImpl's map.
    override fun hashCode(): Int = map.hashCode()

    companion object {
        val empty = BagImpl<Nothing>(emptyMap(), 0)
    }
}

private class MutableBagImpl<E> : MutableBag<E>() {
    private val map = mutableMapOf<E, Int>()
    override var size = 0
        private set

    override fun iterator(): Iterator<MutableEntry<E>> = EntryIterator()

    override fun set(key: E, count: Int) {
        if (count != 0) {
            val oldCount = map[key] ?: 0
            map[key] = count
            size += count - oldCount
        } else {
            val oldCount = map.remove(key)
            if (oldCount != null) {
                size -= oldCount
            }
        }
    }

    override fun get(key: Any?): Int = map[key] ?: 0

    override fun isEmpty(): Boolean = map.isEmpty()

    class MutableEntryImpl<E>(
        override val key: E,
        private val bag: MutableBagImpl<E>,
    ) : MutableEntry<E>() {
        override var count: Int
            get() = bag[key]
            set(value) {
                bag[key] = value
            }
    }

    fun toImmutableBag(): Bag<E> {
        var size = 0
        val entryMap = buildMap {
            map.forEach {
                val e = BagImpl.EntryImpl(it.key, it.value)
                // Use e as authoritative to preserve BagImpl invariants in
                // the face of concurrency oddness.
                size += e.count
                if (e.count != 0) {
                    put(e.key, e)
                }
            }
        }
        return BagImpl(entryMap, size)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Bag<*> || this.size != other.size) { return false }
        when (other) {
            is BagImpl<*> -> {
                val countMap: Map<*, Int> = this.map
                val entryMap: Map<*, BagImpl.EntryImpl<*>> = other.map
                if (countMap.size != entryMap.size) { return false } // Same keys
                for ((key, count) in countMap) {
                    val entry = entryMap[key]
                    if (count != entry?.count) { return false }
                }
                return true
            }
            is MutableBagImpl<*> -> return this.map == other.map
        }
    }

    override fun hashCode(): Int = map.hashCode()

    @Suppress("IteratorNotThrowingNoSuchElementException") // keys.next() does
    inner class EntryIterator : Iterator<MutableEntry<E>> {
        private val keys = map.keys.toSet().iterator()

        override fun hasNext(): Boolean = keys.hasNext()

        override fun next(): MutableEntry<E> =
            MutableEntryImpl(keys.next(), this@MutableBagImpl)
    }
}

const val TIMES = "\u00D7" // Used in toString() output

private data class BagExplosion<E>(val bag: Bag<E>) : Iterable<E> {
    override fun iterator(): Iterator<E> = object : Iterator<E> {
        private val entries = bag.iterator()
        private var entry: Bag.Entry<E>? = null
        private var countProduced = 0

        override fun next(): E {
            val entry = nextEntry() ?: throw NoSuchElementException()
            countProduced += 1
            return entry.key
        }

        override fun hasNext(): Boolean = nextEntry() != null

        private fun nextEntry(): Bag.Entry<E>? {
            while (true) {
                val entry = this.entry
                if (entry != null && countProduced < entry.count) { return entry }
                if (entries.hasNext()) {
                    countProduced = 0
                    this.entry = entries.next()
                } else {
                    return null
                }
            }
        }
    }
}
