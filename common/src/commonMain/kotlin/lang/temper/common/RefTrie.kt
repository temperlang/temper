package lang.temper.common

import kotlin.math.min

/**
 * A trie that works with reference types as keys which may be used to solve
 * value-associated-with-longest-prefix problems.
 */
class RefTrie<K : Any, V : Any> private constructor(
    /** Sorted unique keys. */
    private val childList: List<K>,
    /** Elements correspond to childMap */
    val children: List<RefTrie<K, V>>,
    /** Does this node correspond to a complete string in the input set. */
    val terminal: Boolean,
    /** The value associated with the prefix leading to this Trie if terminal; null otherwise. */
    val value: V?,
    /** Used to order the keys in [childList]. */
    private val orderKeys: Comparator<K>,
) {

    companion object {
        fun <K : Any, V : Any> empty(): RefTrie<K, V> =
            RefTrie(emptyList(), emptyList(), false, null) { _, _ ->
                // Should not be called.  No keys to compare to
                throw UnsupportedOperationException()
            }

        operator fun <K : Any, V : Any> invoke(
            elements: Iterable<Pair<List<K>, V>>,
            comparator: Comparator<K>,
        ): RefTrie<K, V> {
            val sorted = sortedUniqEntries(elements, comparator)
            return new(sorted, 0, 0, sorted.size, comparator)
        }

        operator fun <K : Comparable<K>, V : Any>
        invoke(
            elements: Iterable<Pair<List<K>, V>>,
        ): RefTrie<K, V> = invoke(elements, naturalOrder())

        /**
         * @param elements not empty, non null.  Not modified.
         * @param depth the depth in the tree.
         * @param start an index into punctuationStrings of the first string in this
         *   subtree.
         * @param end an index into punctuationStrings past the last string in this
         *   subtree.
         */
        private fun <K : Any, V : Any> new(
            elements: List<Pair<List<K>, V>>,
            depth: Int,
            start: Int,
            end: Int,
            comparator: Comparator<K>,
        ): RefTrie<K, V> {
            var pos: Int = start
            val terminal = depth == elements[pos].first.size
            val value: V?
            if (terminal) {
                value = elements[pos].second
                if (pos + 1 == end) { // base case
                    return RefTrie(emptyList(), emptyList(), terminal, value, comparator)
                } else {
                    ++pos
                }
            } else {
                value = null
            }
            var childCount = 0
            run {
                var last: K? = null
                for (i in pos until end) {
                    val key = elements[i].first[depth]
                    if (key != last) {
                        ++childCount
                        last = key
                    }
                }
            }
            val childList = mutableListOf<K>()
            val children = mutableListOf<RefTrie<K, V>>()
            var childStart = pos
            var lastKey = elements[pos].first[depth]
            for (i in (pos + 1) until end) {
                val key = elements[i].first[depth]
                if (key != lastKey) {
                    childList.add(lastKey)
                    children.add(new(elements, depth + 1, childStart, i, comparator))
                    childStart = i
                    lastKey = key
                }
            }
            childList.add(lastKey)
            children.add(new(elements, depth + 1, childStart, end, comparator))
            return RefTrie(childList, children.toList(), terminal, value, comparator)
        }

        private fun <K, V> sortedUniqEntries(
            pairs: Iterable<Pair<List<K>, V>>,
            comparator: Comparator<K>,
        ) =
            pairs.toMap().toList().sortedWith compare@{ (a), (b) ->
                val minSize = min(a.size, b.size)
                for (i in 0 until minSize) {
                    val delta = comparator.compare(a[i], b[i])
                    if (delta != 0) { return@compare delta }
                }
                a.size.compareTo(b.size)
            }
    }

    /**
     * The child corresponding to the given key.
     * @return null if no such trie.
     */
    operator fun get(key: K): RefTrie<K, V>? {
        val i = binarySearch(childList, key) { a, b -> orderKeys.compare(a, b) }
        return if (i >= 0) children[i] else null
    }

    /**
     * The descendant of this trie corresponding to the sequence of values for
     * this trie appended with s.
     * @return null if no such trie.
     */
    operator fun get(keySequence: Iterable<K>): RefTrie<K, V>? {
        var t = this
        for (k in keySequence) {
            t = t[k] ?: return null
        }
        return t
    }

    /**
     * If there is a prefix of s that reaches a terminal node, the length of the longest such
     * prefix, and the corresponding Trie; else null.
     */
    fun longestPrefix(keySequence: Iterable<K>): Pair<Int, RefTrie<K, V>>? {
        var best: Pair<Int, RefTrie<K, V>>? = null
        var t = this
        var i = 0
        for (k in keySequence) {
            if (t.terminal) {
                best = i to t
            }
            t = t[k] ?: return best
            i += 1
        }
        return if (t.terminal) {
            i to t
        } else {
            best
        }
    }

    operator fun contains(key: K): Boolean {
        return binarySearch(childList, key) { a, b -> orderKeys.compare(a, b) } >= 0
    }

    operator fun contains(keys: List<K>): Boolean = longestPrefix(keys)?.first == keys.size

    override fun toString() = toStringViaBuilder { sb ->
        toStringBuilder(0, sb)
    }

    private fun toStringBuilder(depth: Int, sb: StringBuilder) {
        sb.append(if (terminal) "terminal" else "nonterminal")
        val childDepth = depth + 1
        for (i in childList.indices) {
            sb.append('\n')
            repeat(childDepth) {
                sb.append('\t')
            }
            sb.append(childList[i]).append(' ')
            children[i].toStringBuilder(childDepth, sb)
        }
    }
}
