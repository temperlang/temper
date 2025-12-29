package lang.temper.common

interface Producer<out T> {
    fun get(): T
}
