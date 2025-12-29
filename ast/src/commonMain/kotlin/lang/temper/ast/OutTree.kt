package lang.temper.ast

import lang.temper.log.Positioned

/** Base class for ASTs produced by a backend. */
interface OutTree<TREE : OutTree<TREE>> : Positioned, OutFormattable<TREE> {
    val parent: OutTree<TREE>?

    fun ancestor(filter: (OutTree<TREE>) -> Boolean): OutTree<TREE>? {
        var ancestor = parent
        while (ancestor != null && !filter(ancestor)) {
            ancestor = ancestor.parent
        }
        return ancestor
    }

    /**
     * Constructs a deep copy of the tree.
     *
     * This is meant to allow copying of small subtrees for re-use, and to avoid
     * parent pointer conflicts.
     *
     * Backends should not preserve two or more copies of large subtrees to avoid code-size bloat.
     */
    fun deepCopy(): OutTree<TREE>
}

@Suppress("UNCHECKED_CAST")
fun <T : OutTree<*>> Iterable<T>.deepCopy(): List<T> = map { it.deepCopy() as T }

@Suppress("UNCHECKED_CAST")
fun <T : OutTree<*>> Iterable<T>.deepSlice(start: Int, end: Int): List<T> = when (this) {
    is List<T> -> subList(start, end).map { it.deepCopy() as T }
    else -> asSequence().drop(start).take(end - start).map { it.deepCopy() as T }.toList()
}
