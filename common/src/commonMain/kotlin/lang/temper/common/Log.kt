package lang.temper.common

/** Constants related to logging */
@Suppress("MagicNumber") // ordinals
object Log {
    /** A log [Level] or an end-point that may be used to configure filtering in a log channel. */
    sealed interface LevelFilter : Comparable<LevelFilter> {
        val ordinal: Int
        val name: String

        override fun compareTo(other: LevelFilter): Int = this.ordinal.compareTo(other.ordinal)
    }

    /** A priority level which may be associated with a log message. */
    sealed class Level(
        override val ordinal: Int,
        override val name: String,
    ) : LevelFilter {
        override fun toString() = name
    }

    /** May be applied to a logger to show all messages. */
    object All : LevelFilter {
        override val name: String = "all"
        override val ordinal: Int = Int.MIN_VALUE
        override fun toString() = name
    }

    object Fine : Level(1, "fine")
    object Info : Level(2, "info")
    object Warn : Level(3, "warn")

    /**
     * A very-low frequency log level for informative messages
     * that summarize other lower level messages.
     * For example, "99 out of 100 tests passed"
     */
    object Summary : Level(4, "summary")
    object Error : Level(5, "error")
    object Fatal : Level(6, "fatal")

    /** May be applied to a logger to show no messages. */
    object None : LevelFilter {
        override val name: String = "none"
        override val ordinal: Int = Int.MAX_VALUE
        override fun toString() = name
    }

    val levels = listOf(Fine, Info, Summary, Warn, Error, Fatal)
    val levelFilters = listOf(All) + levels + listOf(None)
}
