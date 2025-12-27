package lang.temper.common

/**
 * A value that contains its own natural key.
 *
 * @param <N> the type of its natural key.  often a name or a symbol or a string
 */
interface Named<N> {
    val name: N
}

fun <K, V : Named<K>> MutableMap<K, V>.add(v: V) {
    val key = v.name
    this[key] = v
}
