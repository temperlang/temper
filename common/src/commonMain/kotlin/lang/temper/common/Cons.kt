package lang.temper.common

/**
 * An immutable Cons list.
 * Good for representing rarely changed (via reassignment to `var`), often read, cheaply copied lists.
 */
sealed class Cons<out T> : Iterable<T> {
    /** Throws [NoSuchElementException] if `this` `is` [Empty]. */
    abstract val head: T

    /**
     * A list containing all elements excepting [head].
     * The tail of the [Empty] list is itself.
     */
    abstract val tail: Cons<T>

    /** [head] when [NotEmpty], `null` otherwise. */
    abstract val headOrNull: T?

    /** Allow destructuring via `val (head, tail) = myCons`. */
    open operator fun component1() = headOrNull
    operator fun component2() = tail

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var cursor: NotEmpty<T>? = this@Cons as? NotEmpty
        override fun hasNext(): Boolean = cursor != null
        override fun next(): T {
            val (h, t) = cursor ?: throw NoSuchElementException()
            this.cursor = t as? NotEmpty
            return h
        }
    }

    override fun toString(): String = toStringViaBuilder { out ->
        out.append('[')
        for ((i, x) in this.withIndex()) {
            if (i != 0) { out.append(", ") }
            out.append(x)
        }
        out.append(']')
    }

    object Empty : Cons<Nothing>() {
        override val head: Nothing get() { throw NoSuchElementException() }
        override val tail = Empty
        override val headOrNull: Nothing? = null
    }

    class NotEmpty<out T>(
        override val head: T,
        override val tail: Cons<T>,
    ) : Cons<T>() {
        override val headOrNull: T get() = head

        override operator fun component1() = headOrNull

        override fun equals(other: Any?): Boolean =
            other === this ||
                (other is NotEmpty<*> && head == other.head && tail == other.tail)

        override fun hashCode() = (head?.hashCode() ?: 0) + 31 * tail.hashCode()
    }

    companion object {
        // Allow `Cons(h, t)` to work for construction.
        /** Creates a non-empty [Cons] list with the given head and tail. */
        operator fun <T> invoke(head: T, tail: Cons<T> = Empty): NotEmpty<T> =
            NotEmpty(head = head, tail = tail)
    }
}

fun Cons<*>.isEmpty() = this is Cons.Empty
fun Cons<*>.isNotEmpty() = this !is Cons.Empty
fun <T> Cons<T>.filter(f: (T) -> Boolean): Cons<T> = when (this) {
    is Cons.Empty -> Cons.Empty
    is Cons.NotEmpty<*> -> {
        val keepHead = f(head)
        val tailFiltered = tail.filter(f)
        when {
            !keepHead -> tailFiltered
            tail === tailFiltered -> this
            else -> Cons(head, tailFiltered)
        }
    }
}
