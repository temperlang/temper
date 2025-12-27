package lang.temper.common

/**
 * A min heap.
 */
class MinHeap<T>(private val comparator: Comparator<T>) {
    /** count of used [pq]. */
    private val pq = mutableListOf<T>()

    val size: Int get() = pq.size

    fun isEmpty(): Boolean = pq.isEmpty()

    fun isNotEmpty(): Boolean = pq.isNotEmpty()

    override fun equals(other: Any?): Boolean =
        other is MinHeap<*> && this.comparator == other.comparator && this.pq == other.pq

    override fun hashCode(): Int = pq.hashCode()

    override fun toString() = "$pq"

    operator fun plusAssign(x: T) {
        add(x)
    }

    // The below is based on MinPQ.java from https://algs4.cs.princeton.edu/24pq/
    fun peek() = pq.getOrNull(0)

    fun add(x: T) {
        pq.add(x)
        swim(pq.size)
    }

    fun pop(): T? = when (val n = pq.size) {
        0 -> null
        else -> {
            val min = pq[0]
            exch(1, n)
            pq.removeAt(n - 1)
            sink(1)
            min
        }
    }

    /***************************************************************************
     * Helper functions to restore the heap invariant.
     ***************************************************************************/

    private fun swim(oneIndexed: Int) {
        var k = oneIndexed
        while (k > 1 && greater(k / 2, k)) {
            exch(k, k / 2)
            k = k / 2
        }
    }

    private fun sink(oneIndexed: Int) {
        var k = oneIndexed
        val n = pq.size
        while (2 * k <= n) {
            var j = 2 * k
            if (j < n && greater(j, j + 1)) {
                j++
            }
            if (!greater(k, j)) {
                break
            }
            exch(k, j)
            k = j
        }
    }

    /***************************************************************************
     * Helper functions for compares and swaps.
     ***************************************************************************/
    private fun greater(iOneIndexed: Int, jOneIndexed: Int): Boolean {
        val i = iOneIndexed - 1
        val j = jOneIndexed - 1
        return comparator.compare(pq[i], pq[j]) > 0
    }

    private fun exch(iOneIndexed: Int, jOneIndexed: Int) {
        val i = iOneIndexed - 1
        val j = jOneIndexed - 1
        val swap = pq[i]
        pq[i] = pq[j]
        pq[j] = swap
    }
}
