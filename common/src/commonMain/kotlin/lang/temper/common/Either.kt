package lang.temper.common

sealed class Either<out T, out U> {
    /** The value if a left.  This is ambiguous if [T] is nullable. */
    abstract val leftOrNull: T?

    /** The value if a left.  This is ambiguous if [T] is nullable. */
    abstract val rightOrNull: U?

    data class Left<T>(val item: T) : Either<T, Nothing>() {
        override val leftOrNull: T get() = item
        override val rightOrNull: Nothing? get() = null
    }

    data class Right<U>(val item: U) : Either<Nothing, U>() {
        override val leftOrNull: Nothing? get() = null
        override val rightOrNull: U get() = item
    }

    companion object {
        fun <T, U> List<Either<T, U>>.partition(): Pair<List<T>, List<U>> {
            val lefts = mutableListOf<T>()
            val rights = mutableListOf<U>()
            this.forEach {
                when (it) {
                    is Left<T> -> lefts.add(it.item)
                    is Right<U> -> rights.add(it.item)
                }
            }
            return lefts to rights
        }
    }
}

/**
 * `foo.nullCoalesce{ bar }` is conceptually equivalent to `foo ?: bar` for a right biased either
 */
fun <T, U> Either<U, T?>.nullCoalesce(replacement: () -> T): Either<U, T> {
    return when (this) {
        is Either.Left -> this
        is Either.Right -> Either.Right(this.item ?: replacement.invoke())
    }
}
