package lang.temper.common

val weakMapMaker: () -> dynamic = eval("() => new WeakMap()").unsafeCast<() -> dynamic>()

private class MutableWeakMapImpl<K, V> : MutableWeakMap<K, V> {
    private val jsWeakMap = weakMapMaker()

    override fun contains(key: K): Boolean = jsWeakMap.has(key) as Boolean

    override fun get(key: K): V? = jsWeakMap.get(key).unsafeCast<V?>()

    override fun set(key: K, value: V): V? {
        val oldValue = jsWeakMap.get(key).unsafeCast<V?>()
        jsWeakMap.set(key, value)
        return oldValue
    }

    override fun remove(key: K): V? {
        val oldValue = jsWeakMap.get(key).unsafeCast<V?>()
        jsWeakMap.delete(key)
        return oldValue
    }

    override fun toString() = "$jsWeakMap"
}

actual fun <K, V> mutableWeakMapOf(): MutableWeakMap<K, V> = MutableWeakMapImpl()
