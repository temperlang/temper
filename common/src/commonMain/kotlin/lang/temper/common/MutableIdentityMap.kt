package lang.temper.common

/**
 * Like Java's identity hash map but may not be O(1) on other backends where multiple values have
 * different identities but are hashCode/equals equivalent.
 */
expect fun <K : Any, V> mutableIdentityMapOf(): MutableMap<K, V>

expect fun <V : Any> mutableIdentitySetOf(): MutableSet<V>
