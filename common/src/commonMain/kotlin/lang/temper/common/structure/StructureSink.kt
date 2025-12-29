package lang.temper.common.structure

/**
 * Receives instructions about how to construct a JSON digest.
 */
interface StructureSink : StructureContext {
    /** Calls its argument which should recursively use [PropertySink.key] to specify properties. */
    fun obj(emitProperties: (PropertySink).() -> Unit)

    /** Calls its argument which should recursively use [value] to specify elements. */
    fun arr(emitElements: (StructureSink).() -> Unit)

    /** Emits a quoted string whose content is [s]. */
    fun value(s: String)

    fun value(x: Any?): Unit = when (x) {
        null -> nil()
        is Int -> value(x)
        is Double -> value(x)
        is Long -> value(x)
        is Boolean -> value(x)
        is Structured -> value(x)
        is Number -> value(x.toDouble())
        is Iterable<*> -> arr {
            x.forEach { el: Any? ->
                value(el)
            }
        }
        is Map<*, *> -> obj {
            x.forEach { (k, v) ->
                key("$k") { value(v) }
            }
        }
        else ->
            (
                StructureAdapter.forValue(x)
                    ?: StructureAdapter.toStringAdapter()
                ).adapt(x, this)
    }

    /** Emits a quoted string whose content is [s] or `null`. */
    fun valueOrNull(s: String?) = when (s) {
        null -> nil()
        else -> value(s)
    }

    /** Emits a numeric literal whose content is [n]. */
    fun value(n: Int)

    /** Emits a numeric literal whose content is [n]. */
    fun value(n: Long)

    /** Emits a numeric literal whose content is [n]. */
    fun value(n: Double)

    /** Emits a numeric literal whose content is [b]. */
    fun value(b: Boolean)

    /** Delegates to [x] if it's not null; otherwise emits [nil]. */
    fun value(x: Structured?) {
        if (x == null) {
            nil()
        } else {
            x.destructure(this)
        }
    }

    /** Emits the enum's name as a string if no other strategy works. */
    fun value(e: Enum<*>?) = when (e) {
        null -> nil()
        is Structured -> value(e as Structured)
        else -> value(e.name)
    }

    /** Emits a `null` value. */
    fun nil()

    /** Sinks the map with sorted keys, although any sorting of maps inside values depends on how those destructure. */
    fun <K : Comparable<K>> sorted(map: Map<K, *>) = obj {
        map.entries.sortedBy { it.key }.forEach { (k, v) ->
            key("$k") { value(v) }
        }
    }
}
