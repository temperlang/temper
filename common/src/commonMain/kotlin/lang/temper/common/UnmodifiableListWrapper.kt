package lang.temper.common

/**
 * Wraps a (possibly mutable) list to prevent casting to *MutableList*
 * like `Collections.unmodifiableList`
 */
fun <T> (List<T>).unmodifiableView(): List<T> = if (this is RandomAccess) {
    UnmodifiableRandomAccessListWrapper(this)
} else {
    UnmodifiableListWrapper(this)
}

private fun <T> (ListIterator<T>).unmodifiableView(): ListIterator<T> =
    UnmodifiableListIteratorWrapper(this)

private open class UnmodifiableListWrapper<T>(val underlying: List<T>) : List<T> by underlying {
    override fun iterator(): Iterator<T> = listIterator()

    override fun listIterator(): ListIterator<T> = listIterator(0)

    override fun listIterator(index: Int): ListIterator<T> =
        underlying.listIterator(index).unmodifiableView()

    override fun subList(fromIndex: Int, toIndex: Int): List<T> =
        underlying.subList(fromIndex, toIndex).unmodifiableView()

    override fun toString(): String = underlying.toString()

    override fun equals(other: Any?): Boolean = underlying == other

    override fun hashCode(): Int = underlying.hashCode()
}

private class UnmodifiableRandomAccessListWrapper<T>(
    underlying: List<T>,
) : UnmodifiableListWrapper<T>(underlying), RandomAccess

private class UnmodifiableListIteratorWrapper<T>(
    val underlying: ListIterator<T>,
) : ListIterator<T> by underlying
