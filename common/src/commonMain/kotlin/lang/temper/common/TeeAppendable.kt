package lang.temper.common

/**
 * Communicates output to multiple, independent channels.
 */
class TeeAppendable(
    private val underlying: List<Appendable>,
) : Appendable, Flushable {
    override fun append(value: Char): Appendable {
        underlying.forEach {
            it.append(value)
        }
        return this
    }

    override fun append(value: CharSequence?): Appendable {
        val chars = value!!
        underlying.forEach {
            it.append(chars)
        }
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        val chars = value!!
        check(startIndex in 0..endIndex && endIndex <= chars.length)
        underlying.forEach {
            it.append(chars)
        }
        return this
    }

    override fun flush() {
        underlying.forEach {
            (it as? Flushable)?.flush()
        }
    }
}
