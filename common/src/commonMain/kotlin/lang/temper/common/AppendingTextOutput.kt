package lang.temper.common

/** Appends text to an [Appendable] buffer. */
class AppendingTextOutput(
    private val out: Appendable,
    private val flushAtEndOfLine: Boolean = out is Flushable,
    override val isTtyLike: Boolean = DEFAULT_IS_TTY_LIKE,
) : TextOutput() {
    override fun emitLineChunk(text: CharSequence) {
        out.append(text)
    }

    override fun endLine() {
        out.append('\n')
        if (flushAtEndOfLine) {
            flush()
        }
    }

    override fun flush() {
        (out as? Flushable)?.flush()
    }

    companion object {
        const val DEFAULT_IS_TTY_LIKE = false
    }
}

fun toStringViaTextOutput(
    isTtyLike: Boolean = AppendingTextOutput.DEFAULT_IS_TTY_LIKE,
    f: (out: TextOutput) -> Unit,
): String = toStringViaBuilder {
    f(AppendingTextOutput(it, isTtyLike = isTtyLike))
}
