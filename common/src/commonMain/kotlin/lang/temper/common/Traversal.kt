package lang.temper.common

/**
 * Construct simple traversals by burning down a Deque.
 *
 * The block should call [ArrayDeque.removeFirst] or [ArrayDeque.removeLast] to ensure [NoSuchElementException] is
 * thrown per the [Iterator] contract.
 *
 * Caveat: this implementation assumes that filtering, if any, is done on the children of nodes passed in; in
 * particular, the first node is expected to produce at least one output. Recommend writing a pre-scanning
 * `notNull` variant if this doesn't suit you.
 */
inline fun <T, U> T.dequeIterator(crossinline nextBlock: (ArrayDeque<T>) -> U): Iterator<U> =
    ArrayDeque(listOf(this)).let { deque ->
        @Suppress("IteratorNotThrowingNoSuchElementException")
        object : Iterator<U> {
            override fun hasNext(): Boolean = deque.isNotEmpty()

            override fun next(): U = nextBlock(deque)
        }
    }

/**
 * Construct a simple iterable object that traverses a graph by burning down a Deque.
 *
 * The block should call [ArrayDeque.removeFirst] or [ArrayDeque.removeLast] to ensure [NoSuchElementException] is
 * thrown per the [Iterator] contract.
 *
 * See [unit tests][lang.temper.common.TraversalTest] for examples.
 *
 * Caveat: this implementation assumes that filtering, if any, is done on the children of nodes passed in; in
 * particular, the first node is expected to produce at least one output. Recommend writing a pre-scanning
 * `notNull` variant if this doesn't suit you.
 */
inline fun <T, U> T.dequeIterable(crossinline nextBlock: (ArrayDeque<T>) -> U): Iterable<U> =
    object : Iterable<U> {
        override fun iterator(): Iterator<U> = this@dequeIterable.dequeIterator(nextBlock)
    }

/** Given a list of iterables, find the first element common to all sequences, or return null. */
fun <T> findFirstInTraversals(walks: List<Iterable<T>>): T? {
    val iterators = walks.map { it.iterator() }
    val allPresent = KBitSet()
    allPresent.set(0, walks.size)
    val best = mutableMapOf<T, KBitSet>()
    var active = true
    while (active) {
        active = false
        for (treeIndex in iterators.indices) {
            val iter = iterators[treeIndex]
            if (iter.hasNext()) {
                active = true
                val elem = iter.next()
                val found = best.getOrPut(elem) { KBitSet() }
                found.set(treeIndex)
                if (found == allPresent) {
                    return elem
                }
            }
        }
    }
    return null
}
