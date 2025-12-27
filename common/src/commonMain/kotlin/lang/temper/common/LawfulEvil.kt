package lang.temper.common

/**
 * A mutable data structure that obeys promises not to allow mutation
 * to make it easy to separate
 *
 * This allows one agent to delegate to another without possibly getting confused
 * by changes to data structures it assumed the delegate wouldn't alter.
 *
 * Implementing classes should raise [AttemptToMutateWhileStable] on any attempts
 * to mutate while stable.
 */
interface LawfulEvil {

    /**
     * Performs the given action while mutations are prohibited.
     *
     * This prohibition is shallow; just because *x* must be stable, this method
     * does not prevent mutation of a property of a property of *x* while *f* is
     * running.
     *
     * @throws AttemptToMutateWhileStable on changes to stable data structures while
     *     [f] was running.
     */
    fun <T> doWhileStable(f: () -> T): T {
        incrStableReaderCount()
        try {
            return f()
        } finally {
            decrStableReaderCount()
        }
    }

    /**
     * Clients **should not** call this directly; this method is only meant to be
     * called via [doWhileStable].
     *
     * Increments the stable reader count.
     * While the stable reader count is non-zero, `this` should disallow mutation.
     */
    fun incrStableReaderCount()

    /**
     * Clients **should not** call this directly; see [caveats][incrStableReaderCount].
     *
     * Decrements the stable reader count.
     * Do not call this directly; this method is only meant to be called via [doWhileStable].
     */
    fun decrStableReaderCount()
}

/**
 * Keeps lawful evil entities contained (presumably via some kind of summoning circle).
 */
abstract class LawfulEvilOverlord : LawfulEvil {
    /**
     * This must not change between matched calls to increment and decrement
     * the stable reader count, so that the overlord decrements the same entities
     * that it incremented.
     *
     * Implementations are responsible for ensuring no containment cycles.
     */
    protected abstract val minions: List<LawfulEvil>

    override fun decrStableReaderCount() {
        minions.forEach { it.decrStableReaderCount() }
    }

    override fun incrStableReaderCount() {
        minions.forEach { it.incrStableReaderCount() }
    }
}

/**
 * Raised on an attempt to mutate a [LawfulEvil] data structure
 * [while it's stable][LawfulEvil.doWhileStable].
 */
class AttemptToMutateWhileStable(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Performs the given action while mutations are prohibited on all elements, and, if this
 * collection is [LawfulEvil], on itself.
 *
 * @throws AttemptToMutateWhileStable on changes to stable data structures while
 *     [f] was running.
 */
fun <T> Collection<LawfulEvil>.doWhileStable(f: () -> T): T {
    val toDecrement = mutableListOf<LawfulEvil>()
    try {
        if (this is LawfulEvil) {
            this.incrStableReaderCount()
            toDecrement.add(this)
        }
        this.forEach {
            it.incrStableReaderCount()
            toDecrement.add(it)
        }
        return f()
    } finally {
        toDecrement.forEach {
            it.decrStableReaderCount()
        }
    }
}

interface LawfulEvilIterator<T> : MutableIterator<T>, LawfulEvil
interface LawfulEvilListIterator<T> : MutableListIterator<T>, LawfulEvilIterator<T>
interface LawfulEvilIterable<T> : MutableIterable<T>, LawfulEvil {
    override fun iterator(): LawfulEvilIterator<T>
}
interface LawfulEvilCollection<T> : MutableCollection<T>, LawfulEvilIterable<T>
interface LawfulEvilList<T> : MutableList<T>, LawfulEvilCollection<T> {
    override fun subList(fromIndex: Int, toIndex: Int): LawfulEvilList<T>
    override fun iterator(): LawfulEvilListIterator<T> = listIterator(0)
    override fun listIterator(): LawfulEvilListIterator<T> = listIterator(0)
    override fun listIterator(index: Int): LawfulEvilListIterator<T>
}
interface LawfulEvilEntry<K, V> : MutableMap.MutableEntry<K, V>, LawfulEvil
interface LawfulEvilMap<K, V> : MutableMap<K, V>, LawfulEvil {
    override val values: LawfulEvilCollection<V>
    override val entries: LawfulEvilSet<MutableMap.MutableEntry<K, V>>
    override val keys: LawfulEvilSet<K>
}
interface LawfulEvilSet<T> : MutableSet<T>, LawfulEvilCollection<T>

/**
 * A simple implementation of [LawfulEvil] that keeps a count internally.
 * Classes can delegate (`: LawfulEvil by myStableReaderCount`) to an
 * instance that is shared by all views of some underlying data structure.
 */
class StableReaderCount : LawfulEvil {
    private var readers = 0

    val readerCount: Int get() = readers

    override fun incrStableReaderCount() {
        readers += 1
    }

    override fun decrStableReaderCount() {
        if (readers == 0) {
            throw IllegalStateException("underflow")
        }
        readers -= 1
    }

    /** Checks that the stable reader count is zero before calling the given function. */
    inline fun <T> doWhileUnstable(f: () -> T): T = when (val c = readerCount) {
        0 -> f()
        else -> throw AttemptToMutateWhileStable("cannot mutate $this while count=$c")
    }
}

/** A new, empty, lawful evil list. */
fun <T> lawfulEvilListOf(mutationCounter: AtomicCounter? = null): LawfulEvilList<T> =
    LawfulEvilListImpl(mutableListOf(), StableReaderCount(), mutationCounter)

/** A new, empty, lawful evil set. */
fun <T> lawfulEvilSetOf(): LawfulEvilSet<T> =
    LawfulEvilSetImpl(mutableSetOf(), StableReaderCount())

/** A new, empty, lawful evil map. */
fun <K, V> lawfulEvilMapOf(): LawfulEvilMap<K, V> =
    LawfulEvilMapImpl(mutableMapOf(), StableReaderCount())

private class LawfulEvilIteratorImpl<T>(
    private val underlying: MutableIterator<T>,
    private val count: StableReaderCount,
) : LawfulEvilIterator<T>, Iterator<T> by underlying, LawfulEvil by count {
    override fun remove() = count.doWhileUnstable { underlying.remove() }
}

private class LawfulEvilListIteratorImpl<T>(
    private val underlying: MutableListIterator<T>,
    private val count: StableReaderCount,
) : LawfulEvilListIterator<T>, ListIterator<T> by underlying, LawfulEvil by count {
    override fun remove() = count.doWhileUnstable { underlying.remove() }
    override fun add(element: T) = count.doWhileUnstable { underlying.add(element) }
    override fun set(element: T) = count.doWhileUnstable { underlying.set(element) }
}

private class LawfulEvilListImpl<T>(
    private val underlying: MutableList<T>,
    private val count: StableReaderCount,
    private val mutationCounter: AtomicCounter?,
) : LawfulEvilList<T>, List<T> by underlying, LawfulEvil by count {

    override fun iterator(): LawfulEvilListIterator<T> = listIterator(0)

    override fun listIterator(): LawfulEvilListIterator<T> = listIterator(0)

    override fun listIterator(index: Int) =
        LawfulEvilListIteratorImpl(underlying.listIterator(), count)

    override fun subList(fromIndex: Int, toIndex: Int): LawfulEvilList<T> =
        LawfulEvilListImpl(underlying.subList(fromIndex, toIndex), count, mutationCounter)

    override fun add(element: T) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.add(element)
    }

    override fun add(index: Int, element: T) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.add(index, element)
    }

    override fun addAll(index: Int, elements: Collection<T>) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.addAll(index, elements)
    }

    override fun addAll(elements: Collection<T>) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.addAll(elements)
    }

    override fun clear() = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.clear()
    }

    override fun remove(element: T) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.remove(element)
    }

    override fun removeAll(elements: Collection<T>) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.removeAll(elements)
    }

    override fun removeAt(index: Int) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.removeAt(index)
    }

    override fun retainAll(elements: Collection<T>) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.retainAll(elements)
    }

    override fun set(index: Int, element: T) = count.doWhileUnstable {
        mutationCounter?.incrementAndGet()
        underlying.set(index, element)
    }

    override fun hashCode(): Int = underlying.hashCode()
    override fun equals(other: Any?): Boolean = underlying == other
}

private class LawfulEvilMapImpl<K, V>(
    private val underlying: MutableMap<K, V>,
    private val count: StableReaderCount,
) : LawfulEvilMap<K, V>, Map<K, V> by underlying, LawfulEvil by count {

    override val values
        get() = LawfulEvilCollectionImpl(underlying.values, count)

    override val entries
        get() = LawfulEvilEntrySetImpl(underlying.entries, count)

    override val keys
        get() = LawfulEvilSetImpl(underlying.keys, count)

    override fun clear() = count.doWhileUnstable { underlying.clear() }

    override fun put(key: K, value: V) = count.doWhileUnstable { underlying.put(key, value) }

    override fun putAll(from: Map<out K, V>) = count.doWhileUnstable { underlying.putAll(from) }

    override fun remove(key: K) = count.doWhileUnstable { underlying.remove(key) }

    override fun hashCode(): Int = underlying.hashCode()
    override fun equals(other: Any?): Boolean = underlying == other
}

private class LawfulEvilSetImpl<T>(
    private val underlying: MutableSet<T>,
    private val count: StableReaderCount,
) : LawfulEvilSet<T>, Set<T> by underlying, LawfulEvil by count {
    override fun add(element: T) = count.doWhileUnstable { underlying.add(element) }

    override fun addAll(elements: Collection<T>) = count.doWhileUnstable {
        underlying.addAll(elements)
    }

    override fun clear() = count.doWhileUnstable { underlying.clear() }

    override fun remove(element: T) = count.doWhileUnstable { underlying.remove(element) }

    override fun removeAll(elements: Collection<T>) = count.doWhileUnstable {
        underlying.removeAll(elements)
    }

    override fun retainAll(elements: Collection<T>) = count.doWhileUnstable {
        underlying.retainAll(elements)
    }

    override fun iterator() = LawfulEvilIteratorImpl(underlying.iterator(), count)

    override fun hashCode(): Int = underlying.hashCode()
    override fun equals(other: Any?): Boolean = underlying == other
}

private class LawfulEvilCollectionImpl<T>(
    private val underlying: MutableCollection<T>,
    private val count: StableReaderCount,
) : LawfulEvilCollection<T>, Collection<T> by underlying, LawfulEvil by count {
    override fun add(element: T) = count.doWhileUnstable { underlying.add(element) }

    override fun addAll(elements: Collection<T>) = count.doWhileUnstable {
        underlying.addAll(elements)
    }

    override fun clear() = count.doWhileUnstable { underlying.clear() }

    override fun remove(element: T) = count.doWhileUnstable { underlying.remove(element) }

    override fun removeAll(elements: Collection<T>) = count.doWhileUnstable {
        underlying.removeAll(elements)
    }

    override fun retainAll(elements: Collection<T>) = count.doWhileUnstable {
        underlying.retainAll(elements)
    }

    override fun iterator() = LawfulEvilIteratorImpl(underlying.iterator(), count)

    override fun hashCode(): Int = underlying.hashCode()
    override fun equals(other: Any?): Boolean = underlying == other
}

private class LawfulEvilEntrySetImpl<K, V>(
    private val entries: MutableSet<MutableMap.MutableEntry<K, V>>,
    private val count: StableReaderCount,
) : LawfulEvilSet<MutableMap.MutableEntry<K, V>>,
    LawfulEvil by count {

    override fun add(element: MutableMap.MutableEntry<K, V>) = count.doWhileUnstable {
        entries.add(element)
    }

    override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>) =
        count.doWhileUnstable {
            entries.addAll(elements)
        }

    override fun clear() = count.doWhileUnstable { entries.clear() }

    @Suppress("UNCHECKED_CAST")
    override fun iterator(): LawfulEvilIterator<MutableMap.MutableEntry<K, V>> =
        LawfulEvilEntrySetIteratorImpl(entries.iterator(), count)
            as LawfulEvilIterator<MutableMap.MutableEntry<K, V>>

    override fun remove(element: MutableMap.MutableEntry<K, V>) = count.doWhileUnstable {
        entries.remove(element)
    }

    override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>) =
        count.doWhileUnstable {
            entries.removeAll(elements)
        }

    override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>) =
        count.doWhileUnstable {
            entries.retainAll(elements)
        }

    override val size: Int = entries.size

    override fun contains(element: MutableMap.MutableEntry<K, V>) = when (element) {
        is LawfulEvilEntryImpl<K, V> -> entries.contains(element.underlying)
        else -> false
    }

    override fun containsAll(elements: Collection<MutableMap.MutableEntry<K, V>>) =
        elements.all { it in this@LawfulEvilEntrySetImpl }

    override fun isEmpty() = entries.isEmpty()

    override fun hashCode(): Int = entries.hashCode()
    override fun equals(other: Any?): Boolean = entries == other
}

private class LawfulEvilEntryImpl<K, V>(
    val underlying: MutableMap.MutableEntry<K, V>,
    private val count: StableReaderCount,
) : LawfulEvilEntry<K, V>, Map.Entry<K, V> by underlying, LawfulEvil by count {
    override fun setValue(newValue: V): V =
        count.doWhileUnstable { underlying.setValue(newValue) }

    override fun hashCode(): Int = underlying.hashCode()
    override fun equals(other: Any?): Boolean = underlying == other
}

private class LawfulEvilEntrySetIteratorImpl<K, V>(
    private val underlying: MutableIterator<MutableMap.MutableEntry<K, V>>,
    private val count: StableReaderCount,
) : LawfulEvilIterator<LawfulEvilEntry<K, V>>, LawfulEvil by count {
    override fun hasNext() = underlying.hasNext()

    override fun next() = LawfulEvilEntryImpl(underlying.next(), count)

    override fun remove() = count.doWhileUnstable { underlying.remove() }
}
