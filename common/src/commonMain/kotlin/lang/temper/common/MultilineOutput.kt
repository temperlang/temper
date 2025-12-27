package lang.temper.common

/**
 * An output that can be represented as multiple strings without right padding or line terminator
 * characters.
 */
interface MultilineOutput {
    fun addOutputLines(ls: MutableList<String>)

    object Empty : MultilineOutput {
        override fun addOutputLines(ls: MutableList<String>) {
            // Nothing to add
        }
    }

    companion object {
        fun of(text: String?): MultilineOutput {
            val n = text?.length

            return if (n == 0 || n == null) {
                Empty
            } else {
                val lines = mutableListOf<String>()
                var pos = 0
                var i = 0
                while (i < n) {
                    val c = text[i]
                    i += 1
                    if (c == '\n' || c == '\r') {
                        lines.add(text.substring(pos, i - 1))
                        if (c == '\r' && i < n && text[i] == '\n') {
                            i += 1
                        }
                        pos = i
                    }
                }
                if (pos != n) {
                    lines.add(text.substring(pos, n))
                }
                SplitLines(lines.toList())
            }
        }
    }
}

private data class SplitLines(val lines: List<String>) : MultilineOutput {
    override fun addOutputLines(ls: MutableList<String>) {
        ls.addAll(lines)
    }
}
