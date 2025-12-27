package lang.temper.common

/**
 * A thunk that calls its [function argument][f] at most once.
 * It may be called like a function, and the first time it is called, it delegates to [f],
 * holds onto the result and re-returns it for subsequent calls.
 */
class Memoized<T>(
    private val f: () -> T,
) {
    private var cached: List<T>? = null

    operator fun invoke(): T = (
        cached
            ?: run {
                val cached = listOf(f())
                this.cached = cached
                cached
            }
        )[0]
}
