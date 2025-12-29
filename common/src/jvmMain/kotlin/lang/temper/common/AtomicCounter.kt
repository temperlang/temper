package lang.temper.common

import java.util.concurrent.atomic.AtomicLong

actual class AtomicCounter actual constructor(initial: Long) {
    private val javaAtomic = AtomicLong(initial)

    actual fun get(): Long = javaAtomic.get()
    actual fun set(newValue: Long) {
        javaAtomic.set(newValue)
    }
    actual fun incrementAndGet(): Long = javaAtomic.incrementAndGet()
    actual fun getAndIncrement(): Long = javaAtomic.getAndIncrement()
}
