package lang.temper.common

actual class AtomicCounter actual constructor(initial: Long) {
    private var n = initial
    actual fun get() = n
    actual fun set(n: Long) { this.n = n }
    actual fun incrementAndGet() = ++n
    actual fun getAndIncrement() = n++
}
