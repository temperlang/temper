package lang.temper.common

import kotlin.math.sign

/** Like Comparable but allows for partial orders. */
interface PComparable<T> {
    /** Null means incomparable. */
    fun pcompareTo(that: T): PComparison?
}

/** Partial-order [Comparator] analogue. */
interface PComparator<T> {
    fun compare(a: T, b: T): PComparison?

    companion object {
        /** A PComparator that deleagates to the given Comparator. */
        fun <T> of(c: Comparator<T>) = object : PComparator<T> {
            override fun compare(a: T, b: T) = PComparison.from(c.compare(a, b))
        }
    }
}

/** The result of a comparison.  By convention, `null` means don't know. */
enum class PComparison(val ival: Int) {
    /** Indicates the left is less than the right. */
    LESSER(-1),

    /** Indicates the left is equivalent to the right. */
    EQUIVALENT(0),

    /** Indicates the left is greater than the right. */
    GREATER(1),
    ;

    /** [LESSER] -> [GREATER] and vice versa. */
    fun inverse(): PComparison {
        return INVERSES[ival + 1]
    }

    companion object {
        /** [LESSER] -> [GREATER] and vice versa. */
        fun inverse(x: PComparison?): PComparison? = x?.inverse()

        private val INVERSES = arrayOf(GREATER, EQUIVALENT, LESSER)
        private val BY_INDEX = arrayOf(LESSER, EQUIVALENT, GREATER)

        /** Given [x] in (-1, 0, 1) the equivalent value per [Comparable.compareTo]'s contract. */
        fun from(x: Int): PComparison = BY_INDEX[x.sign + 1]

        /** The result of comparing two [Comparable]s per their natural order. */
        fun <T : Comparable<T>> compare(a: T, b: T): PComparison = from(a.compareTo(b))
    }
}
