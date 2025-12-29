package lang.temper.common

expect class AtomicCounter(initial: Long = 0) {
    fun get(): Long
    fun incrementAndGet(): Long
    fun getAndIncrement(): Long
    fun set(newValue: Long)
}
