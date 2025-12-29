package lang.temper.common

/**
 * A thing that can be merged with another of its type to produce a combined instance of that type.
 * This is useful when traversing graphs and computing metadata along each path.  Where a node is
 * reachable by two paths (or, perish the thought, is part of a cycle), we can merge state computed
 * across both paths.
 *
 * Where cycles are a problem, as long as merge is monotonic and bounded
 * (e.g. intersects or unions towards a closed U), we can use equivalence of merged results to
 * decide whether to continue exploring the cycle.
 *
 * Weird things probably happen if [merge](x, x) != x.
 */
interface Mergeable<T : Mergeable<T>> {
    fun merge(other: T): T

    companion object {
        fun <K, V : Mergeable<V>> mergeMapValues(
            a: Map<K, V>,
            b: Map<K, V>,
            zero: V,
        ): Map<K, V> {
            val merged = mutableMapOf<K, V>()
            merged.putAll(a)
            for ((k, bv) in b.entries) {
                merged[k] = (merged[k] ?: zero).merge(bv)
            }
            return merged.toMap()
        }

        fun <T, M : Mergeable<M>> foldMergingOrNull(elements: Iterable<T>, f: (T) -> M?): M? {
            val iterator = elements.iterator()
            if (!iterator.hasNext()) {
                return null
            }
            var m = f(iterator.next()) ?: return null
            while (iterator.hasNext()) {
                m = m.merge(f(iterator.next()) ?: return null)
            }
            return m
        }
    }
}
