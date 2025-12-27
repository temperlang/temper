package lang.temper.log

/**
 * An object shared by multiple code locations that provides access to per-location state.
 *
 * When processing, and logging errors for, code from one location, we might find ourselves dealing
 * with things derived from other code locations.  This allows a logger, tightly associated with
 * one location to, for example, retrieve enough information to show a snippet of code associated
 * with a [Position] in another [CodeLocation].
 *
 * We retrieve values for [CodeLocationKey]s from this shared object, and not directly from
 * [CodeLocation]s so that:
 * - [CodeLocation]s may be data classes that serve as keys and so created multiply by different
 *   parts of the system and compared for equality, without having to refer to the same module
 *   objects that contain answers.
 * - [CodeLocation]s can be created long before and far away from the module objects that contain
 *   the answers.
 */
interface SharedLocationContext {
    /**
     * Get some state associated with the location.
     * For example, the source code so a [LogSink] can provide a snippet of a problem in context.
     * This is best-effort, and may return null if not available.
     */
    operator fun <T : Any> get(loc: CodeLocation, v: CodeLocationKey<T>): T?
}

/**
 * Something which may be associated with a code location.
 *
 * @see SharedLocationContext.get
 */
@Suppress("unused") // T is used in the return type of SharedLocationContext.get
interface CodeLocationKey<T : Any> {
    fun cast(x: Any): T

    object FilePositionsKey : CodeLocationKey<FilePositions> {
        override fun cast(x: Any) = x as FilePositions
    }

    object SourceCodeKey : CodeLocationKey<CharSequence> {
        override fun cast(x: Any) = x as CharSequence
    }
}
