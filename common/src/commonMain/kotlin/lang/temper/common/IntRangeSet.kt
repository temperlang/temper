package lang.temper.common

import kotlin.math.max
import kotlin.math.min

operator fun IntRangeSet.plus(that: IntRangeSet): IntRangeSet =
    IntRangeSet.union(this, that)

operator fun IntRangeSet.plus(that: IntRange): IntRangeSet =
    IntRangeSet.union(this, IntRangeSet.new(that))

operator fun IntRangeSet.minus(minuend: IntRangeSet): IntRangeSet =
    IntRangeSet.difference(this, minuend)

operator fun IntRangeSet.minus(other: IntRange): IntRangeSet =
    IntRangeSet.difference(this, IntRangeSet.new(other))

/**
 * A set of integers which is efficient when (i in set) is a good predictor of (i+1 in set).
 */
class IntRangeSet private constructor(private val bounds: IntArray) : Iterable<IntRange> {

    fun containsAll(r: IntRange): Boolean {
        val leftMatch = binarySearch(bounds, r.first)
        return if (leftMatch >= 0) {
            // If the left of r falls on an exclusive right endpoint then we do not contain r.first
            ((leftMatch and 1) == 0) &&
                // If r.last exceeds the corresponding right then there's something left out.
                // We check `>` not `>=` since the value in bounds is exclusive while r.last is
                // inclusive.
                bounds[leftMatch + 1] > r.last
        } else {
            val leftInsPt = leftMatch.inv()
            if (leftInsPt >= bounds.size) {
                // r.left exceeds the greatest value in this set
                false
            } else if ((leftInsPt and 1) == 0) {
                // r.left is between the end of one range and the start of another.
                false
            } else {
                // r.left starts after the start of a range in this.
                r.last < bounds[leftInsPt]
            }
        }
    }

    operator fun contains(x: Int): Boolean {
        return (binarySearch(bounds, x) and 1) == 0
        // If the match result is positive and even, then we found an inclusive left endpoint.
        // If the match result is negative and even, then the insertion point is odd so x falls
        // before an exclusive endpoint so much be greater than the corresponding left and less than
        // the exclusive right so is included in the match.
    }

    val min get() = bounds.firstOrNull()

    /** Inclusive upper bound if any. */
    val max get() = bounds.lastOrNull()?.let { it - 1 }

    override fun iterator(): Iterator<IntRange> = IntRangeIterator(bounds)

    fun isEmpty() = bounds.isEmpty()

    fun isNotEmpty() = !isEmpty()

    override fun equals(other: Any?): Boolean =
        other is IntRangeSet && this.bounds.contentEquals(other.bounds)

    override fun hashCode(): Int = bounds.contentHashCode()

    override fun toString(): String {
        if (bounds.isEmpty()) {
            return "()"
        }
        val sb = StringBuilder()
        sb.append('(')
        var i = 0
        val n = bounds.size
        while (i < n) {
            if (i != 0) {
                sb.append('∪')
            }
            val left = bounds[i]
            val right = bounds[i + 1]
            sb.append('[').append(left)
            if (left + 1 < right) {
                sb.append('-')
                sb.append(right - 1)
            }
            sb.append(']')
            i += 2
        }
        sb.append(')')
        return sb.toString()
    }

    companion object {
        val empty = IntRangeSet(emptyIntArray)

        /**
         * Constructs a range set from an array where even elements are inclusive range starts
         * and odd elements are exclusive ends for the range started by the preceding element.
         *
         * The input as a whole must be strictly monotonically increasing and even in length.
         */
        fun new(sortedUniqEvenLengthArray: IntArray): IntRangeSet {
            val copy = sortedUniqEvenLengthArray.copyOf()
            require((copy.size and 1) == 0)
            for (i in 0 until copy.size - 1) {
                require(copy[i] < copy[i + 1])
            }
            return IntRangeSet(copy)
        }

        fun new(range: IntRange) =
            if (range.isEmpty()) {
                empty
            } else {
                val array = IntArray(2)
                array[0] = range.first
                array[1] = range.last + 1
                IntRangeSet(array)
            }

        fun union(a: IntRangeSet, b: IntRangeSet): IntRangeSet = when {
            a.isEmpty() -> b
            b.isEmpty() -> a
            else -> union(listOf(a, b))
        }

        fun unionRanges(ranges: Iterable<IntRange>): IntRangeSet {
            val rangesSorted = ranges.sortedWith(IntRangeComparator)
            val bounds = mutableListOf<Int>()
            for (r in rangesSorted) {
                if (r.isEmpty()) {
                    continue
                }
                unionOnto(bounds, r.first, r.last + 1)
            }
            return fromMonotonicInts(bounds)
        }

        fun union(sets: Iterable<IntRangeSet>): IntRangeSet {
            val pq = MinHeap(CompareRangeAt)
            sets.forEach {
                if (it.isNotEmpty()) {
                    pq.add(it to 0)
                }
            }
            val bounds = mutableListOf<Int>()
            while (true) {
                val (s, i) = pq.pop() ?: break
                unionOnto(bounds, s.bounds[i], s.bounds[i + 1])
                val next = i + 2
                if (next < s.bounds.size) {
                    pq.add(s to next)
                }
            }
            return fromMonotonicInts(bounds)
        }

        private fun unionOnto(bounds: MutableList<Int>, first: Int, lastExcl: Int) {
            val lastIndex = bounds.size - 1
            if (lastIndex < 0) {
                bounds.add(first)
                bounds.add(lastExcl)
            } else {
                val lastBound = bounds[lastIndex]
                if (lastBound >= first) {
                    bounds[lastIndex] = max(lastBound, lastExcl)
                } else {
                    bounds.add(first)
                    bounds.add(lastExcl)
                }
            }
        }

        private fun fromMonotonicInts(bounds: List<Int>) = IntRangeSet(
            IntArray(bounds.size) {
                bounds[it]
            },
        )

        fun difference(a: IntRangeSet, b: IntRangeSet): IntRangeSet {
            val aBounds = a.bounds
            val bBounds = b.bounds
            val deltaBounds = mutableListOf<Int>()

            // Keep two indices into the two arrays.
            // Each loop iteration advances one and may raise min.
            var i = 0
            var j = 0
            val m = aBounds.size
            val n = bBounds.size
            var min = Int.MIN_VALUE
            while (i < m && j < n) {
                val aFirst = max(min, aBounds[i])
                val aLast = aBounds[i + 1] - 1
                if (aLast < aFirst) {
                    i += 2
                    continue
                }

                val bFirst = bBounds[j]
                val bLast = bBounds[j + 1] - 1

                // Skip deleted section if it doesn't overlap.
                if (bLast < aFirst) {
                    j += 2
                    continue
                }
                // Add a block if it overlaps nothing removed.
                if (aLast < bFirst) {
                    deltaBounds.add(aFirst)
                    deltaBounds.add(aLast + 1)
                    i += 2
                    continue
                }
                // Skip added section if the whole is deleted.
                if (bFirst <= aFirst && bLast >= aLast) {
                    i += 2
                    continue
                }
                // Something to add.
                if (aFirst < bFirst) {
                    deltaBounds.add(aFirst)
                    deltaBounds.add(min(bFirst, aLast + 1))
                }
                min = bLast + 1
                j += 2
            }
            while (i < m) {
                val aFirst = max(min, aBounds[i])
                val aLast = aBounds[i + 1] - 1
                if (aLast >= aFirst) {
                    deltaBounds.add(aFirst)
                    deltaBounds.add(aLast + 1)
                }
                i += 2
            }
            return fromMonotonicInts(deltaBounds)
        }

        fun intersection(a: IntRangeSet, b: IntRangeSet): IntRangeSet {
            val aBounds = a.bounds
            val bBounds = b.bounds
            val commonBounds = mutableListOf<Int>()

            // Keep two indices into the two arrays.
            // Each loop iteration advances one.
            var i = 0
            var j = 0
            val m = aBounds.size
            val n = bBounds.size
            while (i < m && j < n) {
                val aFirst = aBounds[i]
                val aLast = aBounds[i + 1] - 1
                val bFirst = bBounds[j]
                val bLast = bBounds[j + 1] - 1

                if (aLast < bFirst) {
                    i += 2
                } else if (bLast < aFirst) {
                    j += 2
                } else {
                    val left = max(aFirst, bFirst)
                    val right = min(aLast, bLast) + 1
                    if (left < right) {
                        commonBounds.add(left)
                        commonBounds.add(right)
                    }
                    if (aLast < bLast) {
                        i += 2
                    } else {
                        j += 2
                    }
                }
            }
            return fromMonotonicInts(commonBounds)
        }

        private object IntRangeComparator : Comparator<IntRange> {
            override fun compare(a: IntRange, b: IntRange) =
                when (val d = a.first.compareTo(b.first)) {
                    0 -> a.last.compareTo(b.last)
                    else -> d
                }
        }

        private object CompareRangeAt : Comparator<Pair<IntRangeSet, Int>> {
            override fun compare(a: Pair<IntRangeSet, Int>, b: Pair<IntRangeSet, Int>): Int {
                val (aSet, ai) = a
                val (bSet, bi) = b
                val aBounds = aSet.bounds
                val bBounds = bSet.bounds
                return when (val d = aBounds[ai].compareTo(bBounds[bi])) {
                    0 -> aBounds[ai + 1].compareTo(bBounds[bi + 1])
                    else -> d
                }
            }
        }
    }
}

private class IntRangeIterator(val bounds: IntArray) : Iterator<IntRange> {
    private var i = 0

    override fun hasNext(): Boolean = i < bounds.size

    override fun next(): IntRange {
        if (i == bounds.size) {
            throw NoSuchElementException()
        }
        val progression = bounds[i] until bounds[i + 1]
        i += 2
        return progression
    }
}
