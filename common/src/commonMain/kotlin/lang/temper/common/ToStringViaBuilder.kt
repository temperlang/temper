package lang.temper.common

inline fun toStringViaBuilder(
    capacity: Int,
    f: (out: StringBuilder) -> Unit,
): String {
    val out = StringBuilder(capacity)
    f(out)
    return out.toString()
}

inline fun toStringViaBuilder(
    f: (out: StringBuilder) -> Unit,
): String {
    val out = StringBuilder()
    f(out)
    return out.toString()
}
