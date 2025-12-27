@file:JvmName("KBitSetCommon")

package lang.temper.common

/** A Kotlin common bitset. */
expect class KBitSet() {
    /** Set the given bit. */
    fun set(bit: Int)

    /** Set a closed-open range of bits. */
    operator fun set(start: Int, stop: Int)

    /** Get the given bit. */
    operator fun get(bit: Int): Boolean

    fun nextSetBit(bit: Int): Int
    fun nextClearBit(bit: Int): Int

    fun previousSetBit(bit: Int): Int
    fun previousClearBit(bit: Int): Int

    /** Count of set bits */
    fun cardinality(): Int

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

object KBitSetHelpers {
    operator fun KBitSet.contains(int: Int): Boolean = this[int]
}

/**
 * An iterable for maximal, non-empty ranges that together include all and only
 * set bits in this in left-to-right order.
 *
 * Mutation of the underlying bitset may cause under-specified behaviour from
 * this.
 *
 * For example:
 *
 * - Setting bits earlier than iterated may violate the "include all" invariant.
 * - Clearing bits after checking hasNext but before calling next may violate
 *   the "and only" invariant.
 */
fun KBitSet.rangesOfSetBits(): Iterable<IntRange> = BitSetRanges(this)

val KBitSet.bitIndices: KBitSetIterable get() = KBitSetIterableImpl(this, true)

fun KBitSet.clearBitIndices(range: IntRange): KBitSetIterable = KBitSetIterableImpl(this, false, range)

private class BitSetRanges(
    val bitset: KBitSet,
) : Iterable<IntRange> {
    override fun iterator(): Iterator<IntRange> = object : Iterator<IntRange> {
        private var i = 0
        private var pending: IntRange? = null

        override fun hasNext(): Boolean = findPending() != null
        override fun next(): IntRange {
            val result = findPending() ?: throw NoSuchElementException()
            pending = null
            return result
        }

        private fun findPending(): IntRange? {
            val p = pending
            if (p != null || i < 0) { return p }
            val start = bitset.nextSetBit(i)
            if (start < 0) {
                i = -1
                return null
            }
            val end = bitset.nextClearBit(start)
            pending = start until end
            i = end
            return pending
        }
    }
}

interface KBitSetIterable : Iterable<Int> {
    fun limited(range: IntRange): KBitSetIterable

    fun reverse(): KBitSetIterable

    override fun iterator(): IntIterator
}

private data class KBitSetIterableImpl(
    val bitset: KBitSet,
    val target: Boolean,
    val range: IntRange? = null,
    val reversed: Boolean = false,
) : KBitSetIterable {
    override fun limited(range: IntRange): KBitSetIterable = this.copy(
        range = if (this.range != null) {
            if (this.range.first > range.last || this.range.last < range.first) {
                IntRange.EMPTY
            } else {
                max(range.first, this.range.first)..min(range.last, this.range.last)
            }
        } else {
            range
        },
    )

    override fun reverse(): KBitSetIterable = this.copy(reversed = !reversed)

    override fun iterator(): IntIterator = KBitSetIterator(bitset, target, range, reversed)
}

private class KBitSetIterator(
    val bitset: KBitSet,
    val target: Boolean,
    range: IntRange?,
    val reversed: Boolean = false,
) : IntIterator() {
    private val first = range?.first ?: 0
    private val last = range?.last ?: Int.MAX_VALUE

    /** The next set bit if [needToScan] is true, otherwise the starting point to find the next set bit. */
    private var pos: Int = if (first > last) { -1 } else if (reversed) { last } else { first }

    /** Set to false when a bit is consumed */
    private var needToScan: Boolean = pos >= 0 && bitset[pos] != target

    override fun nextInt(): Int {
        scan()
        if (pos < 0) { throw NoSuchElementException() }
        needToScan = true
        return pos
    }

    override fun hasNext(): Boolean {
        scan()
        return pos >= 0
    }

    private val stateNum = (if (reversed) 1 else 0) or
        ((if (target) 1 else 0) shl 1)
    private fun scan() {
        if (needToScan) {
            needToScan = false
            val next = when (stateNum) {
                0 -> bitset.nextClearBit(pos + 1)
                1 -> bitset.previousClearBit(pos - 1)
                2 -> bitset.nextSetBit(pos + 1)
                else -> bitset.previousSetBit(pos - 1)
            }
            pos = if (first <= next && next <= last) {
                next
            } else {
                -1
            }
        }
    }
}
