package lang.temper.common

import kotlin.math.max
import kotlin.math.min

// This file is a home for extension functions related to collections.

inline fun <T : Any, O> MutableList<T>.stackWithElementIfNotNull(
    element: T?,
    body: () -> O,
): O {
    if (element != null) {
        add(element)
    }
    try {
        return body()
    } finally {
        if (element != null) {
            compatRemoveLast()
        }
    }
}

/** A multimap with keys and values swapped. */
fun <T, VALUES : Collection<T>> Map<T, Collection<T>>.inverseMultimap(
    newGroup: (values: Iterable<T>) -> VALUES,
): Map<T, VALUES> {
    val revListMap = mutableMapOf<T, MutableList<T>>()
    for ((k, values) in this) {
        for (v in values) {
            revListMap.getOrPut(v) { mutableListOf() }.add(k)
        }
    }
    return revListMap.map { (k, vs) -> k to newGroup(vs) }.toMap()
}

fun <K, V> Map<K, V>.inverse(): Map<V, K> = buildMap(this.size) {
    for ((k, v) in this@inverse) {
        this@buildMap[v] = k
    }
}

fun <T, U : T> List<U>.padTo(minSize: Int, padding: U): List<U> {
    val nNeeded = minSize - this.size
    return if (nNeeded <= 0) {
        this
    } else {
        buildList {
            addAll(this)
            repeat(nNeeded) {
                add(padding)
            }
        }
    }
}

/**
 * Multimap version of [MutableMap.put].
 */
fun <K, V, C : MutableCollection<V>> MutableMap<K, C>.putMulti(
    key: K,
    value: V,
    newC: () -> C,
) = this.getOrPut(key) { newC() }.add(value)

fun <K, V> MutableMap<K, MutableList<V>>.putMultiList(
    key: K,
    value: V,
) = putMulti(key, value) { mutableListOf() }

fun <K, V> MutableMap<K, MutableSet<V>>.putMultiSet(
    key: K,
    value: V,
) = putMulti(key, value) { mutableSetOf() }

fun <A, B, C> MutableMap<A, MutableMap<B, C>>.putMultiMap(
    a: A,
    b: B,
    c: C,
): C? = getOrPut(a) { mutableMapOf() }.put(b, c)

/** Allows creating a list multimap.  [f] may use [putMultiList] to add entries. */
fun <K, V> buildListMultimap(f: MutableMap<K, MutableList<V>>.() -> Unit): Map<K, List<V>> {
    val map = mutableMapOf<K, MutableList<V>>()
    map.f()
    return map.mapValues { it.value.toList() }
}

/** Allows creating a list multimap.  [f] may use [putMultiSet] to add entries. */
fun <K, V> buildSetMultimap(f: MutableMap<K, MutableSet<V>>.() -> Unit): Map<K, Set<V>> {
    val map = mutableMapOf<K, MutableSet<V>>()
    map.f()
    return map.mapValues { it.value.toSet() }
}

/** Allows creating a list multimap.  [f] may use [putMultiMap] to add entries. */
fun <A, B, C> buildMapMultimap(f: MutableMap<A, MutableMap<B, C>>.() -> Unit): Map<A, Map<B, C>> {
    val map = mutableMapOf<A, MutableMap<B, C>>()
    map.f()
    return map.mapValues { it.value.toMap() }
}

/**
 * Multimap version of [MutableMap.put] for multiple [values] associated with a single [key].
 */
fun <K, V, C : MutableCollection<V>> MutableMap<K, C>.putAllMulti(
    key: K,
    values: Iterable<V>,
    newC: () -> C,
) = this.getOrPut(key) { newC() }.addAll(values)

/**
 * Multimap version of [MutableMap.putAll].
 */
fun <K, V, C : MutableCollection<V>, D : Collection<V>> MutableMap<K, C>.putAllMulti(
    map: Map<K, D>,
    newC: () -> C,
) {
    map.forEach { (key, values) ->
        this.getOrPut(key) { newC() }.addAll(values)
    }
}

/**
 * Copies an iterator to a list so that defensive code can assume that the copy is immutable
 * and not subject to weirdness due to custom collections that override `.toList` in odd ways.
 */
fun <T> defensiveListCopy(elements: Iterable<T>): List<T> {
    val mList = mutableListOf<T>()
    mList.addAll(elements)
    return mList.toList()
}

fun <K, V> defensiveMapCopy(map: Map<K, V>): Map<K, V> {
    val mMap = mutableMapOf<K, V>()
    mMap.putAll(map)
    return mMap.toMap()
}

/**
 * Remove all elements that match the given predicate from the given *mutable* list and return
 * the removed elements in order.
 *
 * This is basically partition in-place.
 */
fun <T> MutableList<T>.removeMatching(predicate: (x: T) -> Boolean): List<T> {
    val removed = mutableListOf<T>()
    for (i in this.indices.reversed()) {
        val element = this[i]
        if (predicate(element)) {
            removed.add(element)
            this.removeAt(i)
        }
    }
    removed.reverse()
    return removed.toList()
}

fun <K, V> MutableMap<K, V>.removeMatching(predicate: (Map.Entry<K, V>) -> Boolean) {
    val entriesIterator = entries.iterator()
    while (entriesIterator.hasNext()) {
        val e = entriesIterator.next()
        if (predicate(e)) {
            entriesIterator.remove()
        }
    }
}

fun <I, O> Iterable<I>.mapReduceNotEmpty(mapper: (I) -> O, reducer: (O, O) -> O): O {
    val it = this.iterator()
    check(it.hasNext())
    var o = mapper(it.next())
    while (it.hasNext()) {
        o = reducer(o, mapper(it.next()))
    }
    return o
}

/** True if there is an element that is both in this and other. */
fun <T> Collection<T>.containsAny(other: Iterable<T>): Boolean {
    if (other is Collection<T> && this.size < other.size) {
        for (element in this) {
            if (element in other) {
                return true
            }
        }
        return false
    }
    for (element in other) {
        if (element in this) {
            return true
        }
    }
    return false
}

/**
 Splits a [List] into two parts those up to and including when [predicate] is true and those after
 */
fun <T> List<T>.partitionAfterFirstMatch(predicate: (x: T) -> Boolean): Pair<List<T>, List<T>> {
    val firstMatchIndex = this.indexOfFirst(predicate)
    return if (firstMatchIndex < 0) {
        this to emptyList()
    } else {
        this.subList(0, firstMatchIndex + 1) to this.subListToEnd(firstMatchIndex + 1)
    }
}

/** Splits into two lists, but excludes those for which [predicate] returns null. */
fun <T> Iterable<T>.partitionNotNull(predicate: (T) -> Boolean?): Pair<List<T>, List<T>> {
    val first = mutableListOf<T>()
    val second = mutableListOf<T>()
    for (item in this) {
        when (predicate(item)) {
            true -> first.add(item)
            false -> second.add(item)
            null -> {}
        }
    }
    return first to second
}

private class TypeSplit<T> {
    private val claimers = mutableListOf<(T) -> Boolean>()

    fun <O : T> register(out: MutableCollection<O>, claim: (T) -> Either<O, Unit>) {
        claimers.add {
            when (val e = claim(it)) {
                is Either.Left -> {
                    out.add(e.item)
                    true
                }
                is Either.Right -> false
            }
        }
    }

    fun partition(items: Iterable<T>, unclaimed: MutableCollection<T>?) {
        for (item in items) {
            for (claimer in claimers) {
                if (claimer(item)) { continue }
            }
            unclaimed?.add(item)
        }
    }
}

/** Partitions a list of T's into two lists, one of A's and one of B's.  Uses `is` to filter. */
inline fun <T, reified A : T, reified B : T> Iterable<T>.partitionByType(): Pair<List<A>, List<B>> =
    partitionByType(
        {
            if (it is A) { Either.Left(it) } else { Either.Right(Unit) }
        },
        {
            if (it is B) { Either.Left(it) } else { Either.Right(Unit) }
        },
    )

fun <T, A : T, B : T> Iterable<T>.partitionByType(
    boxA: (T) -> Either<A, Unit>,
    boxB: (T) -> Either<B, Unit>,
): Pair<List<A>, List<B>> {
    val aList = mutableListOf<A>()
    val bList = mutableListOf<B>()

    val split = TypeSplit<T>()
    split.register(aList, boxA)
    split.register(bList, boxB)
    split.partition(this, null)

    return aList.toList() to bList.toList()
}

/**
 * A minimal set multimap, *mT*, such that (v in m\[k]) implies
 * (v in mT\[k] && mT\[k].containsAll(mT\[v] ?: emptySet())).
 */
fun <T> transitiveClosure(m: Map<T, Set<T>>): Map<T, Set<T>> {
    val transitiveM = m.mapValues { it.value.toMutableSet() }
    addTransitiveClosure(transitiveM)
    return transitiveM.mapValues { it.value.toSet() }
}

/**
 * Adds entries to value sets until [m] is the minimal set multimap, such that (v in m\[k]) implies
 * (v in m\[k] && m\[k].containsAll(m\[v] ?: emptySet())).
 */
fun <T> addTransitiveClosure(m: Map<T, MutableSet<T>>) {
    var progressMade: Boolean
    do {
        progressMade = false
        for (vs in m.values) {
            val n = vs.size
            for (v in vs.toList()) {
                val s = m[v]
                if (s != null) {
                    vs.addAll(s)
                }
            }
            if (vs.size > n) {
                progressMade = true
            }
        }
    } while (progressMade)
}

/**
 * A list containing [afterMap]'s keys such that each, k, appears once after all of [afterMap]\[k].
 *
 * If there is a cycle in [afterMap]'s transitive closure it is broken arbitrarily.
 *
 * If there is a <code>v in [afterMap]\[k]</code> such that <code>v !in [afterMap]</code> it will
 * also end up in the partial order.
 */
fun <T> partiallyOrder(afterMap: Map<T, Collection<T>>): List<T> =
    partiallyOrder(afterMap.keys, afterMap) { it }

/**
 * A list containing [items] and values from [afterMap] such that each, v, appears once after all of
 * [afterMap]\[k].
 *
 * If there is a cycle in [afterMap]'s transitive closure it is broken arbitrarily.
 *
 * If there is a v in afterMap\[k] such that k is a dep key for an output element, then v will end
 * up in the partial order regardless of whether v in items.
 */
fun <K, V> partiallyOrder(
    items: Iterable<V>,
    afterMap: Map<K, Collection<V>>,
    depKeyOf: (V) -> K,
): List<V> {
    val ordered = mutableSetOf<V>()
    val partialOrder = mutableListOf<V>()
    fun addToOrdered(v: V) {
        if (v !in ordered) {
            ordered.add(v) // Arbitrarily breaks cycles.
            val k = depKeyOf(v)
            val after = afterMap[k]
            if (after != null) {
                for (y in after) {
                    addToOrdered(y)
                }
            }
            partialOrder.add(v)
        }
    }
    for (v in items) {
        addToOrdered(v)
    }
    return partialOrder.toList()
}

fun intersect(a: IntRange, b: IntRange): IntRange {
    val start = max(a.first, b.first)
    val endInclusive = min(a.last, b.last)
    return if (start <= endInclusive) {
        IntRange(start = start, endInclusive = endInclusive)
    } else {
        IntRange.EMPTY
    }
}

fun <T> (List<T>).indexOf(x: T, from: Int): Int {
    for (i in from until this.size) {
        if (this[i] == x) {
            return i
        }
    }
    return -1
    // TODO: do we need to use subList for non-random-access lists
}

fun <T> (MutableList<T>).mutSubListToEnd(from: Int): MutableList<T> = this.subList(from, this.size)

fun <T> (List<T>).subListToEnd(from: Int): List<T> {
    val sublist = this.subList(from, this.size)
    return if (sublist is MutableList<T>) sublist.toList() else sublist
}

fun <T> (MutableList<T>).truncateTo(newSize: Int) = this.mutSubListToEnd(newSize).clear()

/**
 * Returns *y* when [f]\(*x*\) == *y* for all *x* in *this*.
 * Otherwise, returns null.
 */
fun <I, O : Any> (Iterable<I>).allMapToSameElseNull(f: (I) -> O?): O? {
    val iterator = this.iterator()
    if (!iterator.hasNext()) {
        return null
    }
    val result = f(iterator.next()) ?: return null
    while (iterator.hasNext()) {
        if (result != f(iterator.next())) {
            return null
        }
    }
    return result
}

/**
 * Finds the common prefix of the given complex inputs.
 *
 * @param by called for each item of [ls] in order to produce a series
 *     unless we conclude that the prefix is empty before reaching item.
 */
fun <T, E> commonPrefixBy(
    ls: Iterable<T>,
    by: (T) -> Iterable<E>,
): List<E> {
    val iterator = ls.iterator()
    if (!iterator.hasNext()) {
        return emptyList() // A tad ambiguous.
    }
    val prefix = mutableListOf<E>()
    prefix.addAll(by(iterator.next()))
    while (prefix.isNotEmpty() && iterator.hasNext()) {
        val items = by(iterator.next())
        var i = 0
        val n = prefix.size
        for (item in items) {
            if (item != prefix[i]) {
                break
            }
            i += 1
            if (i == n) { break }
        }
        prefix.subList(i, n).clear()
    }
    return prefix.toList()
}

/**
 * The count of items that can be iterated past from [a] and [b]'s respective iterators before
 * encountering a pair that are not `==`.
 */
fun <T> commonPrefixLength(a: Iterable<T>, b: Iterable<T>): Int {
    var n = 0
    val aIt = a.iterator()
    val bIt = b.iterator()
    while (aIt.hasNext() && bIt.hasNext() && aIt.next() == bIt.next()) {
        n += 1
    }
    return n
}

/**
 * The count of characters that are common to both except that leading surrogates are not
 * considered separable.
 */
fun commonPrefixLength(a: CharSequence, b: CharSequence): Int {
    val minLength = min(a.length, b.length)
    var i = 0
    while (i < minLength && a[i] == b[i]) {
        i += 1
    }
    if (
        i != 0 && a[i - 1] in '\uD800'..'\uDBFF' &&
        (
            (i < a.length && a[i] in '\uDC00'..'\uDFFF') ||
                (i < b.length && b[i] in '\uDC00'..'\uDFFF')
            )
    ) {
        i -= 1
    }
    return i
}

/**
 * For an iterable
 *     (x0, x1, x2, ... xn)
 * returns the sequence
 *     (el(x0), sep(x0, x1), el(x1), sep(x1, x2), ..., sep(xn-1, xn), el(x))
 */
fun <I, O> (Iterable<I>).mapInterleaving(el: (I) -> O, separator: (I, I) -> O): Iterable<O> =
    Interleaving(this, el, separator)

private class Interleaving<I, O>(
    val ins: Iterable<I>,
    val element: (I) -> O,
    val separator: (I, I) -> O,
) : Iterable<O> {
    override fun iterator(): Iterator<O> = InterleavingIterator()

    private inner class InterleavingIterator : Iterator<O> {
        private val iterator = ins.iterator()
        private var last: I? = null
        private var next: I? = null
        private var produceSeparatorNext = false
        private var producedOne = false
        private var hasMore = iterator.hasNext()

        override fun hasNext() = hasMore

        override fun next(): O = when {
            !hasMore -> throw NoSuchElementException()
            produceSeparatorNext -> {
                val item = iterator.next()

                @Suppress("UNCHECKED_CAST") // Safe by state reasoning over booleans
                val lastAsI = last as I
                val sep = separator(lastAsI, item)
                next = item
                hasMore = true // Because we've yet to emit item.
                produceSeparatorNext = false
                sep
            }
            producedOne -> {
                @Suppress("UNCHECKED_CAST") // Safe by state reasoning over booleans
                val nextAsI = next as I
                val out = element(nextAsI)
                hasMore = iterator.hasNext()
                last = if (hasMore) { next } else { null } // release for gc
                next = null
                produceSeparatorNext = true
                out
            }
            else -> {
                val item = iterator.next()
                val out = element(item)
                produceSeparatorNext = true
                producedOne = true
                hasMore = iterator.hasNext()
                if (hasMore) {
                    last = item
                }
                out
            }
        }
    }
}

/**
 * Applies [transformOrNull] to each element in turn, until a non-null value is returned and
 * returns that value, or returns null if [transformOrNull] returned null for all elements.
 */
fun <E, T : Any> (Iterable<E>).mapFirst(transformOrNull: (E) -> T?): T? {
    for (e in this) {
        return (transformOrNull(e) ?: continue)
    }
    return null
}

fun <T, U : T> (MutableList<T>).replaceSubList(
    startInclusive: Int,
    endExclusive: Int,
    replacements: List<U>,
) {
    val nOld = endExclusive - startInclusive
    val nNew = replacements.size
    val nMin = min(nOld, nNew)
    for (i in 0 until nMin) {
        this[startInclusive + i] = replacements[i]
    }
    val delta = nNew - nOld
    if (delta > 0) {
        this.addAll(startInclusive + nMin, replacements.subList(nMin, nNew))
    } else if (delta < 0) {
        this.subList(startInclusive + nMin, endExclusive).clear()
    }
}

fun <T> (Set<T>).mapReusing(f: (T) -> T): Set<T> {
    val mapped = mutableSetOf<T>()
    var changed = false
    for (element in this) {
        val newElement = f(element)
        if (!changed && newElement != element) {
            changed = true
        }
        mapped.add(newElement)
    }
    return if (changed || mapped.size != this.size) {
        mapped.toSet()
    } else {
        this
    }
}

fun <T> (List<T>).mapReusing(f: (T) -> T): List<T> {
    var mapped: MutableList<T>? = null
    var i = 0
    for (element in this) {
        val newElement = f(element)
        if (mapped == null && newElement != element) {
            mapped = this.subList(0, i).toMutableList()
        }
        mapped?.add(newElement)
        i += 1
    }
    return mapped?.toList() ?: this
}

/** Like [Iterable.all] but predicate receives the index of the element. */
inline fun <T> (Iterable<T>).allIndexed(predicate: (Int, T) -> Boolean): Boolean {
    for ((i, element) in this.withIndex()) {
        if (!predicate(i, element)) {
            return false
        }
    }
    return true
}

/** Like [Iterable.any] but predicate receives the index of the element. */
inline fun <T> (Iterable<T>).anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
    for ((i, element) in this.withIndex()) {
        if (predicate(i, element)) {
            return true
        }
    }
    return false
}

inline val <T> (Sequence<T>).soleElement: T?
    get() = iterator().soleElement
inline val <T> (Iterable<T>).soleElement: T?
    get() = iterator().soleElement
inline val <T> (Iterator<T>).soleElement: T?
    get() {
        if (!hasNext()) { return null }
        val first = next()
        if (hasNext()) { return null }
        return first
    }

inline val <T : Any> (Iterable<T>).soleElementOrNull: T?
    get() {
        val iterator = this.iterator()
        return if (iterator.hasNext()) {
            val first = iterator.next()
            return if (iterator.hasNext()) {
                null
            } else {
                first
            }
        } else {
            null
        }
    }

fun <T : Any> (Iterable<T>).soleMatchingOrNull(filter: (T) -> Boolean): T? {
    val iterator = this.iterator()
    while (iterator.hasNext()) {
        val item = iterator.next()
        if (filter(item)) {
            while (iterator.hasNext()) {
                val subsequentItem = iterator.next()
                if (filter(subsequentItem)) { return null }
            }
            return item
        }
    }
    return null
}

/**
 * Returns the first entry in [suffixOptions] that is a suffix of [this] or null if none found
 */
fun <T> Iterable<T>.suffix(suffixOptions: Iterable<Iterable<T>>): Iterable<T>? {
    val reversed = this.reversed()
    return suffixOptions.firstOrNull { option ->
        option.reversed().allIndexed { i, t -> reversed[i] == t }
    }
}

/** Like [List.indexOfFirst] but does not consider elements at indices < [startInclusive] */
fun <T> List<T>.indexOfNext(startInclusive: Int, predicate: (T) -> Boolean): Int {
    var i = startInclusive
    val n = size
    while (i < n) {
        if (predicate(this[i])) {
            return i
        }
        i += 1
    }
    return -1
}

/**
 * Removes an occurrence of the given item from this list
 * in an O(1) way (assuming O(1) random access)
 * but, unlike [remove], does not attempt to preserve the
 * relative order of remaining items.
 */
fun <T> MutableList<T>.removeNotOrderPreserving(item: T) {
    val index = this.indexOf(item)
    if (index >= 0) {
        val lastIndex = this.lastIndex
        if (index != lastIndex) {
            // Swap the last item into the spot of the removed.
            this[index] = this[lastIndex]
        }
        removeAt(lastIndex)
    }
}

/**
 * The maximum value, according to [cmp], among [lowerBound]
 * and the values as if from applying [mapNotNull] with [mapFn].
 */
fun <E, C : Any> Iterable<E>.maxMapsNotNull(
    lowerBound: C,
    cmp: Comparator<C>,
    mapFn: (E) -> C?,
): C {
    var maxSoFar = lowerBound
    for (element in this) {
        val mapped = mapFn(element) ?: continue
        if (cmp.compare(mapped, maxSoFar) > 0) {
            maxSoFar = mapped
        }
    }
    return maxSoFar
}

/**
 * Like [maxMapsNotNull] with the natural ordering comparator.
 */
fun <E, C : Comparable<C>> Iterable<E>.maxMapsNotNull(
    lowerBound: C,
    mapFn: (E) -> C?,
): C = maxMapsNotNull(lowerBound = lowerBound, cmp = naturalOrder(), mapFn = mapFn)

/**
 * Given two immutable maps, produces a map with the keys of both
 * such that the value associated with key *k* is
 * [merge]\([a]\[*k*\] ?: [zeroValue], [b]\[*k*\] ?: [zeroValue]\)
 * except that this function checks for containment via `in` instead of using
 * null-coalescing.
 *
 * @return [a] if no changes are required.
 */
fun <K, V> mergeMaps(zeroValue: V, a: Map<K, V>, b: Map<K, V>, merge: (V, V) -> V): Map<K, V> {
    // This is optimized to avoid allocating memory in the common case
    // where merging leads to no difference.
    var m: MutableMap<K, V>? = null
    var countNotInB = 0
    for ((k, v) in a) {
        val bv = if (k in b) {
            b.getValue(k)
        } else {
            countNotInB += 1
            zeroValue
        }
        val mv = merge(v, bv)
        if (mv != v) {
            if (m == null) {
                m = a.toMutableMap()
            }
            m[k] = mv
        }
    }
    // If b has a key not in a, then we need to iterate it too
    if (a.size - countNotInB != b.size) {
        for ((k, v) in b) {
            val av = if (k in a) {
                a.getValue(k)
            } else {
                zeroValue
            }
            val mv = merge(av, v)
            if (mv != av) {
                if (m == null) {
                    m = a.toMutableMap()
                }
                m[k] = mv
            }
        }
    }
    return m?.toMap() ?: a
}

/**
 * Given two immutable maps, produces a map with the keys of both
 * such that the value associated with key *k* is
 * [merge]\([a]\[*k*\] ?: [zeroValue], [b]\[*k*\] ?: [zeroValue]\)
 * except that this function checks for containment via `in` instead of using
 * null-coalescing.
 *
 * @return [a] if no changes are required.
 */
fun <K, V : Any> mergeMapsNotNull(a: Map<K, V>, b: Map<K, V>, merge: (V?, V?) -> V?): Map<K, V> {
    // This is optimized to avoid allocating memory in the common case
    // where merging leads to no difference.
    var m: MutableMap<K, V>? = null
    var countNotInB = 0
    for ((k, v) in a) {
        val bv = if (k in b) {
            b.getValue(k)
        } else {
            countNotInB += 1
            null
        }
        val mv = merge(v, bv)
        if (mv != v) {
            if (m == null) {
                m = a.toMutableMap()
            }
            if (mv != null) {
                m[k] = mv
            } else {
                m.remove(k)
            }
        }
    }
    // If b has a key not in a, then we need to iterate it too
    if (a.size - countNotInB != b.size) {
        for ((k, v) in b) {
            val av = if (k in a) {
                a.getValue(k)
            } else {
                null
            }
            val mv = merge(av, v)
            if (mv != av) {
                if (m == null) {
                    m = a.toMutableMap()
                }
                if (mv != null) {
                    m[k] = mv
                } else {
                    m.remove(k)
                }
            }
        }
    }
    return m?.toMap() ?: a
}

/** Check if an iterable is empty by inspecting its iterator if not a [Collection]. */
fun <T> Iterable<T>.isNotEmpty() = when (this) {
    is Collection<T> -> !isEmpty()
    else -> this.iterator().hasNext()
}

fun <T> Iterable<T>.isEmpty() = !isNotEmpty()

fun <T> joinedIterable(vararg iterables: Iterable<T>): Iterable<T> = JoinedIterable(iterables.toList())

fun <T> joinedIterable(iterables: Iterable<Iterable<T>>): Iterable<T> = JoinedIterable(iterables)

private class JoinedIterable<T>(val iterables: Iterable<Iterable<T>>) : Iterable<T> {
    override fun iterator(): Iterator<T> =
        @Suppress("IteratorHasNextCallsNextMethod")
        object : Iterator<T> {
            private val iteratorIterator: Iterator<Iterable<T>> = iterables.iterator()
            private var iterator: Iterator<T>? = null

            override fun hasNext(): Boolean {
                while (true) {
                    val iterator = this.iterator
                    if (iterator?.hasNext() == true) { return true }
                    this.iterator = null
                    if (!iteratorIterator.hasNext()) { return false }
                    this.iterator = iteratorIterator.next().iterator()
                }
            }

            override fun next(): T {
                if (!hasNext()) { throw NoSuchElementException() }
                return iterator!!.next()
            }
        }
}

inline fun <T> buildUniqList(block: MutableSet<T>.() -> Unit): List<T> = buildSet {
    this.block()
}.toList()

/**
 * Iterates over the elements of this iterable but the body may call
 * [MutableIterator.remove] from within the body.
 *
 * For example:
 *
 *    val ls = mutableListOf(1, 2, 3)
 *    ls.forEachFiltering { it ->
 *      if (it == 2) { this.remove() }
 *    }
 *    // ls has [1, 3]
 */
inline fun <T> MutableIterable<T>.forEachFiltering(body: MutableIterator<T>.(el: T) -> Unit) {
    val iterator: MutableIterator<T> = iterator()
    while (iterator.hasNext()) {
        iterator.body(iterator.next())
    }
}

inline fun <T : Any, reified U : T> Iterable<T>.firstOrNullAs(predicate: (U) -> Boolean): U? {
    for (x in this) {
        if (x is U && predicate(x)) {
            return x
        }
    }
    return null
}

class ConcatenatedListView<T>(
    private val left: List<T>,
    private val right: List<T>,
) : AbstractList<T>() {
    override val size: Int
        get() = left.size + right.size

    override fun get(index: Int): T {
        val leftSize = left.size
        return if (index < leftSize) {
            left[index]
        } else {
            right[index - leftSize]
        }
    }
}

class MappingListView<I, O>(
    private val underlying: List<I>,
    private val mapFn: (I) -> O,
) : AbstractList<O>() {
    override val size: Int get() = underlying.size
    override fun get(index: Int): O = mapFn(underlying[index])
    override fun subList(fromIndex: Int, toIndex: Int): List<O> =
        MappingListView(underlying.subList(fromIndex, toIndex), mapFn)
}
