package lang.temper.common

/**
 * A trie used to separate tokens in a run of non-space characters by preferring the longest
 * punctuation string possible in a greedy left-to-right scan.
 */
class Trie<T> private constructor(
    /** Sorted unique characters. */
    private val childMap: CharArray,
    /** Elements correspond to childMap */
    val children: List<Trie<T>>,
    /** Does this node correspond to a complete string in the input set. */
    val terminal: Boolean,
    /** The value associated with the prefix leading to this Trie if terminal; null otherwise. */
    val value: T?,
) {

    companion object {
        operator fun <T> invoke(elements: Iterable<Pair<String, T>>): Trie<T> {
            if (elements.isEmpty()) {
                return Trie(emptyCharArray, emptyList(), false, null)
            }
            val sorted = sortedUniqEntries(elements)
            return new(sorted, 0, 0, sorted.size)
        }

        /**
         * @param elements not empty, non null.  Not modified.
         * @param depth the depth in the tree.
         * @param start an index into punctuationStrings of the first string in this
         *   subtree.
         * @param end an index into punctuationStrings past the last string in this
         *   subtree.
         */
        private fun <T> new(
            elements: List<Pair<String, T>>,
            depth: Int,
            start: Int,
            end: Int,
        ): Trie<T> {
            var pos: Int = start
            val terminal = depth == elements[pos].first.length
            val value: T?
            if (terminal) {
                value = elements[pos].second
                if (pos + 1 == end) { // base case
                    return Trie(emptyCharArray, emptyList(), terminal, value)
                } else {
                    ++pos
                }
            } else {
                value = null
            }
            var childCount = 0
            run {
                var last: Char? = null
                for (i in pos until end) {
                    val ch = elements[i].first[depth]
                    if (ch != last) {
                        ++childCount
                        last = ch
                    }
                }
            }
            val childMap = CharArray(childCount)
            val children = mutableListOf<Trie<T>>()
            var childStart = pos
            var lastCh = elements[pos].first[depth]
            for (i in (pos + 1) until end) {
                val ch = elements[i].first[depth]
                if (ch != lastCh) {
                    childMap[children.size] = lastCh
                    children.add(new(elements, depth + 1, childStart, i))
                    childStart = i
                    lastCh = ch
                }
            }
            childMap[children.size] = lastCh
            children.add(new(elements, depth + 1, childStart, end))
            return Trie(childMap, children.toList(), terminal, value)
        }

        private fun <U> sortedUniqEntries(pairs: Iterable<Pair<String, U>>) =
            pairs.toMap().toList().sortedBy { it.first }
    }

    /**
     * The child corresponding to the given key.
     * @return null if no such trie.
     */
    operator fun get(ch: Char): Trie<T>? {
        val i = binarySearch(childMap, ch)
        return if (i >= 0) children[i] else null
    }

    /**
     * The descendant of this trie corresponding to the sequence of values for
     * this trie appended with s.
     * @return null if no such trie.
     */
    operator fun get(s: CharSequence): Trie<T>? {
        var t = this
        for (i in s.indices) {
            t = t[s[i]] ?: return null
        }
        return t
    }

    /**
     * If there is a prefix of s that reaches a terminal node, the length of the longest such
     * prefix, and the corresponding Trie; else null.
     */
    fun longestPrefix(s: CharSequence): Pair<Int, Trie<T>>? {
        var best: Pair<Int, Trie<T>>? = null
        var t = this
        val n = s.length
        for (i in 0 until n) {
            if (t.terminal) {
                best = i to t
            }
            t = t[s[i]] ?: return best
        }
        return if (t.terminal) {
            n to t
        } else {
            best
        }
    }

    operator fun contains(ch: Char): Boolean {
        return binarySearch(childMap, ch) >= 0
    }

    /**
     * Append all strings s such that `this.ge(s).isTerminal()` to the
     * given list in lexical order.
     */
    fun toStringCollection(strings: MutableCollection<String>) {
        toStringCollection("", strings)
    }

    private fun toStringCollection(prefix: String, strings: MutableCollection<String>) {
        if (terminal) {
            strings.add(prefix)
        }
        for (i in childMap.indices) {
            children[i].toStringCollection(prefix + childMap[i], strings)
        }
    }

    override fun toString() = toStringViaBuilder { sb ->
        toStringBuilder(0, sb)
    }

    private fun toStringBuilder(depth: Int, sb: StringBuilder) {
        sb.append(if (terminal) "terminal" else "nonterminal")
        val childDepth = depth + 1
        for (i in childMap.indices) {
            sb.append('\n')
            repeat(childDepth) {
                sb.append('\t')
            }
            sb.append('\'').append(childMap[i]).append("' ")
            children[i].toStringBuilder(childDepth, sb)
        }
    }
}
