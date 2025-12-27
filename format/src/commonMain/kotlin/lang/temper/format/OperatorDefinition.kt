package lang.temper.format

/**
 * Used to decide whether to insert parentheses around an element when formatting it in the
 * context of its parent's template.
 */
interface OperatorDefinition {
    fun canNest(inner: OperatorDefinition, childIndex: Int): Boolean

    /**
     * True if this is a comma operation which is needed to parenthesize children that appear
     * inside a comma separated list as in the wildcard format string placeholder: `{{*:, }}`.
     */
    val isCommaOperator: Boolean get() = false
}
