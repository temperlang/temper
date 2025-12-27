package lang.temper.common

/**
 * May choose to intervene to format a value onto a [FormatSink].
 * May choose not to allowing [chaining][CustomValueFormatter.chain] value
 * formatters together from most-specific to least specific.
 */
interface CustomValueFormatter {
    /** @return true iff value was formatted.  Else, the caller is responsible */
    fun maybeFormat(value: Any?, sink: FormatSink): Boolean

    /** A formatter that knows how to format no values. */
    object Nope : CustomValueFormatter {
        override fun maybeFormat(value: Any?, sink: FormatSink): Boolean = false

        override fun toString(): String = "Nope"
    }

    companion object {
        /** A formatter that lets [a] try, and if it doesn't, falls back to [b]. */
        fun chain(a: CustomValueFormatter?, b: CustomValueFormatter?) = when {
            a == null || a is Nope -> b ?: Nope
            b == null || b is Nope -> a
            else -> CustomValueFormatterChain(a, b)
        }
    }
}

private data class CustomValueFormatterChain(
    val a: CustomValueFormatter,
    val b: CustomValueFormatter,
) : CustomValueFormatter {
    override fun maybeFormat(value: Any?, sink: FormatSink): Boolean =
        a.maybeFormat(value, sink) || b.maybeFormat(value, sink)
}
