package lang.temper.common

/*
 * [JEP 431](https://openjdk.org/jeps/431) adds "sequenced collections"
 * and these conflict with a number of methods provided by the Kotlin
 * collections APIs.
 *
 * While Kotlin figures out what it's doing, we have these methods to
 * compile without warnings on Java 21. Once they do, we can inline
 * all these with the new signature.
 */

/**
 * Emulate [ArrayDeque.addFirst]; this avoids a conflict with the
 * the method of the same name introduced in JEP 431 that will affect
 * [AbstractMutableList].
 */
fun <E> ArrayDeque<E>.compatAddFirst(elem: E) = this.add(0, elem)

/**
 * Emulate [MutableList.removeFirst]; this avoids a conflict with the
 * method of the same name introduced in JEP 431.
 */
fun <E> MutableList<E>.compatRemoveFirst(): E = this.removeAt(0)

/**
 * Emulate [MutableList.removeLast]; this avoids a conflict with the
 * method of the same name introduced in JEP 431.
 */
fun <E> MutableList<E>.compatRemoveLast(): E = this.removeAt(this.lastIndex)

/**
 * Emulate [List.reversed]. Prefer [List.asReversed] when a view is acceptable.
 * this avoids a conflict with the extension method and the method of the same
 * name introduced in JEP 431.
 */
fun <E> Iterable<E>.compatReversed(): List<E> = when (this) {
    is Collection -> buildList(this.size) {
        addAll(this@compatReversed)
        reverse()
    }
    else -> buildList {
        addAll(this@compatReversed)
        reverse()
    }
}
