package lang.temper.common

fun prefixLinesWith(whole: String, prefix: String): String {
    if (whole.isEmpty()) { return "" }
    return buildString {
        var i = 0 // Index into whole <= n
        val n = whole.length
        var emittedTo = 0 // Index into whole <= i, written to this
        while (i < n) {
            val c = whole[i]
            i += 1

            if (c == '\r' || c == '\n') {
                if (c == '\r' && whole.getOrNull(i) == '\n') {
                    i += 1
                }
                append(prefix)
                append(whole, emittedTo, i)
                emittedTo = i
            }
        }
        if (emittedTo < n) { // Did not end in a newline
            append(prefix)
            append(whole, emittedTo, n)
        }
    }
}
