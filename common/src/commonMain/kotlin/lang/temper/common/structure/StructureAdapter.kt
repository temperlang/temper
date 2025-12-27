package lang.temper.common.structure

import kotlin.jvm.Synchronized
import kotlin.reflect.KClass

/**
 * Allows registering adapters for legacy types that do not su
 */
interface StructureAdapter<T> {
    fun adapt(x: T, sink: StructureSink)

    companion object {
        private val adapterRegistry = mutableListOf<Pair<KClass<*>, StructureAdapter<*>>>()
        private val adapterRegistryCache = mutableMapOf<KClass<*>, StructureAdapter<*>>()

        @Suppress("UNCHECKED_CAST")
        @Synchronized // TODO: is there something lighter in Kotlin common?
        fun <T : Any> forValue(x: T?): StructureAdapter<T>? {
            return if (x == null) {
                NullAdapter as StructureAdapter<T>
            } else {
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // Checked above
                val type = x::class
                when (val fromCache = adapterRegistryCache[type]) {
                    null -> {
                        var adapter: StructureAdapter<*>? = null
                        for ((typeCandidate, adapterForCandidate) in adapterRegistry) {
                            if (typeCandidate.isInstance(x)) {
                                adapterRegistryCache[type] = adapterForCandidate
                                adapter = adapterForCandidate
                            }
                        }
                        adapter
                    }
                    else -> fromCache
                } as StructureAdapter<T>?
            }
        }

        @Synchronized
        fun <T : Any> register(type: KClass<T>, adapter: StructureAdapter<T>) {
            adapterRegistry.add(type to adapter)
            adapterRegistryCache.clear()
        }

        @Suppress("UNCHECKED_CAST")
        internal fun <T> toStringAdapter() = ToStringAdapter as StructureAdapter<T>
    }
}

private object ToStringAdapter : StructureAdapter<Any?> {
    override fun adapt(x: Any?, sink: StructureSink) {
        if (x == null) {
            sink.nil()
        } else {
            sink.value("$x")
        }
    }
}

private object NullAdapter : StructureAdapter<Any?> {
    override fun adapt(x: Any?, sink: StructureSink) {
        sink.nil()
    }
}
