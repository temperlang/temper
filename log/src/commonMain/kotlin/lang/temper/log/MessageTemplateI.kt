package lang.temper.log

import lang.temper.common.sprintf

/**
 * Kinds of log messages.
 */
interface MessageTemplateI {
    /** A programmatic identifier. */
    val name: String

    /** Allows producing a human-readable string.  Form is a la `sprintf`. */
    val formatString: String

    /**
     * True for a kind of message that is expected to be logged several times
     * at the same position, each time about something different, so that a
     * sink deduplicating by position, as with [PositionFilter], must not drop
     * the repeats.  For example, one test failure per failing test, all at
     * [unknownPos] because a test result has no source position.
     */
    val repeatsAtSamePosition: Boolean get() = false

    fun format(values: List<Any>) = sprintf(formatString, values)
}
