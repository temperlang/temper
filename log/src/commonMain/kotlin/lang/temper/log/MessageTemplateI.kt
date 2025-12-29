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

    fun format(values: List<Any>) = sprintf(formatString, values)
}
