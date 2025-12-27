package lang.temper.common

/**
 * Wraps a (possibly mutable) collection to prevent casting to *MutableCollection*
 * like `Collections.unmodifiable*`
 */
fun <T> (Collection<T>).unmodifiableView(): Collection<T> = UnmodifiableCollectionWrapper(this)

private fun <T> (Iterator<T>).unmodifiableView(): Iterator<T> =
    UnmodifiableIteratorWrapper(this)

private open class UnmodifiableCollectionWrapper<T>(
    val underlying: Collection<T>,
) : Collection<T> by underlying {
    override fun iterator(): Iterator<T> = underlying.iterator().unmodifiableView()

    override fun toString(): String = underlying.toString()

    override fun equals(other: Any?): Boolean = underlying == other

    override fun hashCode(): Int = underlying.hashCode()
}

private class UnmodifiableIteratorWrapper<T>(
    val underlying: Iterator<T>,
) : Iterator<T> by underlying
