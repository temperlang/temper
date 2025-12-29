package lang.temper.common

/** A range over instances of the same enum class ordered by [Enum.ordinal]. */
class EnumRange<E : Enum<E>>(
    override val start: E,
    override val endInclusive: E,
) : ClosedRange<E> {
    override fun toString(): String = when {
        start == endInclusive && endInclusive == start -> "[$start]"
        else -> "[$start..$endInclusive]"
    }
}

inline fun <reified E : Enum<E>> (EnumRange<E>).asSequence(): Sequence<E> {
    return EnumRangeSequence(enumValues<E>(), start, endInclusive)
}

inline fun <reified E : Enum<E>> (E).preceder() = when (val ordinal = this.ordinal) {
    0 -> null
    else -> enumValues<E>()[ordinal - 1]
}

fun <T : Comparable<T>> max(a: T, b: T): T = if (a >= b) a else b
fun <T : Comparable<T>> min(a: T, b: T): T = if (a <= b) a else b

class EnumRangeSequence<E : Enum<E>>(
    private val values: Array<E>,
    private val start: E,
    private val endInclusive: E,
) : Sequence<E> {
    override fun iterator() = object : Iterator<E> {
        private var i = start.ordinal
        private val limit = endInclusive.ordinal

        override fun hasNext(): Boolean = i <= limit
        override fun next(): E {
            if (i > limit) throw NoSuchElementException()
            val e = values[i]
            i += 1
            return e
        }
    }
}
