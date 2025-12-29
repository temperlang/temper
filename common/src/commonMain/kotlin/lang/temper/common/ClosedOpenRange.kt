package lang.temper.common

/**
 * Like [ClosedRange], but this is easier to implement for hard to enumerate values.
 */
interface ClosedOpenRange<T : Comparable<T>> {
    /**
     * The minimum value in the range.
     */
    val start: T

    /**
     * The maximum value in the range (exclusive).
     */
    val endExclusive: T

    /**
     * Checks whether the specified [value] belongs to the range.
     */
    operator fun contains(value: T): Boolean = value >= start && value < endExclusive

    /**
     * Checks whether the range is empty.
     *
     * The range is empty if its start value is greater than or equal to the end value.
     */
    fun isEmpty(): Boolean = start >= endExclusive
}

data class SimpleCoRange<T : Comparable<T>>(
    override val start: T,
    override val endExclusive: T,
) : ClosedOpenRange<T>
