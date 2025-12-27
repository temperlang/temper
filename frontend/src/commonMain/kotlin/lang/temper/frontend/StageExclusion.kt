package lang.temper.frontend

/**
 * Allows exclusion between a module while advancing and other modules that might
 * need to read its top-levels.
 */
interface StageExclusion {
    fun <T> whileSynchronized(action: () -> T): T

    object NotExclusive : StageExclusion {
        override fun <T> whileSynchronized(action: () -> T): T = action()
    }
}
